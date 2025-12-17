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
    val tipo: TipoDespesa
)