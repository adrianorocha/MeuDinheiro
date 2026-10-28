package com.meudinheiro.domain

import org.junit.Assert.*
import org.junit.Test

class NovaReceitaTest {
    @Test fun parseValor() {
        assertEquals(1234.5, NovaReceita.parseValor("1234,5")!!, 0.0)
        assertEquals(10.0, NovaReceita.parseValor(" 10.00 ")!!, 0.0)
        assertNull(NovaReceita.parseValor("0"))
        assertNull(NovaReceita.parseValor(""))
        assertNull(NovaReceita.parseValor("abc"))
        assertNull(NovaReceita.parseValor("-5"))
    }

    @Test fun categoriaInicial() {
        assertEquals("salário", NovaReceita.categoriaInicial(listOf("Mercado", "salário")))
        assertEquals("Mercado", NovaReceita.categoriaInicial(listOf("Mercado", "Lazer")))
        assertNull(NovaReceita.categoriaInicial(emptyList()))
    }

    @Test fun pagoDependeDaData() {
        assertTrue(NovaReceita.pagoInicial(100, 200))
        assertFalse(NovaReceita.pagoInicial(300, 200))
        assertTrue(NovaReceita.pagoFinal(true, 100, 200))
        assertFalse(NovaReceita.pagoFinal(false, 100, 200))
        assertFalse(NovaReceita.pagoFinal(true, 300, 200))
    }

    @Test fun origemViraTagInformativa() {
        assertEquals(listOf("De: João"), NovaReceita.tagsComOrigem(emptyList(), " João "))
        assertEquals(listOf("De: João", "x"), NovaReceita.tagsComOrigem(listOf("x"), "João"))
        assertEquals(listOf("x"), NovaReceita.tagsComOrigem(listOf("x"), null))
        assertEquals(listOf("x"), NovaReceita.tagsComOrigem(listOf("x"), "  "))
        // não duplica e respeita o limite de 5 mantendo a origem
        assertEquals(listOf("De: Ana", "a", "b", "c", "d"), NovaReceita.tagsComOrigem(listOf("De: ana", "a", "b", "c", "d", "e"), "Ana"))
        // nome longo é cortado em 30 caracteres
        assertTrue(NovaReceita.tagsComOrigem(emptyList(), "N".repeat(60)).single().length <= Tags.MAX_CHARS)
    }

    @Test fun validar() {
        assertNotNull(NovaReceita.validar(" ", "10", "Salário"))
        assertNotNull(NovaReceita.validar("Salário", "0", "Salário"))
        assertNotNull(NovaReceita.validar("Salário", "10", null))
        assertNull(NovaReceita.validar("Salário", "10,50", "Salário"))
    }
}
