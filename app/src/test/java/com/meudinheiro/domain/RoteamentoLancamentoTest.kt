package com.meudinheiro.domain

import com.meudinheiro.domain.RoteamentoLancamento.Modo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoteamentoLancamentoTest {
    @Test fun `acao de nova receita abre em receita`() {
        assertEquals(Modo.RECEITA, RoteamentoLancamento.modoInicial(RoteamentoLancamento.ACAO_NOVA_RECEITA, null))
        assertEquals(Modo.RECEITA, RoteamentoLancamento.modoInicial(RoteamentoLancamento.ACAO_NOVA_RECEITA, "DEBITO")) // a ação manda
    }

    @Test fun `novo lancamento comeca em despesa`() {
        assertEquals(Modo.DESPESA, RoteamentoLancamento.modoInicial(RoteamentoLancamento.ACAO_NOVO_LANCAMENTO, null))
        assertEquals(Modo.DESPESA, RoteamentoLancamento.modoInicial(RoteamentoLancamento.ACAO_NOVO_LANCAMENTO, "CREDITO"))
    }

    @Test fun `sem acao vale o extra do widget`() {
        assertEquals(Modo.RECEITA, RoteamentoLancamento.modoInicial(null, "CREDITO"))
        assertEquals(Modo.DESPESA, RoteamentoLancamento.modoInicial(null, "DEBITO"))
    }

    @Test fun `entrada desconhecida cai em despesa`() {
        assertEquals(Modo.DESPESA, RoteamentoLancamento.modoInicial(null, null))
        assertEquals(Modo.DESPESA, RoteamentoLancamento.modoInicial("android.intent.action.MAIN", ""))
        assertEquals(Modo.DESPESA, RoteamentoLancamento.modoInicial("outra", "credito"))
    }

    @Test fun `extra do widget ida e volta`() {
        Modo.values().forEach { m -> assertEquals(m, RoteamentoLancamento.modoInicial(null, RoteamentoLancamento.tipoDoExtra(m))) }
        assertEquals("CREDITO", RoteamentoLancamento.tipoDoExtra(Modo.RECEITA))
        assertEquals("DEBITO", RoteamentoLancamento.tipoDoExtra(Modo.DESPESA))
    }

    @Test fun `acoes conhecidas`() {
        assertTrue(RoteamentoLancamento.acaoConhecida(RoteamentoLancamento.ACAO_NOVA_RECEITA))
        assertTrue(RoteamentoLancamento.acaoConhecida(RoteamentoLancamento.ACAO_NOVO_LANCAMENTO))
        assertFalse(RoteamentoLancamento.acaoConhecida(AtalhosAppAcoes.NOVA_DESPESA))
        assertFalse(RoteamentoLancamento.acaoConhecida(null))
    }

    private object AtalhosAppAcoes { const val NOVA_DESPESA = "com.meudinheiro.action.NOVA_DESPESA" }
}
