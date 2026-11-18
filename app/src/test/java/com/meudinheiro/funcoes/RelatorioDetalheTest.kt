package com.meudinheiro.funcoes

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.AgruparPor
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.ExtratoResumo
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.FiltroRelatorio
import com.meudinheiro.domain.Natureza
import com.meudinheiro.domain.RelatorioDetalhe
import com.meudinheiro.domain.Relatorios
import com.meudinheiro.domain.TipoRelatorio
import com.meudinheiro.domain.parcelaDaDescricao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R48: o detalhamento traz os itens de cada total e os subtotais (em centavos) fecham exatamente com o total do relatório. */
class RelatorioDetalheTest {

    private fun ms(m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(2026, m - 1, d, 12, 0, 0) }.timeInMillis

    private fun l(
        id: Long, desc: String, valor: Double, cat: String, tipo: TipoDespesa = TipoDespesa.DEBITO, dia: Int = 5,
        cartao: Int? = null, natureza: String = Natureza.NORMAL, pago: Boolean = true
    ) = Despesa(
        id = id, descricao = desc, valor = valor, data = Date(ms(9, dia)), categoria = cat, conta = "111", pic = "p",
        tipo = tipo, mes = 9, ano = 2026, cartaoId = cartao, pago = pago, natureza = natureza
    )

    private val dados = listOf(
        l(1, "Mercado (1/3)", 0.1, "Alimentação", dia = 3),
        l(2, "Mercado (2/3)", 0.2, "Alimentação", dia = 4),
        l(3, "Padaria", 33.33, "Alimentação", dia = 2),
        l(4, "Gasolina", 150.07, "Transporte", cartao = 7, dia = 9, pago = false),
        l(5, "Estorno gasolina", 20.01, "Transporte", TipoDespesa.CREDITO, cartao = 7, dia = 10),
        l(6, "Salário", 5000.0, "Salário", TipoDespesa.CREDITO, dia = 1),
        l(7, "Ajuste de saldo", 99.0, "Ajuste", natureza = Natureza.AJUSTE),
        l(8, "Sem categoria", 7.77, "  ")
    )
    private val mes = Financas.inicioDoMes(9, 2026) to Financas.fimDoMes(9, 2026)

    private fun resultado(tipo: TipoRelatorio) = Relatorios.gerar(FiltroRelatorio(mes.first, mes.second, tipo = tipo), dados)

    @Test fun `subtotais fecham com o total em todos os tipos e agrupamentos`() {
        for (tipo in TipoRelatorio.entries) for (agr in AgruparPor.entries) {
            val r = resultado(tipo)
            val det = RelatorioDetalhe.detalhar(r, agr)
            assertEquals("$tipo/$agr total", Dinheiro.centavos(r.total), det.totalCentavos)
            assertEquals("$tipo/$agr quantidade", r.quantidade, det.quantidade)
            det.secoes.forEach { s ->
                assertEquals(s.totalCentavos, s.grupos.sumOf { it.subtotalCentavos })
                s.grupos.forEach { g -> assertEquals(g.subtotalCentavos, g.linhas.sumOf { it.centavos }) }
            }
        }
    }

    @Test fun `ajuste fica fora e despesas incluem estorno de cartao abatendo`() {
        val det = RelatorioDetalhe.detalhar(resultado(TipoRelatorio.DESPESA))
        val ids = det.secoes.flatMap { s -> s.grupos.flatMap { g -> g.linhas.map { it.id } } }
        assertTrue(7L !in ids)
        assertEquals(1, det.secoes.size)
        assertNull(det.secoes[0].titulo)
        val transporte = det.secoes[0].grupos.first { it.titulo == "Transporte" }
        assertEquals(15007L - 2001L, transporte.subtotalCentavos)
        assertTrue(det.secoes[0].grupos.any { it.titulo == "Sem categoria" })
    }

    @Test fun `tipo todos separa receitas e despesas e ordena linhas por data`() {
        val det = RelatorioDetalhe.detalhar(resultado(TipoRelatorio.TODOS))
        assertEquals(listOf("Receitas", "Despesas"), det.secoes.map { it.titulo })
        val alim = det.secoes[1].grupos.first { it.titulo == "Alimentação" }
        assertEquals(listOf(3L, 1L, 2L), alim.linhas.map { it.id })
        assertEquals(listOf("", "1/3", "2/3"), alim.linhas.map { it.parcela })
        assertEquals("Mercado", alim.linhas.first { it.id == 1L }.descricao)
        assertEquals(-3333L - 10L - 20L, alim.subtotalCentavos)
    }

    @Test fun `resumo para imagem limita itens e conta os restantes`() {
        val muitos = (1..12).map { l(100L + it, "Item $it", it.toDouble(), "Lazer", dia = it) }
        val r = Relatorios.gerar(FiltroRelatorio(mes.first, mes.second, tipo = TipoRelatorio.DESPESA), muitos)
        val sec = RelatorioDetalhe.detalhar(r).secoes[0]
        val (grupos, omitidos) = RelatorioDetalhe.resumirParaImagem(sec, maxPorGrupo = 5)
        assertEquals(0, omitidos)
        assertEquals(5, grupos[0].linhas.size)
        assertEquals(7, grupos[0].restantes)
        assertEquals(Dinheiro.centavos(r.total), grupos[0].subtotalCentavos)
        assertEquals(setOf(12L, 11L, 10L, 9L, 8L).map { 100 + it }.toSet(), grupos[0].linhas.map { it.id }.toSet())
    }

    @Test fun `parcela da descricao`() {
        assertEquals(Triple("Sofá", 3, 10), parcelaDaDescricao("Sofá (3/10)"))
        assertNull(parcelaDaDescricao("Compra 3/10 loja"))
        assertNull(parcelaDaDescricao("X (1/1)"))
    }

    @Test fun `extrato mensal subtotais fecham`() {
        fun d(id: Long, v: Double, t: TipoDespesa, cat: String) =
            DespesasDomain(id, "p", "i$id", v, ms(9, 2), "111", cat, t, true)
        val itens = listOf(d(1, 10.10, TipoDespesa.DEBITO, "A"), d(2, 20.20, TipoDespesa.DEBITO, "B"), d(3, 100.05, TipoDespesa.CREDITO, "A"))
        val r = ExtratoResumo.calcular(itens)
        assertEquals(10005L, r.entradasCentavos)
        assertEquals(3030L, r.saidasCentavos)
        assertEquals(6975L, r.saldoCentavos)
        assertEquals(r.saldoCentavos, r.porCategoria.sumOf { it.second })
    }
}
