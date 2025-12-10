package com.meudinheiro.data

data class DespesasDomain(
    val id: Int,
    val pic: String,
    val descricao: String,
    val valor: Double,
    val data: String,
    val conta: String,
    val categoria: String
)