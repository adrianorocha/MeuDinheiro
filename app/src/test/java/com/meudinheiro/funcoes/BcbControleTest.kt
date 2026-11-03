package com.meudinheiro.funcoes

import com.meudinheiro.domain.Bcb
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class CacheFake(var indices: String? = null, var cambio: String? = null) : BcbCache {
    var gravacoesIndices = 0
    var gravacoesCambio = 0
    override suspend fun lerIndices() = indices
    override suspend fun gravarIndices(texto: String) { indices = texto; gravacoesIndices++ }
    override suspend fun lerCambio() = cambio
    override suspend fun gravarCambio(texto: String) { cambio = texto; gravacoesCambio++ }
}

/** R100 — TTL de 6 h com relógio injetável, offline, erro total/parcial e cache sem rede. */
class BcbControleTest {

    private var agora = msUtc(2026, 10, 28, 14, 30)
    private val http = FakeBcbHttp { url ->
        if (url.contains("olinda")) """{"value":[${boletimJson("2026-10-27 13:05:00", 5.34, "Fechamento")},${boletimJson("2026-10-26 13:05:00", 5.3, "Fechamento")}]}"""
        else sgsJson("01/09/2026", "0.5")
    }
    private val cache = CacheFake()
    private fun controle(h: BcbHttp = http, c: BcbCache = cache) = BcbControle(c, BcbClient(h, { agora }, { TZ_UTC }), { agora })

    @Test fun `sem cache consulta e grava com atualizadoEm`() = runTest {
        val c = controle()
        c.atualizarIndices(forcar = false, conectado = true)
        assertEquals(13, http.urls.size)
        assertEquals(1, cache.gravacoesIndices)
        assertEquals(agora, c.indices.value.snapshot!!.atualizadoEm)
        assertFalse(c.indices.value.carregando)
        assertNull(c.indices.value.erro)
        assertEquals(agora, Bcb.lerSnapshotIndices(cache.indices)!!.atualizadoEm)
    }

    @Test fun `dentro de 6 h nao consulta, passadas 6 h consulta e forcar sempre consulta`() = runTest {
        val c = controle()
        c.atualizarIndices(false, true)
        val n = http.urls.size
        agora += 5 * 60 * 60 * 1000L + 59 * 60 * 1000L
        c.atualizarIndices(false, true)
        assertEquals(n, http.urls.size) // 5h59: cache valido
        agora += 2 * 60 * 1000L // 6h01
        c.atualizarIndices(false, true)
        assertEquals(2 * n, http.urls.size)
        c.atualizarIndices(true, true) // "Atualizar agora"
        assertEquals(3 * n, http.urls.size)
    }

    @Test fun `cache salvo de uma sessao anterior e respeitado sem rede`() = runTest {
        controle().atualizarIndices(false, true)
        val antes = http.urls.size
        agora += 60 * 60 * 1000L // 1 h depois, novo controle (nova abertura da tela)
        val c2 = controle()
        c2.atualizarIndices(false, true)
        assertEquals(antes, http.urls.size)
        assertNotNull(c2.indices.value.snapshot)
    }

    @Test fun `offline nao faz requisicao e mantem o cache`() = runTest {
        val c = controle()
        c.atualizarIndices(false, true)
        val n = http.urls.size
        c.atualizarIndices(true, conectado = false)
        assertEquals(n, http.urls.size)
        assertTrue(c.indices.value.offline)
        assertNotNull(c.indices.value.snapshot)
        assertEquals("Offline: usando valores salvos de 28/10 14:30", Bcb.descreverEstado(Bcb.EstadoAtualizacao(c.indices.value.snapshot!!.atualizadoEm, false, null, true), TZ_UTC))
    }

    @Test fun `erro total mantem o cache anterior e nao grava`() = runTest {
        val c = controle()
        c.atualizarIndices(false, true)
        val antigo = c.indices.value.snapshot
        val gravou = cache.gravacoesIndices
        val c2 = controle(FakeBcbHttp { throw BcbErro("HTTP 500") })
        agora += 7 * 60 * 60 * 1000L
        c2.atualizarIndices(false, true)
        assertEquals("HTTP 500", c2.indices.value.erro)
        assertEquals(antigo!!.atualizadoEm, c2.indices.value.snapshot!!.atualizadoEm)
        assertEquals(antigo.itens, c2.indices.value.snapshot!!.itens)
        assertEquals(gravou, cache.gravacoesIndices)
        assertFalse(c2.indices.value.carregando)
    }

    @Test fun `falha parcial mescla com o cache e avisa`() = runTest {
        val c = controle()
        c.atualizarIndices(false, true)
        agora += 7 * 60 * 60 * 1000L
        val parcial = FakeBcbHttp { url -> if (url.contains("sgs.433/")) throw BcbErro("HTTP 500") else sgsJson("01/10/2026", "0.7") }
        val c2 = controle(parcial)
        c2.atualizarIndices(false, true)
        val s = c2.indices.value.snapshot!!
        assertEquals(listOf(0.5), s.itens["ipca"]!!.serie) // manteve o ultimo valor conhecido
        assertEquals(listOf(0.7), s.itens["igpm"]!!.serie)
        assertEquals(agora, s.atualizadoEm)
        assertTrue(c2.indices.value.avisoParcial!!.contains("IPCA"))
    }

    @Test fun `cambio com TTL e historico so quando pedido`() = runTest {
        val c = controle()
        c.atualizarCambio(false, true)
        assertEquals(10, http.urls.size)
        assertEquals(10, c.cambio.value.snapshot!!.cotacoes.size)
        assertTrue(c.historico.value.isEmpty()) // nenhuma consulta de historico ate o usuario pedir
        c.atualizarCambio(false, true)
        assertEquals(10, http.urls.size)
        c.carregarHistorico("USD", true)
        assertEquals(11, http.urls.size)
        assertEquals(2, c.historico.value["USD"]!!.pontos.size)
        assertEquals(1, cache.gravacoesCambio) // o historico nao vai ao cache
        agora += 6 * 60 * 60 * 1000L
        c.atualizarCambio(false, true)
        assertEquals(21, http.urls.size)
    }

    @Test fun `historico offline e com erro mostra a mensagem`() = runTest {
        val c = controle()
        c.carregarHistorico("USD", conectado = false)
        assertEquals("sem conexão", c.historico.value["USD"]!!.erro)
        assertTrue(http.urls.isEmpty())
        val c2 = controle(FakeBcbHttp { throw BcbErro("HTTP 503") })
        c2.carregarHistorico("EUR", true)
        assertEquals("HTTP 503", c2.historico.value["EUR"]!!.erro)
        assertFalse(c2.historico.value["EUR"]!!.carregando)
    }

    @Test fun `cache corrompido e ignorado`() = runTest {
        val c = controle(c = CacheFake(indices = "lixo", cambio = "{}"))
        c.carregarCacheIndices()
        c.carregarCacheCambio()
        assertNull(c.indices.value.snapshot)
        assertNull(c.cambio.value.snapshot)
    }
}
