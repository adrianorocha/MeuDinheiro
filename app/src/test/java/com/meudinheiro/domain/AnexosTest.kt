package com.meudinheiro.domain

import com.meudinheiro.data.Anexo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R60 — mesmos cenários de `portal/src/lib/finance/anexos.test.ts`. */
class AnexosTest {

    private val ok = Anexo("a1", "nota.jpg", "image/jpeg", 1000, "usuarios/u1/anexos/55/a1.jpg", 1)

    @Test fun `aceita jpeg png e pdf ate 5 MB`() {
        assertNull(Anexos.validarNovo("image/jpeg", 1, 0))
        assertNull(Anexos.validarNovo("image/png", 1000, 4))
        assertNull(Anexos.validarNovo("application/pdf", Anexos.MAX_BYTES, 0))
    }

    @Test fun `recusa tipo fora da lista`() {
        assertEquals("Tipo de arquivo não permitido. Use JPEG, PNG ou PDF.", Anexos.validarNovo("image/gif", 10, 0))
        assertEquals("Tipo de arquivo não permitido. Use JPEG, PNG ou PDF.", Anexos.validarNovo("", 10, 0))
        assertEquals("Tipo de arquivo não permitido. Use JPEG, PNG ou PDF.", Anexos.validarNovo("text/html", 10, 0))
    }

    @Test fun `recusa arquivo vazio ou acima de 5 MB`() {
        assertEquals("O arquivo está vazio.", Anexos.validarNovo("image/png", 0, 0))
        assertEquals("Arquivo muito grande (5,1 MB). O limite é 5 MB.", Anexos.validarNovo("application/pdf", Anexos.MAX_BYTES + 100_000, 0))
    }

    @Test fun `recusa o sexto anexo`() {
        assertEquals("Limite de 5 anexos por lançamento.", Anexos.validarNovo("image/png", 10, 5))
        assertEquals("Limite de 5 anexos por lançamento.", Anexos.validarNovo("image/png", 10, 7))
    }

    @Test fun `caminho do storage segue usuarios uid anexos despesa id ext`() {
        assertEquals("usuarios/u1/anexos/123/abc.jpg", Anexos.caminhoStorage("u1", 123, "abc", "image/jpeg"))
        assertEquals("usuarios/u1/anexos/123/abc.png", Anexos.caminhoStorage("u1", 123, "abc", "image/png"))
        assertEquals("usuarios/u1/anexos/123/abc.pdf", Anexos.caminhoStorage("u1", 123, "abc", "application/pdf"))
        assertNull(Anexos.caminhoStorage("u1", 123, "abc", "image/gif"))
        assertNull(Anexos.caminhoStorage("", 123, "abc", "image/png"))
        assertNull(Anexos.caminhoStorage("u/1", 123, "abc", "image/png"))
        assertNull(Anexos.caminhoStorage("u1", 123, "../x", "image/png"))
    }

    @Test fun `caminho local tem prefixo local`() {
        val c = Anexos.caminhoLocal(9, "abc", "application/pdf")!!
        assertEquals("local:anexos/9/abc.pdf", c)
        assertTrue(Anexos.ehLocal(c))
        assertFalse(Anexos.ehLocal("usuarios/u1/anexos/9/abc.pdf"))
        assertTrue(Anexos.caminhoValido(c))
        assertTrue(Anexos.caminhoValido("usuarios/u1/anexos/9/abc.pdf"))
        assertFalse(Anexos.caminhoValido("usuarios/u1/outros/9/abc.pdf"))
        assertFalse(Anexos.caminhoValido("../../etc/passwd"))
    }

    @Test fun `reducao de imagem mantem proporcao e nunca amplia`() {
        assertEquals(1600 to 1200, Anexos.ladoReduzido(4000, 3000))
        assertEquals(1200 to 1600, Anexos.ladoReduzido(3000, 4000))
        assertEquals(800 to 600, Anexos.ladoReduzido(800, 600))
        assertEquals(1600 to 1600, Anexos.ladoReduzido(1600, 1600))
        assertEquals(1600 to 1, Anexos.ladoReduzido(32000, 1))
        assertEquals(1 to 1, Anexos.ladoReduzido(0, 10))
        assertEquals(2, Anexos.amostragem(4000, 3000))
        assertEquals(1, Anexos.amostragem(1700, 1000))
    }

    @Test fun `normalizar descarta invalidos, repetidos e passa de 5`() {
        val lixo = listOf(
            ok,
            ok.copy(nome = "repetido"),
            ok.copy(id = "", caminho = "usuarios/u1/anexos/55/x.jpg"),
            ok.copy(id = "b", tipo = "image/gif"),
            ok.copy(id = "c", caminho = "http://evil/x.jpg"),
            null
        )
        assertEquals(listOf("a1"), Anexos.normalizar(lixo).map { it.id })
        val muitos = (1..8).map { ok.copy(id = "i$it", caminho = "usuarios/u1/anexos/55/i$it.jpg") }
        assertEquals(5, Anexos.normalizar(muitos).size)
        assertTrue(Anexos.normalizar(null).isEmpty())
        assertEquals("anexo", Anexos.nomeSeguro("   "))
        assertEquals("a_b_c", Anexos.nomeSeguro("a/b\\c"))
        assertEquals(120, Anexos.nomeSeguro("x".repeat(500)).length)
    }
}
