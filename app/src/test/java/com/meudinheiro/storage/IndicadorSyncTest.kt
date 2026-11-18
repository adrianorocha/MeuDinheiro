package com.meudinheiro.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicadorSyncTest {
    private val t0 = 1_000_000_000L
    private fun info(
        status: SyncStatus = SyncStatus.Sincronizado(t0), pendentes: Int = 0, rede: Boolean = true,
        quarentena: Int = 0, confirmadoEm: Long? = null, nuvem: Boolean = true
    ) = InfoSync(nuvem, status, pendentes, rede, quarentena, confirmadoEm)

    private fun txt(i: InfoSync, agora: Long = t0) = IndicadorSync.derivar(i, agora)?.texto

    @Test fun `modo local ou desligado nao mostra nada`() {
        assertNull(txt(info(nuvem = false)))
        assertNull(txt(info(status = SyncStatus.Desligado)))
    }

    @Test fun `sincronizado mostra tempo relativo`() {
        assertEquals("Sincronizado agora", txt(info(), t0 + 30_000))
        assertEquals("Sincronizado há 2 min", txt(info(), t0 + 125_000))
        assertEquals("Sincronizado há 3 h", txt(info(), t0 + 3 * 3_600_000L + 5))
        assertEquals("Sincronizado há 2 d", txt(info(), t0 + 2 * 86_400_000L))
    }

    @Test fun `usa a confirmacao mais recente e tolera relogio atrasado`() {
        assertEquals("Sincronizado agora", txt(info(confirmadoEm = t0 + 600_000), t0 + 610_000))
        assertEquals("Sincronizado agora", txt(info(), t0 - 5_000_000))
    }

    @Test fun `enviando com pendencias`() {
        assertEquals("Enviando 3…", txt(info(pendentes = 3)))
    }

    @Test fun `offline com pendencias`() {
        assertEquals("Sem conexão · 3 pendentes", txt(info(pendentes = 3, rede = false)))
        assertEquals("Sem conexão · 1 pendente", txt(info(pendentes = 1, rede = false)))
        // offline sem nada a enviar segue mostrando sincronizado
        assertEquals("Sincronizado agora", txt(info(rede = false)))
    }

    @Test fun `erro tem prioridade e abre detalhes`() {
        val i = IndicadorSync.derivar(info(status = SyncStatus.Erro("x"), pendentes = 2, quarentena = 1), t0)!!
        assertEquals("Erro ao enviar · toque para ver", i.texto)
        assertEquals(TomIndicador.ERRO, i.tom)
        assertTrue(i.detalhes)
    }

    @Test fun `quarentena aparece quando nao ha envio em andamento`() {
        assertEquals("1 registro em quarentena · toque para ver", txt(info(quarentena = 1)))
        assertEquals("4 registros em quarentena · toque para ver", txt(info(quarentena = 4)))
        assertEquals("Enviando 2…", txt(info(pendentes = 2, quarentena = 4)))
    }

    @Test fun `conectando e sem login`() {
        assertEquals("Sincronizando…", txt(info(status = SyncStatus.Conectando)))
        val login = IndicadorSync.derivar(info(status = SyncStatus.AguardandoLogin), t0)!!
        assertEquals("Nuvem: sem login", login.texto)
        assertFalse(login.detalhes)
    }

    @Test fun `so recompoe com o relogio quando o texto depende dele`() {
        assertTrue(IndicadorSync.dependeDoRelogio(IndicadorSync.derivar(info(), t0)))
        assertFalse(IndicadorSync.dependeDoRelogio(IndicadorSync.derivar(info(pendentes = 1), t0)))
        assertFalse(IndicadorSync.dependeDoRelogio(null))
    }

    @Test fun `confirmacao so quando pendencias zeram por snapshot do servidor`() {
        assertEquals(50L, IndicadorSync.confirmacao(2, 0, true, 50L, 10L))
        assertEquals(10L, IndicadorSync.confirmacao(2, 0, false, 50L, 10L))
        assertEquals(10L, IndicadorSync.confirmacao(2, 1, true, 50L, 10L))
        assertEquals(10L, IndicadorSync.confirmacao(0, 0, true, 50L, 10L))
    }
}
