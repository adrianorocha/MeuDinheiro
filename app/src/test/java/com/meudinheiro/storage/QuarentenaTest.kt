package com.meudinheiro.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarentenaTest {
    private class Mem : PersistenciaQuarentena {
        var texto: String? = null
        override fun ler() = texto
        override fun gravar(texto: String) { this.texto = texto }
    }

    private fun item(id: String, hash: String = "h1") =
        ItemEnvio("despesas", id, mapOf("descricao" to "Mercado", "valor" to 12.5), hash)

    @Test fun `registrar bloqueia e persiste`() {
        val mem = Mem()
        val q = Quarentena(mem)
        q.registrar(item("7"), "Recusado", 100L)
        assertTrue(q.bloqueia("despesas", "7"))
        assertEquals(1, q.pendentesDeAtencao)
        // novo processo: continua em quarentena (sem loop de reenvio após reiniciar)
        val q2 = Quarentena(mem)
        assertTrue(q2.bloqueia("despesas", "7"))
        assertTrue(q2.lista.value.single().descricao.startsWith("Mercado · R$ 12"))
    }

    @Test fun `nova recusa soma tentativas e preserva a data`() {
        val q = Quarentena(Mem())
        q.registrar(item("7"), "a", 100L)
        q.liberarTodos()
        assertFalse(q.bloqueia("despesas", "7"))
        q.registrar(item("7"), "b", 900L)
        val r = q.lista.value.single()
        assertEquals(2, r.tentativas)
        assertEquals(100L, r.desde)
        assertEquals("b", r.motivo)
        assertTrue(q.bloqueia("despesas", "7"))
    }

    @Test fun `sucesso remove da quarentena`() {
        val q = Quarentena(Mem())
        q.registrar(item("7"), "a", 1L)
        q.remover("despesas", "7")
        assertTrue(q.lista.value.isEmpty())
        assertFalse(q.bloqueia("despesas", "7"))
    }

    @Test fun `ignorar tira do aviso mas continua sem reenviar`() {
        val q = Quarentena(Mem())
        q.registrar(item("7"), "a", 1L)
        q.ignorarTodos()
        assertEquals(0, q.pendentesDeAtencao)
        assertTrue(q.bloqueia("despesas", "7"))
        assertEquals(1, q.lista.value.size) // dado preservado
        q.liberarTodos()
        assertEquals(1, q.pendentesDeAtencao)
        assertFalse(q.bloqueia("despesas", "7"))
    }

    @Test fun `atualizacao do app libera uma unica vez por versao`() {
        val mem = Mem()
        val q = Quarentena(mem)
        assertFalse(q.liberarSeNovaVersao("v1")) // primeira execução: só marca
        q.registrar(item("7"), "a", 1L)
        assertFalse(q.liberarSeNovaVersao("v1"))
        assertTrue(q.bloqueia("despesas", "7"))
        assertTrue(q.liberarSeNovaVersao("v2"))
        assertFalse(q.bloqueia("despesas", "7"))
        q.registrar(item("7"), "a", 2L) // falhou de novo
        assertFalse(q.liberarSeNovaVersao("v2")) // mesma versão: sem ciclo
        assertTrue(q.bloqueia("despesas", "7"))
        assertFalse(Quarentena(mem).liberarSeNovaVersao("v2")) // versão persistida
    }

    @Test fun `edicao do registro libera nova tentativa`() {
        val q = Quarentena(Mem())
        q.registrar(item("7", "h1"), "a", 1L)
        assertFalse(q.liberarSeEditado("despesas", "7", "h1"))
        assertTrue(q.liberarSeEditado("despesas", "7", "h2"))
        assertFalse(q.bloqueia("despesas", "7"))
    }

    @Test fun `limpar e texto corrompido`() {
        val mem = Mem()
        val q = Quarentena(mem)
        q.registrar(item("7"), "a", 1L)
        q.limpar()
        assertTrue(Quarentena(mem).lista.value.isEmpty())
        mem.texto = "lixo\u001Ecompleto"
        assertTrue(Quarentena(mem).lista.value.isEmpty())
    }

    @Test fun `motivo com separadores nao corrompe a persistencia`() {
        val mem = Mem()
        Quarentena(mem).registrar("c", "1", "d\u001Fx", "m\u001Ey\nz", "h", 5L)
        val r = Quarentena(mem).lista.value.single()
        assertEquals("c", r.colecao)
        assertEquals(5L, r.desde)
    }
}
