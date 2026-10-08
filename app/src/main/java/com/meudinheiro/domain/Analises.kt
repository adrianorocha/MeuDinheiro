package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Investimento
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

/** Análises e atalhos do dia a dia (R21–R30). Funções puras — espelhadas no portal (docs/CONTRATO_DADOS.md §6). */
object Analises {

    private const val DIA_MS = 86_400_000L

    // ------------------------------------------------------------------ R21

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    /** Categoria mais provável para [descricao], aprendida do histórico; `null` se nada for parecido. */
    fun sugerirCategoria(descricao: String, historico: List<Despesa>): String? {
        val alvoNorm = Texto.normalizar(descricao)
        if (alvoNorm.isEmpty()) return null
        val alvoTokens = Texto.tokens(descricao)
        val pontos = HashMap<String, Double>()
        val maisRecente = HashMap<String, Despesa>()
        for (h in historico) {
            if (h.natureza != Natureza.NORMAL || h.categoria.isBlank()) continue
            val sim = if (Texto.normalizar(h.descricao) == alvoNorm) 1.0 else jaccard(alvoTokens, Texto.tokens(h.descricao))
            if (sim < 0.5) continue
            val chave = Texto.normalizar(h.categoria)
            pontos[chave] = (pontos[chave] ?: 0.0) + sim
            if ((maisRecente[chave]?.dataMs ?: Long.MIN_VALUE) < h.dataMs) maisRecente[chave] = h
        }
        val melhor = pontos.entries.maxWithOrNull(
            compareBy<Map.Entry<String, Double>>({ it.value }, { maisRecente[it.key]?.dataMs ?: 0L })
        ) ?: return null
        return maisRecente[melhor.key]?.categoria
    }

    // ------------------------------------------------------------------ R22

    data class AlertaOrcamento(val categoria: String, val limiar: Int, val chave: String)

    /**
     * Alertas novos de orçamento. [jaAvisados] guarda chaves `categoria|AAAA-MM|limiar`; ao emitir o de 100%
     * o de 80% também deve ser marcado (ver [chavesParaMarcar]).
     */
    fun alertasOrcamento(
        progressos: List<Pair<String, Double>>,
        mes: String,
        jaAvisados: Set<String>
    ): List<AlertaOrcamento> = progressos.mapNotNull { (categoria, pct) ->
        fun chave(l: Int) = "${Texto.normalizar(categoria)}|$mes|$l"
        when {
            pct >= 1.0 && chave(100) !in jaAvisados -> AlertaOrcamento(categoria, 100, chave(100))
            pct in 0.8..<1.0 && chave(80) !in jaAvisados && chave(100) !in jaAvisados -> AlertaOrcamento(categoria, 80, chave(80))
            else -> null
        }
    }

    fun chavesParaMarcar(alerta: AlertaOrcamento): Set<String> =
        if (alerta.limiar == 100) setOf(alerta.chave, alerta.chave.removeSuffix("100") + "80") else setOf(alerta.chave)

    // ------------------------------------------------------------------ R23

    enum class UnidadeRepeticao { DIAS, SEMANAS, MESES }

    /** Cópia de um lançamento comum para hoje, em aberto. `null` para lançamentos internos. */
    fun duplicar(original: Despesa, agora: Long): Despesa? {
        if (original.natureza != Natureza.NORMAL) return null
        return original.copy(
            id = 0, data = Date(agora), mes = Financas.mesDe(agora), ano = Financas.anoDe(agora),
            pago = false, grupoId = null
        )
    }

    /** [n] ocorrências futuras (1..n) a cada [intervalo] [unidade]. Sempre em aberto, com `grupoId = rep:<uuid>`. */
    fun repetir(
        base: Despesa,
        n: Int,
        intervalo: Int,
        unidade: UnidadeRepeticao,
        grupoId: String = "rep:${UUID.randomUUID()}"
    ): List<Despesa> {
        require(n in 1..120) { "Número de repetições inválido (1 a 120)" }
        require(intervalo >= 1) { "Intervalo inválido" }
        require(base.natureza == Natureza.NORMAL) { "Só lançamentos comuns podem ser repetidos" }
        val diaOriginal = Calendar.getInstance().apply { timeInMillis = base.dataMs }.get(Calendar.DAY_OF_MONTH)
        return (1..n).map { k ->
            val ms = when (unidade) {
                UnidadeRepeticao.MESES -> Financas.somarMeses(base.dataMs, k * intervalo, diaOriginal)
                UnidadeRepeticao.DIAS, UnidadeRepeticao.SEMANAS -> Calendar.getInstance().apply {
                    timeInMillis = base.dataMs
                    add(Calendar.DAY_OF_YEAR, k * intervalo * if (unidade == UnidadeRepeticao.SEMANAS) 7 else 1)
                }.timeInMillis
            }
            base.copy(id = 0, data = Date(ms), mes = Financas.mesDe(ms), ano = Financas.anoDe(ms), pago = false, grupoId = grupoId)
        }
    }

    // ------------------------------------------------------------------ R24

    data class ResultadoBusca(
        val lancamentos: List<Despesa>,
        val contas: List<ContaSaldo>,
        val cartoes: List<Cartao>,
        val metas: List<Meta>
    ) {
        val vazio get() = lancamentos.isEmpty() && contas.isEmpty() && cartoes.isEmpty() && metas.isEmpty()
    }

    private val REGEX_DATA = Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?$")

    fun buscar(
        termo: String,
        despesas: List<Despesa>,
        contas: List<ContaSaldo>,
        cartoes: List<Cartao>,
        metas: List<Meta>,
        limite: Int = 200
    ): ResultadoBusca {
        val t = Texto.normalizar(termo)
        if (t.isEmpty()) return ResultadoBusca(emptyList(), emptyList(), emptyList(), emptyList())
        val valorC = t.replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let { Dinheiro.centavos(it) }
        val data = REGEX_DATA.matchEntire(t)?.destructured?.let { (d, m, a) -> Triple(d.toInt(), m.toInt(), a.toIntOrNull()) }
        val bancoPorConta = contas.associate { it.conta to Texto.normalizar(it.banco) }

        val lanc = despesas.filter { l ->
            Texto.normalizar(l.descricao).contains(t) ||
                Texto.normalizar(l.categoria).contains(t) ||
                l.conta.contains(t) ||
                (bancoPorConta[l.conta]?.contains(t) == true) ||
                (valorC != null && Dinheiro.centavos(l.valor) == valorC) ||
                (data != null && Calendar.getInstance().apply { timeInMillis = l.dataMs }.let {
                    it.get(Calendar.DAY_OF_MONTH) == data.first && it.get(Calendar.MONTH) + 1 == data.second &&
                        (data.third == null || it.get(Calendar.YEAR) == data.third)
                })
        }.sortedByDescending { it.dataMs }.take(limite)

        return ResultadoBusca(
            lancamentos = lanc,
            contas = contas.filter { listOf(it.banco, it.agencia, it.conta, it.titular).any { c -> Texto.normalizar(c).contains(t) } },
            cartoes = cartoes.filter { Texto.normalizar(it.nome).contains(t) || it.finalCartao.contains(t) },
            metas = metas.filter { Texto.normalizar(it.nome).contains(t) }
        )
    }

    // ------------------------------------------------------------------ R25

    enum class StatusReserva { CRITICO, ATENCAO, OK, SEM_DADOS }

    data class Reserva(
        val mediaDespesas3m: Double?,
        val liquidez: Double,
        val meses: Double?,
        val faltante: Double,
        val status: StatusReserva
    )

    const val META_RESERVA_MESES = 6

    fun reservaEmergencia(
        hoje: Long,
        despesas: List<Despesa>,
        saldosContas: List<Double>,
        investimentos: List<Investimento>
    ): Reserva {
        val atual = Financas.FaturaRef(Financas.mesDe(hoje), Financas.anoDe(hoje))
        val totais = (1..3).mapNotNull { k ->
            val ref = atual.deslocar(-k)
            val ini = Financas.inicioDoMes(ref.mes, ref.ano)
            val fim = Financas.fimDoMes(ref.mes, ref.ano)
            val doMes = despesas.filter { it.dataMs in ini..fim && it.natureza == Natureza.NORMAL }
            if (doMes.isEmpty()) null else Financas.kpisPeriodo(doMes, ini, fim).despesasTotal
        }
        val liquidez = Dinheiro.reais(
            saldosContas.sumOf { Dinheiro.centavos(it) } +
                investimentos.filter { Texto.normalizar(it.tipo) == "renda fixa" }.sumOf { Dinheiro.centavos(it.valorAtual) }
        )
        val media = if (totais.isEmpty()) null else Dinheiro.arredondar(totais.sum() / totais.size)
        if (media == null || media <= 0.0) return Reserva(media, liquidez, null, 0.0, StatusReserva.SEM_DADOS)
        val meses = liquidez / media
        val faltante = Dinheiro.arredondar((META_RESERVA_MESES * media - liquidez).coerceAtLeast(0.0))
        val status = when {
            meses < 3 -> StatusReserva.CRITICO
            meses < META_RESERVA_MESES -> StatusReserva.ATENCAO
            else -> StatusReserva.OK
        }
        return Reserva(media, liquidez, meses, faltante, status)
    }

    // ------------------------------------------------------------------ R26

    enum class OrigemAssinatura { FIXA, DETECTADA }

    data class Assinatura(
        val nome: String,
        val valorMedio: Double,
        val ultimaData: Long?,
        val categoria: String,
        val origem: OrigemAssinatura
    ) {
        val totalMensal: Double get() = valorMedio
        val totalAnual: Double get() = Dinheiro.reais(Dinheiro.centavos(valorMedio) * 12)
    }

    private val SUFIXO_PARCELA = Regex("\\(\\d+/\\d+\\)")
    private fun chaveAssinatura(descricao: String): String =
        Texto.normalizar(descricao.replace(SUFIXO_PARCELA, " ")).replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun assinaturas(hoje: Long, despesas: List<Despesa>, fixas: List<DespesaFixa>): List<Assinatura> {
        val doFixas = fixas.filter { it.tipo == TipoDespesa.DEBITO }.map {
            Assinatura(it.descricao, Dinheiro.arredondar(it.valor), it.ultimaDataLancamento?.time, it.categoria, OrigemAssinatura.FIXA)
        }
        val chavesFixas = fixas.map { chaveAssinatura(it.descricao) }.toSet()
        val inicio = Financas.somarMeses(hoje, -6)

        val detectadas = despesas
            .filter { it.natureza == Natureza.NORMAL && it.tipo == TipoDespesa.DEBITO && it.dataMs in inicio..hoje }
            .filter { !SUFIXO_PARCELA.containsMatchIn(it.descricao) }
            .groupBy { chaveAssinatura(it.descricao) }
            .filterKeys { it.isNotEmpty() && it !in chavesFixas }
            .mapNotNull { (_, itens) ->
                val meses = itens.map { Financas.anoDe(it.dataMs) * 12 + Financas.mesDe(it.dataMs) }.distinct()
                if (meses.size < 3) return@mapNotNull null
                val ultima = itens.maxOf { it.dataMs }
                if (hoje - ultima > 45 * DIA_MS) return@mapNotNull null
                val cents = itens.map { Dinheiro.centavos(it.valor) }.sorted()
                val mediana = cents[cents.size / 2].toDouble()
                if (mediana <= 0 || cents.any { abs(it - mediana) / mediana > 0.10 }) return@mapNotNull null
                val dias = itens.map { Calendar.getInstance().apply { timeInMillis = it.dataMs }.get(Calendar.DAY_OF_MONTH) }
                if (dias.max() - dias.min() > 5) return@mapNotNull null
                val recente = itens.maxBy { it.dataMs }
                Assinatura(recente.descricao, Dinheiro.reais(cents.sum() / cents.size), ultima, recente.categoria, OrigemAssinatura.DETECTADA)
            }
        return (doFixas + detectadas).sortedByDescending { it.valorMedio }
    }

    // ------------------------------------------------------------------ R27

    enum class StatusMeta { CONCLUIDA, ATRASADA, NO_RITMO, ABAIXO, SEM_PRAZO }

    data class PrazoMeta(
        val restante: Double,
        val mesesRestantes: Int?,
        val aporteMensalNecessario: Double?,
        val ritmoMensal: Double,
        val status: StatusMeta
    )

    fun prazoDaMeta(meta: Meta, hoje: Long, despesas: List<Despesa>): PrazoMeta {
        val restanteC = (Dinheiro.centavos(meta.valorObjetivo) - Dinheiro.centavos(meta.valorGuardado)).coerceAtLeast(0)
        val inicio = Financas.somarMeses(hoje, -3)
        val aportesC = despesas
            .filter { it.natureza == Natureza.APORTE_META && it.descricao == "Aporte: ${meta.nome}" && it.dataMs in inicio..hoje }
            .sumOf { Dinheiro.centavos(it.valor) }
        val ritmo = Dinheiro.reais(aportesC / 3)
        val restante = Dinheiro.reais(restanteC)
        val alvo = meta.dataAlvo
        if (restanteC == 0L) return PrazoMeta(restante, null, null, ritmo, StatusMeta.CONCLUIDA)
        if (alvo == null) return PrazoMeta(restante, null, null, ritmo, StatusMeta.SEM_PRAZO)
        if (alvo < hoje) return PrazoMeta(restante, 0, null, ritmo, StatusMeta.ATRASADA)
        val meses = maxOf(1, ceil((alvo - hoje) / (30.4375 * DIA_MS)).toInt())
        val necessario = Dinheiro.reais(Math.round(restanteC.toDouble() / meses))
        val status = if (ritmo >= necessario) StatusMeta.NO_RITMO else StatusMeta.ABAIXO
        return PrazoMeta(restante, meses, necessario, ritmo, status)
    }

    // ------------------------------------------------------------------ R28

    data class Simulacao(
        val valorPresente: Double,
        val totalParcelado: Double,
        val parcelarVale: Boolean,
        val diferenca: Double,
        val jurosImplicitosMensais: Double
    )

    fun simularParcelamento(
        valorAVista: Double,
        parcelas: Int,
        valorParcela: Double,
        taxaMensal: Double = 0.01,
        entrada: Double = 0.0
    ): Simulacao {
        require(parcelas >= 1 && valorAVista > 0 && valorParcela >= 0 && taxaMensal >= 0 && entrada >= 0)
        fun vp(i: Double) = (1..parcelas).sumOf { k -> valorParcela / (1 + i).pow(k) }
        val valorPresente = Dinheiro.arredondar(entrada + vp(taxaMensal))
        val total = Dinheiro.arredondar(entrada + parcelas * valorParcela)
        val financiado = valorAVista - entrada
        val implicitos = when {
            financiado <= 0 || parcelas * valorParcela <= financiado -> 0.0
            else -> {
                var lo = 0.0; var hi = 1.0
                if (vp(hi) > financiado) hi else {
                    repeat(60) { val mid = (lo + hi) / 2; if (vp(mid) > financiado) lo = mid else hi = mid }
                    (lo + hi) / 2
                }
            }
        }
        return Simulacao(
            valorPresente = valorPresente,
            totalParcelado = total,
            parcelarVale = valorPresente < Dinheiro.arredondar(valorAVista),
            diferenca = Dinheiro.arredondar(valorAVista - valorPresente),
            jurosImplicitosMensais = implicitos
        )
    }

    // ------------------------------------------------------------------ R29

    enum class StatusGrupo { OK, ACIMA, ABAIXO }
    data class GrupoOrcamento(val valor: Double, val percentual: Double, val alvo: Int, val status: StatusGrupo)
    data class Regra503020(val necessidades: GrupoOrcamento, val desejos: GrupoOrcamento, val poupanca: GrupoOrcamento)

    private val NECESSIDADES = setOf(
        "supermercado", "saude", "educacao", "transporte", "combustivel", "oficina", "casa", "aluguel", "moradia",
        "contas", "luz", "agua", "internet"
    ).map(Texto::normalizar).toSet()

    /** [despesasDoMes]: lançamentos do mês (inclusive internos, para aportes); [receitas]: receitas realizadas do mês. */
    fun regra503020(receitas: Double, despesasDoMes: List<Despesa>): Regra503020 {
        var nec = 0L; var des = 0L; var poup = 0L
        for (l in despesasDoMes) {
            val c = Dinheiro.centavos(l.valor)
            when {
                l.natureza == Natureza.APORTE_META -> poup += c
                l.natureza == Natureza.RESGATE_META -> poup -= c
                l.natureza != Natureza.NORMAL || l.tipo != TipoDespesa.DEBITO -> Unit
                Texto.normalizar(l.categoria) == "reserva" -> poup += c
                Texto.normalizar(l.categoria) in NECESSIDADES -> nec += c
                else -> des += c
            }
        }
        val recC = Dinheiro.centavos(receitas)
        fun pct(c: Long) = if (recC > 0) c.toDouble() / recC * 100 else 0.0
        return Regra503020(
            necessidades = GrupoOrcamento(Dinheiro.reais(nec), pct(nec), 50, if (pct(nec) <= 50) StatusGrupo.OK else StatusGrupo.ACIMA),
            desejos = GrupoOrcamento(Dinheiro.reais(des), pct(des), 30, if (pct(des) <= 30) StatusGrupo.OK else StatusGrupo.ACIMA),
            poupanca = GrupoOrcamento(
                Dinheiro.reais(poup.coerceAtLeast(0)), pct(poup.coerceAtLeast(0)), 20,
                if (pct(poup) >= 20) StatusGrupo.OK else StatusGrupo.ABAIXO
            )
        )
    }

    // ------------------------------------------------------------------ R30

    data class MelhorDia(val dia: Int, val prazoMaximoDias: Int)

    fun melhorDiaDeCompra(diaFechamento: Int, diaVencimento: Int, hoje: Long): MelhorDia {
        val ref = Financas.FaturaRef(Financas.mesDe(hoje), Financas.anoDe(hoje))
        val fecha = Financas.diaDeFechamento(diaFechamento, ref)
        val dataCompra = if (fecha >= Financas.diasNoMes(ref.mes, ref.ano)) {
            val prox = ref.proxima()
            Calendar.getInstance().apply { clear(); set(prox.ano, prox.mes - 1, 1, 12, 0, 0) }.timeInMillis
        } else {
            Calendar.getInstance().apply { clear(); set(ref.ano, ref.mes - 1, fecha + 1, 12, 0, 0) }.timeInMillis
        }
        val fatura = Financas.faturaDaCompra(dataCompra, diaFechamento)
        val venc = Financas.dataVencimento(diaFechamento, diaVencimento, fatura)
        val dia = Calendar.getInstance().apply { timeInMillis = dataCompra }.get(Calendar.DAY_OF_MONTH)
        return MelhorDia(dia, ceil((venc - dataCompra).toDouble() / DIA_MS).toInt())
    }
}
