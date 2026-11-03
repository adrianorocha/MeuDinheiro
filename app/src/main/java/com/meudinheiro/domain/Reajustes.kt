package com.meudinheiro.domain

/**
 * R98 — calculadora de reajuste (aluguel/mensalidade) por índice acumulado. Paridade com `portal/src/lib/finance/reajuste.ts` (R97).
 * Acumulado = composição dos últimos N meses da série do índice (Π(1+i)−1); se a série tiver menos de N meses, usa os
 * disponíveis e avisa ([Linha.incompleto]). O usuário pode informar o acumulado oficial de 12 meses (substitui a composição).
 * Novo valor = valor × (1 + acumulado), arredondado para centavos (meio para cima). Índice negativo REDUZ o valor (sem piso;
 * contratos podem prever que não há redução).
 */
object Reajustes {

    /** [serie]: % ao mês, a mais recente por último. [acumuladoInformadoPct]: acumulado informado (%), substitui a composição. */
    data class Indice(val id: String, val nome: String, val serie: List<Double>, val acumuladoInformadoPct: Double? = null)

    /** [acumulado] em fração; [novoValor] e [diferenca] em centavos. */
    data class Linha(
        val id: String,
        val nome: String,
        val acumulado: Double,
        val mesesUsados: Int,
        val incompleto: Boolean,
        val informado: Boolean,
        val novoValor: Long,
        val diferenca: Long
    )

    private fun c(x: Double): Long = Math.round(x * 100 + 1e-7 * Math.signum(x))

    /** Reajuste direto por um acumulado (fração). [valor] em reais; devolve centavos. */
    fun aplicar(valor: Double, acumulado: Double): Long = c(valor * (1 + acumulado))

    fun calcular(valor: Double, meses: Int, indices: List<Indice>): List<Linha>? {
        if (!(valor > 0) || meses < 1 || meses > 120) return null
        val base = c(valor)
        return indices.map { i ->
            val inf = i.acumuladoInformadoPct?.takeIf { it.isFinite() }
            val r = if (inf != null) IndicesEconomicos.AcumuladoN(IndicesEconomicos.pctParaFracao(inf), meses, false)
            else IndicesEconomicos.acumuladoDosUltimos(i.serie, meses)
            val novo = aplicar(valor, r.acumulado)
            Linha(i.id, i.nome, r.acumulado, r.mesesUsados, r.incompleto, inf != null, novo, novo - base)
        }
    }

    /** Composição de uma lista de percentuais mensais (%) em fração. */
    fun acumuladoDePcts(pcts: List<Double>): Double = IndicesEconomicos.acumular(pcts.map(IndicesEconomicos::pctParaFracao))
}
