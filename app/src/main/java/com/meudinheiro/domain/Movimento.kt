package com.meudinheiro.domain

import com.meudinheiro.data.TipoDespesa

/** Visão mínima de um lançamento do extrato, comum a `Despesa` (entidade) e `DespesasDomain` (projeção). */
interface Movimento {
    val valor: Double
    val dataMs: Long
    val tipo: TipoDespesa
    val pago: Boolean
    val cartaoId: Int?
    val natureza: String
    val categoria: String
    val conta: String
}

val Movimento.semCartao: Boolean get() = cartaoId == null || cartaoId == 0
val Movimento.ehEntrada: Boolean get() = tipo == TipoDespesa.CREDITO

/** Valor com sinal em centavos: entrada positiva, saída negativa. */
val Movimento.centavosAssinados: Long
    get() = Dinheiro.centavos(valor).let { if (ehEntrada) it else -it }
