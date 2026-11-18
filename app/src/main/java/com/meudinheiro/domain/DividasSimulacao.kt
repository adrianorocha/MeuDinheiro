package com.meudinheiro.domain

import com.meudinheiro.data.Divida

/**
 * R98 — simulador de quitação com taxa/mínimo informados NO SIMULADOR (sem alterar a dívida cadastrada). Paridade com
 * `portal/src/lib/finance/dividasSimulacao.ts` (R97). Reaproveita o motor da R58 ([Dividas]): mesmas regras e números.
 * Os ajustes ficam só em memória/tela.
 */
object DividasSimulacao {

    /** [taxaPct] % ao mês informado no simulador (nulo = a da dívida); [minimoReais] mínimo mensal em reais (nulo = o da dívida). */
    data class Ajuste(val taxaPct: Double? = null, val minimoReais: Double? = null)

    /** Dívidas ativas com saldo, já com os ajustes aplicados (taxa limitada a 100% a.m.). */
    fun paraSimular(dividas: List<Divida>, ajustes: Map<Int, Ajuste>): List<Dividas.DividaSim> {
        val out = ArrayList<Dividas.DividaSim>()
        for (d in dividas) {
            val base = Dividas.paraSim(d) ?: continue
            val aj = ajustes[d.id]
            var taxaBp = base.taxaBp
            var minimo = base.minimo
            val t = aj?.taxaPct
            if (t != null && t.isFinite() && t >= 0) taxaBp = Math.round(Math.min(t, Dividas.TAXA_MAXIMA_PERCENTUAL) * 100)
            val m = aj?.minimoReais
            if (m != null && m.isFinite() && m >= 0) minimo = Dinheiro.centavos(m)
            out += base.copy(taxaBp = taxaBp, minimo = minimo)
        }
        return out
    }

    fun compararComAjustes(dividas: List<Divida>, ajustes: Map<Int, Ajuste>, extraReais: Double): Dividas.Comparativo =
        Dividas.comparar(paraSimular(dividas, ajustes), Dinheiro.centavos(maxOf(0.0, if (extraReais.isFinite()) extraReais else 0.0)))
}
