package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar

/**
 * R108 — "Contas do mês": visão mensal das despesas fixas (luz, água, telefone, gás…) sobre as recorrências (R16). Lógica PURA, em
 * centavos inteiros, igual à do portal (`lib/finance/contasMes.ts`): não grava nada. Cada conta mostra a ocorrência do mês
 * (`grupoId` `fixa:<id>:<AAAA-MM>`), seu estado e o que dá para fazer (provisionar, alterar, dar baixa, desabilitar).
 */
object ContasMes {
    const val MAX_MESES_PROVISAO = 12

    enum class Estado { PAGA, VENCIDA, PENDENTE, PROVISIONADA, NAO_LANCADA, DESABILITADA }

    data class Linha(
        val regra: DespesaFixa,
        val ocorrencia: Despesa?,
        val estado: Estado,
        /** valor do lançamento do mês, ou o da regra se ainda não existe */
        val valor: Double,
        /** data do lançamento do mês, ou a prevista pela regra (epoch ms) */
        val data: Long,
        /** regra desabilitada (pausada), mesmo que o mês já tenha lançamento */
        val desabilitada: Boolean,
        /** compra no cartão de crédito: a baixa é pela fatura */
        val noCartao: Boolean,
        /** valor do lançamento difere do da regra (conta variável) */
        val valorDifere: Boolean
    )

    data class Resumo(val linhas: List<Linha>, val previstoCentavos: Long, val pagoCentavos: Long, val vencidas: Int) {
        val aPagarCentavos get() = previstoCentavos - pagoCentavos
    }

    fun grupoOcorrencia(regraId: Int, mes: Int, ano: Int) = "fixa:$regraId:$ano-${mes.toString().padStart(2, '0')}"

    private fun norm(t: String) = Texto.normalizar(t)

    /**
     * Ocorrência de uma conta mensal num mês: pelo `grupoId` (R16); se não houver, lançamento ANTIGO gerado pelo app sem `grupoId`
     * (mesmo tipo e descrição, no mês, NORMAL) desde que seja um só (ambíguo = nenhum).
     */
    fun ocorrenciaDoMes(despesas: List<Despesa>, regra: DespesaFixa, mes: Int, ano: Int): Despesa? {
        val g = grupoOcorrencia(regra.id, mes, ano)
        despesas.firstOrNull { it.grupoId == g }?.let { return it }
        val desc = norm(regra.descricao)
        val legados = despesas.filter {
            it.grupoId == null && it.natureza == Natureza.NORMAL && it.tipo == regra.tipo && it.mes == mes && it.ano == ano && norm(it.descricao) == desc
        }
        return legados.singleOrNull()
    }

    /** Meio-dia do dia de vencimento da regra no mês (dia 29–31 vira o último dia do mês), como o R16. */
    fun dataPrevista(regra: DespesaFixa, mes: Int, ano: Int): Long {
        val dia = regra.diaVencimento.coerceIn(1, 31).coerceAtMost(Financas.diasNoMes(mes, ano))
        return Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis
    }

    private fun inicioDoDia(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun contasDoMes(despesas: List<Despesa>, regras: List<DespesaFixa>, mes: Int, ano: Int, agora: Long): Resumo {
        val hojeIni = inicioDoDia(agora)
        val c = Calendar.getInstance().apply { timeInMillis = agora }
        val passado = ano * 12 + (mes - 1) < c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        val linhas = ArrayList<Linha>()
        for (regra in regras) {
            if (regra.tipo != TipoDespesa.DEBITO) continue
            val oc = ocorrenciaDoMes(despesas, regra, mes, ano)
            if (oc == null && passado) continue // mês passado sem lançamento: a conta não existia / estava pausada
            val desabilitada = Financas.recorrenciaPausada(regra, agora)
            val noCartao = if (oc != null) (oc.cartaoId != null && oc.cartaoId != 0) else (regra.cartaoId != null && regra.cartaoId != 0)
            val estado = when {
                oc == null -> if (desabilitada) Estado.DESABILITADA else Estado.NAO_LANCADA
                oc.pago -> Estado.PAGA
                !noCartao && oc.data.time < hojeIni -> Estado.VENCIDA
                oc.data.time > agora -> Estado.PROVISIONADA
                else -> Estado.PENDENTE
            }
            linhas += Linha(
                regra = regra, ocorrencia = oc, estado = estado, valor = oc?.valor ?: regra.valor,
                data = oc?.data?.time ?: dataPrevista(regra, mes, ano), desabilitada = desabilitada, noCartao = noCartao,
                valorDifere = oc != null && Dinheiro.centavos(oc.valor) != Dinheiro.centavos(regra.valor)
            )
        }
        linhas.sortWith(compareBy<Linha>({ it.data }, { it.regra.descricao.lowercase() }))
        var previsto = 0L
        var pago = 0L
        var vencidas = 0
        for (l in linhas) {
            if (l.estado == Estado.DESABILITADA) continue
            val cents = Dinheiro.centavos(l.valor)
            previsto += cents
            if (l.estado == Estado.PAGA) pago += cents
            if (l.estado == Estado.VENCIDA) vencidas++
        }
        return Resumo(linhas, previsto, pago, vencidas)
    }

    /** Meses (mês, ano) a provisionar: do mês corrente até [meses] à frente, só os que ainda não têm ocorrência. */
    fun mesesParaProvisionar(despesas: List<Despesa>, regra: DespesaFixa, hoje: Long, meses: Int): List<Pair<Int, Int>> {
        val c = Calendar.getInstance().apply { timeInMillis = hoje }
        val idxHoje = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        return (0 until meses).map { k ->
            val idx = idxHoje + k
            (idx % 12 + 1) to (idx / 12)
        }.filter { (m, a) -> ocorrenciaDoMes(despesas, regra, m, a) == null }
    }

    /** Ocorrências provisionadas futuras ainda em aberto de uma regra (as que "desabilitar" pode enviar para a lixeira). */
    fun provisionadasFuturas(despesas: List<Despesa>, regraId: Int, agora: Long): List<Despesa> =
        despesas.filter { it.grupoId?.startsWith("fixa:$regraId:") == true && !it.pago && it.data.time > agora && it.natureza == Natureza.NORMAL }
}
