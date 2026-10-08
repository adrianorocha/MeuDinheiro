package com.meudinheiro.domain

import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.Despesa

/** Regras puras da tela de cartões (filtro por cartão do grupo, avisos de limite, exclusão de virtual). Paridade com o portal. */
object CartoesUi {

    /** Ao focar um cartão VIRTUAL o filtro vem nele; no físico, "Todos" (null). */
    fun filtroInicial(focado: CartaoComConta?): Int? = focado?.takeIf { it.ehVirtual }?.id

    /** Filtro só vale se o cartão ainda pertence ao grupo. */
    fun filtroValido(filtroId: Int?, grupo: List<CartaoComConta>): Int? =
        filtroId?.takeIf { id -> grupo.any { it.id == id } }

    /** Compras de crédito do cartão [filtroId] (null = todas do grupo). */
    fun filtrarCredito(itens: List<Despesa>, filtroId: Int?): List<Despesa> =
        if (filtroId == null) itens else itens.filter { it.cartaoId == filtroId }

    /** Débitos vinculados (`debito:<id>`) do cartão [filtroId] (null = todos do grupo). */
    fun filtrarDebitos(itens: List<Despesa>, filtroId: Int?): List<Despesa> =
        if (filtroId == null) itens else itens.filter { Financas.cartaoDeDebito(it) == filtroId }

    /** Cartão de origem de um lançamento da lista (crédito: cartaoId; débito: vínculo `debito:<id>`). */
    fun cartaoDaCompra(d: Despesa, cartoes: List<CartaoComConta>): CartaoComConta? {
        val id = d.cartaoId ?: Financas.cartaoDeDebito(d)
        return cartoes.firstOrNull { it.id == id }
    }

    /** Soma (compras positivas, estornos negativos) da lista, em reais. */
    fun subtotal(itens: List<Despesa>): Double = Dinheiro.reais(itens.sumOf { -it.centavosAssinados })

    /** Compras de [cartaoId] ainda não pagas (impedem excluir um virtual). */
    fun comprasEmAberto(cartaoId: Int, despesas: List<Despesa>): Int =
        despesas.count { it.cartaoId == cartaoId && !it.pago }

    /** Mensagem quando não dá para excluir o virtual; `null` se pode. */
    fun bloqueioExclusaoVirtual(cartaoId: Int, nome: String, despesas: List<Despesa>): String? {
        val n = comprasEmAberto(cartaoId, despesas)
        return if (n > 0) "O cartão virtual \"$nome\" tem $n compra(s) em aberto. Pague a fatura antes de excluí-lo." else null
    }

    /**
     * Aviso (não bloqueante) quando uma compra de CRÉDITO passa do limite próprio / disponível do grupo.
     * [valor] em reais. Débito nunca avisa.
     */
    fun avisoDeLimite(saldo: Financas.SaldoCartao, valor: Double, debito: Boolean): String? {
        if (debito || valor <= 0.0) return null
        val v = Dinheiro.centavos(valor)
        val proprio = saldo.limiteProprio
        if (proprio != null) {
            val dispProprio = Dinheiro.centavos(proprio) - Dinheiro.centavos(saldo.usado)
            if (v > dispProprio) return "Esta compra ultrapassa o limite próprio deste cartão."
        }
        if (v > Dinheiro.centavos(saldo.disponivelGrupo)) return "Esta compra ultrapassa o limite disponível do grupo."
        return null
    }
}
