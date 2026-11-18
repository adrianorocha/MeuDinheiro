package com.meudinheiro.domain

import com.meudinheiro.domain.Rendimentos.Entrada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** R98 — mesmos cenários e números de portal/src/lib/finance/rendimento.test.ts (R97-B). */
class RendimentosTest {

    private val base = Entrada(10000.0, 0.0, 12, 0.0108, 1.0, 0.006624, 0.0082)

    @Test fun `aliquota do IR por faixa de dias`() {
        assertEquals(
            listOf(0.225, 0.225, 0.2, 0.2, 0.175, 0.175, 0.15, 0.15),
            listOf(1, 180, 181, 360, 361, 720, 721, 1000).map(Rendimentos::aliquotaIR)
        )
    }

    @Test fun `fator liquido 12 meses = IR de 20 por cento sobre o rendimento`() {
        assertEquals(1 + (Math.pow(1.0108, 12.0) - 1) * 0.8, Rendimentos.fatorLiquido(0.0108, 12), 1e-12)
        assertEquals(1 + (Math.pow(1.0108, 13.0) - 1) * 0.825, Rendimentos.fatorLiquido(0.0108, 13), 1e-12)
    }

    @Test fun `poupanca pela regra`() {
        assertEquals(0.0066241, Rendimentos.poupancaPelaRegra(0.1375, 0.001616), 1e-7)
        val esperado = (1 + (Math.pow(1.049, 1.0 / 12) - 1)) * 1.001616 - 1
        assertEquals(esperado, Rendimentos.poupancaPelaRegra(0.07, 0.001616), 1e-12)
        assertEquals(0.0056168555, Rendimentos.poupancaPelaRegra(0.07, 0.001616), 1e-9) // vetor da R97: 0,56169% a.m.
        assertEquals(Math.pow(1 + 0.7 * 0.085, 1.0 / 12) - 1, Rendimentos.poupancaPelaRegra(0.085, 0.0), 1e-12)
    }

    @Test fun `12 meses R$ 10000 CDI 1,08 - poupanca e CDI liquido`() {
        val r = Rendimentos.comparar(base)
        assertEquals(1000000L, r.totalAplicado)
        assertEquals(1082449L, r.poupanca.saldoFinal)
        assertEquals(82449L, r.poupanca.ganhoLiquido)
        assertEquals(27516L, r.cdi.ir)
        assertEquals(1110066L, r.cdi.saldoFinal)
        assertEquals(110066L, r.cdi.ganhoLiquido)
        assertEquals(0.2, r.aliquotaInicial, 0.0)
    }

    @Test fun `ganho real descontada a inflacao`() {
        val r = Rendimentos.comparar(base)
        assertEquals(1102961L, r.aplicadoCorrigido)
        assertEquals(7105L, r.cdi.ganhoReal)
        assertEquals(-20512L, r.poupanca.ganhoReal)
        assertEquals(1000000L, r.parado.saldoFinal)
        assertEquals(-102961L, r.parado.ganhoReal)
    }

    @Test fun `serie de evolucao comeca no aplicado e termina nos saldos finais`() {
        val r = Rendimentos.comparar(base)
        assertEquals(13, r.serie.size)
        assertEquals(Rendimentos.Ponto(0, 1000000, 1000000, 1000000, 1000000), r.serie[0])
        assertEquals(r.cdi.saldoFinal, r.serie[12].cdiLiquido)
        assertEquals(1051596L, r.serie[6].cdiLiquido)
    }

    @Test fun `com aportes R$ 1000 + R$ 500 por mes, 24 meses, 110 por cento do CDI`() {
        val r = Rendimentos.comparar(Entrada(1000.0, 500.0, 24, 0.0108, 1.1, 0.006624, 0.005))
        assertEquals(1300000L, r.totalAplicado)
        assertEquals(1413180L, r.poupanca.saldoFinal)
        assertEquals(1473314L, r.cdi.saldoFinal)
        assertEquals(38616L, r.cdi.ir)
        assertEquals(173314L, r.cdi.ganhoLiquido)
        assertEquals(0L, r.parado.ganhoLiquido)
        assertEquals(1384314L, r.aplicadoCorrigido)
    }

    @Test fun `o aporte do ultimo mes nao rende nem paga IR`() {
        val so = Rendimentos.comparar(Entrada(0.0, 100.0, 1, 0.01, 1.0, 0.01, 0.0))
        assertEquals(10000L, so.cdi.saldoFinal)
        assertEquals(10000L, so.poupanca.saldoFinal)
        assertEquals(0L, so.cdi.ir)
    }

    @Test fun `valida entradas`() {
        assertNull(Rendimentos.validar(base))
        assertNotNull(Rendimentos.validar(base.copy(meses = 0)))
        assertNotNull(Rendimentos.validar(base.copy(valorInicial = 0.0, aporteMensal = 0.0)))
        assertNotNull(Rendimentos.validar(base.copy(aporteMensal = -1.0)))
        assertNotNull(Rendimentos.validar(base.copy(cdiMensal = Double.NaN)))
        assertNotNull(Rendimentos.validar(base.copy(meses = 601)))
    }

    @Test fun `taxa liquida mensal equivalente em 12 meses`() {
        assertEquals(0.00873958, Rendimentos.taxaLiquidaMensal(0.0108, 12), 1e-7)
    }

    @Test fun `aporte para meta`() {
        assertEquals(86470L, Rendimentos.aporteParaMeta(12000.0, 1000.0, 12, Rendimentos.taxaLiquidaMensal(0.0108, 12)))
        assertEquals(91667L, Rendimentos.aporteParaMeta(12000.0, 1000.0, 12, 0.0)) // 11.000/12 = 916,666... para cima
        assertEquals(0L, Rendimentos.aporteParaMeta(1000.0, 5000.0, 12, 0.01))
    }
}
