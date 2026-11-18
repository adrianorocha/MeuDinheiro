package com.meudinheiro.domain

import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Divida
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * R98 — MESMOS vetores de portal/src/lib/finance/planejamentoR97.test.ts (agora = 28/10/2026): dívidas (avalanche x bola de
 * neve), reajuste, fluxo de caixa e insights de economia.
 */
class PlanejamentoPortalVetoresTest {

    private fun dt(ano: Int, mes: Int, dia: Int) = Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis
    private val agora = dt(2026, 10, 28)
    private val conta = ContaSaldo(id = 1, saldo = 0.0, banco = "Banco A", pic = "", agencia = "0001", conta = "111", titular = "Teste")

    private fun desp(
        valor: Double = 10.0, data: Long, tipo: TipoDespesa = TipoDespesa.DEBITO, categoria: String = "Alimentação",
        pago: Boolean = true, grupoId: String? = null, descricao: String = "Item"
    ) = Despesa(
        descricao = descricao, valor = valor, data = Date(data), categoria = categoria, conta = "111", pic = "", tipo = tipo,
        mes = 1, ano = 2026, pago = pago, grupoId = grupoId
    )

    private fun divida(id: Int, nome: String, saldo: Double, taxa: Double?, minima: Double?) =
        Divida(id = id, nome = nome, saldoDevedor = saldo, taxaJurosMensal = taxa ?: 0.0, parcelaMinima = minima)

    // ------------------------------------------------------------------ D

    private val dividas = listOf(divida(1, "Cartão", 3000.0, 12.0, 300.0), divida(2, "Empréstimo", 8000.0, 3.0, 400.0), divida(3, "Cheque especial", 1000.0, 8.0, 100.0))

    @Test fun `bola de neve - 20 meses, R$ 7467,25 de juros`() {
        val c = DividasSimulacao.compararComAjustes(dividas, emptyMap(), 200.0)
        assertEquals(20, c.bolaDeNeve.meses)
        assertEquals(746725L, c.bolaDeNeve.jurosTotal)
        assertEquals(1946725L, c.bolaDeNeve.pagoTotal)
        assertEquals(listOf(3 to 5, 1 to 14, 2 to 20), c.bolaDeNeve.quitacoes.map { it.id to it.mes })
    }

    @Test fun `avalanche - 19 meses, R$ 6695,63 de juros, e economia de R$ 771,62 sobre a bola de neve`() {
        val c = DividasSimulacao.compararComAjustes(dividas, emptyMap(), 200.0)
        assertEquals(19, c.avalanche.meses)
        assertEquals(669563L, c.avalanche.jurosTotal)
        assertEquals(1869563L, c.avalanche.pagoTotal)
        assertEquals(listOf(1 to 12, 3 to 13, 2 to 19), c.avalanche.quitacoes.map { it.id to it.mes })
        assertEquals(77162L, c.economia.avalancheVsBola)
    }

    @Test fun `so minimos - o minimo do cartao nao cobre os juros, nunca quita e avisa`() {
        val c = DividasSimulacao.compararComAjustes(dividas, emptyMap(), 200.0)
        assertTrue(c.minimos.nuncaQuita)
        assertNull(c.economia.avalancheVsMinimos)
        assertTrue(c.avisos.any { it.contains("Cartão") })
    }

    @Test fun `ajustes do simulador nao alteram a divida cadastrada`() {
        val sem = listOf(divida(9, "Sem taxa", 1000.0, null, null))
        val ajustada = DividasSimulacao.paraSimular(sem, mapOf(9 to DividasSimulacao.Ajuste(taxaPct = 5.0, minimoReais = 100.0)))
        assertEquals(listOf(Dividas.DividaSim(9, "Sem taxa", 100000, 500, 10000)), ajustada)
        assertEquals(0.0, sem[0].taxaJurosMensal, 0.0)
        val padrao = DividasSimulacao.paraSimular(sem, emptyMap())[0]
        assertEquals(0L, padrao.taxaBp)
        assertEquals(0L, padrao.minimo)
        assertEquals(10000L, DividasSimulacao.paraSimular(sem, mapOf(9 to DividasSimulacao.Ajuste(taxaPct = 500.0)))[0].taxaBp)
        assertTrue(DividasSimulacao.paraSimular(listOf(sem[0].copy(ativa = false), divida(8, "Zero", 0.0, 1.0, 1.0)), emptyMap()).isEmpty())
    }

    // ------------------------------------------------------------------ F

    @Test fun `reajuste com acumulado informado substitui a composicao (IGP-M 12 meses = 4,5 por cento)`() {
        val l = Reajustes.calcular(1500.0, 12, listOf(Reajustes.Indice("igpm", "IGP-M", listOf(1.0), 4.5)))!!
        assertTrue(l[0].informado)
        assertEquals(156750L, l[0].novoValor)
        assertEquals(156750L, Reajustes.aplicar(1500.0, 0.045))
        assertEquals(99000L, Reajustes.aplicar(1000.0, -0.01))
    }

    @Test fun `reajuste composto - IPCA 3 meses 0,5677 por cento dando R$ 2011,35`() {
        val l = Reajustes.calcular(2000.0, 3, listOf(Reajustes.Indice("ipca", "IPCA", listOf(0.07, -0.32, 0.82)), Reajustes.Indice("igpm", "IGP-M", listOf(-1.16, -0.22, 1.57))))!!
        assertEquals(201135L, l[0].novoValor)
        assertEquals(1135L, l[0].diferenca)
        assertEquals(200342L, l[1].novoValor)
        val curta = Reajustes.calcular(1000.0, 12, listOf(Reajustes.Indice("ipca", "IPCA", listOf(0.07, -0.32, 0.82))))!!
        assertEquals(3, curta[0].mesesUsados)
        assertTrue(curta[0].incompleto)
    }

    // ------------------------------------------------------------------ E: fluxo

    private fun fixa(id: Int, desc: String, tipo: TipoDespesa, valor: Double, dia: Int) = DespesaFixa(
        id = id, descricao = desc, valor = valor, conta = "111", categoria = "Casa", pic = "", tipo = tipo, diaVencimento = dia,
        ultimaDataLancamento = Date(dt(2026, 10, 5))
    )

    private fun base() = listOf(
        desp(5000.0, dt(2026, 6, 1), TipoDespesa.CREDITO, categoria = "Salário", descricao = "Saldo"),
        desp(1000.0, dt(2026, 7, 10), descricao = "Mercado"),
        desp(1000.0, dt(2026, 8, 10), descricao = "Mercado"),
        desp(1000.0, dt(2026, 9, 10), descricao = "Mercado")
    )

    private fun fluxo(despesas: List<Despesa>, fixas: List<DespesaFixa> = emptyList(), meses: Int = 6, infl: Double = 0.0, contas: List<ContaSaldo> = listOf(conta)) =
        FluxoCaixa.projetar(contas, emptyList(), despesas, fixas, agora, meses, infl)

    @Test fun `deficit - salario 5000, fixa 5500, variavel 1000 dando corte de R$ 1291,67 por mes em 12 meses`() {
        val fixas = listOf(fixa(1, "Salário", TipoDespesa.CREDITO, 5000.0, 5), fixa(2, "Aluguel", TipoDespesa.DEBITO, 5500.0, 10))
        val r = fluxo(base(), fixas, 12)
        assertEquals(200000L, r.saldoInicial)
        assertEquals(100000L, r.variavelBase)
        assertEquals(3, r.mesesComDados)
        assertEquals(12, r.meses.size)
        val m0 = r.meses[0]
        assertEquals(0L, m0.entradas); assertEquals(0L, m0.saidas); assertEquals(100000L, m0.variavel); assertEquals(100000L, m0.saldoFinal)
        assertFalse(m0.negativo)
        val m1 = r.meses[1]
        assertEquals(500000L, m1.entradas); assertEquals(550000L, m1.saidas); assertEquals(-50000L, m1.saldoFinal)
        assertTrue(m1.negativo)
        assertEquals(1, r.primeiroNegativo)
        assertEquals(-1550000L, r.meses[11].saldoFinal)
        assertEquals(129167L, r.cortePorMes)
    }

    @Test fun `superavit - sem meses negativos e sem corte`() {
        val fixas = listOf(fixa(1, "Salário", TipoDespesa.CREDITO, 5000.0, 5), fixa(2, "Aluguel", TipoDespesa.DEBITO, 2000.0, 10))
        val r = fluxo(base(), fixas, 6)
        assertEquals(6, r.meses.size)
        assertNull(r.primeiroNegativo)
        assertEquals(0L, r.cortePorMes)
        assertEquals(100000L + 5 * 200000L, r.meses[5].saldoFinal)
    }

    @Test fun `inflacao corrige a despesa variavel (1 por cento ao mes)`() {
        val r = fluxo(base(), meses = 6, infl = 0.01)
        assertEquals(Math.round(100000 * (1.01 + Math.pow(1.01, 2.0) + Math.pow(1.01, 3.0)) / 3), r.variavelBase)
        assertEquals(Math.round(r.variavelBase * Math.pow(1.01, 2.0)), r.meses[2].variavel)
    }

    @Test fun `horizonte limitado a 6-12 meses e sem dados nada e estimado`() {
        assertEquals(6, fluxo(emptyList(), meses = 3, contas = emptyList()).meses.size)
        assertEquals(12, fluxo(emptyList(), meses = 30, contas = emptyList()).meses.size)
        val r = fluxo(emptyList(), meses = 6, contas = emptyList())
        assertEquals(0, r.mesesComDados)
        assertTrue(FluxoCaixa.vazio(r))
        assertFalse(FluxoCaixa.vazio(fluxo(base())))
    }

    @Test fun `parcela futura entra como saida conhecida`() {
        val ds = listOf(
            desp(300.0, dt(2026, 11, 20), pago = false, grupoId = "parc:abc", descricao = "TV (2/3)"),
            desp(300.0, dt(2026, 12, 20), pago = false, grupoId = "parc:abc", descricao = "TV (3/3)")
        )
        val r = fluxo(ds)
        assertEquals(30000L, r.meses[1].saidas)
        assertEquals(30000L, r.meses[2].saidas)
        assertEquals(0L, r.meses[3].saidas)
    }

    // ------------------------------------------------------------------ E: insights

    private val par = InsightsEconomia.Parametros(0.005, 0.0108, 1.0)

    @Test fun `categoria acima da inflacao + 5pp - media 200 x 100 anterior dando R$ 96,96 por mes`() {
        val ds = ArrayList<Despesa>()
        for (m in 1..6) ds += desp(100.0, dt(2026, m, 12), categoria = "Lazer", descricao = "Cinema")
        for (m in 7..9) ds += desp(200.0, dt(2026, m, 12), categoria = "Lazer", descricao = "Cinema")
        for (m in 1..6) ds += desp(100.0, dt(2026, m, 15), categoria = "Mercado", descricao = "Feira")
        for (m in 7..9) ds += desp(102.0, dt(2026, m, 15), categoria = "Mercado", descricao = "Feira")
        val r = InsightsEconomia.categorias(ds, agora, 0.005)
        assertEquals(1, r.size)
        assertEquals("categoria:lazer", r[0].id)
        assertEquals(9696L, r[0].valorMensal)
        assertTrue(r[0].calculo.size >= 3)
    }

    @Test fun `categoria sem historico anterior suficiente nao gera insight`() {
        val ds = listOf(desp(500.0, dt(2026, 5, 3), categoria = "Nova")) + listOf(7, 8, 9).map { desp(500.0, dt(2026, it, 3), categoria = "Nova") }
        assertTrue(InsightsEconomia.categorias(ds, agora, 0.005).isEmpty())
    }

    @Test fun `assinaturas - soma e a de maior peso`() {
        fun fx(id: Int, nome: String, valor: Double) = DespesaFixa(id = id, descricao = nome, valor = valor, conta = "111", categoria = "Assinaturas", pic = "", tipo = TipoDespesa.DEBITO, diaVencimento = 5)
        val i = InsightsEconomia.assinaturas(agora, emptyList(), listOf(fx(1, "Streaming", 50.0), fx(2, "Música", 20.0)))!!
        assertEquals(5000L, i.valorMensal)
        assertTrue(i.calculo[0].contains("R$ 70,00/mês"))
        assertTrue(i.calculo[1].contains("Streaming"))
        assertNull(InsightsEconomia.assinaturas(agora, emptyList(), emptyList()))
    }

    @Test fun `50-30-20 do mes anterior - receita 10000, necessidades 4000, desejos 4000, poupanca 0 dando R$ 2000`() {
        val ds = listOf(
            desp(10000.0, dt(2026, 9, 5), TipoDespesa.CREDITO, categoria = "Salário"),
            desp(4000.0, dt(2026, 9, 6), categoria = "Supermercado"),
            desp(4000.0, dt(2026, 9, 7), categoria = "Lazer")
        )
        val i = InsightsEconomia.regra503020(ds, agora)!!
        assertEquals(200000L, i.valorMensal)
        assertTrue(i.calculo.joinToString(" ").contains("faltou R$ 2.000,00"))
        assertTrue(i.calculo.joinToString(" ").contains("excesso R$ 1.000,00"))
        val ok = listOf(
            desp(10000.0, dt(2026, 9, 5), TipoDespesa.CREDITO, categoria = "Salário"),
            desp(5000.0, dt(2026, 9, 6), categoria = "Supermercado"),
            desp(3000.0, dt(2026, 9, 7), categoria = "Lazer"),
            desp(2000.0, dt(2026, 9, 8), categoria = "Reserva")
        )
        assertNull(InsightsEconomia.regra503020(ok, agora))
        assertNull(InsightsEconomia.regra503020(emptyList(), agora))
    }

    @Test fun `meta - R$ 12000 em 12 meses com R$ 1000 guardados dando aporte de R$ 864,70 (sem rendimento R$ 916,67)`() {
        val meta = Meta(id = 7, nome = "Viagem", valorObjetivo = 12000.0, valorGuardado = 1000.0, dataAlvo = dt(2027, 10, 28))
        val i = InsightsEconomia.metas(listOf(meta), agora, par)[0]
        assertEquals("meta:7", i.id)
        assertEquals(86470L, i.valorMensal)
        assertTrue(i.calculo.joinToString(" ").contains("R$ 916,67"))
        assertTrue(InsightsEconomia.metas(listOf(meta.copy(valorGuardado = 12000.0), meta.copy(id = 8, dataAlvo = null)), agora, par).isEmpty())
    }

    @Test fun `gerar ordena do maior para o menor valor estimado`() {
        val meta = Meta(id = 7, nome = "Viagem", valorObjetivo = 12000.0, valorGuardado = 1000.0, dataAlvo = dt(2027, 10, 28))
        val lista = InsightsEconomia.gerar(listOf(desp(10000.0, dt(2026, 9, 5), TipoDespesa.CREDITO, categoria = "Salário")), emptyList(), listOf(meta), agora, par)
        val valores = lista.map { it.valorMensal }
        assertEquals(valores.sortedDescending(), valores)
        assertTrue(lista.any { it.tipo == InsightsEconomia.Tipo.META })
    }

    @Test fun `dispensados serializam e limitam a 300`() {
        assertEquals(setOf("a", "b:2"), InsightsEconomia.desserializarDispensados(InsightsEconomia.serializarDispensados(listOf("a", "b:2", "", " "))))
        val muitos = (1..350).map { "i$it" }
        val lido = InsightsEconomia.desserializarDispensados(InsightsEconomia.serializarDispensados(muitos))
        assertEquals(300, lido.size)
        assertTrue("i350" in lido)
        assertFalse("i1" in lido)
        assertTrue(InsightsEconomia.desserializarDispensados(null).isEmpty())
    }

    @Test fun `inflacao padrao da projecao e a media geometrica do IPCA`() {
        val m = IndicesEconomicos.mediaMensalPct(listOf(0.07, -0.32, 0.82))
        assertEquals((Math.pow(1.0007 * 0.9968 * 1.0082, 1.0 / 3) - 1) * 100, m, 1e-12)
        assertEquals(0.188884, m, 1e-6)
        assertTrue(IndicesEconomicos.mediaMensalPct(emptyList()).isNaN())
    }
}
