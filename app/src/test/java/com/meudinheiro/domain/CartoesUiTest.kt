package com.meudinheiro.domain

import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class CartoesUiTest {
    private fun c(id: Int, principal: Int? = null, proprio: Double? = null) = CartaoComConta(
        id = id, nomeCartao = "C$id", finalCartao = "000$id", tipo = "CRÉDITO", limiteDisponivel = 1000.0, limiteTotal = 1000.0,
        diaFechamento = 25, diaVencimento = 5, contaId = 1, nomeConta = "Banco", numeroConta = "111",
        cartaoPrincipalId = principal, limiteProprio = proprio
    )

    private fun d(id: Long, valor: Double, cartao: Int?, pago: Boolean = false, grupo: String? = null) = Despesa(
        id = id, descricao = "x", valor = valor, data = Date(0), categoria = "Outros", conta = "111", pic = "p",
        tipo = TipoDespesa.DEBITO, mes = 1, ano = 2026, cartaoId = cartao, pago = pago, grupoId = grupo
    )

    private val fisico = c(1)
    private val virtual = c(2, principal = 1)

    @Test fun `filtro inicial - virtual pre-seleciona, fisico mostra todos`() {
        assertEquals(2, CartoesUi.filtroInicial(virtual))
        assertNull(CartoesUi.filtroInicial(fisico))
        assertNull(CartoesUi.filtroInicial(null))
    }

    @Test fun `filtro invalido fora do grupo volta para todos`() {
        assertEquals(2, CartoesUi.filtroValido(2, listOf(fisico, virtual)))
        assertNull(CartoesUi.filtroValido(9, listOf(fisico, virtual)))
    }

    @Test fun `filtra credito e debito por cartao e soma o subtotal`() {
        val itens = listOf(d(1, 10.0, 1), d(2, 25.5, 2), d(3, 4.5, 2))
        assertEquals(listOf(2L, 3L), CartoesUi.filtrarCredito(itens, 2).map { it.id })
        assertEquals(3, CartoesUi.filtrarCredito(itens, null).size)
        assertEquals(30.0, CartoesUi.subtotal(CartoesUi.filtrarCredito(itens, 2)), 0.0)
        val debitos = listOf(d(5, 9.0, null, grupo = "debito:1"), d(6, 7.0, null, grupo = "debito:2"))
        assertEquals(listOf(6L), CartoesUi.filtrarDebitos(debitos, 2).map { it.id })
        assertEquals(2, CartoesUi.filtrarDebitos(debitos, null).size)
    }

    @Test fun `cartao de origem do lancamento vem do cartaoId ou do vinculo de debito`() {
        val todos = listOf(fisico, virtual)
        assertEquals(2, CartoesUi.cartaoDaCompra(d(1, 1.0, 2), todos)?.id)
        assertEquals(2, CartoesUi.cartaoDaCompra(d(2, 1.0, null, grupo = "debito:2"), todos)?.id)
        assertNull(CartoesUi.cartaoDaCompra(d(3, 1.0, null), todos))
    }

    @Test fun `virtual com compra em aberto nao pode ser excluido`() {
        val msg = CartoesUi.bloqueioExclusaoVirtual(2, "C2", listOf(d(1, 10.0, 2, pago = false)))
        assertNotNull(msg)
        assertNull(CartoesUi.bloqueioExclusaoVirtual(2, "C2", listOf(d(1, 10.0, 2, pago = true), d(2, 10.0, 1))))
    }

    @Test fun `aviso de limite - proprio, grupo e debito`() {
        val todos = listOf(fisico.paraCartao(), c(2, 1, proprio = 200.0).paraCartao())
        val compras = listOf(d(1, 150.0, 2))
        val s = Financas.saldoDoCartao(todos[1], todos, compras)
        assertNull(CartoesUi.avisoDeLimite(s, 40.0, false))
        assertNotNull(CartoesUi.avisoDeLimite(s, 60.0, false)) // passa do próprio (50 restantes)
        assertNull(CartoesUi.avisoDeLimite(s, 60.0, true))     // débito nunca avisa
        val sf = Financas.saldoDoCartao(todos[0], todos, compras) // grupo: 850 disponíveis
        assertNull(CartoesUi.avisoDeLimite(sf, 850.0, false))
        assertNotNull(CartoesUi.avisoDeLimite(sf, 851.0, false))
    }
}
