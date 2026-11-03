package com.meudinheiro.domain

import com.meudinheiro.domain.ConviteCodigo.Estado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R67 — código de convite. Os mesmos vetores estão em `portal/src/lib/finance/convite.test.ts`. */
class ConviteCodigoTest {

    @Test fun `alfabeto nao tem simbolos ambiguos e tem 31 simbolos`() {
        assertEquals(31, ConviteCodigo.ALFABETO.length)
        assertEquals(31, ConviteCodigo.ALFABETO.toSet().size)
        "01OIL".forEach { assertFalse("contém $it", ConviteCodigo.ALFABETO.contains(it)) }
    }

    @Test fun `gera 8 caracteres do alfabeto e e deterministico com sorteio injetado`() {
        var i = 0
        assertEquals("ABCDEFGH", ConviteCodigo.gerar { n -> (i++) % n })
        var j = 23
        assertEquals("23456789", ConviteCodigo.gerar { _ -> j++ })
        repeat(200) {
            val g = ConviteCodigo.gerar()
            assertEquals(8, g.length)
            assertTrue(ConviteCodigo.valido(g))
        }
    }

    @Test fun `valida formato`() {
        assertTrue(ConviteCodigo.valido("ABCD2345"))
        assertFalse(ConviteCodigo.valido("ABCD234"))
        assertFalse(ConviteCodigo.valido("ABCD23456"))
        assertFalse(ConviteCodigo.valido("ABCD234O"))
        assertFalse(ConviteCodigo.valido("ABCD2341"))
        assertFalse(ConviteCodigo.valido("abcd2345"))
        assertFalse(ConviteCodigo.valido(null))
    }

    @Test fun `normaliza caixa, espacos e hifen`() {
        assertEquals("ABCD2345", ConviteCodigo.normalizar(" abcd-2345 "))
        assertEquals("", ConviteCodigo.normalizar(null))
    }

    @Test fun `estado - validade de 7 dias, uso unico e precedencia`() {
        val agora = 1_000_000L
        val exp = agora + ConviteCodigo.VALIDADE_MS
        assertEquals(7L * 24 * 3600 * 1000, ConviteCodigo.VALIDADE_MS)
        assertEquals(Estado.VALIDO, ConviteCodigo.estado(false, exp, agora))
        assertEquals(Estado.VALIDO, ConviteCodigo.estado(false, exp, exp - 1))
        assertEquals(Estado.EXPIRADO, ConviteCodigo.estado(false, exp, exp))
        assertEquals(Estado.USADO, ConviteCodigo.estado(true, exp, agora))
        assertEquals(Estado.USADO, ConviteCodigo.estado(true, exp, exp + 5))
        assertEquals(Estado.INVALIDO, ConviteCodigo.estado(null, exp, agora))
        assertEquals(Estado.INVALIDO, ConviteCodigo.estado(false, null, agora))
        assertNull(ConviteCodigo.mensagem(Estado.VALIDO))
    }

    @Test fun `documento do convite`() {
        val d = ConviteCodigo.documento("dono1", Papel.EDITOR, "dono1", 100L)
        assertEquals("EDITOR", d["papel"])
        assertEquals(false, d["usado"])
        assertEquals(100L + ConviteCodigo.VALIDADE_MS, d["expiraEm"])
        assertEquals(setOf("donoUid", "papel", "criadoEm", "expiraEm", "usado", "criadoPor"), d.keys)
        assertTrue(runCatching { ConviteCodigo.documento("d", Papel.DONO, "d", 1L) }.isFailure)
    }
}
