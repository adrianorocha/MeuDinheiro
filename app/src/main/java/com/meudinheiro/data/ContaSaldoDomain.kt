package com.meudinheiro.data

data class ContaSaldoDomain(
    val id: Int,
    val saldo: Double,
    val banco: String,
    val agencia: String,
    val conta: String,
    val titular: String
)