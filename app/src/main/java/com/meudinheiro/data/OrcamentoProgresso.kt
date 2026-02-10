package com.meudinheiro.data

data class OrcamentoProgresso(
    val categoria: String,
    val limite: Double,
    val gastoAtual: Double,
    val porcentagem: Float
)
