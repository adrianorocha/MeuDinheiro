package com.meudinheiro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificacoesTest {

    // ---- decisão de registrar o token (opt-in + logado + nuvem)
    @Test
    fun registra_somente_com_as_tres_condicoes() {
        assertTrue(PushRegistro.deveRegistrar(logado = true, nuvemAtiva = true, interruptorLigado = true))
        assertFalse(PushRegistro.deveRegistrar(false, true, true))
        assertFalse(PushRegistro.deveRegistrar(true, false, true))
        assertFalse(PushRegistro.deveRegistrar(true, true, false)) // opt-in: padrão desligado
        assertFalse(PushRegistro.deveRegistrar(false, false, false))
    }

    // ---- ID estável do aparelho
    @Test
    fun id_estavel_reaproveita_o_salvo_e_so_gera_quando_invalido() {
        var geracoes = 0
        val gerar = { geracoes++; "123e4567-e89b-12d3-a456-426614174000" }
        val primeiro = PushRegistro.idEstavel(null, gerar)
        assertEquals("and-123e4567e89b12d3a456426614174000", primeiro)
        assertEquals(primeiro, PushRegistro.idEstavel(primeiro, gerar))
        assertEquals(1, geracoes)
        // lixo (caracteres inválidos para nome de documento, curto, vazio) é substituído
        assertNotEquals("a/b", PushRegistro.idEstavel("a/b", gerar))
        assertTrue(PushRegistro.idEstavel("  ", gerar).startsWith("and-"))
        assertTrue(PushRegistro.idEstavel("curto", gerar).startsWith("and-"))
    }

    // ---- documento do dispositivo
    @Test
    fun documento_do_dispositivo_tem_so_o_minimo() {
        val doc = PushRegistro.montarDoc("tok123", "Pixel 8", "1.0", PushPrefs(resumoSemanal = false), raizUid = "dono1", uid = "eu")
        assertEquals("tok123", doc["token"])
        assertEquals("android", doc["plataforma"])
        assertEquals("Pixel 8", doc["modelo"])
        assertEquals("1.0", doc["appVersao"])
        assertEquals("dono1", doc["raizUid"])
        assertEquals(mapOf("vencimentos" to true, "orcamento" to true, "resumoSemanal" to false, "convites" to true), doc["prefs"])
        assertEquals(setOf("token", "plataforma", "modelo", "appVersao", "prefs", "raizUid"), doc.keys)
    }

    @Test
    fun documento_omite_raiz_propria_e_modelo_vazio_e_recusa_token_vazio() {
        val doc = PushRegistro.montarDoc("t", " ", "1.0", PushPrefs(), raizUid = "eu", uid = "eu")
        assertFalse("raizUid" in doc)
        assertFalse("modelo" in doc)
        assertFalse("raizUid" in PushRegistro.montarDoc("t", null, "1", PushPrefs(), raizUid = "", uid = "eu"))
        val erro = runCatching { PushRegistro.montarDoc("  ", null, "1", PushPrefs(), null, "eu") }.exceptionOrNull()
        assertTrue(erro is IllegalArgumentException)
    }

    // ---- prefs
    @Test
    fun prefs_mapeiam_para_o_doc_e_voltam_tolerantes() {
        val p = PushPrefs(vencimentos = false, orcamento = true, resumoSemanal = false, convites = true)
        assertEquals(p, PushPrefs.deDoc(p.toDoc()))
        assertEquals(PushPrefs(), PushPrefs.deDoc(null))
        assertEquals(PushPrefs(), PushPrefs.deDoc(mapOf("vencimentos" to "nao")))
        assertEquals(PushPrefs(vencimentos = false), PushPrefs.deDoc(mapOf("vencimentos" to false)))
    }

    @Test
    fun prefs_por_tipo() {
        var p = PushPrefs()
        TipoPush.entries.forEach { assertTrue(p.permite(it)) }
        p = p.com(TipoPush.ORCAMENTO, false)
        assertFalse(p.permite(TipoPush.ORCAMENTO))
        assertTrue(p.permite(TipoPush.VENCIMENTOS))
    }

    // ---- textos
    @Test
    fun texto_da_tela_bloqueada_e_generico_e_sem_valores() {
        TipoPush.entries.forEach { t ->
            val publico = PushTextos.publico(t)
            assertFalse(publico.contains("R$"))
            assertFalse(publico.any { it.isDigit() })
        }
        val t = PushTextos.montar(
            mapOf("tipo" to "RESUMO_SEMANAL", "titulo" to "Resumo da semana", "entradasCentavos" to "500000", "saidasCentavos" to "123456", "topCategoria" to "Mercado")
        )!!
        assertTrue(t.corpo.contains("R$"))          // valores só na versão privada
        assertFalse(t.publico.contains("R$"))
        assertTrue(t.corpo.contains("Mercado"))
        assertEquals("Seu resumo da semana está pronto", t.publico)
    }

    @Test
    fun montar_ignora_dado_invalido() {
        assertNull(PushTextos.montar(emptyMap()))
        assertNull(PushTextos.montar(mapOf("tipo" to "QUALQUER", "titulo" to "x")))
        assertNull(PushTextos.montar(mapOf("tipo" to "VENCIMENTOS", "titulo" to "  ")))
    }

    @Test
    fun vencimentos_marca_atraso_e_orcamento_marca_estouro() {
        val v = PushTextos.montar(mapOf("tipo" to "VENCIMENTOS", "titulo" to "Contas a pagar", "corpo" to "1 vence amanhã", "atrasadas" to "2"))!!
        assertTrue(v.atrasado)
        assertFalse(PushTextos.montar(mapOf("tipo" to "VENCIMENTOS", "titulo" to "t", "atrasadas" to "0"))!!.atrasado)
        assertTrue(PushTextos.montar(mapOf("tipo" to "ORCAMENTO", "titulo" to "t", "limiar" to "100"))!!.estourado)
        assertFalse(PushTextos.montar(mapOf("tipo" to "ORCAMENTO", "titulo" to "t", "limiar" to "80"))!!.estourado)
    }
}
