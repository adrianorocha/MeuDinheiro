package com.meudinheiro.funcoes

import com.meudinheiro.storage.InfoSync
import com.meudinheiro.storage.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos da tela de boot: cada um reflete uma checagem real (ver DiagnosticoInicial). */
class DiagnosticoInicialTest {

    @Test fun `formatar completa com pontos ate a largura do rotulo`() {
        assertEquals("CONTAS.................. [3]", DiagnosticoInicial.formatar("CONTAS", "3"))
        assertEquals("ROTULO-MUITO-LONGO-DEMAIS-AQUI [OK]", DiagnosticoInicial.formatar("ROTULO-MUITO-LONGO-DEMAIS-AQUI", "OK"))
    }

    @Test fun `memoria em GB com virgula e aviso de pouca memoria`() {
        assertEquals("1,8 GB LIVRES" to false, DiagnosticoInicial.memoria(1_840_000_000, false))
        val (texto, aviso) = DiagnosticoInicial.memoria(120_000_000, true)
        assertEquals("BAIXA · 0,1 GB LIVRES", texto)
        assertTrue(aviso)
    }

    @Test fun `nuvem - local, sem login, erro, sem rede, pendencias e sincronizado`() {
        assertEquals("LOCAL" to false, DiagnosticoInicial.nuvem(InfoSync()))
        assertEquals("SEM LOGIN" to true, DiagnosticoInicial.nuvem(InfoSync(nuvem = true, status = SyncStatus.AguardandoLogin)))
        assertEquals("ERRO" to true, DiagnosticoInicial.nuvem(InfoSync(nuvem = true, status = SyncStatus.Erro("x"))))
        assertEquals("SEM REDE" to true, DiagnosticoInicial.nuvem(InfoSync(nuvem = true, status = SyncStatus.Conectando, rede = false)))
        assertEquals("CONECTANDO" to false, DiagnosticoInicial.nuvem(InfoSync(nuvem = true, status = SyncStatus.Conectando)))
        assertEquals("21 A ENVIAR" to false, DiagnosticoInicial.nuvem(InfoSync(nuvem = true, status = SyncStatus.Sincronizado(1), pendentes = 21)))
        assertEquals("2 EM QUARENTENA" to true, DiagnosticoInicial.nuvem(InfoSync(nuvem = true, status = SyncStatus.Sincronizado(1), quarentena = 2)))
        assertEquals("SINCRONIZADO" to false, DiagnosticoInicial.nuvem(InfoSync(nuvem = true, status = SyncStatus.Sincronizado(1))))
    }

    @Test fun `acesso e resumo`() {
        assertEquals("SEM CADASTRO" to false, DiagnosticoInicial.acesso(false, true))
        assertEquals("BIOMETRIA" to false, DiagnosticoInicial.acesso(true, true))
        assertEquals("SENHA" to false, DiagnosticoInicial.acesso(true, false))
        assertEquals("SISTEMA PRONTO ⚡" to false, DiagnosticoInicial.resumo(0))
        assertEquals("PRONTO COM 1 AVISO" to true, DiagnosticoInicial.resumo(1))
        assertEquals("PRONTO COM 3 AVISOS", DiagnosticoInicial.resumo(3).first)
        assertFalse(DiagnosticoInicial.resumo(0).second)
    }
}
