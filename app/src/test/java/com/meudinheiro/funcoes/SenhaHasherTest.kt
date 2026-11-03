package com.meudinheiro.funcoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SenhaHasherTest {

    @Test fun `hash confere com a senha correta e rejeita a errada`() {
        val hash = SenhaHasher.gerar("S3nh@-forte")
        assertTrue(SenhaHasher.ehHash(hash))
        assertTrue(SenhaHasher.conferir("S3nh@-forte", hash))
        assertFalse(SenhaHasher.conferir("s3nh@-forte", hash))
        assertFalse(SenhaHasher.conferir("", hash))
    }

    @Test fun `o hash nao contem a senha e usa sal diferente a cada vez`() {
        val a = SenhaHasher.gerar("abc12345")
        val b = SenhaHasher.gerar("abc12345")
        assertFalse(a.contains("abc12345"))
        assertNotEquals(a, b)
        assertTrue(SenhaHasher.conferir("abc12345", a) && SenhaHasher.conferir("abc12345", b))
    }

    @Test fun `senha antiga em texto puro ainda e aceita para migracao`() {
        assertFalse(SenhaHasher.ehHash("1234"))
        assertTrue(SenhaHasher.conferir("1234", "1234"))
        assertFalse(SenhaHasher.conferir("12345", "1234"))
    }

    @Test fun `armazenado vazio ou corrompido nunca confere`() {
        assertFalse(SenhaHasher.conferir("x", ""))
        assertFalse(SenhaHasher.conferir("x", "pbkdf2\$SHA\$1"))
        assertFalse(SenhaHasher.conferir("x", "pbkdf2\$PBKDF2WithHmacSHA256\$abc\$zz\$zz"))
    }

    @Test fun `formato tem cinco partes`() {
        assertEquals(5, SenhaHasher.gerar("x").split("$").size)
    }
}
