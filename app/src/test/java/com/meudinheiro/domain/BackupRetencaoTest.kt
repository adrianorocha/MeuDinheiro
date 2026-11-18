package com.meudinheiro.domain

import com.meudinheiro.data.BackupDto
import com.meudinheiro.data.ContaSaldo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BackupRetencaoTest {
    private val dia = 86_400_000L
    private val agora = 1_800_000_000_000L

    private fun meta(nome: String, diasAtras: Int, tipo: TipoBackup) =
        MetaBackup(nome, agora - diasAtras * dia, 2, emptyMap(), 10, tipo)

    @Test fun `mantem 7 diarios e 4 semanais e apaga o excedente`() {
        val lista = (0 until 10).map { meta("d$it", it, TipoBackup.DIARIO) } + (0 until 6).map { meta("s$it", it * 7, TipoBackup.SEMANAL) }
        val r = BackupRetencao.retencao(lista, agora)
        assertEquals((0 until 7).map { "d$it" }, r.manter.filter { it.tipo == TipoBackup.DIARIO }.map { it.arquivo })
        assertEquals(setOf("d7", "d8", "d9", "s4", "s5"), r.apagar.map { it.arquivo }.toSet())
        assertEquals(4, r.manter.count { it.tipo == TipoBackup.SEMANAL })
    }

    @Test fun `nunca apaga manual e seguranca recente`() {
        val lista = (0 until 20).map { meta("m$it", 100 + it, TipoBackup.MANUAL) } +
            listOf(meta("seg-nova", 5, TipoBackup.SEGURANCA), meta("seg-limite", 30, TipoBackup.SEGURANCA), meta("seg-velha", 31, TipoBackup.SEGURANCA)) +
            (0 until 9).map { meta("d$it", it, TipoBackup.DIARIO) }
        val r = BackupRetencao.retencao(lista, agora)
        assertTrue(r.manter.containsAll(lista.filter { it.tipo == TipoBackup.MANUAL }))
        assertTrue(r.manter.any { it.arquivo == "seg-nova" } && r.manter.any { it.arquivo == "seg-limite" })
        assertEquals(setOf("seg-velha", "d7", "d8"), r.apagar.map { it.arquivo }.toSet())
    }

    @Test fun `poucos backups nada a apagar`() {
        assertTrue(BackupRetencao.retencao(listOf(meta("a", 0, TipoBackup.DIARIO)), agora).apagar.isEmpty())
        assertTrue(BackupRetencao.retencao(emptyList(), agora).manter.isEmpty())
    }

    @Test fun `domingo vira semanal`() {
        val fuso = TimeZone.getTimeZone("America/Sao_Paulo")
        fun ms(dia: Int) = Calendar.getInstance(fuso).apply { clear(); set(2026, Calendar.OCTOBER, dia, 3, 0) }.timeInMillis
        assertEquals(TipoBackup.SEMANAL, BackupRetencao.tipoAutomatico(ms(11), fuso)) // 11/10/2026 = domingo
        assertEquals(TipoBackup.DIARIO, BackupRetencao.tipoAutomatico(ms(12), fuso))
    }

    @Test fun `contagens por colecao`() {
        val c = BackupRetencao.contagens(BackupDto(contas = listOf(ContaSaldo(1, 0.0, "b", "p", "1", "1", "")), despesas = null))
        assertEquals(1, c["contas"]); assertEquals(0, c["lancamentos"]); assertEquals(14, c.size)
        assertEquals("1 contas", BackupRetencao.resumo(c))
        assertFalse(BackupRetencao.linhas(c).isEmpty())
    }
}
