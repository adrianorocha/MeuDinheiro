package com.meudinheiro.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaEnvioTest {
    @Test fun `agenda so com pendencia e app fora da tela`() {
        assertTrue(PoliticaEnvio.deveAgendar(temPendencias = true, emPrimeiroPlano = false))
        assertFalse(PoliticaEnvio.deveAgendar(temPendencias = true, emPrimeiroPlano = true))
        assertFalse(PoliticaEnvio.deveAgendar(temPendencias = false, emPrimeiroPlano = false))
        assertFalse(PoliticaEnvio.deveAgendar(temPendencias = false, emPrimeiroPlano = true))
    }

    @Test fun `recuo cresce de 2 em 2 s e limita em 60 s`() {
        assertEquals(2_000L, PoliticaEnvio.recuoMs(1))
        assertEquals(6_000L, PoliticaEnvio.recuoMs(3))
        assertEquals(60_000L, PoliticaEnvio.recuoMs(30))
        assertEquals(2_000L, PoliticaEnvio.recuoMs(0))
    }
}
