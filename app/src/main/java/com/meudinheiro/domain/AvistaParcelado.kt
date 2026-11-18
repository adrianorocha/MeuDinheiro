package com.meudinheiro.domain

/**
 * R98 — à vista × parcelado: taxa implícita, total pago, juros embutidos e comparação com o rendimento líquido do dinheiro
 * aplicado no CDI. Paridade com `portal/src/lib/finance/avistaParcelado.ts` (R97). Taxas em fração decimal; valores em reais
 * (Double) com arredondamento para centavos só na saída. Parcelas postecipadas (a 1ª, 30 dias depois da compra).
 *
 * Valor presente para comparar: cada parcela k vale hoje `parcela / fatorLiquido(k)` ([Rendimentos.fatorLiquido]: quanto 1 real
 * aplicado hoje no CDI × % vira em k meses, líquido de IR regressivo). Ou seja, "quanto eu precisaria aplicar hoje para pagar
 * cada parcela no vencimento".
 */
object AvistaParcelado {

    data class Entrada(val aVista: Double, val n: Int, val parcela: Double, val entrada: Double = 0.0)

    fun validar(e: Entrada): String? = when {
        !(e.aVista > 0) -> "Informe o preço à vista."
        e.n < 1 || e.n > 600 -> "Informe de 1 a 600 parcelas."
        !(e.parcela > 0) -> "Informe o valor da parcela."
        !(e.entrada >= 0) || e.entrada >= e.aVista -> "A entrada deve ser menor que o preço à vista."
        else -> null
    }

    private fun valorPresenteFixo(parcela: Double, n: Int, i: Double): Double {
        var vp = 0.0
        for (k in 1..n) vp += parcela / Math.pow(1 + i, k.toDouble())
        return vp
    }

    /**
     * Taxa mensal implícita: i tal que (aVista − entrada) = Σ parcela/(1+i)^k, por bisseção em [−0,99; 100] (tolerância 1e-9 em i).
     * Total das parcelas = valor financiado => 0. `null` se as entradas forem inválidas.
     */
    fun taxaImplicita(e: Entrada): Double? {
        if (validar(e) != null) return null
        val alvo = e.aVista - e.entrada
        if (Math.abs(e.n * e.parcela - alvo) < 1e-9) return 0.0
        var lo = -0.99
        var hi = 100.0
        var it = 0
        while (it < 300 && hi - lo > 1e-12) {
            val mid = (lo + hi) / 2
            if (valorPresenteFixo(e.parcela, e.n, mid) > alvo) lo = mid else hi = mid
            it++
        }
        return (lo + hi) / 2
    }

    enum class MenorCusto { AVISTA, PARCELADO, EMPATE }

    data class Resultado(
        val taxaMensal: Double,
        val taxaAnual: Double,
        /** entrada + n × parcela (reais, 2 casas). */
        val totalPago: Double,
        /** totalPago − aVista (pode ser ≤ 0). */
        val juros: Double,
        /** VP das parcelas + entrada à taxa líquida do CDI × %. */
        val valorPresente: Double,
        /** valorPresente − aVista: > 0 = parcelar custa mais que pagar à vista (em valor presente). */
        val diferenca: Double,
        /** Desconto à vista (sobre o total parcelado) que iguala as duas opções: (totalPago − VP)/totalPago. */
        val descontoBreakEven: Double,
        /** Desconto à vista realmente oferecido: (totalPago − aVista)/totalPago. */
        val descontoOferecido: Double,
        val menorCusto: MenorCusto
    )

    private fun r2(x: Double): Double = Math.round(x * 100 + 1e-7 * Math.signum(x)) / 100.0

    fun comparar(e: Entrada, cdiMensal: Double, pctCdi: Double): Resultado? {
        val i = taxaImplicita(e)
        if (i == null || !cdiMensal.isFinite() || !(pctCdi >= 0)) return null
        val taxa = cdiMensal * pctCdi
        var vp = e.entrada
        for (k in 1..e.n) vp += e.parcela / Rendimentos.fatorLiquido(taxa, k)
        val total = e.entrada + e.n * e.parcela
        val dif = vp - e.aVista
        return Resultado(
            taxaMensal = i,
            taxaAnual = Math.pow(1 + i, 12.0) - 1,
            totalPago = r2(total),
            juros = r2(total - e.aVista),
            valorPresente = r2(vp),
            diferenca = r2(dif),
            descontoBreakEven = (total - vp) / total,
            descontoOferecido = (total - e.aVista) / total,
            menorCusto = if (Math.abs(dif) < 0.005) MenorCusto.EMPATE else if (dif > 0) MenorCusto.AVISTA else MenorCusto.PARCELADO
        )
    }
}
