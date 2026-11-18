package com.meudinheiro.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolamentoLoteTest {
    private fun itens(n: Int) = (1..n).map { ItemEnvio("despesas", "$it", mapOf("descricao" to "d$it"), "h$it") }
    /** Erro fake com código do Firestore (o SDK real exige Android para carregar). */
    private class Erro(val codigo: String) : Exception("erro $codigo")
    private object Code {
        const val PERMISSION_DENIED = "PERMISSION_DENIED"
        const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
        const val UNAVAILABLE = "UNAVAILABLE"
        const val DEADLINE_EXCEEDED = "DEADLINE_EXCEEDED"
        const val FAILED_PRECONDITION = "FAILED_PRECONDITION"
    }
    private fun erro(c: String) = Erro(c)
    private val codigoDe: (Throwable) -> String? = { (it as? Erro)?.codigo }
    private fun processar(lote: List<ItemEnvio>, e: IsolamentoLote.Enviador) = runBlocking { IsolamentoLote.processar(lote, e, codigoDe = codigoDe) }

    /** Fake do servidor: recusa o lote se qualquer item for "ruim"; guarda o que foi gravado. */
    private class Servidor(val ruins: Set<String>, val codigo: String = Code.PERMISSION_DENIED) : IsolamentoLote.Enviador {
        val gravados = mutableListOf<String>()
        var commits = 0
        override suspend fun enviar(lote: List<ItemEnvio>) {
            commits++
            if (lote.any { it.id in ruins }) throw Erro(codigo)
            gravados += lote.map { it.id }
        }
    }

    @Test fun `classifica erros`() {
        assertEquals(IsolamentoLote.Classe.RECUSADO, IsolamentoLote.classificar(erro(Code.PERMISSION_DENIED), codigoDe))
        assertEquals(IsolamentoLote.Classe.RECUSADO, IsolamentoLote.classificar(erro(Code.INVALID_ARGUMENT), codigoDe))
        assertEquals(IsolamentoLote.Classe.RECUSADO, IsolamentoLote.classificar(IllegalArgumentException("tipo"), codigoDe))
        assertEquals(IsolamentoLote.Classe.TRANSITORIO, IsolamentoLote.classificar(erro(Code.UNAVAILABLE), codigoDe))
        assertEquals(IsolamentoLote.Classe.TRANSITORIO, IsolamentoLote.classificar(erro(Code.DEADLINE_EXCEEDED), codigoDe))
        assertEquals(IsolamentoLote.Classe.TRANSITORIO, IsolamentoLote.classificar(RuntimeException("rede"), codigoDe))
        assertEquals(IsolamentoLote.Classe.SISTEMICO, IsolamentoLote.classificar(erro(Code.FAILED_PRECONDITION), codigoDe))
    }

    @Test fun `lote sem problema envia uma vez so`() = runBlocking {
        val s = Servidor(emptySet())
        val r = processar(itens(400), s)
        assertTrue(r.ok)
        assertTrue(r.recusados.isEmpty())
        assertEquals(1, s.commits)
        assertEquals(400, s.gravados.size)
    }

    @Test fun `isola um registro ruim e envia os demais`() = runBlocking {
        val s = Servidor(setOf("137"))
        val r = processar(itens(400), s)
        assertTrue(r.ok)
        assertEquals(listOf("137"), r.recusados.map { it.item.id })
        assertEquals(399, s.gravados.size)
        assertFalse("137" in s.gravados)
        assertEquals(s.gravados.toSet().size, s.gravados.size) // ninguém enviado duas vezes
        assertTrue("custo limitado (bisseção)", s.commits <= 2 * 9 + 3)
    }

    @Test fun `isola varios registros ruins`() = runBlocking {
        val s = Servidor(setOf("1", "200", "400"))
        val r = processar(itens(400), s)
        assertEquals(setOf("1", "200", "400"), r.recusados.map { it.item.id }.toSet())
        assertEquals(397, s.gravados.size)
    }

    @Test fun `erro transitorio nao isola e devolve a falha`() = runBlocking {
        val s = Servidor(setOf("5"), Code.UNAVAILABLE)
        val r = processar(itens(10), s)
        assertFalse(r.ok)
        assertFalse(r.sistemico)
        assertTrue(r.recusados.isEmpty())
        assertEquals(1, s.commits) // sem bisseção: continua com o recuo da sessão
    }

    @Test fun `erro sistemico nao poe nada em quarentena`() = runBlocking {
        val s = Servidor(setOf("5"), Code.FAILED_PRECONDITION)
        val r = processar(itens(10), s)
        assertTrue(r.sistemico)
        assertNotNull(r.falha)
        assertTrue(r.recusados.isEmpty())
    }

    @Test fun `recusa em massa vira problema geral e nao enche a quarentena`() = runBlocking {
        val s = Servidor(itens(100).map { it.id }.toSet()) // tudo recusado (ex.: sem permissão)
        val r = processar(itens(100), s)
        assertTrue(r.sistemico)
        assertTrue(r.recusados.isEmpty())
        assertTrue("para cedo, sem varrer tudo", s.commits < 100)
    }

    @Test fun `erro transitorio no meio preserva o que ja foi isolado`() = runBlocking {
        val r = processar(itens(4), IsolamentoLote.Enviador { lote ->
            if (lote.any { it.id == "1" }) throw erro(Code.PERMISSION_DENIED)
            if (lote.size == 2 && lote[0].id == "3") throw erro(Code.UNAVAILABLE)
        })
        assertEquals(listOf("1"), r.recusados.map { it.item.id })
        assertNotNull(r.falha)
    }

    @Test fun `lote unitario recusado vai direto para quarentena`() = runBlocking {
        val r = processar(itens(1), Servidor(setOf("1")))
        assertEquals(1, r.recusados.size)
        assertNull(r.falha)
    }

    @Test fun `erro ao montar o lote isola o registro culpado`() = runBlocking {
        val r = processar(itens(5), IsolamentoLote.Enviador { lote ->
            if (lote.any { it.id == "3" }) throw IllegalArgumentException("Unsupported type")
        })
        assertEquals(listOf("3"), r.recusados.map { it.item.id })
    }

    @Test fun `motivo e descricao`() {
        assertEquals("Recusado pelas regras de segurança da nuvem", IsolamentoLote.motivo(erro(Code.PERMISSION_DENIED), codigoDe))
        assertEquals("exclusão de 9", IsolamentoLote.descrever(ItemEnvio("despesas", "9", null, "")))
        assertEquals("id 9", IsolamentoLote.descrever(ItemEnvio("despesas", "9", mapOf("x" to 1), "h")))
    }
}
