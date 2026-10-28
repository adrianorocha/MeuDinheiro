package com.meudinheiro.domain

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Calendar
import java.util.TimeZone

/**
 * R94 — Lançamentos via PIX. TUDO aqui é puro (sem Android, sem rede, sem log) e NUNCA lança exceção por entrada inválida:
 * devolve tipos de resultado explícitos.
 *
 * Convenção compartilhada com o portal (sem campo novo, só R56 tags + descrição):
 * - tag [TAG] ("PIX");
 * - descrição "Pix para <nome>" (DÉBITO = Pix enviado/pago) ou "Pix de <nome>" (CRÉDITO = Pix recebido);
 *   sem nome: "Pix enviado" / "Pix recebido".
 *
 * Privacidade: o texto lido (BR Code / comprovante) só existe em memória durante a leitura. A chave Pix completa NUNCA vai
 * para tags/descrição; só a versão mascarada ([mascararChave]) aparece no resumo. A URL do payload dinâmico (id 26>25) só é
 * exibida; o app nunca a consulta.
 */
object Pix {
    const val TAG = "PIX"
    const val MAX_DESCRICAO = 120
    private const val MAX_NOME = 40
    private const val DIA_MS = 86_400_000L

    enum class Direcao { ENVIADO, RECEBIDO }

    // ------------------------------------------------------------------ tags

    fun temTag(tags: List<String>?): Boolean = Tags.tem(tags, TAG)

    /** Acrescenta a tag PIX (primeira, nunca descartada pelo limite de 5). */
    fun comTag(tags: List<String>): List<String> = Tags.normalizar(listOf(TAG) + tags)

    fun semTag(tags: List<String>): List<String> = tags.filterNot { Tags.chave(it) == Tags.chave(TAG) }

    // ------------------------------------------------------------------ BR Code (EMV-MPM do Banco Central)

    data class BrCode(
        /** Valor em centavos (id 54); nulo em QR aberto ("digite o valor"). */
        val valorCentavos: Long?,
        /** Nome do beneficiário (id 59), já normalizado. */
        val beneficiario: String?,
        val cidade: String?,
        /** Chave Pix COMPLETA (26>01). Só em memória: use [chaveMascarada] para mostrar. */
        val chave: String?,
        /** Mensagem ao pagador (26>02). */
        val mensagem: String?,
        /** txid (62>05); "***" (sem txid) vira nulo. */
        val txid: String?,
        /** URL do payload dinâmico (26>25). Apenas informativa: NUNCA é buscada. */
        val urlPayload: String?
    ) {
        val dinamico: Boolean get() = urlPayload != null
        val chaveMascarada: String? get() = chave?.let { mascararChave(it) }
    }

    enum class MotivoBrCode(val mensagem: String) {
        VAZIO("Nada para ler."),
        NAO_E_BRCODE("Esse texto não parece um Pix copia e cola."),
        FORMATO_INVALIDO("O código Pix está incompleto ou corrompido."),
        CRC_INVALIDO("O código Pix não passou na verificação (CRC): copie de novo."),
        NAO_E_PIX("Esse código não é um Pix."),
        VALOR_INVALIDO("O valor do código Pix é inválido."),
        MOEDA_INVALIDA("O código Pix não é em reais."),
        MUITO_LONGO("O texto é grande demais para ser um código Pix.")
    }

    sealed interface ResultadoBrCode {
        data class Ok(val brCode: BrCode) : ResultadoBrCode
        data class Invalido(val motivo: MotivoBrCode) : ResultadoBrCode
    }

    /** CRC16/CCITT-FALSE (poly 0x1021, init 0xFFFF), 4 hex maiúsculos. */
    fun crc16(texto: String): String {
        var crc = 0xFFFF
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return "%04X".format(crc)
    }

    private data class Campo(val id: String, val valor: String, val inicioValor: Int)

    /** Lê campos TLV (id 2 dígitos, tamanho 2 dígitos). Em [parar63], para depois do campo 63. Nulo = formato inválido. */
    private fun lerTlv(s: String, base: Int = 0, parar63: Boolean = false): List<Campo>? {
        val out = ArrayList<Campo>()
        var i = 0
        while (i < s.length) {
            if (i + 4 > s.length) return null
            val id = s.substring(i, i + 2)
            val tam = s.substring(i + 2, i + 4)
            if (!id.all { it in '0'..'9' } || !tam.all { it in '0'..'9' }) return null
            val n = tam.toInt()
            if (i + 4 + n > s.length) return null
            out += Campo(id, s.substring(i + 4, i + 4 + n), base + i + 4)
            i += 4 + n
            if (parar63 && id == "63") return out
        }
        return out
    }

    private fun limparTexto(s: String?, max: Int): String? {
        val t = s?.filter { !it.isISOControl() }?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return t.takeIf { it.isNotEmpty() }?.take(max)
    }

    /**
     * Interpreta um Pix "copia e cola" / texto de QR Code. Tolerante a quebras de linha e a texto antes/depois do código;
     * rigoroso no CRC (obrigatório) e no formato. O campo 54 (valor) é opcional.
     */
    fun lerBrCode(bruto: String?): ResultadoBrCode {
        val limpo = bruto.orEmpty().replace(Regex("[\\r\\n\\t\\u00A0]+"), "").trim()
        if (limpo.isEmpty()) return ResultadoBrCode.Invalido(MotivoBrCode.VAZIO)
        if (limpo.length > 4_000) return ResultadoBrCode.Invalido(MotivoBrCode.MUITO_LONGO)
        val ini = limpo.indexOf("000201")
        if (ini < 0) return ResultadoBrCode.Invalido(MotivoBrCode.NAO_E_BRCODE)
        val cod = limpo.substring(ini)

        val campos = lerTlv(cod, parar63 = true) ?: return ResultadoBrCode.Invalido(MotivoBrCode.FORMATO_INVALIDO)
        val crcCampo = campos.lastOrNull()?.takeIf { it.id == "63" && it.valor.length == 4 }
            ?: return ResultadoBrCode.Invalido(MotivoBrCode.CRC_INVALIDO)
        val corpo = cod.substring(0, crcCampo.inicioValor) // inclui "6304"
        if (!crcCampo.valor.equals(crc16(corpo), ignoreCase = true)) return ResultadoBrCode.Invalido(MotivoBrCode.CRC_INVALIDO)

        fun top(id: String) = campos.firstOrNull { it.id == id }?.valor

        val conta = campos.firstOrNull { c ->
            c.id.toInt() in 26..51 && lerTlv(c.valor)?.any { it.id == "00" && it.valor.equals("br.gov.bcb.pix", ignoreCase = true) } == true
        } ?: return ResultadoBrCode.Invalido(MotivoBrCode.NAO_E_PIX)
        val sub = lerTlv(conta.valor).orEmpty()
        fun sub(id: String) = sub.firstOrNull { it.id == id }?.valor

        top("53")?.let { if (it != "986") return ResultadoBrCode.Invalido(MotivoBrCode.MOEDA_INVALIDA) }

        var valor: Long? = null
        top("54")?.let { v ->
            if (!Regex("^\\d{1,10}(\\.\\d{1,2})?$").matches(v)) return ResultadoBrCode.Invalido(MotivoBrCode.VALOR_INVALIDO)
            val c = BigDecimal(v).movePointRight(2).toLong()
            valor = c.takeIf { it > 0 }
        }

        val adicional = top("62")?.let { lerTlv(it) }.orEmpty()
        val txid = adicional.firstOrNull { it.id == "05" }?.valor?.trim()?.takeIf { it.isNotEmpty() && it != "***" }

        return ResultadoBrCode.Ok(
            BrCode(
                valorCentavos = valor,
                beneficiario = normalizarContraparte(top("59")),
                cidade = limparTexto(top("60"), 30),
                chave = sub("01")?.trim()?.takeIf { it.isNotEmpty() }?.take(100),
                mensagem = limparTexto(sub("02"), 72),
                txid = limparTexto(txid, 35),
                urlPayload = limparTexto(sub("25"), 200)
            )
        )
    }

    // ------------------------------------------------------------------ chave mascarada

    private const val P = "•"

    /** Mascara a chave Pix para exibir (e-mail, CPF, CNPJ, telefone, chave aleatória). Nunca devolve a chave inteira. */
    fun mascararChave(chave: String): String {
        val c = chave.trim()
        if (c.isEmpty()) return ""
        return when {
            c.contains('@') -> {
                val (u, d) = c.substringBefore('@') to c.substringAfter('@')
                (u.take(1) + P.repeat(3)) + "@" + d
            }
            c.length == 11 && c.all { it in '0'..'9' } -> "$P$P$P.$P$P$P.$P$P$P-" + c.takeLast(2)
            c.length == 14 && Regex("^[A-Za-z0-9]{12}[0-9]{2}$").matches(c) -> "$P$P.$P$P$P.$P$P$P/$P$P$P$P-" + c.takeLast(2)
            c.startsWith("+") && c.drop(1).all { it in '0'..'9' } && c.length >= 11 -> c.take(3) + " " + P.repeat(5) + c.takeLast(4)
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(c) ->
                "$P$P$P$P$P$P$P$P-$P$P$P$P-$P$P$P$P-$P$P$P$P-" + P.repeat(8) + c.takeLast(4)
            c.length <= 4 -> P.repeat(c.length)
            else -> P.repeat((c.length - 4).coerceAtMost(8)) + c.takeLast(4)
        }
    }

    // ------------------------------------------------------------------ contraparte (nome)

    private val PARTICULAS = setOf("de", "da", "do", "das", "dos", "e", "di", "du", "van", "von", "del")
    private val PREFIXO_ROTULO = Regex("^(?:nome|favorecido|benefici[aá]rio|destinat[aá]rio|recebedor|pagador|remetente|origem|destino)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)
    private val CORTE_NOME = Regex(
        "\\s+(?:cpf|cnpj|chave|ag[eê]ncia|ag\\.|conta|institui[cç][aã]o|banco|ispb|valor|r\\$|em\\s+\\d|[àa]s\\s+\\d)\\b|\\s[-–|•;]\\s|[|•;]|R\\$|\\*{2,}|\\d",
        RegexOption.IGNORE_CASE
    )
    private val CAUDA = Regex("\\s+(?:de|da|do|no|na|no valor|o valor|valor|em|para|a|ao|pix)$", RegexOption.IGNORE_CASE)

    /**
     * Nome de beneficiário/pagador pronto para a descrição: sem rótulos ("Nome:"), sem CPF/CNPJ/mascaras, espaços
     * colapsados, em "Título" quando veio todo em maiúsculas (padrão do BR Code). Nulo se não sobrar nome.
     */
    fun normalizarContraparte(bruto: String?): String? {
        var t = Normalizer.normalize(bruto.orEmpty(), Normalizer.Form.NFC).filter { !it.isISOControl() }.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        t = t.replace(PREFIXO_ROTULO, "")
        CORTE_NOME.find(t)?.let { t = t.substring(0, it.range.first) }
        t = t.replace(Regex("[^\\p{L}\\s'.&-]"), " ").replace(Regex("\\s+"), " ").trim(' ', '.', '-', ',', '\'')
        var ant: String
        do { ant = t; t = t.replace(CAUDA, "").trim() } while (t != ant)
        if (t.count { it.isLetter() } < 2) return null
        if (t.none { it.isLowerCase() }) {
            t = t.lowercase().split(' ').filterNot { it.isEmpty() }.mapIndexed { i, p ->
                if (i > 0 && p in PARTICULAS) p else p.split('-').joinToString("-") { s -> s.split('\'').joinToString("'") { x -> x.replaceFirstChar { c -> c.uppercaseChar() } } }
            }.joinToString(" ")
        }
        return t.take(MAX_NOME).trim()
    }

    /**
     * Últimos 8 caracteres alfanuméricos do identificador (E2E ou txid); nulo se curto demais (< 8). Mesma regra do
     * portal (R93), que guarda só este sufixo na descrição.
     */
    fun idCurto(id: String?): String? {
        val limpo = id.orEmpty().filter { it.isLetterOrDigit() }
        return if (limpo.length >= 8) limpo.takeLast(8) else null
    }

    private val REGEX_ID_DESCRICAO = Regex("\\s*\\(ID ([A-Za-z0-9]{6,12})\\)\\s*$")

    /** Sufixo "(ID xxxxxxxx)" de uma descrição na convenção; nulo se não houver. */
    fun idDaDescricao(descricao: String): String? = REGEX_ID_DESCRICAO.find(descricao)?.groupValues?.get(1)

    private fun semId(descricao: String): String = descricao.replace(REGEX_ID_DESCRICAO, "").trim()

    /**
     * Descrição na convenção compartilhada com o portal (R93): "Pix para X" (enviado) / "Pix de X" (recebido); sem nome,
     * "Pix enviado" / "Pix recebido". Com identificador ([id] = E2E ou txid) termina em " (ID xxxxxxxx)" (8 últimos
     * caracteres; o ID completo nunca é gravado).
     */
    fun descricao(direcao: Direcao, contraparte: String?, id: String? = null): String {
        val nome = normalizarContraparte(contraparte)
        val base = when (direcao) {
            Direcao.ENVIADO -> if (nome == null) "Pix enviado" else "Pix para $nome"
            Direcao.RECEBIDO -> if (nome == null) "Pix recebido" else "Pix de $nome"
        }
        return base + (idCurto(id)?.let { " (ID $it)" } ?: "")
    }

    // ------------------------------------------------------------------ E2E e comprovante

    private val REGEX_E2E = Regex("(?<![A-Za-z0-9])E\\d{8}[A-Za-z0-9]{23}(?![A-Za-z0-9])")

    /** ID fim a fim (E2E) do Pix: "E" + 31 alfanuméricos (ISPB 8 dígitos + data/hora UTC + sequência). Nulo se não houver. */
    fun extrairE2e(texto: String?): String? = texto?.let { REGEX_E2E.find(it)?.value }

    /** E2E parcialmente oculto para exibir: E + 8 primeiros + … + 4 últimos. */
    fun e2eResumido(e2e: String): String = if (e2e.length < 14) e2e else e2e.take(9) + "…" + e2e.takeLast(4)

    data class ComprovantePix(
        /** Direção inferida pelas palavras; nula quando não dá para dizer. */
        val direcao: Direcao?,
        val valorCentavos: Long?,
        val contraparte: String?,
        val dataMs: Long?,
        val e2e: String?
    )

    enum class MotivoComprovante(val mensagem: String) {
        VAZIO("Nada para ler."),
        MUITO_LONGO("O texto é grande demais para ser um comprovante."),
        NADA_RECONHECIDO("Não encontrei valor nem ID de Pix nesse texto.")
    }

    sealed interface ResultadoComprovante {
        data class Ok(val comprovante: ComprovantePix) : ResultadoComprovante
        data class Invalido(val motivo: MotivoComprovante) : ResultadoComprovante
    }

    private fun dobrar(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in Normalizer.normalize(s, Normalizer.Form.NFC)) {
            val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            sb.append(if (d.isNotEmpty()) d[0].lowercaseChar() else c.lowercaseChar())
        }
        return sb.toString() // mesmo comprimento do NFC: índices servem no texto original
    }

    private val REGEX_VALOR = Regex("R\\$\\s*(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val LINHA_RUIDO_VALOR = Regex("\\b(saldo|tarifa|limite|taxa|juros|desconto|iof)\\b")

    /** "1.234,56", "10,5", "10.50", "1234" → centavos; nulo se inválido/≤0/exagerado. */
    fun parseValorCentavos(txt: String): Long? {
        val t = txt.trim()
        val norm = when {
            t.contains(',') -> t.replace(".", "").replace(',', '.')
            Regex("^\\d{1,3}(\\.\\d{3})+$").matches(t) -> t.replace(".", "")
            else -> t
        }
        if (!Regex("^\\d{1,10}(\\.\\d{1,2})?$").matches(norm)) return null
        val c = BigDecimal(norm).movePointRight(2).toLong()
        return c.takeIf { it > 0 }
    }

    private fun valorDoComprovante(linhas: List<String>): Long? {
        val rotulados = ArrayList<Long>()
        val outros = ArrayList<Long>()
        for (l in linhas) {
            val f = dobrar(l)
            if (LINHA_RUIDO_VALOR.containsMatchIn(f)) continue
            val achados = REGEX_VALOR.findAll(l).mapNotNull { parseValorCentavos(it.groupValues[1]) }.toList()
            if (achados.isEmpty()) continue
            if (Regex("\\bvalor\\b").containsMatchIn(f)) rotulados += achados else outros += achados
        }
        val lista = rotulados.ifEmpty { outros }.distinct()
        return if (lista.size == 1) lista[0] else null // dois valores diferentes: ambíguo, o usuário digita
    }

    private val MESES = mapOf(
        "jan" to 1, "fev" to 2, "mar" to 3, "abr" to 4, "mai" to 5, "jun" to 6,
        "jul" to 7, "ago" to 8, "set" to 9, "out" to 10, "nov" to 11, "dez" to 12
    )
    private val DATA_NUM = Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})(?:\\D{0,6}(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?")
    private val DATA_EXT = Regex("(\\d{1,2})\\s+(?:de\\s+)?(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-z]*\\.?\\s+(?:de\\s+)?(\\d{4})(?:\\D{0,6}(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?")

    private fun montarData(d: Int, m: Int, a: Int, h: Int?, mi: Int?, s: Int?, tz: TimeZone): Long? {
        if (a !in 2000..2100) return null
        val c = Calendar.getInstance(tz)
        c.isLenient = false
        return try {
            c.clear()
            c.set(a, m - 1, d, h ?: 12, mi ?: 0, s ?: 0)
            c.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private val DATA_ISO = Regex("(\\d{4})-(\\d{2})-(\\d{2})(?:[T\\s]{1,3}(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?")

    private fun dataDoComprovante(texto: String, tz: TimeZone): Long? {
        val f = dobrar(texto)
        DATA_ISO.find(f)?.let { m ->
            val g = m.groupValues
            montarData(g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toIntOrNull(), g[5].toIntOrNull(), g[6].toIntOrNull(), tz)?.let { return it }
        }
        DATA_NUM.find(f)?.let { m ->
            val g = m.groupValues
            montarData(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toIntOrNull(), g[5].toIntOrNull(), g[6].toIntOrNull(), tz)?.let { return it }
        }
        DATA_EXT.find(f)?.let { m ->
            val g = m.groupValues
            val mes = MESES[g[2]] ?: return null
            return montarData(g[1].toInt(), mes, g[3].toInt(), g[4].toIntOrNull(), g[5].toIntOrNull(), g[6].toIntOrNull(), tz)
        }
        return null
    }

    /** Data (UTC) embutida no E2E (posições 9..16 = AAAAMMDD), convertida para o meio-dia local desse dia. */
    fun dataDoE2e(e2e: String, tz: TimeZone = TimeZone.getDefault()): Long? {
        if (e2e.length != 32) return null
        val a = e2e.substring(9, 13).toIntOrNull() ?: return null
        val m = e2e.substring(13, 15).toIntOrNull() ?: return null
        val d = e2e.substring(15, 17).toIntOrNull() ?: return null
        return montarData(d, m, a, null, null, null, tz)
    }

    private val FRASES_DE_ROTULO = Regex("\\b(quem recebeu|quem pagou|quem enviou|quem recebe|quem paga)\\b")
    private val SINAL_ENVIADO = Regex("\\b(enviad[oa]s?|enviou|paguei|pagou|voce pagou|pix pago|transferiu)\\b")
    private val SINAL_RECEBIDO = Regex("\\b(recebid[oa]s?|recebeu|recebemos|voce recebeu)\\b")
    private val SINAL_FRACO_ENVIADO = Regex("\\b(pago|pagamento|debito|saida)\\b")
    private val SINAL_FRACO_RECEBIDO = Regex("\\b(credito|entrada|deposito)\\b")

    /** Direção pelas palavras do comprovante; nulo se não há sinal ou se há sinais dos dois lados. */
    fun inferirDirecao(texto: String): Direcao? {
        val f = dobrar(texto).replace(FRASES_DE_ROTULO, " ")
        val env = SINAL_ENVIADO.findAll(f).count()
        val rec = SINAL_RECEBIDO.findAll(f).count()
        if (env != rec) return if (env > rec) Direcao.ENVIADO else Direcao.RECEBIDO
        if (env > 0) return null // empate com sinais fortes dos dois lados
        val fe = SINAL_FRACO_ENVIADO.findAll(f).count()
        val fr = SINAL_FRACO_RECEBIDO.findAll(f).count()
        return when {
            fe > fr -> Direcao.ENVIADO
            fr > fe -> Direcao.RECEBIDO
            else -> null
        }
    }

    private const val CAB_DESTINO = "destino|para|favorecido|recebedor|beneficiario|destinatario|quem recebeu|quem recebe|pago a|pago para|dados de quem recebeu|dados do recebedor"
    private const val CAB_ORIGEM = "origem|de|pagador|remetente|quem pagou|quem enviou|quem paga|enviado por|recebido de|dados de quem pagou|dados do pagador"

    private fun secao(linhas: List<String>, cabecalhos: String): String? {
        val cab = Regex("^(?:$cabecalhos)\\b\\s*[:\\-]?\\s*(.*)$")
        val outraCab = Regex("^(?:$CAB_DESTINO|$CAB_ORIGEM|instituicao|chave|cpf|cnpj|agencia|conta|id |codigo|data|valor|tipo)\\b")
        for (i in linhas.indices) {
            val f = dobrar(linhas[i]).trim()
            val m = cab.find(f) ?: continue
            val resto = linhas[i].trim().substring(f.length - m.groupValues[1].length).trim()
            // "De" / "Para" soltos só valem como cabeçalho quando a linha é curta (evita frases corridas)
            normalizarContraparte(resto.replace(Regex("^nome\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE), ""))?.let { return it }
            var j = i + 1
            while (j < linhas.size && j <= i + 5) {
                val fl = dobrar(linhas[j]).trim()
                val nome = Regex("^nome\\b\\s*[:\\-]?\\s*(.*)$").find(fl)
                if (nome != null) {
                    val r = linhas[j].trim().substring(fl.length - nome.groupValues[1].length)
                    val n = normalizarContraparte(r) ?: linhas.getOrNull(j + 1)?.let { normalizarContraparte(it) }
                    if (n != null) return n
                } else if (outraCab.containsMatchIn(fl) && j > i) {
                    if (Regex("^(?:$CAB_DESTINO|$CAB_ORIGEM)\\b").containsMatchIn(fl)) break
                } else if (!fl.contains(':') && fl.isNotBlank()) {
                    normalizarContraparte(linhas[j])?.let { return it }
                }
                j++
            }
        }
        return null
    }

    private val GATILHO_FRASE = Regex("\\b(pix|enviou|enviado|enviada|pagou|paguei|pago|recebeu|recebido|recebida|transferiu|transferencia)\\b")
    private val PREPOSICAO = Regex("\\b(para|de)\\s+", RegexOption.IGNORE_CASE)

    /**
     * Interpreta o texto colado de um comprovante/aviso de Pix (parser tolerante por regex: rótulos "Valor", "Destino"/"Origem",
     * "Nome:", "ID da transação" etc.; frases como "Pix enviado para Maria"). O texto bruto NÃO é guardado nem devolvido.
     */
    fun lerComprovantePix(bruto: String?, tz: TimeZone = TimeZone.getDefault()): ResultadoComprovante {
        val texto = Normalizer.normalize(bruto.orEmpty(), Normalizer.Form.NFC).filter { it == '\n' || it == '\r' || !it.isISOControl() }
        if (texto.isBlank()) return ResultadoComprovante.Invalido(MotivoComprovante.VAZIO)
        if (texto.length > 6_000) return ResultadoComprovante.Invalido(MotivoComprovante.MUITO_LONGO)

        val linhas = texto.split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val e2e = extrairE2e(texto)
        val valor = valorDoComprovante(linhas)
        if (valor == null && e2e == null) return ResultadoComprovante.Invalido(MotivoComprovante.NADA_RECONHECIDO)

        var direcao = inferirDirecao(texto)
        var contra: String? = null
        val dest = secao(linhas, CAB_DESTINO)
        val orig = secao(linhas, CAB_ORIGEM)
        when (direcao) {
            Direcao.ENVIADO -> contra = dest
            Direcao.RECEBIDO -> contra = orig
            null -> when {
                dest != null && orig == null -> { direcao = Direcao.ENVIADO; contra = dest }
                orig != null && dest == null -> { direcao = Direcao.RECEBIDO; contra = orig }
            }
        }
        if (contra == null) { // frase corrida: "Pix enviado para Maria", "Você recebeu um Pix de João"
            for (l in linhas) {
                if (!GATILHO_FRASE.containsMatchIn(dobrar(l))) continue
                for (m in PREPOSICAO.findAll(l)) {
                    val nome = normalizarContraparte(l.substring(m.range.last + 1)) ?: continue
                    val d = if (dobrar(m.groupValues[1]) == "para") Direcao.ENVIADO else Direcao.RECEBIDO
                    if (direcao == null || direcao == d) { direcao = d; contra = nome; break }
                }
                if (contra != null) break
            }
        }
        val data = dataDoComprovante(texto, tz) ?: e2e?.let { dataDoE2e(it, tz) }
        return ResultadoComprovante.Ok(ComprovantePix(direcao, valor, contra, data, e2e))
    }

    // ------------------------------------------------------------------ rascunho para o formulário

    /** O que a tela mostra antes de confirmar. A chave só aparece mascarada. */
    data class ResumoPix(
        val contraparte: String?,
        val chaveMascarada: String?,
        val cidade: String?,
        val e2e: String?,
        val mensagem: String?,
        val txid: String?,
        /** Só o host da URL do payload dinâmico (informativo; nunca consultado). */
        val hostPayload: String?,
        val origem: String
    )

    data class RascunhoPix(
        val direcao: Direcao,
        val descricao: String,
        val valorCentavos: Long?,
        /** Nulo = manter a data do formulário (BR Code não tem data). */
        val dataMs: Long?,
        /** Tags a acrescentar: só [TAG]. */
        val tags: List<String>,
        val resumo: ResumoPix,
        /** O comprovante lido tem direção diferente da do formulário (débito/crédito): a tela bloqueia o uso. */
        val conflitoDirecao: Boolean,
        /** Direção lida do comprovante, quando houve. */
        val direcaoLida: Direcao?
    )

    private fun hostDe(url: String?): String? =
        url?.trim()?.removePrefix("https://")?.removePrefix("http://")?.substringBefore('/')?.substringBefore('?')?.take(80)?.takeIf { it.isNotEmpty() }

    /**
     * BR Code -> rascunho. O QR descreve quem RECEBE: em [Direcao.ENVIADO] o beneficiário é a contraparte; em
     * [Direcao.RECEBIDO] (QR do próprio usuário) não há como saber quem pagará, então a descrição fica "Pix recebido".
     */
    fun rascunhoPix(brCode: BrCode, direcao: Direcao): RascunhoPix {
        val contra = if (direcao == Direcao.ENVIADO) brCode.beneficiario else null
        return RascunhoPix(
            direcao = direcao,
            descricao = descricao(direcao, contra, brCode.txid),
            valorCentavos = brCode.valorCentavos,
            dataMs = null,
            tags = listOf(TAG),
            resumo = ResumoPix(
                contraparte = brCode.beneficiario, chaveMascarada = brCode.chaveMascarada, cidade = brCode.cidade, e2e = null,
                mensagem = brCode.mensagem, txid = brCode.txid, hostPayload = hostDe(brCode.urlPayload),
                origem = if (brCode.dinamico) "QR Code Pix dinâmico" else "QR Code Pix"
            ),
            conflitoDirecao = false,
            direcaoLida = null
        )
    }

    /** Comprovante -> rascunho. Sem direção no texto vale a do formulário ([direcaoForm]). */
    fun rascunhoPix(c: ComprovantePix, direcaoForm: Direcao): RascunhoPix {
        val dir = c.direcao ?: direcaoForm
        return RascunhoPix(
            direcao = direcaoForm,
            descricao = descricao(dir, c.contraparte, c.e2e),
            valorCentavos = c.valorCentavos,
            dataMs = c.dataMs,
            tags = listOf(TAG),
            resumo = ResumoPix(
                contraparte = c.contraparte, chaveMascarada = null, cidade = null, e2e = c.e2e, mensagem = null, txid = null,
                hostPayload = null, origem = "Comprovante colado"
            ),
            conflitoDirecao = c.direcao != null && c.direcao != direcaoForm,
            direcaoLida = c.direcao
        )
    }

    /** Rascunho a partir dos campos de uma sugestão de notificação (R64/R89). */
    fun rascunhoPix(direcao: Direcao, contraparte: String?, valorCentavos: Long?, dataMs: Long?, e2e: String? = null): RascunhoPix =
        RascunhoPix(
            direcao = direcao, descricao = descricao(direcao, contraparte, e2e), valorCentavos = valorCentavos, dataMs = dataMs,
            tags = listOf(TAG),
            resumo = ResumoPix(normalizarContraparte(contraparte), null, null, e2e, null, null, null, "Notificação do banco"),
            conflitoDirecao = false, direcaoLida = direcao
        )

    /** Contraparte contida numa descrição na convenção ("Pix para X" / "Pix de X"); nulo em "Pix enviado/recebido" ou texto livre. */
    fun contraparteDaDescricao(descricao: String): String? =
        Regex("^\\s*pix\\s+(?:para|de)\\s+(.+)$", RegexOption.IGNORE_CASE).find(semId(descricao))?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    // ------------------------------------------------------------------ pessoa (só sugestão)

    data class PessoaRef(val id: Int, val nome: String)

    /** Pessoa cujo nome casa com a contraparte (todos os nomes dela aparecem na contraparte) e é a única; nunca vincula sozinha. */
    fun sugerirPessoa(contraparte: String?, pessoas: List<PessoaRef>): PessoaRef? {
        val toks = Texto.tokens(contraparte.orEmpty())
        if (toks.isEmpty()) return null
        val normC = Texto.normalizar(contraparte.orEmpty())
        val achadas = pessoas.filter { p ->
            val n = Texto.normalizar(p.nome)
            val pt = Texto.tokens(p.nome)
            n.isNotEmpty() && (n == normC || (pt.isNotEmpty() && toks.containsAll(pt) && (pt.size >= 2 || toks.size == 1)))
        }
        return achadas.singleOrNull()
    }

    // ------------------------------------------------------------------ duplicidade

    data class Candidato(
        val valorCentavos: Long, val entrada: Boolean, val dataMs: Long, val conta: String, val contraparte: String?,
        /** Sufixo "(ID xxxxxxxx)" do lançamento em edição (ver [idDaDescricao]); mesmo ID na mesma conta já basta para avisar. */
        val idCurto: String? = null
    )
    data class Existente(
        val valorCentavos: Long, val entrada: Boolean, val dataMs: Long, val conta: String,
        val descricao: String, val tags: List<String>
    )

    private fun dia(ms: Long, tz: TimeZone) = Math.floorDiv(ms + tz.getOffset(ms), DIA_MS)

    private val PALAVRAS_GENERICAS = setOf("pix", "para", "enviado", "recebido", "pagamento", "transferencia")

    private fun tokensNome(s: String): Set<String> = Texto.tokens(semId(s)) - PALAVRAS_GENERICAS

    /**
     * Lançamentos existentes que provavelmente são o mesmo Pix: mesma conta, mesmo sentido, mesmo valor, data ±1 dia e
     * contraparte semelhante (nome em comum) — ou contraparte desconhecida / lançamento já marcado PIX (notificação +
     * comprovante do mesmo Pix). Só avisa; nunca bloqueia nem apaga.
     */
    fun possiveisDuplicados(c: Candidato, existentes: List<Existente>, tz: TimeZone = TimeZone.getDefault()): List<Existente> {
        val d0 = dia(c.dataMs, tz)
        val conta = c.conta.trim().lowercase()
        val nomeC = c.contraparte?.let { tokensNome(it) }.orEmpty()
        return existentes.filter { e ->
            if (e.conta.trim().lowercase() != conta) return@filter false
            // Mesmo "(ID xxxxxxxx)" na mesma conta: é o mesmo Pix, mesmo com valor/data diferentes (igual ao portal).
            if (c.idCurto != null && idDaDescricao(e.descricao).equals(c.idCurto, ignoreCase = true)) return@filter true
            c.valorCentavos > 0 && e.valorCentavos == c.valorCentavos && e.entrada == c.entrada &&
                Math.abs(dia(e.dataMs, tz) - d0) <= 1 &&
                (nomeC.isEmpty() || temTag(e.tags) || tokensNome(e.descricao).any { it in nomeC })
        }
    }
}
