package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.ResumoInteligente.Insight
import com.meudinheiro.domain.ResumoInteligente.TipoInsight
import com.meudinheiro.domain.ResumoInteligente.TipoPeriodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** Mesmos cenários e números de portal/src/lib/finance/resumo.test.ts. */
class ResumoInteligenteTest {

    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis

    private var seq = 5000L
    private fun d(
        data: Long, descricao: String, categoria: String, valor: Double,
        tipo: TipoDespesa = TipoDespesa.DEBITO, natureza: String = Natureza.NORMAL
    ) = Despesa(
        id = seq++, descricao = descricao, valor = valor, data = Date(data), categoria = categoria, conta = "111", pic = "p",
        tipo = tipo, mes = Financas.mesDe(data), ano = Financas.anoDe(data), pago = true, natureza = natureza
    )

    private fun sal(data: Long, valor: Double) = d(data, "Salário", "Salário", valor, TipoDespesa.CREDITO)

    private val agora = ms(2026, 10, 15)

    private fun gerar(
        despesas: List<Despesa>, tipo: TipoPeriodo = TipoPeriodo.MES, ref: Long = ms(2026, 10, 15), hoje: Long = agora,
        orcamentos: List<Orcamento> = emptyList()
    ) = ResumoInteligente.gerar(despesas, emptyList(), orcamentos, tipo, ref, hoje)

    private fun List<Insight>.tipo(t: TipoInsight) = firstOrNull { it.tipo == t }
    private fun List<Insight>.tipos() = map { it.tipo }

    // ----------------------------------------------------------------- mês em andamento

    private val cenario1 = listOf(
        sal(ms(2026, 9, 2), 5000.0),
        d(ms(2026, 9, 5), "Aluguel", "Moradia", 1500.0),
        d(ms(2026, 9, 8), "Mercado", "Supermercado", 400.0),
        d(ms(2026, 9, 10), "Restaurante", "Restaurantes", 100.0),
        d(ms(2026, 9, 20), "Mercado", "Supermercado", 300.0),
        d(ms(2026, 9, 25), "Cinema", "Lazer", 200.0),
        d(ms(2026, 6, 10), "Farmácia A", "Farmácia", 50.0),
        d(ms(2026, 7, 10), "Farmácia B", "Farmácia", 50.0),
        d(ms(2026, 8, 10), "Farmácia C", "Farmácia", 50.0),
        sal(ms(2026, 10, 2), 5500.0),
        d(ms(2026, 10, 5), "Aluguel", "Moradia", 1500.0),
        d(ms(2026, 10, 8), "Mercado", "Supermercado", 600.0),
        d(ms(2026, 10, 10), "Restaurante", "Restaurantes", 400.0),
        d(ms(2026, 10, 14), "Farmácia grande", "Farmácia", 250.0),
        d(ms(2026, 10, 12), "Transferência", "Transferência", 2000.0, natureza = Natureza.TRANSFERENCIA),
        d(ms(2026, 10, 13), "Fatura", "Cartão", 1000.0, natureza = Natureza.PAGAMENTO_FATURA)
    )

    @Test fun `compara despesas e receitas com o mesmo ponto do mes anterior em centavos`() {
        val r = gerar(cenario1)
        assertTrue(r.periodo.emAndamento)
        assertEquals(15, r.periodo.diasDecorridos)
        assertEquals("Outubro de 2026", r.periodo.rotulo)
        assertEquals(ResumoInteligente.Comparativo(275000, 200000, 75000, 375), r.despesas)
        assertEquals(ResumoInteligente.Comparativo(550000, 500000, 50000, 100), r.receitas)
        assertEquals(275000L, r.resultadoCentavos)
        assertEquals(500L, r.taxaPoupancaDecimos)
        val desp = r.insights.tipo(TipoInsight.DESPESAS_TOTAL)!!
        assertEquals("Você gastou R$ 2.750,00: +R$ 750,00 (+37,5%) em relação ao mesmo período do mês anterior (até o dia 15).", desp.texto)
        assertEquals(ResumoInteligente.Tom.ALERTA, desp.tom)
    }

    @Test fun `projeta o fechamento no ritmo atual contra o mes anterior inteiro`() {
        val r = gerar(cenario1)
        assertEquals(568333L, r.projecaoCentavos)
        val p = r.insights.tipo(TipoInsight.PROJECAO)!!
        assertEquals(1273L, p.variacaoDecimos)
        assertEquals("No ritmo atual, o mês deve fechar em R$ 5.683,33 em despesas: R$ 3.183,33 acima (+127,3%) do mês anterior inteiro (R$ 2.500,00).", p.texto)
    }

    @Test fun `lista so categorias relevantes maiores altas primeiro`() {
        val r = gerar(cenario1)
        val altas = r.insights.filter { it.tipo == TipoInsight.CATEGORIA_ALTA }
        assertEquals(listOf("Restaurantes em alta", "Farmácia em alta", "Supermercado em alta"), altas.map { it.titulo })
        assertEquals(listOf<Long?>(3000, null, 500), altas.map { it.variacaoDecimos })
        assertEquals(listOf(73, 72, 72), altas.map { it.relevancia })
        assertFalse(r.insights.any { it.titulo.startsWith("Moradia") })
        assertNull(r.insights.tipo(TipoInsight.CATEGORIA_QUEDA))
    }

    @Test fun `detecta gasto fora do padrao`() {
        val f = gerar(cenario1).insights.filter { it.tipo == TipoInsight.FORA_PADRAO }
        assertEquals(1, f.size)
        assertEquals(25000L, f[0].valorCentavos)
        assertEquals("Farmácia grande (Farmácia): R$ 250,00, 5,0x a mediana da categoria (R$ 50,00).", f[0].texto)
        assertFalse(f.any { it.texto.contains("Restaurante") }) // amostra pequena demais
    }

    @Test fun `ordena por relevancia com empate pela ordem de geracao e ignora natureza interna`() {
        val r = gerar(cenario1)
        assertEquals(
            listOf(
                TipoInsight.DESPESAS_TOTAL, TipoInsight.RECEITAS_TOTAL, TipoInsight.POUPANCA, TipoInsight.FORA_PADRAO,
                TipoInsight.PROJECAO, TipoInsight.CATEGORIA_ALTA, TipoInsight.CATEGORIA_ALTA, TipoInsight.CATEGORIA_ALTA,
                TipoInsight.BOA_NOTICIA
            ),
            r.insights.tipos()
        )
        assertEquals("Você está poupando 50,0% da receita, acima dos 20% recomendados.", r.insights.tipo(TipoInsight.BOA_NOTICIA)!!.texto)
        assertEquals(listOf(90, 85, 80, 78, 75, 73, 72, 72, 45), r.insights.map { it.relevancia })
    }

    @Test fun `texto compartilhavel traz o titulo e cada insight`() {
        val t = ResumoInteligente.texto(gerar(cenario1))
        assertTrue(t.startsWith("Resumo de Outubro de 2026 - MeuDinheiro"))
        assertTrue(t.contains("- Despesas: Você gastou R$ 2.750,00"))
    }

    // ----------------------------------------------------------------- recorrências

    @Test fun `aponta recorrencia nova e a que ficou mais cara`() {
        val despesas = listOf(
            d(ms(2026, 7, 5), "Streaming X", "Streaming", 40.0),
            d(ms(2026, 8, 5), "Streaming X", "Streaming", 40.0),
            d(ms(2026, 9, 5), "Streaming X", "Streaming", 40.0),
            d(ms(2026, 10, 5), "Streaming X", "Streaming", 44.0),
            d(ms(2026, 8, 7), "Academia Y", "Academia", 100.0),
            d(ms(2026, 9, 7), "Academia Y", "Academia", 100.0),
            d(ms(2026, 10, 7), "Academia Y", "Academia", 100.0)
        )
        val r = gerar(despesas)
        assertEquals("Academia Y passou a se repetir todo mês: cerca de R$ 100,00 por mês.", r.insights.tipo(TipoInsight.RECORRENCIA_NOVA)!!.texto)
        val aumento = r.insights.tipo(TipoInsight.RECORRENCIA_AUMENTO)!!
        assertEquals("Streaming X subiu de R$ 40,00 para R$ 44,00 (+10,0%).", aumento.texto)
        assertEquals(100L, aumento.variacaoDecimos)
    }

    // ----------------------------------------------------------------- mês anterior zerado

    @Test fun `mes anterior zerado nao divide por zero`() {
        val r = gerar(listOf(sal(ms(2026, 10, 2), 3000.0), d(ms(2026, 10, 5), "Mercado", "Supermercado", 800.0)))
        assertNull(r.despesas.variacaoDecimos)
        assertNull(r.receitas.variacaoDecimos)
        assertEquals(80000L, r.despesas.deltaCentavos)
        assertEquals("Você gastou R$ 800,00. Não houve despesas no mês anterior para comparar.", r.insights.tipo(TipoInsight.DESPESAS_TOTAL)!!.texto)
        val alta = r.insights.tipo(TipoInsight.CATEGORIA_ALTA)!!
        assertNull(alta.variacaoDecimos)
        assertEquals("Supermercado: R$ 800,00, sem gasto equivalente no mês anterior (+R$ 800,00).", alta.texto)
        assertEquals(165333L, r.projecaoCentavos)
        assertNull(r.insights.tipo(TipoInsight.PROJECAO)!!.variacaoDecimos)
        assertEquals(733L, r.taxaPoupancaDecimos)
    }

    // ----------------------------------------------------------------- período fechado

    private val fim = ms(2026, 11, 5)

    @Test fun `ruido abaixo dos limiares nao vira insight`() {
        val despesas = listOf(
            d(ms(2026, 9, 5), "Mercado", "Supermercado", 400.0),
            d(ms(2026, 9, 6), "Lazer", "Lazer", 1000.0),
            d(ms(2026, 10, 5), "Mercado", "Supermercado", 440.0), // +R$ 40 (10%): abaixo de R$ 50
            d(ms(2026, 10, 6), "Lazer", "Lazer", 1100.0) // +R$ 100 mas só 10%
        )
        val r = gerar(despesas, ref = ms(2026, 10, 10), hoje = fim)
        assertFalse(r.periodo.emAndamento)
        assertNull(r.projecaoCentavos)
        val t = r.insights.tipos()
        assertFalse(TipoInsight.CATEGORIA_ALTA in t)
        assertFalse(TipoInsight.CATEGORIA_QUEDA in t)
        assertFalse(TipoInsight.PROJECAO in t)
        assertEquals(14000L, r.despesas.deltaCentavos)
    }

    @Test fun `queda relevante mes sem receita e boa noticia de orcamento`() {
        val despesas = listOf(
            d(ms(2026, 9, 5), "Lazer", "Lazer", 1000.0),
            d(ms(2026, 9, 6), "Mercado", "Supermercado", 400.0),
            d(ms(2026, 10, 5), "Lazer", "Lazer", 500.0),
            d(ms(2026, 10, 6), "Mercado", "Supermercado", 440.0)
        )
        val orcamentos = listOf(Orcamento(1, "Lazer", 600.0), Orcamento(2, "supermercado", 500.0))
        val r = gerar(despesas, ref = ms(2026, 10, 10), hoje = fim, orcamentos = orcamentos)
        assertEquals(ResumoInteligente.Comparativo(94000, 140000, -46000, -329), r.despesas)
        assertNull(r.taxaPoupancaDecimos)
        val queda = r.insights.tipo(TipoInsight.CATEGORIA_QUEDA)!!
        assertEquals("Lazer em queda", queda.titulo)
        assertEquals(-500L, queda.variacaoDecimos)
        assertEquals("Lazer: R$ 500,00, -R$ 500,00 (-50,0%) em relação ao mês anterior.", queda.texto)
        assertEquals("Nenhuma receita realizada no período: não dá para calcular a taxa de poupança.", r.insights.tipo(TipoInsight.POUPANCA)!!.texto)
        assertEquals("Orçamento respeitado em todas as 2 categorias.", r.insights.tipo(TipoInsight.BOA_NOTICIA)!!.texto)
        assertEquals(
            listOf(TipoInsight.DESPESAS_TOTAL, TipoInsight.POUPANCA, TipoInsight.CATEGORIA_QUEDA, TipoInsight.BOA_NOTICIA),
            r.insights.tipos()
        )
    }

    // ----------------------------------------------------------------- semana

    @Test fun `semana de segunda a domingo compara com o mesmo ponto da semana anterior`() {
        val despesas = listOf(
            d(ms(2026, 10, 6), "Mercado", "Supermercado", 200.0),
            d(ms(2026, 10, 9), "Mercado", "Supermercado", 100.0),
            d(ms(2026, 10, 13), "Mercado", "Supermercado", 300.0)
        )
        val r = gerar(despesas, tipo = TipoPeriodo.SEMANA, ref = ms(2026, 10, 14))
        assertEquals("Semana de 12/10 a 18/10", r.periodo.rotulo)
        assertEquals(4, r.periodo.diasDecorridos)
        assertEquals(ResumoInteligente.Comparativo(30000, 20000, 10000, 500), r.despesas)
        assertEquals(52500L, r.projecaoCentavos)
        assertEquals(750L, r.insights.tipo(TipoInsight.PROJECAO)!!.variacaoDecimos)
        assertEquals(500L, r.insights.tipo(TipoInsight.CATEGORIA_ALTA)!!.variacaoDecimos)
    }

    @Test fun `domingo pertence a semana que termina nele`() {
        val p = ResumoInteligente.periodo(TipoPeriodo.SEMANA, ms(2026, 10, 18), agora)
        assertEquals(ms(2026, 10, 12, 0), p.inicio)
        assertEquals(18, Calendar.getInstance().apply { timeInMillis = p.fim }.get(Calendar.DAY_OF_MONTH))
    }

    // ----------------------------------------------------------------- sem dados / formato

    @Test fun `sem dados vem vazio`() {
        val r = gerar(emptyList(), ref = agora)
        assertTrue(r.vazio)
        assertTrue(r.insights.isEmpty())
        assertTrue(ResumoInteligente.texto(r).contains("Sem lançamentos suficientes"))
        assertNotNull(r.periodo)
    }

    @Test fun `formata centavos em pt-BR sem depender de locale`() {
        assertEquals("R$ 1.234.567,89", FormatoCentavos.brl(123456789))
        assertEquals("-R$ 0,05", FormatoCentavos.brl(-5))
        assertEquals("12,5", FormatoCentavos.decimos(125))
        assertNull(FormatoCentavos.decimosDe(10, 0))
    }
}
