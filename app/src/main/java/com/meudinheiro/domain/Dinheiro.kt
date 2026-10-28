package com.meudinheiro.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Aritmética monetária segura. Valores continuam armazenados como [Double] (compatibilidade com o
 * banco e com o Firestore), mas toda gravação passa por [arredondar] e toda soma é feita em
 * centavos inteiros, eliminando erros de ponto flutuante (0,1 + 0,2 != 0,3).
 */
object Dinheiro {

    fun centavos(valor: Double): Long {
        require(valor.isFinite()) { "Valor monetário inválido: $valor" }
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    }

    fun reais(centavos: Long): Double = BigDecimal.valueOf(centavos, 2).toDouble()

    fun arredondar(valor: Double): Double = reais(centavos(valor))

    fun somar(valores: Iterable<Double>): Double = reais(valores.sumOf { centavos(it) })
}

/** Natureza do lançamento: separa o que é receita/despesa "real" do que é só movimentação interna. */
object Natureza {
    const val NORMAL = "NORMAL"
    const val SALDO_INICIAL = "SALDO_INICIAL"
    const val TRANSFERENCIA = "TRANSFERENCIA"
    const val APORTE_META = "APORTE_META"
    const val RESGATE_META = "RESGATE_META"
    const val PAGAMENTO_FATURA = "PAGAMENTO_FATURA"
    const val AJUSTE = "AJUSTE"

    val TODAS = setOf(
        NORMAL, SALDO_INICIAL, TRANSFERENCIA, APORTE_META, RESGATE_META, PAGAMENTO_FATURA, AJUSTE
    )
}
