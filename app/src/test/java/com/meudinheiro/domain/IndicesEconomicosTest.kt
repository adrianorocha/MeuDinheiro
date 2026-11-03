package com.meudinheiro.domain

import com.meudinheiro.domain.IndicesEconomicos.AcumuladoN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R98 — mesmos cenários de portal/src/lib/finance/indices.test.ts (R97). */
class IndicesEconomicosTest {

    private fun por(id: String) = IndicesEconomicos.padrao().itens.first { it.id == id }

    @Test fun `semente traz os valores lidos da Debit em 28-10-2026`() {
        val c = IndicesEconomicos.padrao()
        assertEquals(1.08, por("selic").valorMensalPct, 0.0)
        assertEquals(1.08, por("cdi").valorMensalPct, 0.0)
        assertEquals(0.6624, por("poupanca").valorMensalPct, 0.0)
        assertEquals(0.1616, por("tr").valorMensalPct, 0.0)
        assertEquals(listOf(0.07, -0.32, 0.82), por("ipca").serie)
        assertEquals(listOf(-1.16, -0.22, 1.57), por("igpm").serie)
        assertEquals(listOf(0.61, 0.66, 0.22), por("incc").serie)
        assertEquals(162100L, c.salarioMinimo.centavos)
        assertEquals("2026-10-28", c.conferidoEm)
        assertTrue(por("selic").aproximada)
        assertFalse(por("ipca").aproximada)
        assertNull(c.extras.selicMetaAnualPct)
        assertNull(c.extras.inflacao12mPct)
        assertEquals(14.0, c.extras.taxaRotativoMensalPct, 0.0)
        assertEquals(8.0, c.extras.taxaChequeEspecialMensalPct, 0.0)
    }

    @Test fun `link de historico so para quem tem pagina`() {
        assertEquals(
            "https://www.debit.com.br/tabelas/ipca-indice-nacional-de-precos-ao-consumidor-amplo",
            IndicesEconomicos.urlHistorico(por("ipca").slug)
        )
        assertEquals("https://www.debit.com.br/tabelas/tr-bacen", IndicesEconomicos.urlHistorico(por("tr").slug))
        assertNull(IndicesEconomicos.urlHistorico(por("tbf").slug))
        assertEquals("https://www.debit.com.br/tabelas/salario-minimo", IndicesEconomicos.urlHistorico(IndicesEconomicos.SLUG_SALARIO_MINIMO))
    }

    @Test fun `competencia anterior`() {
        assertEquals("09/2026", IndicesEconomicos.competenciaAnterior("09/2026", 0))
        assertEquals("07/2026", IndicesEconomicos.competenciaAnterior("09/2026", 2))
        assertEquals("11/2025", IndicesEconomicos.competenciaAnterior("02/2026", 3))
        assertEquals("", IndicesEconomicos.competenciaAnterior("", 1))
    }

    @Test fun `mensal e anual equivalentes`() {
        assertEquals(0.1375822289, IndicesEconomicos.anualDeMensal(0.0108), 1e-9)
        assertEquals(0.0108, IndicesEconomicos.mensalDeAnual(IndicesEconomicos.anualDeMensal(0.0108)), 1e-12)
        assertEquals(0.0, IndicesEconomicos.anualDeMensal(0.0), 0.0)
    }

    @Test fun `acumulado por composicao`() {
        assertEquals(0.0056772416, IndicesEconomicos.acumular(listOf(0.0007, -0.0032, 0.0082)), 1e-9)
        assertEquals(0.0, IndicesEconomicos.acumular(emptyList()), 0.0)
    }

    @Test fun `acumulado dos ultimos N meses usa o que houver e avisa`() {
        val r = IndicesEconomicos.acumuladoDosUltimos(listOf(0.07, -0.32, 0.82), 12)
        assertEquals(3, r.mesesUsados)
        assertTrue(r.incompleto)
        assertEquals(0.0056772416, r.acumulado, 1e-9)
        val r2 = IndicesEconomicos.acumuladoDosUltimos(listOf(0.07, -0.32, 0.82), 2)
        assertFalse(r2.incompleto)
        assertEquals(0.9968 * 1.0082 - 1, r2.acumulado, 1e-12)
        assertEquals(AcumuladoN(0.0, 0, true), IndicesEconomicos.acumuladoDosUltimos(emptyList(), 3))
    }

    @Test fun `ganho real`() {
        assertEquals(0.083333333, IndicesEconomicos.ganhoReal(0.1375, 0.05), 1e-8)
        assertTrue(IndicesEconomicos.ganhoReal(0.04, 0.05) < 0)
        assertEquals(0.0, IndicesEconomicos.ganhoReal(0.05, 0.05), 1e-12)
    }

    @Test fun `padrao serializa vazio e texto vazio volta ao padrao`() {
        assertEquals("", IndicesEconomicos.serializar(IndicesEconomicos.padrao()))
        assertEquals(IndicesEconomicos.padrao(), IndicesEconomicos.desserializar(""))
        assertEquals(IndicesEconomicos.padrao(), IndicesEconomicos.desserializar(null))
    }

    @Test fun `grava e rele edicoes`() {
        val c0 = IndicesEconomicos.padrao()
        val c = c0.copy(
            // R100: o que o usuário edita é "manual" (e volta como manual ao reler)
            itens = c0.itens.map {
                if (it.id == "cdi") it.copy(serie = listOf(1.1), competencia = "11/2026", aproximada = false, origem = IndicesEconomicos.Origem.MANUAL) else it
            },
            salarioMinimo = IndicesEconomicos.SalarioMinimo(170000, "2027", IndicesEconomicos.Origem.MANUAL),
            extras = c0.extras.copy(
                selicMetaAnualPct = 14.5, inflacao12mPct = 4.25, taxaRotativoMensalPct = 12.5, selicMetaOrigem = IndicesEconomicos.Origem.MANUAL
            )
        )
        val lido = IndicesEconomicos.desserializar(IndicesEconomicos.serializar(c))
        assertEquals(c, lido)
        assertTrue(IndicesEconomicos.foiEditado(lido.itens.first { it.id == "cdi" }))
        assertFalse(IndicesEconomicos.foiEditado(lido.itens.first { it.id == "selic" }))
    }

    @Test fun `texto corrompido ou campos invalidos voltam ao padrao`() {
        assertEquals(IndicesEconomicos.padrao(), IndicesEconomicos.desserializar("lixo sem tab\n\n\t\t"))
        val ruim = "serie\tcdi\tx\ncomp\tcdi\tabc\nserie\tdesconhecido\t1\nsal\t-5\t2026\nextra\trotativo\t-1\nextra\tselicMeta\t99999"
        assertEquals(IndicesEconomicos.padrao(), IndicesEconomicos.desserializar(ruim))
    }
}
