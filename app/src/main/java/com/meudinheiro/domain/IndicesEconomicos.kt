package com.meudinheiro.domain

/**
 * R98 — índices econômicos e taxas de REFERÊNCIA (app). Paridade com `portal/src/lib/finance/indices.ts` (R97).
 *
 * Os valores iniciais ("semente") foram lidos manualmente de https://www.debit.com.br/tabelas/indicadores-economicos em
 * 28/10/2026. A página não informa a data de atualização, então as competências são APROXIMADAS. Não há consulta de rede:
 * o usuário edita os valores e a edição fica só neste aparelho (DataStore), nunca no backup, no Room nem no Firestore.
 *
 * Convenção: a semente e a tela usam PERCENTUAIS como na fonte (1,08 = 1,08% a.m.); as funções de conversão e composição
 * trabalham com FRAÇÕES decimais (0,0108). [pctParaFracao] e [fracaoParaPct] fazem a ponte.
 */
object IndicesEconomicos {

    /** R100: fonte exibida nas telas (igual ao portal): Banco Central, com a semente Debit como reserva. */
    const val FONTE = "Fonte: Banco Central do Brasil (SGS/PTAX); semente Debit (28/10/2026) como valor de reserva; confira antes de decidir"
    const val FONTE_SEMENTE = "Fonte: Debit — conferido em 28/10/2026; confira antes de decidir"
    const val URL_FONTE = "https://www.debit.com.br/tabelas/indicadores-economicos"
    const val DATA_SEMENTE = "2026-10-28"
    const val SLUG_SALARIO_MINIMO = "salario-minimo"

    enum class Grupo { TAXA, INFLACAO }

    /** R100: de onde veio o valor exibido. MANUAL (edição do usuário) sempre prevalece sobre BCB. */
    enum class Origem { SEMENTE, BCB, MANUAL }

    /**
     * [serie]: últimos valores em % no mês, do mais antigo para o mais recente (o último é o "valor mensal").
     * [competencia]: do ÚLTIMO valor ("MM/AAAA"; vazio = desconhecida). [aproximada]: a fonte não informa a competência.
     * [slug]: final de https://www.debit.com.br/tabelas/<slug>; nulo = sem página específica.
     */
    data class Item(
        val id: String,
        val nome: String,
        val grupo: Grupo,
        val serie: List<Double>,
        val competencia: String,
        val aproximada: Boolean,
        val slug: String?,
        /** R100: origem do valor (padrão = semente). */
        val origem: Origem = Origem.SEMENTE,
        /** R100: a competência é o mês corrente (acumulado parcial do mês; só Selic/CDI). */
        val parcial: Boolean = false
    ) {
        val valorMensalPct: Double get() = serie.lastOrNull() ?: Double.NaN
    }

    data class SalarioMinimo(val centavos: Long, val competencia: String, val origem: Origem = Origem.SEMENTE)

    /**
     * Valores que a página NÃO traz: o usuário informa (nulo = não informado). Rotativo e cheque especial são estimativas
     * editáveis (marcadas "estimativa/edite" na tela).
     */
    data class Extras(
        val selicMetaAnualPct: Double? = null,
        val inflacao12mPct: Double? = null,
        val taxaRotativoMensalPct: Double = 14.0,
        val taxaChequeEspecialMensalPct: Double = 8.0,
        /** R100: origem da meta da Selic exibida (nulo = não informada). */
        val selicMetaOrigem: Origem? = null
    )

    data class Conjunto(val conferidoEm: String, val itens: List<Item>, val salarioMinimo: SalarioMinimo, val extras: Extras)

    private val SLUGS = mapOf(
        "ipca" to "ipca-indice-nacional-de-precos-ao-consumidor-amplo",
        "igpm" to "igpm-fgv-indice-geral-de-precos-mercado",
        "inpc" to "inpc-indice-nacional-de-precos-ao-consumidor",
        "selic" to "selic",
        "cdi" to "cdi",
        "poupanca" to "poupanca",
        "tr" to "tr-bacen",
        "incc" to "incc-indice-nacional-da-construcao-civil",
        "ipcfipe" to "ipc-indice-de-precos-ao-consumidor-fipe"
    )

    fun urlHistorico(slug: String?): String? = slug?.let { "https://www.debit.com.br/tabelas/$it" }

    private fun item(id: String, nome: String, grupo: Grupo, serie: List<Double>, competencia: String, aproximada: Boolean) =
        Item(id, nome, grupo, serie, competencia, aproximada, SLUGS[id])

    /** Semente (28/10/2026). Taxas: competência aproximada (10/2026). Inflação: colunas 07, 08 e 09/2026. */
    fun padrao(): Conjunto = Conjunto(
        DATA_SEMENTE,
        listOf(
            item("selic", "Selic", Grupo.TAXA, listOf(1.09, 1.08), "10/2026", true),
            item("cdi", "CDI", Grupo.TAXA, listOf(1.09, 1.08), "10/2026", true),
            item("tr", "TR", Grupo.TAXA, listOf(0.1693, 0.169, 0.1616), "10/2026", true),
            item("poupanca", "Poupança (já inclui a TR)", Grupo.TAXA, listOf(0.6701, 0.6698, 0.6624), "10/2026", true),
            item("tbf", "TBF", Grupo.TAXA, listOf(0.9749), "10/2026", true),
            item("tjlp", "TJLP", Grupo.TAXA, listOf(0.74), "10/2026", true),
            item("ipca", "IPCA", Grupo.INFLACAO, listOf(0.07, -0.32, 0.82), "09/2026", false),
            item("inpc", "INPC", Grupo.INFLACAO, listOf(-0.01, -0.32, 0.82), "09/2026", false),
            item("igpm", "IGP-M", Grupo.INFLACAO, listOf(-1.16, -0.22, 1.57), "09/2026", false),
            item("igpdi", "IGP-DI", Grupo.INFLACAO, listOf(-0.86, 0.06, 1.5), "09/2026", false),
            item("incc", "INCC-DI", Grupo.INFLACAO, listOf(0.61, 0.66, 0.22), "09/2026", false),
            item("ipcfipe", "IPC-Fipe", Grupo.INFLACAO, listOf(-0.03, 0.01, 0.51), "09/2026", false),
            item("ipcae", "IPCA-E", Grupo.INFLACAO, listOf(0.06, -0.4, 0.7), "09/2026", false),
            item("ivar", "IVAR", Grupo.INFLACAO, listOf(0.66, 0.29, 0.28), "09/2026", false)
        ),
        SalarioMinimo(162100, "2026"),
        Extras()
    )

    // ------------------------------------------------------------------ conversões (frações decimais)

    fun pctParaFracao(pct: Double): Double = pct / 100
    fun fracaoParaPct(f: Double): Double = f * 100

    /** Taxa anual equivalente: (1 + i_mensal)^12 − 1. */
    fun anualDeMensal(mensal: Double): Double = Math.pow(1 + mensal, 12.0) - 1

    /** Taxa mensal equivalente: (1 + i_anual)^(1/12) − 1. */
    fun mensalDeAnual(anual: Double): Double = Math.pow(1 + anual, 1.0 / 12) - 1

    /** Acumulado por composição (frações): Π(1 + i) − 1. Lista vazia = 0. */
    fun acumular(taxas: List<Double>): Double {
        var f = 1.0
        for (t in taxas) f *= 1 + t
        return f - 1
    }

    /** [acumulado] fração; [mesesUsados] pode ser menor que o pedido; [incompleto] = a série tinha menos meses que o pedido. */
    data class AcumuladoN(val acumulado: Double, val mesesUsados: Int, val incompleto: Boolean)

    /** Acumulado dos ÚLTIMOS [n] meses de uma série em % (a mais recente por último), por composição. */
    fun acumuladoDosUltimos(seriePct: List<Double>, n: Int): AcumuladoN {
        val k = maxOf(0, minOf(n, seriePct.size))
        val fatia = if (k == 0) emptyList() else seriePct.subList(seriePct.size - k, seriePct.size)
        return AcumuladoN(acumular(fatia.map(::pctParaFracao)), k, k < n)
    }

    /**
     * Média mensal (geométrica) em % da série: (Π(1+i))^(1/n) − 1. Padrão da inflação na projeção de caixa (R98-E): evita extrapolar
     * um único mês atípico. Série vazia = `NaN`. Arredondada a 4 casas na tela (HALF_UP), como no portal.
     */
    fun mediaMensalPct(seriePct: List<Double>): Double =
        if (seriePct.isEmpty()) Double.NaN else (Math.pow(1 + acumular(seriePct.map(::pctParaFracao)), 1.0 / seriePct.size) - 1) * 100

    /** Ganho real = (1 + nominal) / (1 + inflação) − 1 (frações). */
    fun ganhoReal(nominal: Double, inflacao: Double): Double = (1 + nominal) / (1 + inflacao) - 1

    /** "MM/AAAA" do mês [k] meses antes da competência dada; "" se inválida. */
    fun competenciaAnterior(competencia: String, k: Int): String {
        val m = Regex("^(\\d{2})/(\\d{4})$").matchEntire(competencia.trim()) ?: return ""
        val idx = m.groupValues[2].toInt() * 12 + (m.groupValues[1].toInt() - 1) - k
        val ano = Math.floorDiv(idx, 12)
        val mes = Math.floorMod(idx, 12) + 1
        return mes.toString().padStart(2, '0') + "/" + ano
    }

    fun buscar(c: Conjunto, id: String): Item = c.itens.firstOrNull { it.id == id } ?: padrao().itens.first { it.id == id }

    /** Compara com a semente (para mostrar "editado"). */
    fun foiEditado(i: Item): Boolean {
        if (i.origem == Origem.BCB) return false
        if (i.origem == Origem.MANUAL) return true
        val p = padrao().itens.firstOrNull { it.id == i.id } ?: return true
        return p.competencia != i.competencia || p.serie != i.serie
    }

    // ------------------------------------------------------------------ persistência local (texto TSV no DataStore)

    private fun ok(n: Double?): Boolean = n != null && n.isFinite() && Math.abs(n) < 1000

    /**
     * Serializa SÓ o que difere da semente. Linhas separadas por `\n`, campos por TAB:
     * `conf\tAAAA-MM-DD`, `serie\tid\tv1;v2;v3`, `comp\tid\tMM/AAAA`, `aprox\tid\t0|1`, `sal\tcentavos\tcompetencia`,
     * `extra\tchave\tvalor` (chaves: selicMeta, inflacao12m, rotativo, cheque),
     * `manual\tid` (R100: o item foi editado pelo usuário). R100: só semente + edições MANUAIS são gravadas; valores vindos do
     * Banco Central (origem BCB) NUNCA entram aqui.
     */
    fun serializar(c: Conjunto): String = buildString {
        val base = padrao()
        if (c.conferidoEm != base.conferidoEm) append("conf\t${c.conferidoEm}\n")
        for (i in c.itens) {
            val p = base.itens.firstOrNull { it.id == i.id } ?: continue
            if (i.origem == Origem.BCB) continue
            if (i.origem == Origem.MANUAL) append("manual\t${i.id}\n")
            if (i.serie != p.serie) append("serie\t${i.id}\t${i.serie.joinToString(";")}\n")
            if (i.competencia != p.competencia) append("comp\t${i.id}\t${i.competencia}\n")
            if (i.aproximada != p.aproximada) append("aprox\t${i.id}\t${if (i.aproximada) 1 else 0}\n")
        }
        val sm = c.salarioMinimo
        if (sm.origem != Origem.BCB && (sm.origem == Origem.MANUAL || sm.centavos != base.salarioMinimo.centavos || sm.competencia != base.salarioMinimo.competencia)) append("sal\t${c.salarioMinimo.centavos}\t${c.salarioMinimo.competencia}\n")
        val e = c.extras
        if (e.selicMetaOrigem != Origem.BCB) e.selicMetaAnualPct?.let { append("extra\tselicMeta\t$it\n") }
        e.inflacao12mPct?.let { append("extra\tinflacao12m\t$it\n") }
        if (e.taxaRotativoMensalPct != base.extras.taxaRotativoMensalPct) append("extra\trotativo\t${e.taxaRotativoMensalPct}\n")
        if (e.taxaChequeEspecialMensalPct != base.extras.taxaChequeEspecialMensalPct) append("extra\tcheque\t${e.taxaChequeEspecialMensalPct}\n")
    }

    /** Reconstrói a partir do texto salvo: parte da semente e só aceita campos válidos (nunca lança). */
    fun desserializar(texto: String?): Conjunto {
        val base = padrao()
        if (texto.isNullOrBlank()) return base
        var conf = base.conferidoEm
        val itens = base.itens.toMutableList()
        var sal = base.salarioMinimo
        var ex = base.extras
        fun troca(id: String, f: (Item) -> Item) {
            val k = itens.indexOfFirst { it.id == id }
            if (k >= 0) itens[k] = f(itens[k])
        }
        for (linha in texto.lines()) {
            val p = linha.split('\t')
            when (p.firstOrNull()) {
                "conf" -> p.getOrNull(1)?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }?.let { conf = it }
                // R100: edição salva (inclusive as da R98, sem a linha `manual`) = valor MANUAL, que o Banco Central não sobrescreve.
                "manual" -> if (p.size >= 2) troca(p[1]) { it.copy(origem = Origem.MANUAL) }
                "serie" -> if (p.size >= 3) {
                    val v = p[2].split(';').map { it.toDoubleOrNull() }
                    if (v.size in 1..24 && v.all { ok(it) }) troca(p[1]) { it.copy(serie = v.map { x -> x!! }, origem = Origem.MANUAL) }
                }
                "comp" -> if (p.size >= 3 && (p[2].isEmpty() || Regex("^\\d{2}/\\d{4}$").matches(p[2]))) troca(p[1]) { it.copy(competencia = p[2], origem = Origem.MANUAL) }
                "aprox" -> if (p.size >= 3) troca(p[1]) { it.copy(aproximada = p[2] == "1") }
                "sal" -> if (p.size >= 3) {
                    val cts = p[1].toLongOrNull()
                    if (cts != null && cts > 0 && cts < 1_000_000_000L) sal = SalarioMinimo(cts, p[2].take(12), Origem.MANUAL)
                }
                "extra" -> if (p.size >= 3) {
                    val v = p[2].toDoubleOrNull()
                    if (ok(v)) ex = when (p[1]) {
                        "selicMeta" -> ex.copy(selicMetaAnualPct = v, selicMetaOrigem = Origem.MANUAL)
                        "inflacao12m" -> ex.copy(inflacao12mPct = v)
                        "rotativo" -> if (v!! >= 0) ex.copy(taxaRotativoMensalPct = v) else ex
                        "cheque" -> if (v!! >= 0) ex.copy(taxaChequeEspecialMensalPct = v) else ex
                        else -> ex
                    }
                }
            }
        }
        return Conjunto(conf, itens, sal, ex)
    }
}
