package com.meudinheiro.domain

import com.meudinheiro.domain.AvistaParcelado.Entrada
import com.meudinheiro.domain.AvistaParcelado.MenorCusto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R98 — mesmos cenários e números de portal/src/lib/finance/avistaParcelado.test.ts (R97-C). */
class AvistaParceladoTest {

    @Test fun `10x de 110 sobre 1000 a vista = 1,7715 por cento ao mes`() {
        val i = AvistaParcelado.taxaImplicita(Entrada(1000.0, 10, 110.0))!!
        assertEquals(0.0177154269, i, 1e-9)
        var vp = 0.0
        for (k in 1..10) vp += 110 / Math.pow(1 + i, k.toDouble())
        assertEquals(1000.0, vp, 1e-6)
    }

    @Test fun `sem juros = 0`() {
        assertEquals(0.0, AvistaParcelado.taxaImplicita(Entrada(1000.0, 10, 100.0))!!, 0.0)
    }

    @Test fun `com entrada`() {
        assertEquals(0.0814416565, AvistaParcelado.taxaImplicita(Entrada(1000.0, 10, 120.0, 200.0))!!, 1e-8)
    }

    @Test fun `10x de 102 sobre 1000 a vista = 0,36168 por cento ao mes`() {
        assertEquals(0.0036167778, AvistaParcelado.taxaImplicita(Entrada(1000.0, 10, 102.0))!!, 1e-9)
    }

    @Test fun `parcelado mais barato que a vista = taxa negativa`() {
        assertTrue(AvistaParcelado.taxaImplicita(Entrada(1000.0, 10, 95.0))!! < 0)
    }

    @Test fun `entradas invalidas = null`() {
        assertNull(AvistaParcelado.taxaImplicita(Entrada(0.0, 10, 100.0)))
        assertNull(AvistaParcelado.taxaImplicita(Entrada(1000.0, 0, 100.0)))
        assertNull(AvistaParcelado.taxaImplicita(Entrada(1000.0, 3, 100.0, 1000.0)))
        assertNotNull(AvistaParcelado.validar(Entrada(1000.0, 3, 0.0)))
    }

    @Test fun `1000 a vista x 10x de 110 - a vista custa menos em valor presente`() {
        val r = AvistaParcelado.comparar(Entrada(1000.0, 10, 110.0), 0.0108, 1.0)!!
        assertEquals(1100.0, r.totalPago, 0.0)
        assertEquals(100.0, r.juros, 0.0)
        assertEquals(0.234571631, r.taxaAnual, 1e-8)
        assertEquals(1049.77, r.valorPresente, 0.0)
        assertEquals(49.77, r.diferenca, 1e-9)
        assertEquals(0.04566525, r.descontoBreakEven, 1e-7)
        assertEquals(0.0909090909, r.descontoOferecido, 1e-9)
        assertEquals(MenorCusto.AVISTA, r.menorCusto)
    }

    @Test fun `1000 a vista x 10x de 102 - o parcelado custa menos em valor presente`() {
        val r = AvistaParcelado.comparar(Entrada(1000.0, 10, 102.0), 0.0108, 1.0)!!
        assertEquals(973.42, r.valorPresente, 0.0)
        assertEquals(-26.58, r.diferenca, 1e-9)
        assertEquals(MenorCusto.PARCELADO, r.menorCusto)
        assertEquals(0.04566525, r.descontoBreakEven, 1e-7)
    }

    @Test fun `preco a vista igual ao VP = empate`() {
        assertEquals(MenorCusto.EMPATE, AvistaParcelado.comparar(Entrada(1049.77, 10, 110.0), 0.0108, 1.0)!!.menorCusto)
    }

    @Test fun `rejeita entradas invalidas`() {
        assertNull(AvistaParcelado.comparar(Entrada(1000.0, 10, 110.0), Double.NaN, 1.0))
        assertNull(AvistaParcelado.comparar(Entrada(-1.0, 10, 110.0), 0.01, 1.0))
    }
}
