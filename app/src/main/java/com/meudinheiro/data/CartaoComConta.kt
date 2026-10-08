package com.meudinheiro.data

data class CartaoComConta(
    val id: Int,
    val nomeCartao: String,
    val finalCartao: String,
    val tipo: String,
    val limiteDisponivel: Double,
    val limiteTotal: Double,
    val diaFechamento: Int,
    val diaVencimento: Int,
    val contaId: Int,
    val nomeConta: String, // 📍 O nome do banco que virá pelo JOIN
    val numeroConta: String,
    val cartaoPrincipalId: Int? = null,
    val limiteProprio: Double? = null
) {
    val ehVirtual: Boolean get() = cartaoPrincipalId != null

    /** Visão de domínio (para as regras puras de saldo por cartão, R41). */
    fun paraCartao() = Cartao(
        id = id, nome = nomeCartao, finalCartao = finalCartao, tipo = tipo, limiteDisponivel = limiteDisponivel,
        limiteTotal = limiteTotal, diaFechamento = diaFechamento, diaVencimento = diaVencimento, contaId = contaId,
        cartaoPrincipalId = cartaoPrincipalId, limiteProprio = limiteProprio
    )
    val idDoGrupo: Int get() = cartaoPrincipalId ?: id
}
