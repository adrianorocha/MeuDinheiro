package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.LancamentoAcoes.Acao
import com.meudinheiro.domain.LancamentoAcoes.DescontoModo
import com.meudinheiro.domain.LancamentoAcoes.ModoAntecipacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class LancamentoAcoesTest {

    private fun ms(ano: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private fun d(
        valor: Double, data: Long = ms(2026, 11, 10), conta: String = "111", pago: Boolean = false, id: Long = 0,
        natureza: String = Natureza.NORMAL, tipo: TipoDespesa = TipoDespesa.DEBITO, cartaoId: Int? = null, desc: String = "Emprestimo"
    ) = Despesa(
        id = id, descricao = desc, valor = valor, data = Date(data), categoria = "Outros", conta = conta, pic = "p", tipo = tipo,
        mes = Financas.mesDe(data), ano = Financas.anoDe(data), cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    // ------------------------------------------------------------------ ações por natureza/estado

    @Test fun `despesa comum de conta em aberto oferece todas as acoes`() {
        assertEquals(
            listOf(Acao.PAGO_PENDENTE, Acao.ANTECIPAR, Acao.DUPLICAR, Acao.REPETIR, Acao.EDITAR, Acao.EXCLUIR),
            LancamentoAcoes.acoes(d(100.0))
        )
    }

    @Test fun `despesa paga ou receita nao antecipa`() {
        assertFalse(Acao.ANTECIPAR in LancamentoAcoes.acoes(d(100.0, pago = true)))
        assertFalse(Acao.ANTECIPAR in LancamentoAcoes.acoes(d(100.0, tipo = TipoDespesa.CREDITO)))
    }

    @Test fun `compra de cartao nao marca pago nem antecipa mas edita duplica repete e exclui`() {
        val a = LancamentoAcoes.acoes(d(100.0, cartaoId = 3))
        assertEquals(listOf(Acao.DUPLICAR, Acao.REPETIR, Acao.EDITAR, Acao.EXCLUIR), a)
    }

    @Test fun `naturezas geradas sao protegidas`() {
        assertEquals(listOf(Acao.EXCLUIR), LancamentoAcoes.acoes(d(1.0, natureza = Natureza.PAGAMENTO_FATURA, pago = true)))
        assertEquals(listOf(Acao.EXCLUIR), LancamentoAcoes.acoes(d(1.0, natureza = Natureza.TRANSFERENCIA, pago = true)))
        assertEquals(listOf(Acao.EXCLUIR), LancamentoAcoes.acoes(d(1.0, natureza = Natureza.APORTE_META, pago = true)))
        assertEquals(listOf(Acao.EXCLUIR), LancamentoAcoes.acoes(d(1.0, natureza = Natureza.RESGATE_META, pago = true)))
        // saldo inicial e ajuste são editáveis; ajuste alterna pago
        assertEquals(listOf(Acao.EDITAR, Acao.EXCLUIR), LancamentoAcoes.acoes(d(1.0, natureza = Natureza.SALDO_INICIAL, pago = true)))
        assertEquals(listOf(Acao.PAGO_PENDENTE, Acao.EDITAR, Acao.EXCLUIR), LancamentoAcoes.acoes(d(1.0, natureza = Natureza.AJUSTE, pago = true)))
    }

    @Test fun `exclusao bloqueada tem mensagem clara so para fatura e metas`() {
        assertNotNull(LancamentoAcoes.motivoExclusaoBloqueada(Natureza.PAGAMENTO_FATURA))
        assertNotNull(LancamentoAcoes.motivoExclusaoBloqueada(Natureza.APORTE_META))
        assertNotNull(LancamentoAcoes.motivoExclusaoBloqueada(Natureza.RESGATE_META))
        assertNull(LancamentoAcoes.motivoExclusaoBloqueada(Natureza.TRANSFERENCIA))
        assertNull(LancamentoAcoes.motivoExclusaoBloqueada(Natureza.NORMAL))
    }

    @Test fun `grupo de parcelas reconhece parc e uuid antigo mas nao os outros vinculos`() {
        assertTrue(LancamentoAcoes.ehGrupoParcelas(Natureza.NORMAL, "parc:abc"))
        assertTrue(LancamentoAcoes.ehGrupoParcelas(Natureza.NORMAL, "0b6f6c60-1111-2222-3333-444455556666")) // parcelas antigas do app
        listOf(null, "debito:3", "rep:x", "fixa:1:2026-10", "fatura:1:2026-10", "transf:x").forEach {
            assertFalse(it.toString(), LancamentoAcoes.ehGrupoParcelas(Natureza.NORMAL, it))
        }
        assertFalse(LancamentoAcoes.ehGrupoParcelas(Natureza.TRANSFERENCIA, "uuid-solto")) // par de transferência
    }

    @Test fun `parcelar gera grupo com prefixo parc`() {
        val p = Financas.parcelar(d(300.0), 3, 0L)
        assertTrue(p.all { it.grupoId!!.startsWith("parc:") })
        assertTrue(LancamentoAcoes.ehGrupoParcelas(Natureza.NORMAL, p[0].grupoId))
    }

    // ------------------------------------------------------------------ R40: quitar com desconto

    @Test fun `quitar parcelas com desconto rateia em centavos`() {
        val itens = listOf(d(100.0, id = 1), d(100.0, id = 2), d(100.0, id = 3))
        val r = Financas.antecipar(itens, valorPago = 270.0, desconto = 30.0, data = ms(2026, 10, 7))
        assertEquals(3, r.pagos.size)
        assertTrue(r.novos.isEmpty())
        assertEquals(0.0, r.restante, 0.0)
        assertEquals(30.0, r.economia, 0.0)
        assertEquals(listOf(90.0, 90.0, 90.0), r.substituidos.map { it.valor })
        assertTrue(r.substituidos.all { it.pago && it.valorOriginal == it.valor && Financas.mesDe(it.dataMs) == 10 })
        assertEquals(270.0, Dinheiro.somar(r.substituidos.map { it.valor }), 0.0)
        assertTrue(r.substituidos[0].descricao.contains("antecipada de 10/11/2026, desc. 10,00"))
    }

    @Test fun `desconto que nao divide exato manda o resto para o ultimo`() {
        val itens = listOf(d(100.0, id = 1), d(100.0, id = 2), d(100.0, id = 3))
        val r = Financas.antecipar(itens, valorPago = 299.0, desconto = 1.0, data = ms(2026, 10, 7))
        // desconto 100 centavos / 3 = 33 (piso), 33 e 34 (resto)
        assertEquals(listOf(99.67, 99.67, 99.66), r.substituidos.map { it.valor })
        assertEquals(299.0, Dinheiro.somar(r.substituidos.map { it.valor }), 0.0)
    }

    // ------------------------------------------------------------------ R40: parcial

    @Test fun `pagamento parcial divide o ultimo item e o resto continua em aberto`() {
        val itens = listOf(d(100.0, id = 1, desc = "Parc (1/3)"), d(100.0, id = 2, desc = "Parc (2/3)"))
        val r = Financas.antecipar(itens, valorPago = 150.0, desconto = 0.0, data = ms(2026, 10, 7))
        assertEquals(50.0, r.restante, 0.0)
        // primeiro quitado por inteiro; segundo dividido: 50 pago (novo) e 50 em aberto (mesmo id, mesmo vencimento)
        assertEquals(2, r.substituidos.size)
        assertEquals(100.0, r.substituidos[0].valor, 0.0); assertTrue(r.substituidos[0].pago)
        val resto = r.substituidos[1]
        assertEquals(50.0, resto.valor, 0.0); assertFalse(resto.pago); assertEquals(2L, resto.id)
        assertEquals(itens[1].data, resto.data)
        val parte = r.novos.single()
        assertEquals(0L, parte.id); assertEquals(50.0, parte.valor, 0.0); assertTrue(parte.pago)
        assertTrue(parte.descricao.endsWith("(adiantamento)"))
        assertEquals(Financas.mesDe(ms(2026, 10, 7)), parte.mes)
    }

    @Test fun `parcial que cabe no primeiro item toca so nele`() {
        val itens = listOf(d(100.0, id = 1), d(100.0, id = 2))
        val r = Financas.antecipar(itens, 30.0, 0.0, ms(2026, 10, 7))
        assertEquals(1, r.substituidos.size)
        assertEquals(70.0, r.substituidos[0].valor, 0.0)
        assertFalse(r.substituidos[0].pago)
        assertEquals(30.0, r.novos.single().valor, 0.0)
        assertEquals(170.0, r.restante, 0.0)
    }

    @Test fun `parcial com desconto a parte paga vale so o que saiu da conta`() {
        val itens = listOf(d(100.0, id = 1))
        val r = Financas.antecipar(itens, valorPago = 40.0, desconto = 10.0, data = ms(2026, 10, 7))
        assertEquals(50.0, r.restante, 0.0)
        assertEquals(40.0, r.novos.single().valor, 0.0)
        assertTrue(r.novos.single().descricao.contains("desc. 10,00"))
        assertEquals(50.0, r.substituidos.single().valor, 0.0)
    }

    // ------------------------------------------------------------------ R40: validações

    private fun erro(itens: List<Despesa>, pago: Double, desc: Double = 0.0): String =
        try { Financas.antecipar(itens, pago, desc, 0L); "" } catch (e: IllegalArgumentException) { e.message!! }

    @Test fun `antecipar recusa compra de cartao paga receita e contas diferentes`() {
        assertTrue(erro(listOf(d(10.0, cartaoId = 1)), 10.0).contains("fatura"))
        assertTrue(erro(listOf(d(10.0, pago = true)), 10.0).contains("já está pago"))
        assertTrue(erro(listOf(d(10.0, tipo = TipoDespesa.CREDITO)), 10.0).contains("despesas comuns"))
        assertTrue(erro(listOf(d(10.0, natureza = Natureza.TRANSFERENCIA)), 10.0).contains("despesas comuns"))
        assertTrue(erro(listOf(d(10.0, conta = "1"), d(10.0, conta = "2")), 20.0).contains("mesma conta"))
    }

    @Test fun `antecipar valida valores e excesso`() {
        val i = listOf(d(100.0))
        assertTrue(erro(i, 0.0).contains("maior que zero"))
        assertTrue(erro(i, 50.0, -1.0).contains("negativo"))
        assertTrue(erro(i, 90.0, 20.0).contains("excede"))
        assertTrue(erro(emptyList(), 10.0).contains("ao menos um"))
    }

    @Test fun `antecipavel so para despesa comum de conta em aberto`() {
        assertTrue(Financas.antecipavel(d(10.0)))
        assertFalse(Financas.antecipavel(d(10.0, cartaoId = 2)))
        assertFalse(Financas.antecipavel(d(10.0, pago = true)))
        assertFalse(Financas.antecipavel(d(10.0, natureza = Natureza.AJUSTE)))
    }

    // ------------------------------------------------------------------ entrada do diálogo

    @Test fun `calculo do dialog por valor cobrado desconto e percentual`() {
        val c = LancamentoAcoes.calcularAntecipacao(ModoAntecipacao.QUITAR, DescontoModo.COBRADO, 300.0, "", "270,00")!!
        assertEquals(270.0, c.pago, 0.0); assertEquals(30.0, c.desconto, 0.0)
        val v = LancamentoAcoes.calcularAntecipacao(ModoAntecipacao.QUITAR, DescontoModo.VALOR, 300.0, "", "30")!!
        assertEquals(270.0, v.pago, 0.0); assertEquals(30.0, v.desconto, 0.0)
        val p = LancamentoAcoes.calcularAntecipacao(ModoAntecipacao.QUITAR, DescontoModo.PERCENTUAL, 300.0, "", "10")!!
        assertEquals(270.0, p.pago, 0.0); assertEquals(30.0, p.desconto, 0.0)
        val sem = LancamentoAcoes.calcularAntecipacao(ModoAntecipacao.QUITAR, DescontoModo.COBRADO, 300.0, "", "")!!
        assertEquals(300.0, sem.pago, 0.0); assertEquals(0.0, sem.desconto, 0.0)
        val parcial = LancamentoAcoes.calcularAntecipacao(ModoAntecipacao.PARCIAL, DescontoModo.COBRADO, 300.0, "1.050,50", "")!!
        assertEquals(1050.5, parcial.pago, 0.0); assertEquals(0.0, parcial.desconto, 0.0)
        assertNull(LancamentoAcoes.calcularAntecipacao(ModoAntecipacao.PARCIAL, DescontoModo.COBRADO, 300.0, "", ""))
        assertNull(LancamentoAcoes.calcularAntecipacao(ModoAntecipacao.QUITAR, DescontoModo.COBRADO, 300.0, "", "abc"))
    }
}
