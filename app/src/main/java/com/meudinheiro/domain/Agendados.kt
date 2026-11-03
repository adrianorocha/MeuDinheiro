package com.meudinheiro.domain

import com.meudinheiro.data.Despesa

/**
 * Lançamentos AGENDADOS: despesas/receitas de data futura ainda não pagas/recebidas (a pagar / a receber).
 * Só leitura: não altera saldo nem regra financeira (o saldo continua contando só o que está pago).
 * Compra de cartão fica de fora: ela entra pela fatura (ver [Financas.faturasEmAberto]).
 */
object Agendados {
    data class Mes(val ano: Int, val mes: Int, val itens: List<Despesa>, val aPagarCentavos: Long, val aReceberCentavos: Long)

    data class Resumo(val itens: List<Despesa>, val aPagarCentavos: Long, val aReceberCentavos: Long, val meses: List<Mes>) {
        val quantidade: Int get() = itens.size
        val aPagar: Double get() = Dinheiro.reais(aPagarCentavos)
        val aReceber: Double get() = Dinheiro.reais(aReceberCentavos)
    }

    /** Futuro (data depois de [agora]), em aberto, normal e fora de cartão; ordem por data. [ate] limita o horizonte. */
    fun selecionar(despesas: List<Despesa>, agora: Long, ate: Long? = null): List<Despesa> =
        despesas.filter {
            it.natureza == Natureza.NORMAL && it.semCartao && !it.pago &&
                it.dataMs > agora && (ate == null || it.dataMs <= ate)
        }.sortedWith(compareBy({ it.dataMs }, { it.id }))

    fun resumir(despesas: List<Despesa>, agora: Long, ate: Long? = null): Resumo {
        val itens = selecionar(despesas, agora, ate)
        fun soma(l: List<Despesa>, entrada: Boolean) = l.filter { it.ehEntrada == entrada }.sumOf { Dinheiro.centavos(it.valor) }
        val meses = itens.groupBy { Financas.anoDe(it.dataMs) * 12 + Financas.mesDe(it.dataMs) - 1 }
            .toSortedMap()
            .map { (idx, l) -> Mes(idx / 12, idx % 12 + 1, l, soma(l, false), soma(l, true)) }
        return Resumo(itens, soma(itens, false), soma(itens, true), meses)
    }
}
