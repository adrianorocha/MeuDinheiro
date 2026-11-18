package com.meudinheiro.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Hash de sincronização: a ordem dos campos de um mapa (anexos) não pode mudar o resultado. */
class CanonizarValorTest {

    @Test fun `mapa em ordem diferente tem a mesma forma canonica`() {
        val local = listOf(linkedMapOf("id" to "a", "nome" to "x.jpg", "tipo" to "image/jpeg", "tamanho" to 209751, "caminho" to "c", "criadoEm" to 1L))
        val nuvem = listOf(hashMapOf("criadoEm" to 1L, "tipo" to "image/jpeg", "tamanho" to 209751.0, "nome" to "x.jpg", "id" to "a", "caminho" to "c"))
        assertEquals(canonizarValor(local), canonizarValor(nuvem))
    }

    @Test fun `lista mantem a ordem e valores diferentes continuam diferentes`() {
        assertNotEquals(canonizarValor(listOf("a", "b")), canonizarValor(listOf("b", "a")))
        assertNotEquals(canonizarValor(listOf(mapOf("id" to "a"))), canonizarValor(listOf(mapOf("id" to "b"))))
    }

    @Test fun `numeros nao dependem do tipo e nulo e distinto de vazio`() {
        assertEquals(canonizarValor(10), canonizarValor(10.0))
        assertEquals(canonizarValor(453.51), canonizarValor(453.510))
        assertNotEquals(canonizarValor(null), canonizarValor(""))
        assertEquals("∅", canonizarValor(null))
    }
}
