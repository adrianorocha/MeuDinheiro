package com.meudinheiro.data

import java.util.Date

data class DespesasDomain(
    val id: Int,
    val pic: String,
    val descricao: String,
    val valor: Double,
    val data: Date,
    val conta: String,
    val categoria: String,
    val tipo: TipoDespesa,
    val pago: Boolean
)

data class DespesaAviso(
    val id: Long,
    val titulo: String,
    val valor: Double,
    val vencimentoMillis: Long,
    val tipo: String
)

data class ResumoFinanceiroDto(
    val conta: String,
    val tipo: TipoDespesa,
    val valorTotal: Double
)