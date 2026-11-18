package com.meudinheiro.funcoes

import com.meudinheiro.data.BackupDto
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.domain.TipoBackup
import com.meudinheiro.repository.BackupJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.TimeZone

class BackupArmazenamentoTest {
    @get:Rule val tmp = TemporaryFolder()
    private val fuso = TimeZone.getTimeZone("UTC")
    private val dia = 86_400_000L
    private val agora = 1_800_000_000_000L

    private fun json(contas: Int) = BackupJson.toJson(BackupDto(contas = (1..contas).map { ContaSaldo(it, 1.0, "B$it", "p", "1", "c$it", "") }))
    private fun arm() = BackupArmazenamento(File(tmp.root, "auto"), fuso)

    @Test fun `salvar grava json e metadados com contagens e tamanho`() {
        val a = arm()
        val m = a.salvar(json(3), TipoBackup.MANUAL, agora)
        assertTrue(m.arquivo.startsWith("manual_") && m.arquivo.endsWith(".json"))
        assertEquals(3, m.contagens["contas"]); assertEquals(0, m.contagens["lancamentos"])
        assertEquals(BackupJson.VERSAO_ATUAL, m.versaoSchema)
        assertEquals(a.arquivo(m).length(), m.tamanho)
        assertTrue(File(tmp.root, "auto/${m.arquivo}.meta.json").isFile)
        assertEquals(m, a.listar().single())
        // mesmo minuto: o nome não colide
        val m2 = a.salvar(json(1), TipoBackup.DIARIO, agora)
        assertTrue(m2.arquivo.startsWith("auto_"))
        assertEquals(2, a.listar().size)
    }

    @Test fun `retencao aplicada na pasta preserva manuais`() {
        val a = arm()
        repeat(9) { a.salvar(json(1), TipoBackup.DIARIO, agora - it * dia) }
        val manual = a.salvar(json(1), TipoBackup.MANUAL, agora - 200 * dia)
        val apagados = a.aplicarRetencao(agora)
        assertEquals(2, apagados.size)
        val restantes = a.listar()
        assertEquals(8, restantes.size)
        assertTrue(restantes.any { it.arquivo == manual.arquivo })
        assertFalse(File(tmp.root, "auto/${apagados[0].arquivo}").exists())
        assertFalse(File(tmp.root, "auto/${apagados[0].arquivo}.meta.json").exists())
    }

    @Test fun `restaurar cria backup de seguranca do estado atual antes`() = runBlocking {
        val a = arm()
        val ordem = mutableListOf<String>()
        val seg = BackupArmazenamento.restaurarComSeguranca(
            a, json(2), gerarAtual = { ordem += "gerar"; json(5) },
            restaurar = { ordem += "restaurar"; assertEquals(1, a.listar().size) /* a cópia já existe */ }, agora = agora
        )
        assertEquals(listOf("gerar", "restaurar"), ordem)
        assertEquals(TipoBackup.SEGURANCA, seg.tipo)
        assertEquals(5, seg.contagens["contas"])
    }

    @Test fun `se a copia de seguranca falha nada e restaurado`() = runBlocking {
        var restaurou = false
        try {
            BackupArmazenamento.restaurarComSeguranca(arm(), json(1), gerarAtual = { error("sem espaço") }, restaurar = { restaurou = true }, agora = agora)
            fail()
        } catch (e: IllegalStateException) { /* esperado */ }
        assertFalse(restaurou)
    }

    @Test fun `arquivo invalido nao cria seguranca nem restaura`() = runBlocking {
        val a = arm()
        var restaurou = false
        try {
            BackupArmazenamento.restaurarComSeguranca(a, "{{nao e json", gerarAtual = { json(1) }, restaurar = { restaurou = true }, agora = agora)
            fail()
        } catch (e: IllegalArgumentException) { /* esperado */ }
        assertFalse(restaurou); assertTrue(a.listar().isEmpty())
    }
}
