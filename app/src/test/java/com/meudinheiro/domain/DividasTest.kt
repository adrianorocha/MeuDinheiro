package com.meudinheiro.domain

import com.meudinheiro.data.Divida
import com.meudinheiro.domain.Dividas.DividaSim
import com.meudinheiro.domain.Dividas.Estrategia
import com.meudinheiro.domain.Dividas.MesSim
import com.meudinheiro.domain.Dividas.QuitacaoSim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R58 — MESMOS cenários e números de portal/src/lib/finance/dividas.test.ts. */
class DividasTest {

    private val s1 = listOf(DividaSim(1, "A", 10000, 100, 5000))
    private val s2 = listOf(
        DividaSim(1, "Cartão", 100000, 1000, 10000),
        DividaSim(2, "Empréstimo", 50000, 200, 5000)
    )

    @Test fun `juros mensais em centavos, meio para cima`() {
        assertEquals(309L, Dividas.jurosDoMes(12345, 250)) // 308,625
        assertEquals(1L, Dividas.jurosDoMes(100, 50)) // 0,5 -> 1
        assertEquals(0L, Dividas.jurosDoMes(30, 50)) // 0,15 -> 0
        assertEquals(0L, Dividas.jurosDoMes(100000, 0))
        assertEquals(0L, Dividas.jurosDoMes(0, 1000))
    }

    @Test fun `uma divida so com minimos - cronograma e ultimo pagamento ajustado`() {
        val r = Dividas.simular(s1, 0, Estrategia.MINIMOS)
        assertEquals(3, r.meses)
        assertEquals(153L, r.jurosTotal)
        assertEquals(10153L, r.pagoTotal)
        assertEquals(
            listOf(MesSim(1, 100, 5000, 5100), MesSim(2, 51, 5000, 151), MesSim(3, 2, 153, 0)),
            r.cronograma
        )
        assertFalse(r.nuncaQuita)
        assertEquals(listOf(QuitacaoSim(1, "A", 3)), r.quitacoes)
    }

    @Test fun `bola de neve x avalanche com orcamento extra`() {
        val c = Dividas.comparar(s2, 20000)
        assertEquals(6, c.bolaDeNeve.meses)
        assertEquals(46039L, c.bolaDeNeve.jurosTotal)
        assertEquals(196039L, c.bolaDeNeve.pagoTotal)
        assertEquals(listOf(QuitacaoSim(2, "Empréstimo", 3), QuitacaoSim(1, "Cartão", 6)), c.bolaDeNeve.quitacoes)
        assertEquals(MesSim(1, 11000, 35000, 126000), c.bolaDeNeve.cronograma[0])
        assertEquals(MesSim(2, 10520, 35000, 101520), c.bolaDeNeve.cronograma[1])

        assertEquals(6, c.avalanche.meses)
        assertEquals(32223L, c.avalanche.jurosTotal)
        assertEquals(182223L, c.avalanche.pagoTotal)
        assertEquals(listOf(QuitacaoSim(1, "Cartão", 5), QuitacaoSim(2, "Empréstimo", 6)), c.avalanche.quitacoes)
        assertEquals(MesSim(2, 8920, 35000, 99920), c.avalanche.cronograma[1])

        assertEquals(150000L + c.bolaDeNeve.jurosTotal, c.bolaDeNeve.pagoTotal)
        assertEquals(150000L + c.avalanche.jurosTotal, c.avalanche.pagoTotal)
        assertEquals(46039L - 32223L, c.economia.avalancheVsBola)
    }

    @Test fun `so minimos que nao cobrem os juros - nunca quita, com aviso e limite de 600 meses`() {
        val c = Dividas.comparar(s2, 20000)
        assertTrue(c.minimos.nuncaQuita)
        assertNull(c.minimos.meses)
        assertEquals(600, c.minimos.cronograma.size)
        assertEquals(listOf(QuitacaoSim(2, "Empréstimo", 12)), c.minimos.quitacoes)
        assertEquals(100000L, c.minimos.saldoRestante)
        assertNull(c.economia.bolaVsMinimos)
        assertNull(c.economia.avalancheVsMinimos)
        assertEquals(
            listOf(
                "Cartão: o pagamento mínimo (R$ 100,00) não supera os juros do mês (R$ 100,00).",
                "Pagando só os mínimos, as dívidas nunca são quitadas (limite de 600 meses)."
            ),
            c.avisos
        )
    }

    @Test fun `juros que explodem param antes dos 600 meses e marcam nunca quita`() {
        val r = Dividas.simular(listOf(DividaSim(1, "X", 100000, 1000, 5000)), 0, Estrategia.MINIMOS)
        assertTrue(r.nuncaQuita)
        assertNull(r.meses)
        assertTrue(r.saldoRestante > 100000)
        assertTrue(r.cronograma.size < 600)
    }

    @Test fun `sem juros e sem dividas`() {
        val r = Dividas.simular(listOf(DividaSim(1, "Z", 30000, 0, 10000)), 0, Estrategia.MINIMOS)
        assertEquals(3, r.meses)
        assertEquals(0L, r.jurosTotal)
        assertEquals(30000L, r.pagoTotal)
        val vazio = Dividas.simular(emptyList(), 5000, Estrategia.AVALANCHE)
        assertEquals(0, vazio.meses)
        assertFalse(vazio.nuncaQuita)
        val semMin = listOf(DividaSim(1, "Z", 30000, 0, 0))
        assertEquals(3, Dividas.simular(semMin, 10000, Estrategia.BOLA_DE_NEVE).meses)
        assertTrue(Dividas.simular(semMin, 10000, Estrategia.MINIMOS).nuncaQuita)
    }

    @Test fun `data estimada de quitacao`() {
        assertEquals(Dividas.AnoMes(2026, 1), Dividas.dataQuitacao(2025, 11, 3))
        assertEquals(Dividas.AnoMes(2025, 11), Dividas.dataQuitacao(2025, 11, 1))
        assertEquals(Dividas.AnoMes(2027, 1), Dividas.dataQuitacao(2025, 1, 25))
        assertNull(Dividas.dataQuitacao(2025, 1, null))
    }

    @Test fun `converte a divida cadastrada em centavos e basis points`() {
        val d = Divida(id = 9, nome = "Cheque", tipo = "CHEQUE_ESPECIAL", saldoDevedor = 1234.56, taxaJurosMensal = 2.5, parcelaMinima = 100.1)
        assertEquals(DividaSim(9, "Cheque", 123456, 250, 10010), Dividas.paraSim(d))
        assertNull(Dividas.paraSim(d.copy(ativa = false)))
        assertNull(Dividas.paraSim(d.copy(saldoDevedor = 0.0)))
        val sem = Dividas.paraSim(d.copy(taxaJurosMensal = 0.0, parcelaMinima = null))
        assertNotNull(sem)
        assertEquals(0L, sem!!.taxaBp)
        assertEquals(0L, sem.minimo)
    }

    @Test fun `aplicar pagamento recusa o que passa do devido e valor zero`() {
        assertNotNull(Dividas.aplicarPagamento(100000, 2000, 102001).erro)
        assertNotNull(Dividas.aplicarPagamento(100000, 2000, 0).erro)
        val ok = Dividas.aplicarPagamento(100000, 2000, 102000)
        assertNull(ok.erro)
        assertEquals(0L, ok.saldo)
        assertEquals(82000L, Dividas.aplicarPagamento(100000, 2000, 20000).saldo)
    }

    @Test fun `progresso e validacao do cadastro`() {
        assertEquals(0.2, Dividas.progresso(Divida(nome = "C", saldoDevedor = 4000.0, valorOriginal = 5000.0)), 1e-9)
        assertEquals(0.0, Dividas.progresso(Divida(nome = "C", saldoDevedor = 6000.0, valorOriginal = 5000.0)), 1e-9)
        assertNotNull(Dividas.validar(" ", "OUTRA", 10.0, null, null, null, null))
        assertNotNull(Dividas.validar("X", "OUTRA", 10.0, 101.0, null, null, null))
        assertNotNull(Dividas.validar("X", "XYZ", 10.0, null, null, null, null))
        assertNotNull(Dividas.validar("X", "OUTRA", 10.0, null, null, null, 32))
        assertNull(Dividas.validar("X", "FINANCIAMENTO", 5000.0, 1.5, 600.0, 36, 10))
    }
}
