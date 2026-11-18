package com.meudinheiro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SinalSyncTest {
    @Test fun `reconhece so o tipo SYNC`() {
        assertTrue(SinalSync.ehSinal(mapOf("tipo" to "SYNC", "origem" to "portal")))
        assertFalse(SinalSync.ehSinal(mapOf("tipo" to "VENCIMENTOS")))
        assertFalse(SinalSync.ehSinal(emptyMap()))
        assertFalse(SinalSync.ehSinal(mapOf("tipo" to "sync")))
    }

    @Test fun `so age com push nuvem e login`() {
        assertTrue(SinalSync.deveAgir(true, true, true))
        assertFalse(SinalSync.deveAgir(false, true, true))
        assertFalse(SinalSync.deveAgir(true, false, true))
        assertFalse(SinalSync.deveAgir(true, true, false))
    }

    @Test fun `anti-rajada`() {
        assertTrue(SinalSync.passouIntervalo(0, 1_000))
        assertFalse(SinalSync.passouIntervalo(100_000, 105_000))
        assertTrue(SinalSync.passouIntervalo(100_000, 110_000))
        assertTrue(SinalSync.passouIntervalo(100_000, 50_000)) // relógio voltou
    }

    @Test fun `janela so fora da tela`() {
        assertTrue(SinalSync.precisaJanela(false))
        assertFalse(SinalSync.precisaJanela(true))
    }

    @Test fun `recebeu depois do sinal`() {
        assertFalse(SinalSync.recebeu(null, 1_000))
        assertFalse(SinalSync.recebeu(999, 1_000))
        assertTrue(SinalSync.recebeu(1_000, 1_000))
        assertEquals(25_000L, SinalSync.JANELA_MAX_MS)
    }
}
