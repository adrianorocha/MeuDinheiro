package com.meudinheiro.domain

import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.InsightsEconomia.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * R98 — projeção de fluxo de caixa (FluxoCaixa) e insights de economia (InsightsEconomia). Regras idênticas às do portal
 * (fluxoCaixa.ts / insightsEconomia.ts, R97); cenários montados à mão (valores conferidos manualmente).
 */
class FluxoCaixaTest {

    private fun ms(ano: Int, mes: Int, dia: Int) = Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis
    private val agora = ms(2025, 3, 12)
    private val conta = ContaSaldo(id = 1, saldo = 0.0, banco = "Banco A", pic = "", agencia = "1", conta = "111", titular = "T")

    private fun d(
        valor: Double, data: Long, tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = true, categoria: String = "Geral",
        natureza: String = Natureza.NORMAL, grupoId: String? = null, cartaoId: Int? = null, descricao: String = "Item"
    ) = Despesa(
        descricao = descricao, valor = valor, data = Date(data), categoria = categoria, conta = "111", pic = "", tipo = tipo,
        mes = 1, ano = 2025, cartaoId = cartaoId, pago = pago, natureza = natureza, grupoId = grupoId
    )

    private fun saldoInicial(reais: Double) = d(reais, ms(2024, 1, 2), TipoDespesa.CREDITO, natureza = Natureza.SALDO_INICIAL)

    /** Dez/24, jan/25 e fev/25 com R$ 300,00 de gasto variável cada. */
    private fun historico() = listOf(ms(2024, 12, 10), ms(2025, 1, 10), ms(2025, 2, 10)).map { d(300.0, it) }

    private fun projetar(despesas: List<Despesa>, infl: Double = 0.0, meses: Int = 6, fixas: List<DespesaFixa> = emptyList()) =
        FluxoCaixa.projetar(listOf(conta), emptyList(), despesas, fixas, agora, meses, infl)

    @Test fun `variavel e media dos 3 meses completos, mes atual so o que falta`() {
        // saldo = 5000 - 3x300 (histórico, pagos) - 100 (já gasto em março) = 4000,00
        val ds = listOf(saldoInicial(5000.0)) + historico() + d(100.0, ms(2025, 3, 5)) + d(1000.0, ms(2025, 3, 20), pago = false)
        val r = projetar(ds)
        assertEquals(400000L, r.saldoInicial)
        assertEquals(30000L, r.variavelBase)
        assertEquals(3, r.mesesComDados)
        assertEquals(6, r.meses.size)
        val m0 = r.meses[0]
        assertEquals(100000L, m0.saidas) // pendência de 20/03
        assertEquals(20000L, m0.variavel) // 300 - 100 já gastos
        assertEquals(280000L, m0.saldoFinal)
        assertEquals(listOf(250000L, 220000L, 190000L, 160000L, 130000L), r.meses.drop(1).map { it.saldoFinal })
        assertNull(r.primeiroNegativo)
        assertEquals(0L, r.cortePorMes)
        assertEquals(listOf(3, 4, 5, 6, 7, 8), r.meses.map { it.mes })
    }

    @Test fun `deficit destaca meses negativos e calcula o corte mensal`() {
        val ds = listOf(saldoInicial(1200.0)) + historico() + d(100.0, ms(2025, 3, 5)) + d(1000.0, ms(2025, 3, 20), pago = false)
        // saldo inicial = 1200 - 900... (3x300 + 100) = 200,00 -> hmm: 1200 - 900 - 100 = 200
        val r = projetar(ds)
        assertEquals(20000L, r.saldoInicial)
        assertEquals(listOf(-100000L, -130000L, -160000L, -190000L, -220000L, -250000L), r.meses.map { it.saldoFinal })
        assertTrue(r.meses.all { it.negativo })
        assertEquals(0, r.primeiroNegativo)
        // max(100000/1, 130000/2, 160000/3 -> 53334, 190000/4, 220000/5, 250000/6 -> 41667) = 100000
        assertEquals(100000L, r.cortePorMes)
    }

    @Test fun `corte vem do mes mais apertado em media por mes`() {
        // sem pendência: saldo inicial 1100 - 900 = 200,00; resultado mensal -300 (mês atual: -300 pois ja gasto 0)
        val ds = listOf(saldoInicial(1100.0)) + historico()
        val r = projetar(ds)
        assertEquals(20000L, r.saldoInicial)
        assertEquals(listOf(-10000L, -40000L, -70000L, -100000L, -130000L, -160000L), r.meses.map { it.saldoFinal })
        // ceil(10000/1)=10000, 40000/2=20000, 70000/3=23334, 100000/4=25000, 130000/5=26000, 160000/6=26667
        assertEquals(26667L, r.cortePorMes)
    }

    @Test fun `inflacao corrige a media e a projecao dos meses`() {
        val ds = listOf(saldoInicial(5000.0)) + historico()
        val r = projetar(ds, infl = 0.01)
        assertEquals(30604L, r.variavelBase)
        assertEquals(30604L, r.meses[0].variavel)
        assertEquals(30910L, r.meses[1].variavel)
        assertEquals(31219L, r.meses[2].variavel)
    }

    @Test fun `recorrencias e parcelas nao entram na media variavel e estorno de cartao abate`() {
        val extras = listOf(
            d(500.0, ms(2025, 2, 5), grupoId = "fixa:1:2025-02"),
            d(500.0, ms(2025, 2, 6), grupoId = "parc:abc"),
            d(40.0, ms(2025, 2, 7), TipoDespesa.CREDITO, cartaoId = 3), // estorno de cartão: -40
            d(40.0, ms(2025, 2, 8), TipoDespesa.CREDITO) // receita em conta: ignorada
        )
        val b = FluxoCaixa.variavelBase(historico() + extras, agora, 0.0)
        // fev: 300 - 40 = 260 -> (300 + 300 + 260) / 3 = 286,67
        assertEquals(28667L, b.base)
        assertEquals(3, b.mesesComDados)
    }

    @Test fun `sem historico a base e zero`() {
        val b = FluxoCaixa.variavelBase(emptyList(), agora, 0.0)
        assertEquals(0L, b.base)
        assertEquals(0, b.mesesComDados)
    }

    @Test fun `horizonte e limitado a 6 a 12 meses e entradas conhecidas entram no mes certo`() {
        val recebimento = d(2000.0, ms(2025, 4, 10), TipoDespesa.CREDITO, pago = false)
        val ds = listOf(saldoInicial(5000.0), recebimento)
        assertEquals(6, projetar(ds, meses = 1).meses.size)
        assertEquals(12, projetar(ds, meses = 99).meses.size)
        val r = projetar(ds, meses = 6)
        assertEquals(200000L, r.meses[1].entradas)
        assertEquals(0L, r.meses[0].entradas)
    }

    // ------------------------------------------------------------------ insights

    private fun par(infl: Double = 0.005) = InsightsEconomia.Parametros(infl, 0.0108, 1.0)

    @Test fun `categoria que subiu acima da inflacao + 5pp vira insight com valor estimado`() {
        val antes = (6..11).map { d(100.0, ms(2024, it, 10), categoria = "Mercado") } // jun..nov/24 = meses -9..-4
        val recentes = listOf(ms(2024, 12, 10), ms(2025, 1, 10), ms(2025, 2, 10)).map { d(150.0, it, categoria = "Mercado") }
        val r = InsightsEconomia.categorias(antes + recentes, agora, 0.005)
        assertEquals(1, r.size)
        assertEquals("categoria:mercado", r[0].id)
        assertEquals(Tipo.CATEGORIA, r[0].tipo)
        assertEquals("Mercado: gasto subiu acima da inflação", r[0].titulo)
        // 15000 - 10000 x 1,005^6 (1,030378) = 4696,22
        assertEquals(4696L, r[0].valorMensal)
        assertEquals("Média dos últimos 3 meses: R$ 150,00", r[0].calculo[0])
        assertEquals("Média dos 6 meses anteriores: R$ 100,00", r[0].calculo[1])
        assertTrue(r[0].calculo[2].startsWith("Variação: 50,0% (limite: inflação de 6 meses 3,0% + 5 p.p. = 8,0%)"))
    }

    @Test fun `categoria dentro do limiar, sem historico suficiente ou excesso pequeno nao gera insight`() {
        val antes = (6..11).map { d(100.0, ms(2024, it, 10), categoria = "Mercado") }
        val pouco = listOf(ms(2024, 12, 10), ms(2025, 1, 10), ms(2025, 2, 10)).map { d(105.0, it, categoria = "Mercado") }
        assertTrue(InsightsEconomia.categorias(antes + pouco, agora, 0.005).isEmpty())
        // só 2 dos 6 meses anteriores com gasto
        val raro = listOf(ms(2024, 6, 10), ms(2024, 7, 10)).map { d(100.0, it, categoria = "Mercado") }
        val alto = listOf(ms(2024, 12, 10), ms(2025, 1, 10), ms(2025, 2, 10)).map { d(300.0, it, categoria = "Mercado") }
        assertTrue(InsightsEconomia.categorias(raro + alto, agora, 0.005).isEmpty())
        // excesso < R$ 10,00 por mês: base de R$ 1 -> R$ 1,90
        val a2 = (6..11).map { d(1.0, ms(2024, it, 10), categoria = "Bala") }
        val r2 = listOf(ms(2024, 12, 10), ms(2025, 1, 10), ms(2025, 2, 10)).map { d(1.9, it, categoria = "Bala") }
        assertTrue(InsightsEconomia.categorias(a2 + r2, agora, 0.005).isEmpty())
    }

    @Test fun `assinaturas somam recorrencias de debito e a estimativa e a maior`() {
        val fixas = listOf(
            DespesaFixa(id = 1, descricao = "Netflix", valor = 50.0, conta = "111", categoria = "Assinaturas", pic = "", tipo = TipoDespesa.DEBITO, diaVencimento = 5),
            DespesaFixa(id = 2, descricao = "Academia", valor = 120.0, conta = "111", categoria = "Saúde", pic = "", tipo = TipoDespesa.DEBITO, diaVencimento = 10),
            DespesaFixa(id = 3, descricao = "Salário", valor = 5000.0, conta = "111", categoria = "Renda", pic = "", tipo = TipoDespesa.CREDITO, diaVencimento = 5)
        )
        val i = InsightsEconomia.assinaturas(agora, emptyList(), fixas)!!
        assertEquals("assinaturas", i.id)
        assertEquals(12000L, i.valorMensal)
        assertEquals("2 item(ns) somam R$ 170,00/mês (R$ 2.040,00/ano)", i.calculo[0])
        assertEquals("Maior peso: Academia — R$ 120,00/mês (70,6% do total)", i.calculo[1])
        assertNull(InsightsEconomia.assinaturas(agora, emptyList(), emptyList()))
    }

    @Test fun `regra 50-30-20 do ultimo mes completo`() {
        val fev = ms(2025, 2, 10)
        val ds = listOf(
            d(5000.0, fev, TipoDespesa.CREDITO, categoria = "Salário"),
            d(2000.0, fev, categoria = "Aluguel"),
            d(2000.0, fev, categoria = "Lazer")
        )
        val i = InsightsEconomia.regra503020(ds, agora)!!
        assertEquals("503020:2025-2", i.id)
        // desejos 40% (excesso 500,00 sobre 30%); poupança 0% (faltou 1.000,00): estimativa = maior
        assertEquals(100000L, i.valorMensal)
        assertEquals("Receita do mês: R$ 5.000,00", i.calculo[0])
        assertEquals("Necessidades: R$ 2.000,00 (40,0%; alvo até 50%)", i.calculo[1])
        assertEquals("Desejos: R$ 2.000,00 (40,0%; alvo até 30%) — excesso R$ 500,00", i.calculo[2])
        assertEquals("Poupança: R$ 0,00 (0,0%; alvo de 20% ou mais) — faltou R$ 1.000,00", i.calculo[3])
    }

    @Test fun `regra 50-30-20 sem receita ou tudo dentro dos alvos nao gera insight`() {
        assertNull(InsightsEconomia.regra503020(listOf(d(100.0, ms(2025, 2, 10))), agora))
        val fev = ms(2025, 2, 10)
        val ok = listOf(
            d(5000.0, fev, TipoDespesa.CREDITO, categoria = "Salário"),
            d(2000.0, fev, categoria = "Aluguel"),
            d(1000.0, fev, categoria = "Lazer"),
            d(1000.0, fev, categoria = "Reserva")
        )
        assertNull(InsightsEconomia.regra503020(ok, agora))
    }

    @Test fun `meta com prazo - aporte mensal com rendimento liquido`() {
        val meta = Meta(id = 7, nome = "Viagem", valorObjetivo = 12000.0, valorGuardado = 1000.0, dataAlvo = ms(2026, 3, 12))
        assertEquals(12, InsightsEconomia.mesesAteAlvo(meta.dataAlvo!!, agora))
        val r = InsightsEconomia.metas(listOf(meta), agora, par())
        assertEquals(1, r.size)
        assertEquals("meta:7", r[0].id)
        assertEquals(86470L, r[0].valorMensal) // vetor do portal: 12.000 em 12 meses, R$ 1.000 guardados, CDI 1,08% a.m.
        assertEquals("Faltam R$ 11.000,00 em 12 mês(es)", r[0].calculo[0])
        assertEquals("Taxa líquida equivalente: 0,8740% a.m.", r[0].calculo[1])
        assertEquals("Aporte com rendimento: R$ 864,70/mês · sem rendimento: R$ 916,67/mês", r[0].calculo[2])
    }

    @Test fun `meta atrasada, concluida ou sem prazo fica de fora`() {
        val atrasada = Meta(id = 1, nome = "A", valorObjetivo = 100.0, valorGuardado = 0.0, dataAlvo = ms(2025, 1, 1))
        val concluida = Meta(id = 2, nome = "B", valorObjetivo = 100.0, valorGuardado = 100.0, dataAlvo = ms(2026, 1, 1))
        val semPrazo = Meta(id = 3, nome = "C", valorObjetivo = 100.0, valorGuardado = 0.0)
        assertTrue(InsightsEconomia.metas(listOf(atrasada, concluida, semPrazo), agora, par()).isEmpty())
        assertNull(InsightsEconomia.mesesAteAlvo(ms(2025, 1, 1), agora))
        assertEquals(1, InsightsEconomia.mesesAteAlvo(ms(2025, 3, 12), agora))
    }

    @Test fun `gerar ordena do maior para o menor valor estimado`() {
        val meta = Meta(id = 7, nome = "Viagem", valorObjetivo = 12000.0, valorGuardado = 1000.0, dataAlvo = ms(2026, 3, 12))
        val fixas = listOf(DespesaFixa(id = 2, descricao = "Academia", valor = 120.0, conta = "111", categoria = "Saúde", pic = "", tipo = TipoDespesa.DEBITO, diaVencimento = 10))
        val todos = InsightsEconomia.gerar(emptyList(), fixas, listOf(meta), agora, par())
        assertEquals(listOf("meta:7", "assinaturas"), todos.map { it.id })
        assertNotNull(todos.firstOrNull { it.tipo == Tipo.ASSINATURAS })
        assertFalse(todos.any { it.tipo == Tipo.CATEGORIA })
    }
}
