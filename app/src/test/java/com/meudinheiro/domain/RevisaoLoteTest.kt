package com.meudinheiro.domain

import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.NotificacaoBancaria.Confianca
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada
import com.meudinheiro.domain.NotificacaoBancaria.Tipo
import com.meudinheiro.domain.RevisaoLote.Destino
import com.meudinheiro.domain.RevisaoLote.Edicao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisaoLoteTest {
    private fun sug(
        tipo: Tipo = Tipo.COMPRA_DEBITO, valor: Long = 8990, desc: String = "PADARIA X", entrada: Boolean = false,
        cartaoId: Int? = null, conta: String? = null, horario: Long = 1_760_000_000_000L
    ) = SugestaoGuardada(
        id = "i", tipo = tipo, valorCentavos = valor, descricao = desc, confianca = Confianca.ALTA, entrada = entrada,
        pacote = "com.nu.production", horarioMs = horario, criadaEm = horario, cartaoId = cartaoId, conta = conta
    )

    @Test fun `valida destino, categoria e receita em cartao`() {
        val g = sug()
        assertEquals("Escolha a conta ou o cartão.", RevisaoLote.validar(g, Edicao("x", "Lazer", Destino())))
        assertEquals("Escolha uma categoria.", RevisaoLote.validar(g, Edicao("x", " ", Destino(conta = "1"))))
        assertNull(RevisaoLote.validar(g, Edicao("x", "Lazer", Destino(conta = "1"))))
        assertNull(RevisaoLote.validar(g, Edicao("x", "Lazer", Destino(cartaoId = 3))))
        val r = sug(Tipo.PIX_RECEBIDO, entrada = true)
        assertNull(RevisaoLote.validar(r, Edicao("x", null, Destino(conta = "1")))) // receita não exige categoria
        assertNotNull(RevisaoLote.validar(r, Edicao("x", null, Destino(cartaoId = 3))))
    }

    @Test fun `monta despesa na conta, paga, com a data da notificacao`() {
        val g = sug()
        val l = RevisaoLote.montar(g, Edicao("Padaria do Ze", "Alimentação", Destino(conta = "123")), "pic")
        assertEquals(TipoDespesa.DEBITO, l.tipo)
        assertEquals(89.90, l.valor, 0.0)
        assertEquals("Padaria do Ze", l.descricao)
        assertEquals("Alimentação", l.categoria)
        assertEquals("123", l.conta)
        assertEquals(g.horarioMs, l.dataMs)
        assertTrue(l.pago)
        assertNull(l.cartaoId)
    }

    @Test fun `monta compra no cartao em aberto e receita em conta`() {
        val c = RevisaoLote.montar(sug(), Edicao("", "Lazer", Destino(cartaoId = 7)), "")
        assertEquals(7, c.cartaoId)
        assertFalse(c.pago)
        assertEquals("PADARIA X", c.descricao) // vazio volta ao título padrão
        val r = RevisaoLote.montar(sug(Tipo.PIX_RECEBIDO, entrada = true, desc = "Maria"), Edicao("Pix de Maria", "qualquer", Destino(conta = "9")), "")
        assertEquals(TipoDespesa.CREDITO, r.tipo)
        assertEquals("Receita", r.categoria)
        assertEquals("9", r.conta)
    }

    @Test fun `modalidade e aprendizado`() {
        assertEquals(Financas.Modalidade.DEBITO, RevisaoLote.modalidade(sug(Tipo.COMPRA_DEBITO)))
        assertEquals(Financas.Modalidade.CREDITO, RevisaoLote.modalidade(sug(Tipo.COMPRA_CREDITO)))
        assertEquals("PADARIA X" to "Lazer", RevisaoLote.paraAprender(sug(), Edicao("outro texto", " Lazer ", Destino(conta = "1"))))
        assertNull(RevisaoLote.paraAprender(sug(entrada = true), Edicao("x", "Lazer", Destino(conta = "1"))))
        assertNull(RevisaoLote.paraAprender(sug(), Edicao("x", null, Destino(conta = "1"))))
    }

    @Test fun `destino padrao prefere o resolvido, depois a ultima origem, depois a primeira conta`() {
        val contas = listOf("10", "20")
        val cartoes = listOf(5)
        assertEquals(Destino(cartaoId = 5), RevisaoLote.destinoPadrao(sug(cartaoId = 5), contas, cartoes, null))
        assertEquals(Destino(conta = "20"), RevisaoLote.destinoPadrao(sug(conta = "20"), contas, cartoes, "c:10"))
        assertEquals(Destino(conta = "20"), RevisaoLote.destinoPadrao(sug(), contas, cartoes, "c:20"))
        assertEquals(Destino(cartaoId = 5), RevisaoLote.destinoPadrao(sug(), contas, cartoes, "k:5"))
        assertEquals(Destino(conta = "10"), RevisaoLote.destinoPadrao(sug(), contas, cartoes, "c:99")) // conta apagada
        assertEquals(Destino(conta = "10"), RevisaoLote.destinoPadrao(sug(entrada = true), contas, cartoes, "k:5")) // receita não vai ao cartão
        assertEquals(Destino(conta = "10"), RevisaoLote.destinoPadrao(sug(entrada = true, cartaoId = 5), contas, cartoes, null))
    }

    @Test fun `chaves de destino ida e volta`() {
        assertEquals("c:12", RevisaoLote.chaveDoDestino(Destino(conta = "12")))
        assertEquals("k:3", RevisaoLote.chaveDoDestino(Destino(cartaoId = 3)))
        assertNull(RevisaoLote.chaveDoDestino(Destino()))
        assertEquals(Destino(cartaoId = 3), RevisaoLote.destinoDeChave("k:3"))
        assertNull(RevisaoLote.destinoDeChave("k:x"))
        assertNull(RevisaoLote.destinoDeChave("lixo"))
    }

    @Test fun `textos`() {
        assertEquals("Confirmar selecionadas (3)", RevisaoLote.rotuloConfirmar(3))
        assertEquals("Descartar selecionadas", RevisaoLote.rotuloDescartar(0))
        assertEquals("2 lançamentos criados.", RevisaoLote.mensagemResultado(2, 0))
        assertEquals("1 criado(s); 1 com erro (veja nas linhas).", RevisaoLote.mensagemResultado(1, 1))
        assertEquals("Nada selecionado.", RevisaoLote.mensagemResultado(0, 0))
        assertFalse(RevisaoLote.precisaResumo(1))
        assertTrue(RevisaoLote.precisaResumo(2))
        assertEquals("4 sugestões de lançamento", RevisaoLote.tituloResumo(4))
    }
}
