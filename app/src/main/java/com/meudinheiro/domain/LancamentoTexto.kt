package com.meudinheiro.domain

import com.meudinheiro.data.RegraCategoria
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * R62 — Lançar por texto/voz. [interpretar] é PURA e espelha `portal/src/lib/finance/lancamentoTexto.ts` (mesmos casos em
 * `LancamentoTextoTest` e `lancamentoTexto.test.ts`). NUNCA salva nada: devolve os campos entendidos, a confiança de cada
 * um e as pendências (o que faltou ou ficou ambíguo); quem chama abre o formulário já preenchido para o usuário revisar.
 */
object LancamentoTexto {

    enum class Confianca { ALTA, MEDIA, BAIXA }

    enum class Destino { CONTA, CARTAO }

    data class Candidato(val tipo: Destino, val id: String, val nome: String)

    /** Data local (sem fuso): o que o usuário quis dizer, independente de horário. */
    data class Data(val ano: Int, val mes: Int, val dia: Int) {
        fun comparar(o: Data) = (ano * 10000 + mes * 100 + dia) - (o.ano * 10000 + o.mes * 100 + o.dia)

        /** epoch ms ao meio-dia local (evita problemas de fuso). */
        fun paraMs(): Long = GregorianCalendar(ano, mes - 1, dia, 12, 0, 0).timeInMillis

        companion object {
            fun de(ms: Long): Data = Calendar.getInstance().apply { timeInMillis = ms }.let {
                Data(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
            }
        }
    }

    data class ContaCtx(val id: String, val nome: String)
    data class CartaoCtx(val id: Int, val nome: String)

    data class Contexto(
        /** `id` = número da conta, `nome` = banco */
        val contas: List<ContaCtx>,
        val cartoes: List<CartaoCtx>,
        /** nomes das categorias existentes (padrão + personalizadas) */
        val categorias: List<String>,
        val regras: List<RegraCategoria>,
        /** "agora" injetado */
        val agora: Data
    )

    data class Confiancas(
        val tipo: Confianca = Confianca.BAIXA,
        val valor: Confianca = Confianca.BAIXA,
        val data: Confianca = Confianca.BAIXA,
        val descricao: Confianca = Confianca.BAIXA,
        val destino: Confianca = Confianca.BAIXA,
        val categoria: Confianca = Confianca.BAIXA,
        val parcelas: Confianca = Confianca.BAIXA
    )

    data class Interpretacao(
        val tipo: TipoDespesa,
        /** valor TOTAL em centavos; null = não entendi */
        val valorCentavos: Long?,
        val descricao: String,
        val data: Data,
        /** conta/cartão escolhido (só quando inequívoco) */
        val destino: Candidato?,
        /** opções quando ambíguo (não escolhe sozinho) */
        val candidatos: List<Candidato>,
        val parcelas: Int?,
        val categoria: String?,
        val confianca: Confiancas,
        val pendencias: List<String>
    )

    /** 2..120, o mesmo teto do formulário. */
    private const val PARCELAS_MAX = 120

    // ------------------------------------------------------------ vocabulário (idêntico ao portal)

    private val VERBOS_DEBITO = listOf("gastei", "gasto", "gastamos", "paguei", "pagamos", "comprei", "compramos", "debitou", "saiu", "torrei", "parcelei")
    private val VERBOS_CREDITO = listOf("recebi", "recebemos", "ganhei", "ganhamos", "entrou", "caiu", "vendi", "depositaram", "depositou")
    private val SUBST_CREDITO = listOf("salario", "deposito", "rendimento", "rendimentos", "reembolso", "cashback", "dividendos", "freela", "freelance", "bonus", "comissao", "mesada")
    private val PARCELAMENTO = listOf("parcelei", "parcelado", "parcelada", "parcelar", "parcelamos")
    private val PALAVRAS_CARTAO = listOf("cartao", "credito")
    private val PALAVRAS_CONTA = listOf("debito", "conta")
    private val MOEDA_SUFIXO = listOf("reais", "real", "conto", "contos", "pila", "pilas")
    private val STOP = listOf("no", "na", "nos", "nas", "em", "de", "do", "da", "dos", "das", "pro", "pra", "para", "com", "um", "uma", "o", "a", "os", "as", "pelo", "pela", "e", "ao", "por", "foi", "deu", "eu")
    private val STOP_NOME = listOf("banco", "cartao", "conta", "credito", "debito", "de", "do", "da", "dos", "das", "sa", "card", "bank")
    private val DIAS_SEMANA = mapOf("domingo" to 0, "segunda" to 1, "terca" to 2, "quarta" to 3, "quinta" to 4, "sexta" to 5, "sabado" to 6)
    private val PASSADA = listOf("passada", "passado", "passadas", "passados")

    /** Categorias padrão do app (contam como existentes). */
    private val CATEGORIAS_PADRAO_NOMES = listOf(
        "Combustível", "Alimentação", "Transporte", "Compras", "Cinema", "Saúde", "Educação", "Salário", "Oficina",
        "Supermercado", "Academia", "Jogos", "Bebidas", "Lanche", "Reserva"
    )

    private val MAPA_DEBITO: List<Pair<List<String>, List<String>>> = listOf(
        listOf("mercado", "supermercado", "atacadao", "sacolao", "hortifruti", "acougue") to listOf("Supermercado"),
        listOf("almoco", "janta", "jantar", "cafe", "restaurante", "pizza", "ifood", "delivery", "marmita", "comida", "churrasco", "padaria", "sorvete", "pastel", "rodizio") to listOf("Alimentação"),
        listOf("lanche", "lanchonete", "hamburguer", "sanduiche", "salgado") to listOf("Lanche", "Alimentação"),
        listOf("bebida", "bebidas", "cerveja", "vinho", "chopp", "bar", "drink") to listOf("Bebidas", "Lazer"),
        listOf("gasolina", "combustivel", "etanol", "diesel", "alcool", "posto") to listOf("Combustível", "Transporte"),
        listOf("uber", "taxi", "onibus", "metro", "passagem", "pedagio", "estacionamento", "cabify", "trem", "brt") to listOf("Transporte"),
        listOf("farmacia", "drogaria", "remedio", "remedios", "medico", "dentista", "consulta", "exame", "hospital", "psicologo", "saude") to listOf("Saúde"),
        listOf("escola", "faculdade", "curso", "livro", "apostila", "matricula") to listOf("Educação"),
        listOf("academia") to listOf("Academia"),
        listOf("cinema", "filme", "ingresso") to listOf("Cinema", "Lazer"),
        listOf("jogo", "jogos", "steam", "playstation", "xbox") to listOf("Jogos"),
        listOf("oficina", "mecanico", "pneu", "revisao") to listOf("Oficina", "Transporte"),
        listOf("roupa", "roupas", "sapato", "tenis", "camisa", "shopping", "loja") to listOf("Compras"),
        listOf("luz", "energia", "eletricidade") to listOf("Luz", "Energia", "Energia elétrica", "Contas", "Casa", "Moradia"),
        listOf("agua", "esgoto") to listOf("Água", "Contas", "Casa", "Moradia"),
        listOf("internet", "wifi", "telefone", "celular") to listOf("Internet", "Telefone", "Contas", "Casa"),
        listOf("aluguel", "condominio") to listOf("Aluguel", "Moradia", "Casa"),
        listOf("gas") to listOf("Gás", "Contas", "Casa"),
        listOf("netflix", "spotify", "assinatura", "streaming") to listOf("Assinaturas", "Lazer")
    )

    private val MAPA_CREDITO: List<Pair<List<String>, List<String>>> = listOf(
        listOf("salario", "ordenado") to listOf("Salário"),
        listOf("freela", "freelance", "bonus", "comissao") to listOf("Freelance", "Extra", "Outras receitas"),
        listOf("reembolso") to listOf("Reembolso"),
        listOf("rendimento", "rendimentos", "dividendos") to listOf("Rendimentos", "Investimentos")
    )

    // ------------------------------------------------------------ tokens

    private class Tok(val orig: String, val n: String, var usado: Boolean = false)

    private val INICIO_LIXO = charArrayOf(',', ';', ':', '!', '?', '(', ')', '"', '\'', '“', '”')
    private val FIM_LIXO = charArrayOf(',', ';', ':', '!', '?', '(', ')', '"', '\'', '“', '”', '.')

    private fun tokenizar(texto: String): List<Tok> {
        val out = ArrayList<Tok>()
        for (raw in texto.trim().split(Regex("\\s+"))) {
            val limpo = raw.trimStart(*INICIO_LIXO).trimEnd(*FIM_LIXO)
            val n = Texto.normalizar(limpo)
            if (n.isNotEmpty()) out += Tok(limpo, n)
        }
        return out
    }

    private val RE_NUMERO = Regex("^\\d+([.,]\\d+)*$")
    private fun ehNumero(s: String) = RE_NUMERO.matches(s)

    /** "1.234,56" / "3.500" / "120,50" / "18.90" / "45" -> centavos; inválido -> null. */
    fun numeroEmCentavos(t: String): Long? {
        if (!ehNumero(t)) return null
        val ultV = t.lastIndexOf(',')
        val ultP = t.lastIndexOf('.')
        var inteiro = t
        var dec = ""
        if (ultV >= 0 && ultP >= 0) {
            val sep = maxOf(ultV, ultP)
            dec = t.substring(sep + 1)
            inteiro = t.substring(0, sep).replace(".", "").replace(",", "")
        } else if (ultV >= 0 || ultP >= 0) {
            val sep = maxOf(ultV, ultP)
            val ch = t[sep]
            val qtd = t.count { it == ch }
            val depois = t.substring(sep + 1)
            if (qtd > 1) {
                if (ch == ',') return null
                inteiro = t.replace(".", "")
            } else if (ch == '.' && depois.length == 3 && t.substring(0, sep) != "0" && t.substring(0, sep).length <= 3) {
                inteiro = t.replace(".", "")
            } else {
                inteiro = t.substring(0, sep)
                dec = depois
            }
        }
        if (dec.length > 2 || inteiro.isEmpty() || inteiro.length > 12) return null
        return inteiro.toLong() * 100 + (if (dec.isNotEmpty()) dec.padEnd(2, '0').toLong() else 0L)
    }

    // ------------------------------------------------------------ datas

    private fun diasNoMes(mes: Int, ano: Int): Int = GregorianCalendar(ano, mes - 1, 1).getActualMaximum(Calendar.DAY_OF_MONTH)

    private fun dataValida(ano: Int, mes: Int, dia: Int): Boolean {
        if (mes < 1 || mes > 12 || dia < 1) return false
        return dia <= diasNoMes(mes, ano)
    }

    private fun deslocar(hoje: Data, dias: Int): Data {
        val c = GregorianCalendar(hoje.ano, hoje.mes - 1, hoje.dia, 12, 0, 0)
        c.add(Calendar.DAY_OF_MONTH, -dias)
        return Data(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** "dia N": o dia N mais recente que não esteja no futuro. */
    private fun ultimoDiaN(hoje: Data, n: Int): Pair<Data, Boolean> {
        for (k in 0 until 24) {
            val idx = hoje.ano * 12 + (hoje.mes - 1) - k
            val ano = Math.floorDiv(idx, 12)
            val mes = Math.floorMod(idx, 12) + 1
            if (k == 0) {
                if (n <= hoje.dia) return Data(ano, mes, n) to false
            } else if (n <= diasNoMes(mes, ano)) return Data(ano, mes, n) to true
        }
        return hoje to false
    }

    private fun diaDaSemana(d: Data): Int = GregorianCalendar(d.ano, d.mes - 1, d.dia, 12, 0, 0).get(Calendar.DAY_OF_WEEK) - 1

    // ------------------------------------------------------------ destino

    private class Pontuado(val c: Candidato, val pontos: Int, val usados: List<Int>)

    private fun tokensDoNome(nome: String): List<String> =
        Texto.normalizar(nome).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP_NOME }

    private fun pontuar(c: Candidato, toks: List<Tok>): Pontuado {
        val nomes = tokensDoNome(c.nome)
        val juntoNome = Texto.normalizar(c.nome).replace(Regex("[^a-z0-9]"), "")
        var pontos = 0
        val usados = ArrayList<Int>()
        for (nt in nomes) {
            var melhor = 0
            var idx = -1
            toks.forEachIndexed { i, t ->
                if (t.usado || ehNumero(t.n)) return@forEachIndexed
                var p = 0
                if (t.n == nt) p = 3 else if (t.n.length >= 4 && nt.startsWith(t.n)) p = 2
                if (p > melhor) { melhor = p; idx = i }
            }
            if (melhor > 0) { pontos += melhor; usados += idx }
        }
        if (pontos == 0 && juntoNome.length >= 5) {
            for (i in 0 until toks.size - 1) {
                if (!toks[i].usado && !toks[i + 1].usado && toks[i].n + toks[i + 1].n == juntoNome) {
                    pontos = 3
                    usados += i
                    usados += i + 1
                    break
                }
            }
        }
        return Pontuado(c, pontos, usados)
    }

    private fun nomeCandidato(c: Candidato) = "${c.nome} (${if (c.tipo == Destino.CONTA) "conta" else "cartão"})"

    // ------------------------------------------------------------ categoria

    private fun categoriaPorPalavra(descTokens: List<String>, tipo: TipoDespesa, existentes: List<String>): String? {
        val mapa = if (tipo == TipoDespesa.CREDITO) MAPA_CREDITO else MAPA_DEBITO
        for (t in descTokens) {
            for ((chaves, prefs) in mapa) {
                if (t !in chaves) continue
                // a 1ª preferida que exista; as categorias padrão do app contam como existentes
                for (p in prefs) {
                    val e = existentes.firstOrNull { Texto.normalizar(it) == Texto.normalizar(p) }
                        ?: CATEGORIAS_PADRAO_NOMES.firstOrNull { Texto.normalizar(it) == Texto.normalizar(p) }
                    if (e != null) return e
                }
                return null
            }
        }
        return null
    }

    // ------------------------------------------------------------ principal

    private class Cand(val ini: Int, val fim: Int, val centavos: Long, val marcado: Boolean)

    private val RE_NX = Regex("^(\\d{1,3})x$")
    private val RE_DIA_MES = Regex("^\\d{1,2}/\\d{1,2}(/(\\d{2}|\\d{4}))?$")
    private val RE_K = Regex("^(\\d+([.,]\\d+)?)k$")
    private val RE_MIL = Regex("^(\\d+([.,]\\d+)?)mil$")

    fun interpretar(texto: String, ctx: Contexto): Interpretacao {
        val toks = tokenizar(texto)
        val pend = ArrayList<String>()
        var cTipo = Confianca.BAIXA; var cValor = Confianca.BAIXA; var cData = Confianca.BAIXA
        var cDesc = Confianca.BAIXA; var cDestino = Confianca.BAIXA; var cCat = Confianca.BAIXA; var cParc = Confianca.BAIXA
        val hoje = ctx.agora
        fun usar(vararg idx: Int) = idx.forEach { toks[it].usado = true }
        fun livre(i: Int) = i >= 0 && i < toks.size && !toks[i].usado
        fun n(i: Int) = if (i >= 0 && i < toks.size) toks[i].n else ""

        // ---- 1) data
        var data = hoje
        for (i in toks.indices) {
            if (!livre(i)) continue
            val t = toks[i].n
            if (t == "hoje") {
                data = hoje; cData = Confianca.ALTA; usar(i)
            } else if (t == "ontem") {
                data = deslocar(hoje, 1); cData = Confianca.ALTA; usar(i)
            } else if (t == "anteontem") {
                data = deslocar(hoje, 2); cData = Confianca.ALTA; usar(i)
            } else if (t == "antes" && n(i + 1) == "de" && n(i + 2) == "ontem" && livre(i + 1) && livre(i + 2)) {
                data = deslocar(hoje, 2); cData = Confianca.ALTA; usar(i, i + 1, i + 2)
            } else if (t == "dia" && livre(i + 1) && Regex("^\\d{1,2}$").matches(n(i + 1))) {
                val dia = n(i + 1).toInt()
                usar(i, i + 1)
                if (dia < 1 || dia > 31) {
                    pend += "Dia $dia inválido: usei hoje."
                } else {
                    val (d, voltou) = ultimoDiaN(hoje, dia)
                    data = d
                    cData = if (voltou) Confianca.MEDIA else Confianca.ALTA
                }
            } else if (RE_DIA_MES.matches(t) || (t == "dia" && livre(i + 1) && RE_DIA_MES.matches(n(i + 1)))) {
                val alvo = if (t == "dia") n(i + 1) else t
                if (t == "dia") usar(i, i + 1) else usar(i)
                val p = alvo.split("/")
                val dia = p[0].toInt()
                val mes = p[1].toInt()
                if (p.size == 3) {
                    val ano = if (p[2].length == 2) 2000 + p[2].toInt() else p[2].toInt()
                    if (dataValida(ano, mes, dia)) {
                        data = Data(ano, mes, dia); cData = Confianca.ALTA
                    } else pend += "Data inválida ($alvo): usei hoje."
                } else if (dataValida(hoje.ano, mes, dia)) {
                    val nesteAno = Data(hoje.ano, mes, dia)
                    if (nesteAno.comparar(hoje) <= 0) {
                        data = nesteAno; cData = Confianca.ALTA
                    } else if (dataValida(hoje.ano - 1, mes, dia)) {
                        data = Data(hoje.ano - 1, mes, dia); cData = Confianca.MEDIA
                    } else pend += "Data inválida ($alvo): usei hoje."
                } else pend += "Data inválida ($alvo): usei hoje."
            } else {
                val base = t.removeSuffix("-feira")
                val alvoDia = DIAS_SEMANA[base]
                if (alvoDia != null) {
                    usar(i)
                    var estrita = false
                    var j = i + 1
                    if (livre(j) && n(j) == "feira") { usar(j); j++ }
                    if (livre(j) && n(j) in PASSADA) { usar(j); estrita = true }
                    var delta = Math.floorMod(diaDaSemana(hoje) - alvoDia, 7)
                    if (estrita && delta == 0) delta = 7
                    data = deslocar(hoje, delta)
                    cData = if (estrita) Confianca.ALTA else Confianca.MEDIA
                }
            }
        }

        // ---- 2) parcelas
        var parcelas: Int? = null
        var valorAposParcela = -1
        var pediuParcelamento = false
        for (i in toks.indices) {
            if (!livre(i)) continue
            val t = toks[i].n
            val mx = RE_NX.matchEntire(t)
            if (mx != null) {
                usar(i)
                val k = mx.groupValues[1].toInt()
                if (k in 2..PARCELAS_MAX) {
                    parcelas = k
                    cParc = Confianca.ALTA
                    if (n(i + 1) == "de" && livre(i + 1) && ehNumero(n(i + 2)) && livre(i + 2)) {
                        usar(i + 1)
                        valorAposParcela = i + 2
                    }
                } else if (k > PARCELAS_MAX) pend += "Parcelas demais ($k): o máximo é $PARCELAS_MAX."
            } else if (Regex("^\\d{1,3}$").matches(t) && n(i + 1) in listOf("vezes", "parcelas", "parcela", "vez") && livre(i + 1)) {
                usar(i, i + 1)
                val k = t.toInt()
                if (k in 2..PARCELAS_MAX) {
                    parcelas = k
                    cParc = Confianca.ALTA
                } else if (k > PARCELAS_MAX) pend += "Parcelas demais ($k): o máximo é $PARCELAS_MAX."
            }
        }
        for (i in toks.indices) {
            if (livre(i) && toks[i].n in PARCELAMENTO) {
                pediuParcelamento = true
                if (toks[i].n != "parcelei") usar(i)
            }
        }

        // ---- 3) valor
        val cands = ArrayList<Cand>()
        var i0 = 0
        while (i0 < toks.size) {
            val i = i0
            i0++
            if (!livre(i)) continue
            var marcado = false
            var fim = i
            val t = toks[i].n
            val numTxt: String
            if (t == "r$" && livre(i + 1) && ehNumero(n(i + 1))) {
                numTxt = n(i + 1); fim = i + 1; marcado = true
            } else if (t.startsWith("r$") && ehNumero(t.substring(2))) {
                numTxt = t.substring(2); marcado = true
            } else if (ehNumero(t)) {
                numTxt = t
            } else {
                val mk = RE_K.matchEntire(t)
                if (mk != null) {
                    numeroEmCentavos(mk.groupValues[1])?.let { cands += Cand(i, i, it * 1000, false) }
                } else {
                    val mm = RE_MIL.matchEntire(t)
                    if (mm != null) numeroEmCentavos(mm.groupValues[1])?.let { cands += Cand(i, i, it * 1000, false) }
                }
                continue
            }
            var c = numeroEmCentavos(numTxt) ?: continue
            if (livre(fim + 1) && n(fim + 1) == "mil") { c *= 1000; fim += 1 }
            if (livre(fim + 1) && n(fim + 1) in MOEDA_SUFIXO) { marcado = true; fim += 1 }
            if (c <= 0) continue
            cands += Cand(i, fim, c, marcado)
            i0 = fim + 1
        }
        var valor: Long? = null
        val porParcela = if (valorAposParcela >= 0) cands.firstOrNull { it.ini == valorAposParcela } else null
        if (porParcela != null && parcelas != null) {
            valor = porParcela.centavos * parcelas
            for (k in porParcela.ini..porParcela.fim) toks[k].usado = true
            cValor = Confianca.MEDIA
        } else {
            val pool = if (cands.any { it.marcado }) cands.filter { it.marcado } else cands
            if (pool.isNotEmpty()) {
                val c = pool[0]
                valor = c.centavos
                for (k in c.ini..c.fim) toks[k].usado = true
                cValor = if (pool.size > 1) Confianca.MEDIA else Confianca.ALTA
                if (pool.size > 1) pend += "Encontrei mais de um valor (${FormatoCentavos.brl(pool[0].centavos)} e ${FormatoCentavos.brl(pool[1].centavos)}): usei o primeiro."
            } else {
                val i = toks.indexOfFirst { !it.usado && it.n == "mil" }
                if (i >= 0) {
                    valor = 100_000L
                    usar(i)
                    if (livre(i + 1) && n(i + 1) in MOEDA_SUFIXO) usar(i + 1)
                    cValor = Confianca.MEDIA
                }
            }
        }
        if (valor == null) pend += "Não entendi o valor. Diga, por exemplo, \"45 reais\" ou \"R$ 45,90\"."

        // ---- 4) tipo
        var deb = 0
        var cred = 0
        for (i in toks.indices) {
            if (!livre(i)) continue
            if (toks[i].n in VERBOS_DEBITO) { deb++; usar(i) }
            else if (toks[i].n in VERBOS_CREDITO) { cred++; usar(i) }
        }
        if (pediuParcelamento && deb == 0 && cred == 0) deb = 1
        var tipo = TipoDespesa.DEBITO
        if (deb > 0 && cred > 0) {
            val primeiro = toks.indexOfFirst { it.n in VERBOS_DEBITO || it.n in VERBOS_CREDITO }
            tipo = if (primeiro >= 0 && toks[primeiro].n in VERBOS_CREDITO) TipoDespesa.CREDITO else TipoDespesa.DEBITO
            cTipo = Confianca.BAIXA
            pend += "Não ficou claro se é despesa ou receita: confira o tipo."
        } else if (cred > 0) {
            tipo = TipoDespesa.CREDITO; cTipo = Confianca.ALTA
        } else if (deb > 0) {
            cTipo = Confianca.ALTA
        } else if (toks.any { it.n in SUBST_CREDITO }) {
            tipo = TipoDespesa.CREDITO; cTipo = Confianca.MEDIA
        } else {
            cTipo = Confianca.BAIXA
        }

        // ---- 5) conta / cartão
        var flagCartao = pediuParcelamento || parcelas != null
        var explicitoCartao = false
        var flagConta = false
        for (i in toks.indices) {
            if (!livre(i)) continue
            if (toks[i].n in PALAVRAS_CARTAO) { explicitoCartao = true; flagCartao = true; usar(i) }
            else if (toks[i].n in PALAVRAS_CONTA) { flagConta = true; usar(i) }
        }
        val todos: List<Candidato> =
            ctx.contas.map { Candidato(Destino.CONTA, it.id, it.nome) } + ctx.cartoes.map { Candidato(Destino.CARTAO, it.id.toString(), it.nome) }
        var pontuados = todos.map { pontuar(it, toks) }.filter { it.pontos > 0 }
        var destino: Candidato? = null
        var candidatos: List<Candidato> = emptyList()
        val dica: Destino? = if (explicitoCartao) Destino.CARTAO else if (flagConta) Destino.CONTA else null
        var tipoSemNome = false
        if (dica != null) {
            val filtrados = pontuados.filter { it.c.tipo == dica }
            tipoSemNome = filtrados.isEmpty() && pontuados.isNotEmpty()
            pontuados = filtrados
        } else if (flagCartao) {
            val soCartao = pontuados.filter { it.c.tipo == Destino.CARTAO }
            if (soCartao.isNotEmpty()) pontuados = soCartao
        }
        if (pontuados.isNotEmpty()) {
            val topo = pontuados.maxOf { it.pontos }
            val melhores = pontuados.filter { it.pontos == topo }
            for (p in melhores) p.usados.forEach { toks[it].usado = true }
            if (melhores.size == 1) {
                destino = melhores[0].c
                cDestino = Confianca.ALTA
            } else {
                candidatos = melhores.map { it.c }
                pend += "Qual conta ou cartão? Encontrei: ${candidatos.joinToString(", ") { nomeCandidato(it) }}."
            }
        } else if (tipoSemNome) {
            val doTipo = todos.filter { it.tipo == dica }
            candidatos = doTipo
            pend += "Não achei ${if (dica == Destino.CARTAO) "um cartão" else "uma conta"} com esse nome. Opções: ${if (doTipo.isNotEmpty()) doTipo.joinToString(", ") { it.nome } else "nenhuma cadastrada"}."
        } else if (explicitoCartao || (flagCartao && pediuParcelamento)) {
            if (ctx.cartoes.size == 1) {
                destino = Candidato(Destino.CARTAO, ctx.cartoes[0].id.toString(), ctx.cartoes[0].nome)
                cDestino = Confianca.MEDIA
            } else if (ctx.cartoes.size > 1) {
                candidatos = ctx.cartoes.map { Candidato(Destino.CARTAO, it.id.toString(), it.nome) }
                pend += "Qual cartão? Encontrei: ${candidatos.joinToString(", ") { it.nome }}."
            } else {
                pend += "Você falou em cartão, mas não há cartões cadastrados."
            }
        }

        // ---- 6) descrição
        val segmentos = ArrayList<String>()
        var atual = ArrayList<Tok>()
        fun fecha() {
            var a = 0
            var b = atual.size
            while (a < b && atual[a].n in STOP) a++
            while (b > a && atual[b - 1].n in STOP) b--
            if (b > a) segmentos += atual.subList(a, b).joinToString(" ") { it.orig }
            atual = ArrayList()
        }
        for (t in toks) {
            if (t.usado) fecha() else atual += t
        }
        fecha()
        var descricao = segmentos.joinToString(" ").trim().take(120)
        if (descricao.isNotEmpty()) {
            descricao = descricao.substring(0, 1).uppercase() + descricao.substring(1)
            cDesc = Confianca.ALTA
        } else {
            pend += "Não entendi o que foi. Diga, por exemplo, \"mercado\" ou \"almoço\"."
        }
        if (pediuParcelamento && parcelas == null) pend += "Em quantas parcelas? Diga, por exemplo, \"em 3x\"."

        // ---- 7) categoria (regras R55 -> palavras-chave)
        var categoria: String? = null
        if (descricao.isNotEmpty()) {
            val contaRegra = if (destino?.tipo == Destino.CONTA) destino.id else null
            val r = Regras.aplicar(ctx.regras, Regras.Entrada(descricao, contaRegra, tipo.name))
            if (r != null) {
                categoria = r.categoria.trim()
                cCat = Confianca.ALTA
            } else {
                val palavras = Texto.normalizar(descricao).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
                categoria = categoriaPorPalavra(palavras, tipo, ctx.categorias)
                if (categoria != null) cCat = Confianca.MEDIA
            }
        }

        return Interpretacao(
            tipo, valor, descricao, data, destino, candidatos, parcelas, categoria,
            Confiancas(cTipo, cValor, cData, cDesc, cDestino, cCat, cParc), pend
        )
    }

    /** Texto curto, pt-BR, do que foi entendido (para a prévia). */
    fun resumo(i: Interpretacao): List<String> {
        val l = ArrayList<String>()
        l += if (i.tipo == TipoDespesa.CREDITO) "Receita" else "Despesa"
        i.valorCentavos?.let { l += FormatoCentavos.brl(it) + (i.parcelas?.let { p -> " em ${p}x" } ?: "") }
        if (i.descricao.isNotEmpty()) l += i.descricao
        l += "%02d/%02d/%04d".format(i.data.dia, i.data.mes, i.data.ano)
        i.destino?.let { l += nomeCandidato(it) }
        i.categoria?.let { l += it }
        return l
    }
}
