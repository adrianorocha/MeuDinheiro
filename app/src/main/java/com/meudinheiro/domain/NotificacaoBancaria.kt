package com.meudinheiro.domain

import com.google.gson.Gson
import java.security.MessageDigest
import java.text.Normalizer
import java.util.TimeZone

/**
 * R64 — captura de notificações do banco. TUDO aqui é puro (sem Android): filtro de pacotes, parser, deduplicação,
 * expiração e serialização da fila. O texto bruto da notificação só existe dentro de [NotificacaoBancaria.interpretar]
 * (em memória) e NUNCA é devolvido nem guardado: só os campos extraídos.
 */
object NotificacaoBancaria {

    enum class Tipo(val rotulo: String) {
        PIX_ENVIADO("Pix enviado"), PIX_RECEBIDO("Pix recebido"),
        COMPRA_DEBITO("Compra no débito"), COMPRA_CREDITO("Compra no crédito"),
        SAQUE("Saque"), BOLETO_PAGO("Boleto pago"), TRANSFERENCIA("Transferência"), OUTRO("Outro")
    }

    enum class Confianca { ALTA, MEDIA, BAIXA }

    /** Campos extraídos (nada do texto original). [entrada] = dinheiro entrando (vira receita). */
    data class Sugestao(
        val tipo: Tipo,
        val valorCentavos: Long,
        val descricao: String,
        val finalCartao: String? = null,
        val confianca: Confianca,
        val entrada: Boolean = false
    )

    // ------------------------------------------------------------------ pacotes permitidos

    /** Apps de banco/carteira conhecidos. Entradas terminadas em `.*` casam o prefixo (ex.: `br.com.sicoobnet.*`). */
    val PACOTES_PADRAO: List<String> = listOf(
        "com.nu.production", "com.itau", "com.bradesco", "com.santander.app", "br.com.bb.android",
        "br.com.gabba.Caixa", "br.com.intermedium", "com.c6bank.app", "com.picpay", "com.mercadopago.wallet",
        "br.com.sicoobnet.*"
    )

    /**
     * Filtro de privacidade, chamado ANTES de ler qualquer coisa da notificação (só o `packageName` é consultado).
     * Comparação exata (sem diferenciar maiúsculas) ou por prefixo quando a entrada termina em `.*`.
     */
    fun pacoteAceito(pacote: String?, permitidos: Collection<String>): Boolean {
        val p = pacote?.trim()?.lowercase().orEmpty()
        if (p.isEmpty()) return false
        return permitidos.any { e ->
            val x = e.trim().lowercase()
            when {
                x.isEmpty() -> false
                x.endsWith(".*") -> x.length > 2 && p.startsWith(x.dropLast(1)) // "br.x.*" → prefixo "br.x."
                else -> p == x
            }
        }
    }

    /** Nome de pacote válido para a lista editável (letras/números/_ separados por ponto; `.*` opcional no fim). */
    fun pacoteValido(texto: String): Boolean =
        texto.trim().length <= 120 && Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+(\\.\\*)?$").matches(texto.trim())

    /** Palavra-chave do banco a partir do pacote, para casar com o nome da conta ("Nubank", "Itaú"…). */
    fun bancoDoPacote(pacote: String): String? {
        val p = pacote.lowercase()
        return when {
            p.startsWith("com.nu.") -> "nu"
            p.startsWith("com.itau") -> "ita"
            p.startsWith("com.bradesco") -> "bradesco"
            p.startsWith("com.santander") -> "santander"
            p.startsWith("br.com.bb.") -> "banco do brasil"
            p.contains("caixa") -> "caixa"
            p.startsWith("br.com.intermedium") -> "inter"
            p.startsWith("com.c6bank") -> "c6"
            p.startsWith("com.picpay") -> "picpay"
            p.startsWith("com.mercadopago") -> "mercado"
            p.startsWith("br.com.sicoob") -> "sicoob"
            else -> null
        }
    }

    // ------------------------------------------------------------------ parser

    private const val MAX_DESC = 60

    private fun dobrar(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFC)
        val sb = StringBuilder(n.length)
        for (c in n) {
            val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            sb.append(if (d.isNotEmpty()) d[0].lowercaseChar() else c.lowercaseChar())
        }
        return sb.toString() // mesmo comprimento do NFC: índices servem no texto original
    }

    private val REGEX_VALOR = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:,\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val REGEX_FINAL = listOf(
        Regex("""(?:final|terminad[oa]\s+em|cartao\s+(?:virtual\s+)?)\s*[:\-]?\s*[•*x]*\s*(\d{4})\b"""),
        Regex("""[•*]{2,}\s*(\d{4})\b""")
    )
    private val CORTES_DESC = Regex(
        """\s+(?:no|na|com|via|as|às|em\s+\d|dia|cpf|cnpj|agencia|ag\.|final|cartao|pelo|pela|usando|credito|debito|aprovad|foi|ja|e\s+seu)\b|\s[-–|•]\s|[,;:()]|\.\s|\.$"""
    )

    private val PROMO = listOf(
        "oferta", "promo", "aproveite", "cashback", "cupom", "desconto", "sorteio", "pre-aprovad", "preaprovad", "simul",
        "emprestimo", "financiamento", "invest", "agendad", "programad", "solicit", "cobranca", "reconhece", "voce conhece",
        "negad", "recusad", "nao aprovad", "nao foi possivel", "nao autorizad", "falha", "cancelad", "estorn", "reembols",
        "codigo", "senha", "token", "seguro", "convite", "indique", "ganhe", "parcel", "lembrete", "venceu", "pendente"
    )
    private val FRASE_DESCARTADA = listOf("saldo", "limite", "fatura", "vencimento", "vence ", "vence.", "disponivel", "pontos", "melhor dia")

    /** Divide em frases sem quebrar "R$ 1.234,56" (o ponto só separa quando é seguido de espaço/fim). */
    private fun frases(s: String): List<String> =
        s.split(Regex("""[\n\r]+|(?<=[.!?])\s+""")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun centavos(txt: String): Long? {
        val limpo = txt.replace(".", "")
        val partes = limpo.split(',')
        val reais = partes[0].toLongOrNull() ?: return null
        val cent = if (partes.size > 1) partes[1].padEnd(2, '0').take(2).toLongOrNull() ?: return null else 0L
        if (reais > 100_000_000L) return null
        return reais * 100 + cent
    }

    private fun limparDescricao(bruto: String): String? {
        var d = bruto.trim().trimStart(':', '-', '–', ' ')
        val corte = CORTES_DESC.find(" " + dobrar(d))?.let { it.range.first - 1 } // " $d": o índice descontado fica no texto original
        if (corte != null && corte >= 0) d = d.substring(0, corte.coerceAtMost(d.length))
        d = d.trim().trim('.', ',', ';', ':', '-', '!', ' ')
        d = d.replace(Regex("\\s+"), " ")
        if (d.length < 2) return null
        if (dobrar(d) in setOf("voce", "sua conta", "conta", "seu cartao", "cartao", "debito", "credito")) return null
        return d.take(MAX_DESC)
    }

    /** Interpreta uma notificação já vinda de um pacote permitido. Devolve null se não for um lançamento claro. */
    fun interpretar(pacote: String, titulo: String?, texto: String?, horarioMs: Long): Sugestao? {
        val bruto = listOfNotNull(titulo, texto).joinToString("\n").trim()
        if (bruto.isEmpty() || bruto.length > 1500) return null
        val dob = dobrar(bruto)
        if (PROMO.any { dob.contains(it) }) return null

        // Mantém só as frases que podem ser a transação (saldo/limite/fatura/vencimento viram ruído).
        val originais = frases(Normalizer.normalize(bruto, Normalizer.Form.NFC))
        val uteis = originais.filter { f -> val d = dobrar(f); FRASE_DESCARTADA.none { d.contains(it) } }
        if (uteis.isEmpty()) return null
        val corpo = uteis.joinToString(" ")
        val f = dobrar(corpo)

        val valores = REGEX_VALOR.findAll(corpo).mapNotNull { centavos(it.groupValues[1]) }.toList()
        if (valores.isEmpty()) return null
        if (valores.distinct().size > 1) return null // dois valores diferentes: ambíguo
        val valor = valores.first()
        if (valor <= 0L) return null
        val fim = REGEX_VALOR.find(corpo)!!.range.last + 1
        val inicio = REGEX_VALOR.find(corpo)!!.range.first
        val antes = corpo.substring(0, inicio)
        val depois = corpo.substring(fim)

        val final = REGEX_FINAL.firstNotNullOfOrNull { it.find(f)?.groupValues?.get(1) }

        fun depoisDe(vararg palavras: String): String? {
            val alt = palavras.joinToString("|")
            val re = Regex("""(?:^|\s)(?:$alt)\s+(.+)""", RegexOption.IGNORE_CASE)
            return re.find(depois)?.groupValues?.get(1)?.let { limparDescricao(it) }
        }
        fun antesDe(vararg palavras: String): String? {
            val alt = palavras.joinToString("|")
            val re = Regex("""\b(?:$alt)\s+(.+?)\s*[:\-–]?\s*$""", RegexOption.IGNORE_CASE)
            return re.find(antes)?.groupValues?.get(1)?.let { limparDescricao(it) }
        }

        val recebeu = Regex("""\b(recebeu|recebido|recebida|recebemos|entrou|te enviou|enviou para voce|depositou)\b""").containsMatchIn(f)
        val enviou = Regex("""\b(enviado|enviada|enviou|voce enviou|transferiu|pagamento pix|pix pago|pagou|paguei|pago)\b""").containsMatchIn(f) &&
            !Regex("""\b(te enviou|enviou para voce)\b""").containsMatchIn(f)

        // PIX
        if (f.contains("pix")) {
            if (recebeu == enviou) return null // nenhum ou os dois sentidos
            return if (recebeu) {
                val nome = depoisDe("de") ?: antesDe("de")
                Sugestao(Tipo.PIX_RECEBIDO, valor, nome ?: "Pix recebido", final, if (nome != null) Confianca.ALTA else Confianca.MEDIA, entrada = true)
            } else {
                val nome = depoisDe("para", "a", "ao") ?: antesDe("para")
                Sugestao(Tipo.PIX_ENVIADO, valor, nome ?: "Pix enviado", final, if (nome != null) Confianca.ALTA else Confianca.MEDIA)
            }
        }
        // SAQUE
        if (Regex("""\b(saque|sacou)\b""").containsMatchIn(f)) {
            return Sugestao(Tipo.SAQUE, valor, "Saque", final, Confianca.ALTA)
        }
        // BOLETO (só quando já foi pago)
        if (f.contains("boleto")) {
            val pago = Regex("""\b(pago|paga|pagamento (?:efetuado|realizado|confirmado|aprovado))\b""").containsMatchIn(f)
            if (!pago) return null
            return Sugestao(Tipo.BOLETO_PAGO, valor, "Boleto pago", final, Confianca.ALTA)
        }
        // TRANSFERÊNCIA / TED / DOC
        if (Regex("""\b(transferencia|ted|doc)\b""").containsMatchIn(f)) {
            if (recebeu == enviou && !Regex("""\b(realizada|efetuada|concluida|enviada|recebida)\b""").containsMatchIn(f)) return null
            val entrada = recebeu || f.contains("recebida")
            val nome = if (entrada) depoisDe("de") else depoisDe("para", "a", "ao")
            return Sugestao(Tipo.TRANSFERENCIA, valor, nome ?: "Transferência", final, if (nome != null) Confianca.ALTA else Confianca.MEDIA, entrada = entrada)
        }
        // COMPRA
        if (Regex("""\bcompra\b""").containsMatchIn(f)) {
            val debito = f.contains("debito")
            val credito = f.contains("credito")
            if (debito && credito) return null
            val tipo = if (debito) Tipo.COMPRA_DEBITO else Tipo.COMPRA_CREDITO
            val local = depoisDe("em", "no estabelecimento", "local") ?: antesDe("em")
            val conf = when {
                debito || credito -> if (local != null) Confianca.ALTA else Confianca.MEDIA
                final != null -> if (local != null) Confianca.ALTA else Confianca.MEDIA
                else -> Confianca.BAIXA // "Compra aprovada" sem modalidade nem final: assume crédito, pede revisão
            }
            return Sugestao(tipo, valor, local ?: "Compra", final, conf)
        }
        // Recebimentos genéricos (depósito/crédito em conta)
        if (Regex("""\b(deposito|credito em conta|salario)\b""").containsMatchIn(f) && recebeu) {
            return Sugestao(Tipo.OUTRO, valor, "Depósito recebido", final, Confianca.BAIXA, entrada = true)
        }
        // Pagamentos genéricos confirmados
        if (Regex("""\bpagamento (efetuado|realizado|confirmado|aprovado)\b|\bdebito em conta\b""").containsMatchIn(f)) {
            val local = depoisDe("em", "para", "a")
            return Sugestao(Tipo.OUTRO, valor, local ?: "Pagamento", final, Confianca.BAIXA)
        }
        return null
    }

    // ------------------------------------------------------------------ destino (conta/cartão)

    data class CartaoRef(val id: Int, val finalCartao: String, val numeroConta: String?)
    data class ContaRef(val numero: String, val banco: String)
    data class Destino(val cartaoId: Int?, val conta: String?)

    /**
     * Casa a sugestão com um cartão pelo final (só se for único) ou com uma conta pelo banco do app (só se for única).
     * Receita e saque/pix entram/saem de CONTA; compras preferem o cartão. Sem casamento claro devolve null
     * (a tela de lançamento escolhe a conta padrão/pergunta).
     */
    fun resolverDestino(s: Sugestao, pacote: String, cartoes: List<CartaoRef>, contas: List<ContaRef>): Destino? {
        val ehCompra = s.tipo == Tipo.COMPRA_CREDITO || s.tipo == Tipo.COMPRA_DEBITO
        if (ehCompra && !s.finalCartao.isNullOrBlank()) {
            val c = cartoes.filter { it.finalCartao == s.finalCartao }
            if (c.size == 1) return Destino(c[0].id, c[0].numeroConta)
        }
        val kw = bancoDoPacote(pacote) ?: return null
        val folded = contas.filter { dobrar(it.banco).contains(kw) }
        return if (folded.size == 1) Destino(null, folded[0].numero) else null
    }

    // ------------------------------------------------------------------ deduplicação

    const val JANELA_DUPLICADA_MS = 10L * 60_000L
    const val VALIDADE_MS = 7L * 24 * 3_600_000L
    const val LIMITE_FILA = 200
    private const val DIA_MS = 86_400_000L

    fun normalizarDescricao(d: String): String = dobrar(d).replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Mesma sugestão (valor + descrição + tipo) dentro de [JANELA_DUPLICADA_MS] de outra já registrada. */
    fun duplicadaRecente(nova: Sugestao, horarioMs: Long, registradas: List<SugestaoGuardada>): Boolean =
        registradas.any { r ->
            r.valorCentavos == nova.valorCentavos && r.tipo == nova.tipo &&
                normalizarDescricao(r.descricao) == normalizarDescricao(nova.descricao) &&
                kotlin.math.abs(r.horarioMs - horarioMs) <= JANELA_DUPLICADA_MS
        }

    /** Lançamento recente do app, reduzido ao que a comparação precisa. [conta] = número da conta (nulo se desconhecido). */
    data class LancamentoRecente(val valorCentavos: Long, val entrada: Boolean, val dataMs: Long, val conta: String?, val cartaoId: Int? = null)

    private fun dia(ms: Long, tz: TimeZone) = Math.floorDiv(ms + tz.getOffset(ms), DIA_MS)

    /**
     * Já existe lançamento com mesmo valor e sentido (entrada/saída) na mesma conta (ou cartão) a ±1 dia (dias do
     * calendário local)? Se o destino da sugestão é desconhecido, compara com qualquer conta.
     */
    fun jaLancado(
        s: Sugestao, horarioMs: Long, destino: Destino?, lancamentos: List<LancamentoRecente>,
        tz: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val d0 = dia(horarioMs, tz)
        return lancamentos.any { l ->
            l.valorCentavos == s.valorCentavos && l.entrada == s.entrada &&
                kotlin.math.abs(dia(l.dataMs, tz) - d0) <= 1 &&
                when {
                    destino == null -> true
                    destino.cartaoId != null -> l.cartaoId == destino.cartaoId || (l.cartaoId == null && l.conta != null && l.conta == destino.conta)
                    destino.conta != null -> l.conta == destino.conta
                    else -> true
                }
        }
    }

    /** Chave de uma notificação (pacote + id + texto) para ignorar atualizações repetidas. Só o hash é guardado (em memória). */
    fun chaveNotificacao(pacote: String, id: Int, titulo: String?, texto: String?): String {
        val md = MessageDigest.getInstance("SHA-256")
        val h = md.digest("$pacote|$id|${titulo.orEmpty()}|${texto.orEmpty()}".toByteArray(Charsets.UTF_8))
        return h.take(10).joinToString("") { "%02x".format(it) }
    }

    /** Memória curta das últimas chaves vistas (não persiste; o hash não é gravado em disco). */
    class ChavesVistas(private val max: Int = 100) {
        private val mapa = LinkedHashMap<String, Long>()
        /** true se é nova (e registra); false se a mesma notificação já foi vista. */
        @Synchronized
        fun registrar(chave: String, agoraMs: Long): Boolean {
            if (mapa.containsKey(chave)) return false
            mapa[chave] = agoraMs
            while (mapa.size > max) mapa.remove(mapa.keys.first())
            return true
        }
    }

    // ------------------------------------------------------------------ fila (persistência)

    enum class Estado { PENDENTE, ENCAMINHADA, IGNORADA }

    /** Item da fila: só campos extraídos + metadados. Nada de texto bruto. */
    data class SugestaoGuardada(
        val id: String,
        val tipo: Tipo,
        val valorCentavos: Long,
        val descricao: String,
        val finalCartao: String? = null,
        val confianca: Confianca = Confianca.MEDIA,
        val entrada: Boolean = false,
        val pacote: String,
        val horarioMs: Long,
        val criadaEm: Long,
        val estado: Estado = Estado.PENDENTE,
        /** Destino resolvido na captura (cartão pelo final, conta pelo banco); nulo = a tela de lançamento pergunta. */
        val cartaoId: Int? = null,
        val conta: String? = null
    ) {
        fun comoSugestao() = Sugestao(tipo, valorCentavos, descricao, finalCartao, confianca, entrada)
    }

    /** Descrição que vai para o lançamento (a que o usuário veria no extrato). */
    fun tituloLancamento(g: SugestaoGuardada): String = when (g.tipo) {
        Tipo.PIX_RECEBIDO -> if (g.descricao == "Pix recebido") g.descricao else "Pix de ${g.descricao}"
        Tipo.PIX_ENVIADO -> if (g.descricao == "Pix enviado") g.descricao else "Pix para ${g.descricao}"
        else -> g.descricao
    }.take(MAX_DESC)

    /** Texto da notificação: "Lançar R$ 89,90 em PADARIA X?". */
    fun pergunta(g: SugestaoGuardada): String =
        if (g.entrada) "Lançar receita de ${FormatoCentavos.brl(g.valorCentavos)} (${tituloLancamento(g)})?"
        else "Lançar ${FormatoCentavos.brl(g.valorCentavos)} em ${tituloLancamento(g)}?"

    fun expirada(s: SugestaoGuardada, agoraMs: Long) = agoraMs - s.criadaEm >= VALIDADE_MS

    /** Remove expiradas (7 dias) e mantém as [LIMITE_FILA] mais recentes. */
    fun manter(lista: List<SugestaoGuardada>, agoraMs: Long): List<SugestaoGuardada> =
        lista.filter { !expirada(it, agoraMs) }.sortedByDescending { it.criadaEm }.take(LIMITE_FILA)

    private val gson = Gson()

    private class Arquivo(val versao: Int = 1, val itens: List<SugestaoGuardada> = emptyList())

    fun serializar(lista: List<SugestaoGuardada>): String = gson.toJson(Arquivo(1, lista))

    /** Tolerante: JSON inválido/vazio → lista vazia; itens inválidos (valor ≤ 0, campos ausentes) são descartados. */
    fun desserializar(json: String?): List<SugestaoGuardada> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val a = gson.fromJson(json, Arquivo::class.java) ?: return emptyList()
            @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
            ((a.itens ?: emptyList<SugestaoGuardada>()) as List<SugestaoGuardada?>).mapNotNull { it }
                .filter { s ->
                    runCatching {
                        s.id.isNotBlank() && s.valorCentavos > 0 && s.descricao != null && s.pacote != null &&
                            s.tipo != null && s.estado != null && s.confianca != null
                    }.getOrDefault(false)
                }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
