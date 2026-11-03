package com.meudinheiro.domain

/**
 * R98 — mini-calculadora do custo de ficar no rotativo do cartão / cheque especial (paridade com
 * `portal/src/lib/finance/custoRotativo.ts`, R97). Juros compostos mensais sobre o saldo em aberto, sem multa/IOF/encargos
 * (simplificação documentada). Taxa em fração mensal; valores em reais, arredondados para centavos só na saída.
 */
object CustoRotativo {

    /** [juros] acumulados e [total] devido no fim do período (reais). */
    data class Custo(val meses: Int, val juros: Double, val total: Double)

    data class Resultado(val m1: Custo, val m3: Custo, val taxaAnual: Double)

    private fun r2(x: Double): Double = Math.round(x * 100 + 1e-7 * Math.signum(x)) / 100.0

    /** juros = saldo × ((1 + i)^meses − 1). Entradas inválidas => `null`. */
    fun custoDoAtraso(saldo: Double, taxaMensal: Double, meses: Int): Custo? {
        if (!(saldo > 0) || !taxaMensal.isFinite() || taxaMensal < 0 || meses < 0) return null
        val total = saldo * Math.pow(1 + taxaMensal, meses.toDouble())
        return Custo(meses, r2(total - saldo), r2(total))
    }

    /** Custos para 1 e 3 meses e a taxa anual equivalente. */
    fun custo(saldo: Double, taxaMensal: Double): Resultado? {
        val m1 = custoDoAtraso(saldo, taxaMensal, 1) ?: return null
        val m3 = custoDoAtraso(saldo, taxaMensal, 3) ?: return null
        return Resultado(m1, m3, Math.pow(1 + taxaMensal, 12.0) - 1)
    }
}
