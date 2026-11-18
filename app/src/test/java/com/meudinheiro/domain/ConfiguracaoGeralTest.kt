package com.meudinheiro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R57 — MESMOS cenários de portal/src/lib/finance/configuracoes.test.ts. */
class ConfiguracaoGeralTest {

    private val painelA = Painel503020.CONFIG_PADRAO.copy(mesesReserva = 3)
    private val painelB = Painel503020.CONFIG_PADRAO.copy(mesesReserva = 9)

    @Test fun `nuvem mais nova substitui o local`() {
        val (cfg, acao) = ConfigGeral.mesclar(
            ConfigGeral(painelA, atualizadoEm = 100),
            ConfigGeral(painelB, limiteDiarioAteReceita = true, atualizadoEm = 200)
        )
        assertEquals(ConfigGeral.Acao.APLICAR_NUVEM, acao)
        assertEquals(9, cfg.painel!!.mesesReserva)
        assertEquals(true, cfg.limiteDiarioAteReceita)
    }

    @Test fun `local mais novo e mantido e deve ser enviado`() {
        val (cfg, acao) = ConfigGeral.mesclar(ConfigGeral(painelA, atualizadoEm = 300), ConfigGeral(painelB, atualizadoEm = 200))
        assertEquals(ConfigGeral.Acao.ENVIAR_LOCAL, acao)
        assertEquals(3, cfg.painel!!.mesesReserva)
    }

    @Test fun `empate a nuvem nao sobrescreve o local`() {
        val (cfg, acao) = ConfigGeral.mesclar(ConfigGeral(painelA, atualizadoEm = 200), ConfigGeral(painelB, atualizadoEm = 200))
        assertEquals(ConfigGeral.Acao.NADA, acao)
        assertEquals(3, cfg.painel!!.mesesReserva)
    }

    @Test fun `nuvem sem data nao sobrescreve e local sem data cede a uma nuvem datada`() {
        assertEquals(ConfigGeral.Acao.ENVIAR_LOCAL, ConfigGeral.decidir(ConfigGeral(painelA, atualizadoEm = 5), ConfigGeral(painelB)))
        assertEquals(ConfigGeral.Acao.ENVIAR_LOCAL, ConfigGeral.decidir(ConfigGeral(painelA, atualizadoEm = 5), ConfigGeral(painelB, atualizadoEm = null)))
        assertEquals(ConfigGeral.Acao.NADA, ConfigGeral.decidir(ConfigGeral(painelA), ConfigGeral(painelB)))
        assertEquals(ConfigGeral.Acao.APLICAR_NUVEM, ConfigGeral.decidir(ConfigGeral(painelA), ConfigGeral(painelB, atualizadoEm = 1)))
    }

    @Test fun `sem nuvem nada a aplicar e local datado manda enviar`() {
        assertEquals(ConfigGeral.Acao.ENVIAR_LOCAL, ConfigGeral.decidir(ConfigGeral(atualizadoEm = 1), null))
        assertEquals(ConfigGeral.Acao.NADA, ConfigGeral.decidir(ConfigGeral(), null))
    }

    @Test fun `normaliza lixo e alterar sempre avanca o carimbo`() {
        val lixo = ConfigGeral.deDoc(
            mapOf("painel503020" to mapOf("metas" to mapOf("necessidades" to 999)), "limiteDiarioAteReceita" to "x", "atualizadoEm" to "ontem")
        )
        assertEquals(ConfigGeral(Painel503020.CONFIG_PADRAO), lixo)
        val a = ConfigGeral.alterar(ConfigGeral(atualizadoEm = 1000), agora = 500, limite = true) // relógio atrasado
        assertEquals(1001L, a.atualizadoEm)
        assertEquals(true, a.limiteDiarioAteReceita)
        assertNull(ConfigGeral.deDoc(null).atualizadoEm)
    }

    @Test fun `ida e volta pelo documento (Firestore e backup) preserva tudo`() {
        val cfg = ConfigGeral(
            Painel503020.normalizarConfig(40, 30, 30, 12, mapOf("Streaming" to Painel503020.Grupo.NECESSIDADES)), true, 1234L
        )
        // o Gson devolve números como Double; o Firestore, como Long: os dois precisam dar o mesmo resultado
        val comoDouble = cfg.toDoc().let { gsonRoundTrip(it) }
        assertEquals(cfg, ConfigGeral.deDoc(cfg.toDoc()))
        assertEquals(cfg, ConfigGeral.deDoc(comoDouble))
    }

    @Suppress("UNCHECKED_CAST")
    private fun gsonRoundTrip(m: Map<String, Any?>): Map<String, Any?> {
        val g = com.google.gson.Gson()
        return g.fromJson(g.toJson(m), Map::class.java) as Map<String, Any?>
    }
}
