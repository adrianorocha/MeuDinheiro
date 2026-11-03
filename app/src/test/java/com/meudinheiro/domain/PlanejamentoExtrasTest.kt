package com.meudinheiro.domain

import com.meudinheiro.data.Divida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R98 — rotativo, reajuste e simulador de dívidas com ajustes (paridade com custoRotativo.ts, reajuste.ts e dividasSimulacao.ts). */
class PlanejamentoExtrasTest {

    // ----- custo do rotativo

    @Test fun `rotativo R$ 1000 a 14 por cento ao mes`() {
        val r = CustoRotativo.custo(1000.0, 0.14)!!
        assertEquals(140.0, r.m1.juros, 0.0)
        assertEquals(1140.0, r.m1.total, 0.0)
        assertEquals(481.54, r.m3.juros, 0.0)
        assertEquals(1481.54, r.m3.total, 0.0)
        assertEquals(3.8179048198, r.taxaAnual, 1e-9)
    }

    @Test fun `rotativo entradas invalidas`() {
        assertNull(CustoRotativo.custo(0.0, 0.14))
        assertNull(CustoRotativo.custo(1000.0, -0.01))
        assertNull(CustoRotativo.custo(1000.0, Double.NaN))
        assertEquals(0.0, CustoRotativo.custo(1000.0, 0.0)!!.m3.juros, 0.0)
    }

    // ----- reajuste

    private val igpm = Reajustes.Indice("igpm", "IGP-M", listOf(-1.16, -0.22, 1.57))
    private val ipca = Reajustes.Indice("ipca", "IPCA", listOf(0.07, -0.32, 0.82))

    @Test fun `reajuste de R$ 2000 por 3 meses compostos`() {
        val l = Reajustes.calcular(2000.0, 3, listOf(igpm, ipca))!!
        assertEquals(0.0017092607, l[0].acumulado, 1e-10)
        assertEquals(200342L, l[0].novoValor)
        assertEquals(342L, l[0].diferenca)
        assertEquals(0.0056772416, l[1].acumulado, 1e-10)
        assertEquals(201135L, l[1].novoValor)
        assertEquals(false, l[0].incompleto)
    }

    @Test fun `reajuste com serie curta avisa incompleto e com acumulado informado substitui a composicao`() {
        val l = Reajustes.calcular(2000.0, 12, listOf(igpm, ipca.copy(acumuladoInformadoPct = 4.5)))!!
        assertTrue(l[0].incompleto)
        assertEquals(3, l[0].mesesUsados)
        assertEquals(200342L, l[0].novoValor)
        assertEquals(false, l[1].incompleto)
        assertTrue(l[1].informado)
        assertEquals(209000L, l[1].novoValor)
        assertEquals(9000L, l[1].diferenca)
    }

    @Test fun `reajuste de 2 ultimos meses e indice negativo reduz`() {
        val l = Reajustes.calcular(2000.0, 2, listOf(igpm))!!
        assertEquals(0.01346546, l[0].acumulado, 1e-9)
        assertEquals(202693L, l[0].novoValor)
        assertEquals(19900L, Reajustes.aplicar(200.0, -0.005))
    }

    @Test fun `reajuste entradas invalidas`() {
        assertNull(Reajustes.calcular(0.0, 12, listOf(igpm)))
        assertNull(Reajustes.calcular(100.0, 0, listOf(igpm)))
        assertNull(Reajustes.calcular(100.0, 121, listOf(igpm)))
        assertEquals(0.0056772416, Reajustes.acumuladoDePcts(listOf(0.07, -0.32, 0.82)), 1e-9)
    }

    // ----- dívidas com ajustes

    private val semTaxa = Divida(id = 1, nome = "Cartão", saldoDevedor = 100.0, taxaJurosMensal = 0.0, parcelaMinima = 50.0)
    private val comTaxa = Divida(id = 2, nome = "Empréstimo", saldoDevedor = 100.0, taxaJurosMensal = 1.0, parcelaMinima = 50.0)

    @Test fun `sem ajustes equivale ao motor da R58`() {
        val a = DividasSimulacao.compararComAjustes(listOf(semTaxa, comTaxa), emptyMap(), 25.0)
        val b = Dividas.compararDasDividas(listOf(semTaxa, comTaxa), 25.0)
        assertEquals(b.avalanche.jurosTotal, a.avalanche.jurosTotal)
        assertEquals(b.minimos.meses, a.minimos.meses)
    }

    @Test fun `taxa informada no simulador nao altera a divida e muda os juros`() {
        val antes = DividasSimulacao.paraSimular(listOf(semTaxa), emptyMap())
        assertEquals(0L, antes[0].taxaBp)
        val ajustes = mapOf(1 to DividasSimulacao.Ajuste(taxaPct = 2.5))
        val sim = DividasSimulacao.paraSimular(listOf(semTaxa), ajustes)
        assertEquals(250L, sim[0].taxaBp)
        assertEquals(0.0, semTaxa.taxaJurosMensal, 0.0)
        val r = DividasSimulacao.compararComAjustes(listOf(semTaxa), ajustes, 0.0)
        
        assertTrue(r.minimos.jurosTotal > 0)
        assertNotNull(r.minimos.meses)
    }

    @Test fun `minimo informado, taxa limitada a 100 por cento e ajuste invalido ignorado`() {
        val ajustes = mapOf(
            1 to DividasSimulacao.Ajuste(taxaPct = 500.0, minimoReais = 75.0),
            2 to DividasSimulacao.Ajuste(taxaPct = -3.0, minimoReais = Double.NaN)
        )
        val sim = DividasSimulacao.paraSimular(listOf(semTaxa, comTaxa), ajustes)
        assertEquals(10000L, sim[0].taxaBp)
        assertEquals(7500L, sim[0].minimo)
        assertEquals(100L, sim[1].taxaBp)
        assertEquals(5000L, sim[1].minimo)
    }

    @Test fun `divida quitada ou inativa fica fora do simulador`() {
        val sim = DividasSimulacao.paraSimular(listOf(semTaxa.copy(ativa = false), comTaxa.copy(saldoDevedor = 0.0)), emptyMap())
        assertTrue(sim.isEmpty())
    }
}
