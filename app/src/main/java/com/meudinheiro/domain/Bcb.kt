package com.meudinheiro.domain

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.meudinheiro.domain.IndicesEconomicos.Conjunto
import com.meudinheiro.domain.IndicesEconomicos.Origem
import java.util.Calendar
import java.util.TimeZone
import java.util.TreeMap

/**
 * R100 — consulta PÚBLICA ao Banco Central do Brasil (SGS e PTAX), lógica PURA (sem rede, sem Android). Paridade com
 * `portal/src/lib/finance/bcb.ts` (R99).
 *
 * - Parsing/validação nunca lançam: lixo vira "sem valor". Datas em ISO (AAAA-MM-DD) internamente; "hoje" é sempre injetado e
 *   datas futuras são inválidas (a série 432 e a 1619 devolvem datas posteriores a hoje).
 * - A rede vive em `funcoes/BcbClient.kt` (só GET público, sem dado do usuário); este arquivo só monta URLs e interpreta respostas.
 * - Valores do BCB NÃO entram na base editável (DataStore dos índices): vivem só no cache (`cache JSON` abaixo) e são mesclados por
 *   [aplicarBcb], com a edição manual sempre prevalecendo.
 */
object Bcb {

    const val SGS_BASE = "https://api.bcb.gov.br/dados/serie/bcdata.sgs."
    const val PTAX_BASE = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata"
    const val HOST_SGS = "api.bcb.gov.br"
    const val HOST_PTAX = "olinda.bcb.gov.br"
    const val TIMEOUT_MS = 8000L

    /** Validade do cache local: 6 horas. */
    const val TTL_MS = 6L * 60 * 60 * 1000
    const val FONTE_BCB = "Banco Central do Brasil (SGS/PTAX)"

    // ------------------------------------------------------------------ séries SGS

    enum class TipoSerie { MENSAL, SELIC_META, SALARIO }

    /** [chave] = id do índice (selic, cdi, …) ou "selicMeta"/"salarioMinimo". */
    data class DefSerie(val chave: String, val codigo: Int, val tipo: TipoSerie, val nome: String)

    /** IVAR, TBF e TJLP não têm série mensal em % confirmada: ficam como a semente/edição manual. */
    val SERIES: List<DefSerie> = listOf(
        DefSerie("selic", 4390, TipoSerie.MENSAL, "Selic acumulada no mês"),
        DefSerie("cdi", 4391, TipoSerie.MENSAL, "CDI acumulado no mês"),
        DefSerie("tr", 7811, TipoSerie.MENSAL, "TR mensal"),
        DefSerie("poupanca", 196, TipoSerie.MENSAL, "Rendimento mensal da poupança"),
        DefSerie("ipca", 433, TipoSerie.MENSAL, "IPCA"),
        DefSerie("inpc", 188, TipoSerie.MENSAL, "INPC"),
        DefSerie("igpm", 189, TipoSerie.MENSAL, "IGP-M"),
        DefSerie("igpdi", 190, TipoSerie.MENSAL, "IGP-DI"),
        DefSerie("incc", 192, TipoSerie.MENSAL, "INCC-DI"),
        DefSerie("ipcfipe", 7465, TipoSerie.MENSAL, "IPC-Fipe"),
        DefSerie("ipcae", 10764, TipoSerie.MENSAL, "IPCA-E"),
        DefSerie("selicMeta", 432, TipoSerie.SELIC_META, "Meta da Selic (% a.a.)"),
        DefSerie("salarioMinimo", 1619, TipoSerie.SALARIO, "Salário mínimo")
    )

    private val IDS_MENSAIS: Set<String> = SERIES.filter { it.tipo == TipoSerie.MENSAL }.map { it.chave }.toSet()

    /** Quantos meses (valores) guardar por índice mensal. */
    const val MESES_SERIE = 3

    class Faixa(val min: Double, val max: Double) {
        operator fun contains(v: Double) = v in min..max
    }

    /** Faixas plausíveis: valores fora delas são descartados como lixo. */
    val FAIXA_MENSAL = Faixa(-5.0, 10.0)
    val FAIXA_SELIC_META = Faixa(0.0, 60.0)
    val FAIXA_SALARIO = Faixa(100.0, 100000.0)

    /** [data] AAAA-MM-DD. */
    data class PontoSgs(val data: String, val valor: Double)

    fun urlSgs(codigo: Int, ultimos: Int): String = "$SGS_BASE$codigo/dados/ultimos/$ultimos?formato=json"

    /** Quantos pontos pedir por série: margem para descartar datas futuras (432/1619). */
    fun ultimosPedidos(def: DefSerie): Int = if (def.tipo == TipoSerie.MENSAL) 8 else 12

    /**
     * Só HTTPS e só os dois hosts do Banco Central, sem credenciais embutidas e sem porta. Qualquer outra URL é recusada
     * (o cliente chama isto antes de cada requisição).
     */
    fun urlPermitida(url: String): Boolean {
        val u = try {
            java.net.URI(url)
        } catch (e: Exception) {
            return false
        }
        if (u.scheme != "https" || u.userInfo != null || u.port != -1 || u.fragment != null) return false
        return when (u.host) {
            HOST_SGS -> url.startsWith(SGS_BASE)
            HOST_PTAX -> url.startsWith("$PTAX_BASE/")
            else -> false
        }
    }

    private fun diasNoMes(ano: Int, mes: Int): Int = when (mes) {
        2 -> if ((ano % 4 == 0 && ano % 100 != 0) || ano % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private val RE_DATA_BR = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")

    /** "dd/mm/aaaa" -> "aaaa-mm-dd" (nulo se inválida, inclusive dia inexistente). */
    fun dataBrParaIso(s: String?): String? {
        val m = RE_DATA_BR.matchEntire(s?.trim() ?: return null) ?: return null
        val d = m.groupValues[1].toInt()
        val mes = m.groupValues[2].toInt()
        val a = m.groupValues[3].toInt()
        if (mes !in 1..12 || d < 1 || d > diasNoMes(a, mes)) return null
        return "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}"
    }

    private fun p2(n: Int) = n.toString().padStart(2, '0')

    private fun cal(ms: Long, tz: TimeZone): Calendar = Calendar.getInstance(tz).apply { timeInMillis = ms }

    /** "AAAA-MM-DD" do dia local (no fuso [tz]) do instante [ms]. */
    fun hojeIso(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val c = cal(ms, tz)
        return "${c.get(Calendar.YEAR).toString().padStart(4, '0')}-${p2(c.get(Calendar.MONTH) + 1)}-${p2(c.get(Calendar.DAY_OF_MONTH))}"
    }

    /** Competência "MM/AAAA" de uma data ISO. */
    fun competenciaDeIso(iso: String): String = "${iso.substring(5, 7)}/${iso.substring(0, 4)}"

    private val RE_NUMERO = Regex("^[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?$")

    /** Valor do BCB (string "0.82" com ponto decimal, ou número) -> número finito; senão NaN. */
    fun lerValor(e: JsonElement?): Double {
        if (e == null || !e.isJsonPrimitive) return Double.NaN
        val p = e.asJsonPrimitive
        return when {
            p.isNumber -> p.asDouble.takeIf { it.isFinite() } ?: Double.NaN
            p.isString -> {
                val s = p.asString.trim()
                if (s.isEmpty() || !RE_NUMERO.matches(s)) Double.NaN else s.toDoubleOrNull()?.takeIf { it.isFinite() } ?: Double.NaN
            }
            else -> Double.NaN
        }
    }

    private fun parseJson(texto: String?): JsonElement? =
        try {
            if (texto.isNullOrBlank()) null else JsonParser.parseString(texto)
        } catch (e: Exception) {
            null
        }

    private fun str(o: JsonObject, k: String): String? =
        o.get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    /** Resposta crua do SGS (texto JSON) -> pontos válidos (data válida e valor numérico). Nunca lança. */
    fun parsePontosSgs(texto: String?): List<PontoSgs> {
        val arr = parseJson(texto) as? JsonArray ?: return emptyList()
        val out = ArrayList<PontoSgs>()
        for (it in arr) {
            val o = it as? JsonObject ?: continue
            val data = dataBrParaIso(str(o, "data")) ?: continue
            val valor = lerValor(o.get("valor"))
            if (valor.isFinite()) out += PontoSgs(data, valor)
        }
        return out
    }

    /** Mantém só pontos com data <= [hoje] e valor na [faixa]; ordena do mais antigo ao mais recente. */
    fun filtrarPontos(pontos: List<PontoSgs>, hoje: String, faixa: Faixa): List<PontoSgs> =
        pontos.filter { it.data <= hoje && it.valor in faixa }.sortedBy { it.data }

    /** Item mais recente cuja data <= [hoje] (e valor na faixa); nulo se não houver. */
    fun maisRecenteAteHoje(pontos: List<PontoSgs>, hoje: String, faixa: Faixa): PontoSgs? = filtrarPontos(pontos, hoje, faixa).lastOrNull()

    /** [serie] em %, do mais antigo ao mais recente (um por mês); [parcial] = competência é o mês corrente. */
    data class IndiceBcb(val serie: List<Double>, val competencia: String, val parcial: Boolean)

    /**
     * Série mensal -> últimos [n] meses (um valor por mês; se repetir, vale o último) com a competência real. A data da série é o
     * 1º dia do mês de referência. `parcial` = competência igual ao mês de [hoje].
     */
    fun indiceMensal(pontos: List<PontoSgs>, hoje: String, n: Int = MESES_SERIE): IndiceBcb? {
        val porMes = TreeMap<String, Double>()
        for (p in filtrarPontos(pontos, hoje, FAIXA_MENSAL)) porMes[p.data.substring(0, 7)] = p.valor
        if (porMes.isEmpty()) return null
        val ult = porMes.keys.toList().takeLast(n)
        val ultimo = ult.last()
        return IndiceBcb(ult.map { porMes.getValue(it) }, "${ultimo.substring(5, 7)}/${ultimo.substring(0, 4)}", ultimo == hoje.substring(0, 7))
    }

    data class SalarioBcb(val centavos: Long, val competencia: String)

    fun salarioDe(pontos: List<PontoSgs>, hoje: String): SalarioBcb? =
        maisRecenteAteHoje(pontos, hoje, FAIXA_SALARIO)?.let { SalarioBcb(Math.round(it.valor * 100), competenciaDeIso(it.data)) }

    /** [valor] % a.a.; [data] AAAA-MM-DD da vigência encontrada. */
    data class SelicMetaBcb(val valor: Double, val data: String)

    fun selicMetaDe(pontos: List<PontoSgs>, hoje: String): SelicMetaBcb? =
        maisRecenteAteHoje(pontos, hoje, FAIXA_SELIC_META)?.let { SelicMetaBcb(it.valor, it.data) }

    // ------------------------------------------------------------------ snapshot de índices

    data class Falha(val fonte: String, val motivo: String)

    /** [atualizadoEm] epoch ms da consulta. [itens] por id de índice. */
    data class SnapshotIndices(
        val atualizadoEm: Long,
        val itens: Map<String, IndiceBcb> = emptyMap(),
        val selicMeta: SelicMetaBcb? = null,
        val salarioMinimo: SalarioBcb? = null,
        val falhas: List<Falha> = emptyList()
    ) {
        val vazio: Boolean get() = itens.isEmpty() && selicMeta == null && salarioMinimo == null
    }

    /**
     * Interpreta a resposta (texto JSON) de UMA série. Lança [IllegalStateException] "sem valor válido" quando nenhum ponto serve
     * (o cliente transforma isso em falha isolada daquela série). Sem rede.
     */
    fun interpretarSerie(def: DefSerie, texto: String?, hoje: String): Resultado {
        val pontos = parsePontosSgs(texto)
        return when (def.tipo) {
            TipoSerie.MENSAL -> {
                val r = indiceMensal(pontos, hoje) ?: throw IllegalStateException("sem valor válido")
                // "parcial do mês" só para o acumulado de Selic/CDI; TR/poupança do mês vigente são taxas fixas do período
                Resultado.Indice(def.chave, if (def.chave == "selic" || def.chave == "cdi") r else r.copy(parcial = false))
            }
            TipoSerie.SELIC_META -> Resultado.Meta(selicMetaDe(pontos, hoje) ?: throw IllegalStateException("sem valor válido"))
            TipoSerie.SALARIO -> Resultado.Salario(salarioDe(pontos, hoje) ?: throw IllegalStateException("sem valor válido"))
        }
    }

    sealed class Resultado {
        data class Indice(val chave: String, val valor: IndiceBcb) : Resultado()
        data class Meta(val valor: SelicMetaBcb) : Resultado()
        data class Salario(val valor: SalarioBcb) : Resultado()
    }

    /** Junta consulta nova sobre a anterior: o que falhou agora mantém o último valor conhecido. */
    fun mesclarSnapshots(antigo: SnapshotIndices?, novo: SnapshotIndices): SnapshotIndices {
        if (antigo == null) return novo
        return SnapshotIndices(
            atualizadoEm = novo.atualizadoEm,
            itens = antigo.itens + novo.itens,
            selicMeta = novo.selicMeta ?: antigo.selicMeta,
            salarioMinimo = novo.salarioMinimo ?: antigo.salarioMinimo,
            falhas = novo.falhas
        )
    }

    // ------------------------------------------------------------------ mesclagem manual × BCB

    /**
     * Conjunto EFETIVO = [base] (semente + edições manuais) com os valores do BCB por cima, EXCETO onde o usuário editou
     * (MANUAL prevalece e nunca é sobrescrito). A meta da Selic do BCB só preenche se o usuário não informou. Não altera [base].
     */
    fun aplicarBcb(base: Conjunto, snap: SnapshotIndices?): Conjunto {
        val extrasBase = base.extras.copy(selicMetaOrigem = if (base.extras.selicMetaAnualPct == null) null else base.extras.selicMetaOrigem ?: Origem.MANUAL)
        if (snap == null) return base.copy(extras = extrasBase)
        val itens = base.itens.map { it0 ->
            val b = snap.itens[it0.id]
            if (b == null || it0.origem == Origem.MANUAL) it0
            else it0.copy(serie = b.serie, competencia = b.competencia, aproximada = false, parcial = b.parcial, origem = Origem.BCB)
        }
        val sal = snap.salarioMinimo
        val salario = if (sal != null && base.salarioMinimo.origem != Origem.MANUAL) IndicesEconomicos.SalarioMinimo(sal.centavos, sal.competencia, Origem.BCB) else base.salarioMinimo
        val meta = snap.selicMeta
        val extras = if (meta != null && base.extras.selicMetaAnualPct == null) extrasBase.copy(selicMetaAnualPct = meta.valor, selicMetaOrigem = Origem.BCB) else extrasBase
        return base.copy(itens = itens, salarioMinimo = salario, extras = extras)
    }

    /** Dica calculada da poupança (regra: Selic > 8,5% a.a. => 0,5% a.m. + TR; senão 70% da Selic + TR), em fração; nulo sem Selic meta. */
    fun dicaPoupanca(c: Conjunto): Double? {
        val meta = c.extras.selicMetaAnualPct ?: return null
        val tr = IndicesEconomicos.buscar(c, "tr").valorMensalPct
        if (!tr.isFinite()) return null
        return Rendimentos.poupancaPelaRegra(meta / 100, IndicesEconomicos.pctParaFracao(tr))
    }

    // ------------------------------------------------------------------ cache (TTL) e texto de estado

    /** Verdadeiro quando não há cache, a data é inválida/futura ou passaram 6 h (ou mais). */
    fun cacheExpirado(atualizadoEm: Long?, agora: Long, ttl: Long = TTL_MS): Boolean {
        if (atualizadoEm == null) return true
        val idade = agora - atualizadoEm
        return idade < 0 || idade >= ttl
    }

    fun serializarSnapshotIndices(s: SnapshotIndices): String {
        val o = JsonObject()
        o.addProperty("atualizadoEm", s.atualizadoEm)
        val itens = JsonObject()
        for ((id, v) in s.itens) {
            val x = JsonObject()
            val arr = JsonArray()
            v.serie.forEach { arr.add(it) }
            x.add("serie", arr)
            x.addProperty("competencia", v.competencia)
            x.addProperty("parcial", v.parcial)
            itens.add(id, x)
        }
        o.add("itens", itens)
        s.selicMeta?.let { o.add("selicMeta", JsonObject().apply { addProperty("valor", it.valor); addProperty("data", it.data) }) }
        s.salarioMinimo?.let { o.add("salarioMinimo", JsonObject().apply { addProperty("centavos", it.centavos); addProperty("competencia", it.competencia) }) }
        return o.toString()
    }

    private val RE_COMP = Regex("^\\d{2}/\\d{4}$")
    private val RE_ISO = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private fun numero(e: JsonElement?): Double? =
        if (e != null && e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asDouble.takeIf { it.isFinite() } else null

    /** Reconstrói o snapshot de índices a partir do JSON salvo, validando tudo. Nulo se inválido. */
    fun lerSnapshotIndices(texto: String?): SnapshotIndices? {
        val r = parseJson(texto) as? JsonObject ?: return null
        val em = numero(r.get("atualizadoEm"))?.toLong() ?: return null
        val itens = LinkedHashMap<String, IndiceBcb>()
        (r.get("itens") as? JsonObject)?.entrySet()?.forEach { (id, v) ->
            val x = v as? JsonObject ?: return@forEach
            if (id !in IDS_MENSAIS) return@forEach
            val serie = (x.get("serie") as? JsonArray)?.map { numero(it) }
            val comp = str(x, "competencia")
            if (serie != null && serie.size in 1..24 && serie.all { it != null && it in FAIXA_MENSAL } && comp != null && RE_COMP.matches(comp)) {
                itens[id] = IndiceBcb(serie.map { it!! }, comp, x.get("parcial")?.let { it !is JsonNull && it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true)
            }
        }
        val sm = r.get("selicMeta") as? JsonObject
        val meta = sm?.let { val v = numero(it.get("valor")); val d = str(it, "data"); if (v != null && v in FAIXA_SELIC_META && d != null && RE_ISO.matches(d)) SelicMetaBcb(v, d) else null }
        val sl = r.get("salarioMinimo") as? JsonObject
        val sal = sl?.let {
            val c = numero(it.get("centavos"))
            val comp = str(it, "competencia")
            if (c != null && c == Math.floor(c) && c > 0 && c < 1e9 && comp != null && comp.length <= 12) SalarioBcb(c.toLong(), comp) else null
        }
        return SnapshotIndices(em, itens, meta, sal, emptyList())
    }

    /** "28/10 14:05" no fuso [tz]. */
    fun fmtDataHora(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val c = cal(ms, tz)
        return "${p2(c.get(Calendar.DAY_OF_MONTH))}/${p2(c.get(Calendar.MONTH) + 1)} ${p2(c.get(Calendar.HOUR_OF_DAY))}:${p2(c.get(Calendar.MINUTE))}"
    }

    data class EstadoAtualizacao(val atualizadoEm: Long?, val carregando: Boolean, val erro: String?, val offline: Boolean)

    /** Linha de estado exibida na tela (mesmos textos do portal). */
    fun descreverEstado(e: EstadoAtualizacao, tz: TimeZone = TimeZone.getDefault()): String {
        if (e.carregando) return "Atualizando com o Banco Central…"
        if (e.offline) return if (e.atualizadoEm != null) "Offline: usando valores salvos de ${fmtDataHora(e.atualizadoEm, tz)}" else "Offline: usando os valores de referência (semente)."
        if (e.erro != null) {
            val resto = if (e.atualizadoEm != null) " Usando valores salvos de ${fmtDataHora(e.atualizadoEm, tz)}." else " Usando os valores de referência (semente)."
            return "Não foi possível atualizar: ${e.erro}.$resto"
        }
        if (e.atualizadoEm != null) return "Atualizado em ${fmtDataHora(e.atualizadoEm, tz)} — Banco Central"
        return "Ainda não atualizado: usando os valores de referência (semente)."
    }

    // ------------------------------------------------------------------ PTAX (câmbio)

    data class MoedaDef(val codigo: String, val nome: String)

    val MOEDAS: List<MoedaDef> = listOf(
        MoedaDef("USD", "Dólar americano"), MoedaDef("EUR", "Euro"), MoedaDef("GBP", "Libra esterlina"), MoedaDef("JPY", "Iene japonês"),
        MoedaDef("CHF", "Franco suíço"), MoedaDef("CAD", "Dólar canadense"), MoedaDef("AUD", "Dólar australiano"),
        MoedaDef("DKK", "Coroa dinamarquesa"), MoedaDef("NOK", "Coroa norueguesa"), MoedaDef("SEK", "Coroa sueca")
    )

    fun nomeMoeda(codigo: String): String = MOEDAS.firstOrNull { it.codigo == codigo }?.nome ?: codigo

    /** Casas decimais exibidas por moeda (o iene vale centavos de real). */
    fun casas(codigo: String): Int = if (codigo == "JPY") 6 else 4

    enum class TipoBoletim(val rotulo: String) {
        ABERTURA("Abertura"), INTERMEDIARIO("Intermediário"), FECHAMENTO("Fechamento");

        companion object {
            fun de(s: String?): TipoBoletim? = entries.firstOrNull { it.rotulo == s }
        }
    }

    /** [dataHora] "AAAA-MM-DD HH:mm:ss"; [compra]/[venda] em R$ por 1 unidade da moeda. */
    data class Boletim(val dataHora: String, val compra: Double, val venda: Double, val tipo: TipoBoletim)

    /** [intermediario] = boletim intermediário do dia corrente (ainda sem fechamento). [variacaoPct] da venda contra o fechamento anterior. */
    data class CotacaoMoeda(
        val moeda: String, val compra: Double, val venda: Double, val dataHora: String, val tipo: TipoBoletim,
        val intermediario: Boolean, val variacaoPct: Double?
    )

    data class SnapshotCambio(val atualizadoEm: Long, val cotacoes: List<CotacaoMoeda>, val falhas: List<Falha> = emptyList())

    /** MM-DD-AAAA exigido pelo OData da PTAX (data local de [ms]). */
    fun dataPtax(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val c = cal(ms, tz)
        return "${p2(c.get(Calendar.MONTH) + 1)}-${p2(c.get(Calendar.DAY_OF_MONTH))}-${c.get(Calendar.YEAR).toString().padStart(4, '0')}"
    }

    fun urlPtax(moeda: String, inicioMs: Long, fimMs: Long, tz: TimeZone = TimeZone.getDefault()): String =
        "$PTAX_BASE/CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)" +
            "?@moeda='$moeda'&@dataInicial='${dataPtax(inicioMs, tz)}'&@dataFinalCotacao='${dataPtax(fimMs, tz)}'" +
            "&\$orderby=dataHoraCotacao%20desc&\$format=json"

    private val RE_DATA_HORA = Regex("^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})")

    /** Resposta OData -> boletins válidos (compra/venda > 0 e <= 1e6, tipo conhecido, data <= hoje), do mais recente ao mais antigo. Nunca lança. */
    fun parseBoletins(texto: String?, hoje: String): List<Boletim> {
        val lista = (parseJson(texto) as? JsonObject)?.get("value") as? JsonArray ?: return emptyList()
        val out = ArrayList<Boletim>()
        for (it in lista) {
            val o = it as? JsonObject ?: continue
            val compra = lerValor(o.get("cotacaoCompra"))
            val venda = lerValor(o.get("cotacaoVenda"))
            val dh = str(o, "dataHoraCotacao")?.let { s -> RE_DATA_HORA.find(s) } ?: continue
            val tipo = TipoBoletim.de(str(o, "tipoBoletim")) ?: continue
            if (!(compra > 0) || !(venda > 0) || compra > 1e6 || venda > 1e6) continue
            if (dh.groupValues[1] > hoje) continue
            out += Boletim("${dh.groupValues[1]} ${dh.groupValues[2]}", compra, venda, tipo)
        }
        return out.sortedByDescending { it.dataHora }
    }

    /** Um fechamento por dia (o mais recente do dia), do mais recente ao mais antigo. */
    fun fechamentosPorDia(boletins: List<Boletim>): List<Boletim> {
        val vistos = HashSet<String>()
        val out = ArrayList<Boletim>()
        for (b in boletins.sortedByDescending { it.dataHora }) {
            if (b.tipo != TipoBoletim.FECHAMENTO) continue
            if (!vistos.add(b.dataHora.substring(0, 10))) continue
            out += b
        }
        return out
    }

    /**
     * Cotação exibida: o fechamento mais recente; se existir intermediário de dia POSTERIOR ao último fechamento (dia corrente antes
     * do fechamento), usa o intermediário mais recente, rotulado. Variação % da venda contra o fechamento de dia anterior ao exibido.
     */
    fun calcularCotacao(moeda: String, boletins: List<Boletim>): CotacaoMoeda? {
        val fech = fechamentosPorDia(boletins)
        val ultimoInterm = boletins.filter { it.tipo != TipoBoletim.FECHAMENTO }.fold(null as Boletim?) { a, b -> if (a != null && a.dataHora >= b.dataHora) a else b }
        val ultimoFech = fech.firstOrNull()
        var atual: Boletim? = ultimoFech
        if (ultimoInterm != null && (ultimoFech == null || ultimoInterm.dataHora.substring(0, 10) > ultimoFech.dataHora.substring(0, 10))) atual = ultimoInterm
        if (atual == null) return null
        val dia = atual.dataHora.substring(0, 10)
        val anterior = fech.firstOrNull { it.dataHora.substring(0, 10) < dia }
        val variacao = anterior?.let { (atual.venda / it.venda - 1) * 100 }
        return CotacaoMoeda(moeda, atual.compra, atual.venda, atual.dataHora, atual.tipo, atual.tipo != TipoBoletim.FECHAMENTO, variacao)
    }

    const val DIA_MS = 24L * 60 * 60 * 1000

    /** Janela de ~10 dias corridos para o quadro de cotações. */
    const val JANELA_COTACAO_DIAS = 10

    /** Janela de ~45 dias corridos para o gráfico (≈ 30 pregões). */
    const val JANELA_HISTORICO_DIAS = 45

    /** [data] AAAA-MM-DD. */
    data class PontoHistorico(val data: String, val venda: Double)

    /** Fechamentos da janela do gráfico, do mais antigo ao mais recente, até 30 pontos. */
    fun historicoDe(boletins: List<Boletim>): List<PontoHistorico> =
        fechamentosPorDia(boletins).take(30).reversed().map { PontoHistorico(it.dataHora.substring(0, 10), it.venda) }

    fun serializarSnapshotCambio(s: SnapshotCambio): String {
        val o = JsonObject()
        o.addProperty("atualizadoEm", s.atualizadoEm)
        val arr = JsonArray()
        for (c in s.cotacoes) {
            arr.add(JsonObject().apply {
                addProperty("moeda", c.moeda); addProperty("compra", c.compra); addProperty("venda", c.venda)
                addProperty("dataHora", c.dataHora); addProperty("tipo", c.tipo.rotulo)
                if (c.variacaoPct == null) add("variacaoPct", JsonNull.INSTANCE) else addProperty("variacaoPct", c.variacaoPct)
            })
        }
        o.add("cotacoes", arr)
        return o.toString()
    }

    /** Reconstrói o snapshot de câmbio salvo; nulo se inválido ou sem nenhuma cotação válida. */
    fun lerSnapshotCambio(texto: String?): SnapshotCambio? {
        val r = parseJson(texto) as? JsonObject ?: return null
        val em = numero(r.get("atualizadoEm"))?.toLong() ?: return null
        val lista = r.get("cotacoes") as? JsonArray ?: return null
        val cotacoes = ArrayList<CotacaoMoeda>()
        for (it in lista) {
            val c = it as? JsonObject ?: continue
            val moeda = str(c, "moeda") ?: continue
            val compra = numero(c.get("compra"))
            val venda = numero(c.get("venda"))
            val dh = str(c, "dataHora")
            val tipo = TipoBoletim.de(str(c, "tipo"))
            val vElem = c.get("variacaoPct")
            val variacao = if (vElem == null || vElem is JsonNull) null else numero(vElem) ?: continue
            if (MOEDAS.none { m -> m.codigo == moeda } || compra == null || compra <= 0 || venda == null || venda <= 0 || dh == null || tipo == null) continue
            cotacoes += CotacaoMoeda(moeda, compra, venda, dh, tipo, tipo != TipoBoletim.FECHAMENTO, variacao)
        }
        return if (cotacoes.isEmpty()) null else SnapshotCambio(em, cotacoes)
    }

    // ------------------------------------------------------------------ conversor

    /** Spread/IOF informado pelo usuário: % entre 0 e 100 (padrão 0). */
    fun spreadValido(pct: Double): Boolean = pct.isFinite() && pct in 0.0..100.0

    /** Quantas unidades da moeda [reais] compram, com a venda PTAX e o acréscimo %: reais / (venda × (1 + spread)). NaN se inválido. */
    fun reaisParaMoeda(reais: Double, venda: Double, spreadPct: Double = 0.0): Double =
        if (!reais.isFinite() || !(venda > 0) || !spreadValido(spreadPct)) Double.NaN else reais / (venda * (1 + spreadPct / 100))

    /** Custo em R$ de [unidades] da moeda: unidades × venda × (1 + spread). NaN se inválido. */
    fun moedaParaReais(unidades: Double, venda: Double, spreadPct: Double = 0.0): Double =
        if (!unidades.isFinite() || !(venda > 0) || !spreadValido(spreadPct)) Double.NaN else unidades * venda * (1 + spreadPct / 100)
}
