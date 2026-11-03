package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class LancamentoRapidoTest {
    private fun ms(m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(2026, m - 1, d, h, 0, 0) }.timeInMillis
    private val agora = ms(10, 15)

    private fun desp(
        desc: String, valor: Double, data: Long, cat: String = "Alimentação", conta: String = "1", cartaoId: Int? = null,
        tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = true, grupoId: String? = null
    ) = Despesa(
        descricao = desc, valor = valor, data = Date(data), categoria = cat, conta = conta, pic = "p", tipo = tipo,
        mes = Financas.mesDe(data), ano = Financas.anoDe(data), cartaoId = cartaoId, pago = pago, grupoId = grupoId
    )

    // ------------------------------------------------------------ parse do valor

    @Test fun `parse aceita formatos brasileiros e internacionais`() {
        assertEquals(12.0, LancamentoRapido.parseValor("12")!!, 0.0)
        assertEquals(12.5, LancamentoRapido.parseValor("12,5")!!, 0.0)
        assertEquals(1234.56, LancamentoRapido.parseValor("1.234,56")!!, 0.0)
        assertEquals(1234.56, LancamentoRapido.parseValor("1234.56")!!, 0.0)
        assertEquals(9.9, LancamentoRapido.parseValor("R$ 9,90")!!, 0.0)
        assertEquals(1234567.0, LancamentoRapido.parseValor("1.234.567")!!, 0.0)
    }

    @Test fun `parse recusa vazio, zero, negativo e lixo`() {
        listOf("", "  ", "0", "0,00", "-5", "abc", ",", "1,2,3").forEach { assertNull("'$it'", LancamentoRapido.parseValor(it)) }
    }

    @Test fun `parse arredonda em centavos`() {
        assertEquals(10.01, LancamentoRapido.parseValor("10,005")!!, 0.0)
    }

    // ------------------------------------------------------------ montagem

    @Test fun `despesa em conta nasce paga e receita tambem`() {
        val d = LancamentoRapido.montar(25.0, "Café", "Lanche", "lunch", TipoDespesa.DEBITO, "111", null, agora)
        assertTrue(d.pago); assertEquals(TipoDespesa.DEBITO, d.tipo); assertEquals(10, d.mes); assertEquals(2026, d.ano)
        assertTrue(LancamentoRapido.montar(100.0, "", "Receita", "", TipoDespesa.CREDITO, "111", null, agora).pago)
    }

    @Test fun `compra no cartao fica em aberto ate pagar a fatura`() {
        assertFalse(LancamentoRapido.montar(80.0, "Posto", "Combustível", "fuel", TipoDespesa.DEBITO, "", 7, agora).pago)
    }

    @Test fun `sem descricao usa a categoria e sem categoria usa um padrao`() {
        assertEquals("Lanche", LancamentoRapido.montar(5.0, "  ", "Lanche", "", TipoDespesa.DEBITO, "1", null, agora).descricao)
        val vazio = LancamentoRapido.montar(5.0, "", "", "", TipoDespesa.DEBITO, "1", null, agora)
        assertEquals("Despesa", vazio.descricao); assertEquals("Outros", vazio.categoria)
    }

    @Test fun `categorias ordenadas pelo uso e sem duplicar`() {
        val hist = listOf(desp("a", 1.0, agora, "Lanche"), desp("b", 1.0, agora, "Lanche"), desp("c", 1.0, agora, "Saúde"))
        val r = LancamentoRapido.categoriasPorUso(listOf("Cinema", "Saúde", "lanche", "Lanche", "Jogos"), hist, TipoDespesa.DEBITO)
        assertEquals(listOf("lanche", "Saúde", "Cinema", "Jogos"), r)
    }

    // ------------------------------------------------------------ widget: mês, hoje, atalhos, orçamento

    @Test fun `mes e hoje consideram so receitas e despesas comuns`() {
        val hist = listOf(
            desp("sal", 5000.0, ms(10, 5), "Salário", tipo = TipoDespesa.CREDITO),
            desp("mercado", 300.0, ms(10, 10)),
            desp("hoje", 40.0, ms(10, 15, 9)),
            desp("mês passado", 999.0, ms(9, 20)),
        )
        val r = WidgetResumo.montar(listOf(0.0), emptyList(), emptyList(), agora, hist)
        assertEquals(5000.0, r.receitasMes, 0.0)
        assertEquals(340.0, r.despesasMes, 0.0)
        assertEquals(40.0, r.gastoHoje, 0.0)
        assertEquals(4660.0, r.resultadoMes, 0.0)
    }

    @Test fun `atalhos sao as despesas repetidas, mais frequentes primeiro, no maximo dois`() {
        val hist = listOf(
            desp("Café", 6.0, ms(10, 1)), desp("Café", 6.0, ms(10, 8)), desp("café ", 6.0, ms(10, 12)),
            desp("Ônibus", 4.5, ms(10, 2)), desp("Ônibus", 4.5, ms(10, 9)),
            desp("Pizza", 50.0, ms(10, 3)), desp("Pizza", 50.0, ms(10, 4)),
            desp("Único", 10.0, ms(10, 5)),
            desp("Café", 6.0, ms(1, 1)), // fora dos 90 dias
        )
        val at = WidgetResumo.atalhos(hist, agora)
        assertEquals(2, at.size)
        assertEquals(3, at[0].vezes); assertEquals(6.0, at[0].valor, 0.0)
        assertEquals("Ônibus", at[1].descricao) // empate em 2 vezes: o mais recente
    }

    @Test fun `atalhos ignoram receitas, parcelas e valores diferentes`() {
        val hist = listOf(
            desp("Salário", 10.0, ms(10, 1), tipo = TipoDespesa.CREDITO), desp("Salário", 10.0, ms(10, 2), tipo = TipoDespesa.CREDITO),
            desp("TV (1/3)", 100.0, ms(10, 1), grupoId = "parc:x"), desp("TV (2/3)", 100.0, ms(10, 2), grupoId = "parc:x"),
            desp("Almoço", 30.0, ms(10, 3)), desp("Almoço", 32.0, ms(10, 4)),
        )
        assertTrue(WidgetResumo.atalhos(hist, agora).isEmpty())
    }

    @Test fun `atalho nao mistura origens diferentes`() {
        val hist = listOf(desp("Café", 6.0, ms(10, 1), conta = "1"), desp("Café", 6.0, ms(10, 2), conta = "2"))
        assertTrue(WidgetResumo.atalhos(hist, agora).isEmpty())
    }

    @Test fun `orcamento mais critico e proxima conta`() {
        val hist = listOf(desp("a", 90.0, ms(10, 3), "Lanche"), desp("b", 100.0, ms(10, 4), "Saúde"))
        val orc = listOf(Orcamento(categoria = "Lanche", valorLimite = 100.0), Orcamento(categoria = "Saúde", valorLimite = 1000.0))
        val pend = listOf(desp("Luz", 150.0, ms(10, 20), pago = false), desp("Aluguel", 1500.0, ms(10, 10), pago = false))
        val r = WidgetResumo.montar(emptyList(), emptyList<Meta>(), pend, agora, hist, orc)
        assertEquals("Lanche", r.orcamento!!.categoria)
        assertEquals(90, r.orcamento!!.percentual)
        assertEquals("Aluguel", r.proximaConta!!.descricao)
        assertTrue(r.proximaConta!!.atrasada)
        assertNotNull(r.proximaConta)
    }
}
