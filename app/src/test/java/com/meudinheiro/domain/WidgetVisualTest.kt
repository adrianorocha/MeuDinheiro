package com.meudinheiro.domain

import com.meudinheiro.domain.WidgetPlano.Variacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class WidgetVisualTest {
    private val fuso = TimeZone.getTimeZone("America/Sao_Paulo")

    private fun ms(d: Int, h: Int = 12) = Calendar.getInstance(fuso).apply { clear(); set(2026, 9, d, h, 0, 0) }.timeInMillis
    private fun conta(desc: String, dia: Int, valor: Double = 10.0, atrasada: Boolean = false) = ContaWidget(desc, Date(ms(dia)), valor, atrasada)

    // ── variação por tamanho ──
    @Test fun `tamanho pequeno ou baixo cai no compacto`() {
        assertEquals(Variacao.COMPACTO, WidgetPlano.para(110, 60).variacao)
        assertEquals(Variacao.COMPACTO, WidgetPlano.para(250, 80).variacao) // largo mas baixo
        assertEquals(Variacao.COMPACTO, WidgetPlano.para(110, 400).variacao) // alto mas estreito
    }

    @Test fun `degraus de altura escolhem enxuto medio e grande`() {
        assertEquals(Variacao.ENXUTO, WidgetPlano.para(170, WidgetPlano.ALT_ENXUTO).variacao)
        assertEquals(Variacao.ENXUTO, WidgetPlano.para(170, WidgetPlano.ALT_MEDIO - 1).variacao)
        assertEquals(Variacao.MEDIO, WidgetPlano.para(170, WidgetPlano.ALT_MEDIO).variacao)
        assertEquals(Variacao.MEDIO, WidgetPlano.para(200, WidgetPlano.ALT_GRANDE - 1).variacao)
        assertEquals(Variacao.GRANDE, WidgetPlano.para(170, WidgetPlano.ALT_GRANDE).variacao)
    }

    @Test fun `medio mostra a proxima conta so com altura de sobra`() {
        assertFalse(WidgetPlano.para(170, WidgetPlano.ALT_MEDIO).proxima)
        assertTrue(WidgetPlano.para(170, WidgetPlano.ALT_MEDIO_PROXIMA).proxima)
    }

    @Test fun `grande escala vencimentos atualizado atalhos e rotulos`() {
        val g = WidgetPlano.para(170, WidgetPlano.ALT_GRANDE)
        assertEquals(2, g.vencimentos); assertFalse(g.atualizado); assertFalse(g.atalhos); assertFalse(g.rotulos)
        assertTrue(WidgetPlano.para(170, WidgetPlano.ALT_GRANDE_ATUALIZADO).atualizado)
        assertEquals(3, WidgetPlano.para(170, WidgetPlano.ALT_GRANDE_3_VENC).vencimentos)
        assertTrue(WidgetPlano.para(170, WidgetPlano.ALT_GRANDE_ATALHOS).atalhos)
        assertTrue(WidgetPlano.para(WidgetPlano.LARG_LARGA, WidgetPlano.ALT_GRANDE).rotulos)
    }

    @Test fun `todo tamanho declarado resulta na variacao esperada e nao repete`() {
        assertEquals(WidgetPlano.TAMANHOS.size, WidgetPlano.TAMANHOS.toSet().size)
        val variacoes = WidgetPlano.TAMANHOS.map { (l, a) -> WidgetPlano.para(l, a).variacao }.toSet()
        assertEquals(Variacao.values().toSet(), variacoes) // as 4 variações são alcançáveis
    }

    // ── formatação ──
    @Test fun `modo privado oculta saldo chips e contas`() {
        assertEquals("••••••", WidgetFormato.saldo(1234.5, true, Variacao.GRANDE))
        assertEquals("▲ ••••••", WidgetFormato.chipEntradas(10.0, true))
        assertEquals("▼ ••••••", WidgetFormato.chipSaidas(10.0, true))
        assertEquals("••••••", WidgetFormato.valorConta(99.0, true))
    }

    @Test fun `saldo compacto abrevia valores muito longos e preserva o sinal`() {
        assertEquals("R$ 1,2 mi", WidgetFormato.saldo(1_234_567.89, false, Variacao.COMPACTO))
        assertEquals("-R$ 123 mil", WidgetFormato.saldo(-123_456.78, false, Variacao.COMPACTO))
        assertTrue(WidgetFormato.saldo(1_234_567.89, false, Variacao.GRANDE).contains("1.234.567,89")) // nos demais fica completo
    }

    @Test fun `chip usa numero completo quando cabe e abrevia quando nao`() {
        assertEquals("950,00", WidgetFormato.valorChip(950.0, false))
        assertEquals("1.234,56", WidgetFormato.valorChip(1234.56, false))
        assertEquals("12 mil", WidgetFormato.valorChip(12_345.67, false))
        assertEquals("1,2 mi", WidgetFormato.valorChip(1_234_567.0, false))
        assertEquals("12 mi", WidgetFormato.valorChip(12_345_678.0, false))
        assertEquals("100 mil", WidgetFormato.valorChip(-100_000.0, false)) // sinal vem da seta, não do número
    }

    @Test fun `fonte do saldo encolhe com o tamanho do texto e nunca passa do limite`() {
        assertEquals(26, WidgetFormato.fonteSaldoSp("R$ 99,90", Variacao.MEDIO))
        assertTrue(WidgetFormato.fonteSaldoSp("R$ 1.234,56", Variacao.MEDIO) < 26)
        assertTrue(WidgetFormato.fonteSaldoSp("R$ 1.234.567,89", Variacao.GRANDE) <= 17)
        assertTrue(WidgetFormato.fonteSaldoSp("R$ 1.234,56", Variacao.COMPACTO) <= 15)
        val textos = listOf("R$ 0,00", "R$ 12,34", "R$ 1.234,56", "R$ 12.345,67", "-R$ 123.456,78", "R$ 1.234.567,89")
        for (v in Variacao.values()) for (t in textos) assertTrue(WidgetFormato.fonteSaldoSp(t, v) in 11..26)
    }

    @Test fun `hora de atualizacao em HH mm no fuso dado`() {
        assertEquals("09:05", WidgetFormato.horaAtualizacao(ms(15, 9) + 5 * 60_000L, fuso))
        assertEquals("23:59", WidgetFormato.horaAtualizacao(ms(15, 23) + 59 * 60_000L, fuso))
    }

    @Test fun `rotulo do dia hoje amanha ou data e atrasada sempre com data`() {
        val agora = ms(15, 10)
        assertEquals("Hoje", WidgetFormato.rotuloDia(conta("a", 15), agora, fuso))
        assertEquals("Amanhã", WidgetFormato.rotuloDia(conta("a", 16), agora, fuso))
        assertEquals("20/10", WidgetFormato.rotuloDia(conta("a", 20), agora, fuso))
        assertEquals("10/10", WidgetFormato.rotuloDia(conta("a", 10, atrasada = true), agora, fuso))
        assertEquals("15/10", WidgetFormato.rotuloDia(conta("a", 15, atrasada = true), agora, fuso))
    }

    // ── vencimentos ──
    @Test fun `vencimentos atrasadas primeiro depois por data e limita ao maximo`() {
        val lista = listOf(
            conta("luz", 20), conta("agua", 16), conta("aluguel", 5, atrasada = true),
            conta("net", 18), conta("gas", 3, atrasada = true)
        )
        val r = WidgetVencimentos.selecionar(lista, 3)
        assertEquals(listOf("gas", "aluguel", "agua"), r.map { it.descricao })
    }

    @Test fun `vencimentos empate por data fica pelo maior valor e depois nome`() {
        val r = WidgetVencimentos.selecionar(listOf(conta("b", 16, 10.0), conta("a", 16, 10.0), conta("c", 16, 50.0)), 5)
        assertEquals(listOf("c", "a", "b"), r.map { it.descricao })
        assertTrue(WidgetVencimentos.selecionar(emptyList(), 3).isEmpty())
        assertTrue(WidgetVencimentos.selecionar(listOf(conta("a", 1)), 0).isEmpty())
    }

    @Test fun `resumo traz no maximo tres proximas e ignora as pagas`() {
        val agora = ms(15)
        fun d(desc: String, dia: Int, pago: Boolean = false) = com.meudinheiro.data.Despesa(
            descricao = desc, valor = 10.0, data = Date(ms(dia)), categoria = "c", conta = "1", pic = "p",
            tipo = com.meudinheiro.data.TipoDespesa.DEBITO, mes = 10, ano = 2026, pago = pago
        )
        val r = WidgetResumo.montar(emptyList(), emptyList(), listOf(d("a", 20), d("b", 10), d("c", 16), d("d", 17), d("paga", 12, pago = true)), agora)
        assertEquals(listOf("b", "c", "d"), r.proximas.map { it.descricao })
        assertTrue(r.proximas.first().atrasada)
    }

    // ── roteamento dos atalhos ──
    @Test fun `cada destino tem acao propria e volta ao mesmo destino`() {
        val acoes = DestinoApp.values().map { it.acao }
        assertEquals(acoes.size, acoes.toSet().size)
        for (d in DestinoApp.values()) assertEquals(d, DestinoApp.daAcao(d.acao))
    }

    @Test fun `acao desconhecida ou nula nao roteia`() {
        assertNull(DestinoApp.daAcao(null))
        assertNull(DestinoApp.daAcao("com.meudinheiro.action.NOVA_DESPESA"))
        assertNull(DestinoApp.daAcao("abrir_extrato"))
    }

    @Test fun `destino aponta para a aba ou a tela de extrato`() {
        assertNull(RoteamentoWidget.abaPrincipal(DestinoApp.EXTRATO)) // tela própria
        assertEquals(2, RoteamentoWidget.abaPrincipal(DestinoApp.CARTOES))
        assertEquals(3, RoteamentoWidget.abaPrincipal(DestinoApp.METAS))
    }
}
