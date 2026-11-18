package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa

/**
 * R98 — projeção de fluxo de caixa de 6 a 12 meses (a partir do mês atual, inclusive) e "quanto cortar por mês".
 * Paridade com `portal/src/lib/finance/fluxoCaixa.ts` (R97). Tudo em CENTAVOS inteiros, sobre os dados existentes (nada é gravado).
 *
 *  1) Saldo inicial = saldo atual das contas ([ProjecaoFinanceira.saldoCentavos], R3).
 *  2) Eventos conhecidos = [ProjecaoFinanceira.eventosPrevistos] (R52): pendências (inclui parcelas futuras ainda não pagas),
 *     faturas de cartão lançadas (no vencimento) e recorrências ainda não lançadas, até o fim do horizonte. Pendência/fatura
 *     vencida entra no mês atual.
 *  3) Despesa variável estimada: média dos 3 meses COMPLETOS anteriores de débitos NORMAL, sem recorrências lançadas
 *     (`grupoId` "fixa:") nem parcelas ("parc:"), líquida de estornos de cartão; cada mês antigo é corrigido pela inflação
 *     mensal informada até o mês atual ((1+π)^k, k = meses atrás) e a média projetada para o mês j por (1+π)^j. A média sempre
 *     divide por 3 (mês sem gasto conta zero). No mês atual conta só o que falta: max(0, estimativa − já gasto variável do mês).
 *  4) Resultado do mês = entradas conhecidas − saídas conhecidas − variável; saldo acumulado mês a mês.
 *  5) Corte necessário por mês: menor c (centavos) tal que saldo_j + c×(j+1) ≥ 0 para todo mês, ou seja
 *     c = max_j ceil(−saldo_j / (j+1)) entre os meses com saldo negativo (0 se nenhum); j = 0 é o mês atual.
 * Limitações: ignora receita variável não cadastrada, 13º/imprevistos e o atraso entre a compra no cartão e o pagamento da fatura.
 */
object FluxoCaixa {

    const val MESES_MIN = 6
    const val MESES_MAX = 12

    /** [indice] 0 = mês atual. Todos os valores em centavos. */
    data class MesFluxo(
        val indice: Int,
        val mes: Int,
        val ano: Int,
        val entradas: Long,
        val saidas: Long,
        val variavel: Long,
        val resultado: Long,
        val saldoFinal: Long,
        val negativo: Boolean
    )

    data class Resultado(
        val saldoInicial: Long,
        val meses: List<MesFluxo>,
        /** Média mensal variável já corrigida para preços do mês atual (centavos). */
        val variavelBase: Long,
        /** Quantos meses completos tinham gasto (0..3); 0 = sem base para estimar. */
        val mesesComDados: Int,
        /** Índice do primeiro mês com saldo negativo; nulo = nenhum. */
        val primeiroNegativo: Int?,
        /** Centavos por mês necessários para fechar o déficit (0 se não há). */
        val cortePorMes: Long
    )

    /** Verdadeiro quando não há nada a projetar (sem eventos, sem histórico e saldo zero). */
    fun vazio(r: Resultado): Boolean =
        r.saldoInicial == 0L && r.mesesComDados == 0 && r.meses.all { it.entradas == 0L && it.saidas == 0L }

    /** Débito NORMAL "variável": não vem de recorrência lançada nem de parcelamento. */
    fun ehVariavel(d: Despesa): Boolean {
        if (d.natureza != Natureza.NORMAL) return false
        val g = d.grupoId.orEmpty()
        return !(g.startsWith("fixa:") || g.startsWith("parc:"))
    }

    private fun semCartao(d: Despesa) = d.cartaoId == null || d.cartaoId == 0

    /** Gasto variável líquido (centavos) entre [ini, fim] (ms): débito soma, estorno de cartão abate. */
    fun somaVariavel(despesas: List<Despesa>, ini: Long, fim: Long): Long {
        var s = 0L
        for (d in despesas) {
            if (d.dataMs < ini || d.dataMs > fim || !ehVariavel(d)) continue
            val cents = Dinheiro.centavos(d.valor)
            if (d.tipo == TipoDespesa.DEBITO) s += cents
            else if (!semCartao(d)) s -= cents
        }
        return s
    }

    /** Mês (1–12) e ano a [delta] meses do mês/ano dados. */
    fun deslocarMes(mes: Int, ano: Int, delta: Int): Pair<Int, Int> {
        val ref = Financas.FaturaRef(mes, ano).deslocar(delta)
        return ref.mes to ref.ano
    }

    data class Base(val base: Long, val mesesComDados: Int)

    /** Média mensal variável corrigida para preços do mês atual e nº de meses com gasto. */
    fun variavelBase(despesas: List<Despesa>, agora: Long, inflacaoMensal: Double): Base {
        val mes = Financas.mesDe(agora)
        val ano = Financas.anoDe(agora)
        var soma = 0.0
        var com = 0
        for (k in 1..3) {
            val (m, a) = deslocarMes(mes, ano, -k)
            val v = somaVariavel(despesas, Financas.inicioDoMes(m, a), Financas.fimDoMes(m, a))
            if (v > 0) com++
            soma += maxOf(0L, v) * Math.pow(1 + inflacaoMensal, k.toDouble())
        }
        return Base(Math.round(soma / 3), com)
    }

    fun projetar(
        contas: List<ContaSaldo>,
        cartoes: List<Cartao>,
        despesas: List<Despesa>,
        fixas: List<DespesaFixa>,
        agora: Long,
        meses: Int,
        inflacaoMensal: Double
    ): Resultado {
        val n = minOf(MESES_MAX, maxOf(MESES_MIN, meses))
        val mesAtual = Financas.mesDe(agora)
        val anoAtual = Financas.anoDe(agora)
        val (mesUlt, anoUlt) = deslocarMes(mesAtual, anoAtual, n - 1)
        val ate = ProjecaoFinanceira.ultimoDiaSerial(mesUlt, anoUlt)
        val refs = contas.map { ProjecaoFinanceira.ContaRef(it.id, it.conta, it.banco) }
        val eventos = ProjecaoFinanceira.eventosPrevistos(refs, cartoes, despesas, fixas, agora, ate)
        val entradas = LongArray(n)
        val saidas = LongArray(n)
        val idx0 = anoAtual * 12 + (mesAtual - 1)
        for (e in eventos) {
            val d = ProjecaoFinanceira.dataDeSerial(e.dia)
            val k = d.ano * 12 + (d.mes - 1) - idx0
            if (k < 0 || k >= n) continue
            if (e.centavos >= 0) entradas[k] += e.centavos else saidas[k] += -e.centavos
        }
        val (base, mesesComDados) = variavelBase(despesas, agora, inflacaoMensal).let { it.base to it.mesesComDados }
        val jaGasto = maxOf(0L, somaVariavel(despesas, Financas.inicioDoMes(mesAtual, anoAtual), agora))
        val saldoInicial = ProjecaoFinanceira.saldoCentavos(refs, despesas)
        var saldo = saldoInicial
        val out = ArrayList<MesFluxo>(n)
        var primeiro: Int? = null
        var corte = 0L
        for (j in 0 until n) {
            val (m, a) = deslocarMes(mesAtual, anoAtual, j)
            val estimativa = Math.round(base * Math.pow(1 + inflacaoMensal, j.toDouble()))
            val variavel = if (j == 0) maxOf(0L, estimativa - jaGasto) else estimativa
            val resultado = entradas[j] - saidas[j] - variavel
            saldo += resultado
            val negativo = saldo < 0
            if (negativo) {
                if (primeiro == null) primeiro = j
                val d = (j + 1).toLong()
                corte = maxOf(corte, (-saldo + d - 1) / d)
            }
            out += MesFluxo(j, m, a, entradas[j], saidas[j], variavel, resultado, saldo, negativo)
        }
        return Resultado(saldoInicial, out, base, mesesComDados, primeiro, corte)
    }
}
