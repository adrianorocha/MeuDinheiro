package com.meudinheiro.data

data class CartaoComConta(
    val id: Int,
    val nomeCartao: String,
    val finalCartao: String,
    val tipo: String,
    val limiteTotal: Double,
    val diaFechamento: Int,
    val diaVencimento: Int,
    val contaId: Int,
    val nomeConta: String // 📍 O nome do banco que virá pelo JOIN
)
