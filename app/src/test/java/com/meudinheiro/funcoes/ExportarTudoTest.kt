package com.meudinheiro.funcoes

import com.meudinheiro.data.BackupDto
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.zip.ZipInputStream

class ExportarTudoTest {
    private val fuso = TimeZone.getTimeZone("America/Sao_Paulo")

    @Test fun `escape de ponto e virgula aspas e quebra de linha`() {
        assertEquals("a", ExportarTudo.campo("a"))
        assertEquals("\"a;b\"", ExportarTudo.campo("a;b"))
        assertEquals("\"diz \"\"oi\"\"\"", ExportarTudo.campo("diz \"oi\""))
        assertEquals("\"l1\nl2\"", ExportarTudo.campo("l1\nl2"))
        assertEquals("\"x\r\ny\"", ExportarTudo.campo("x\r\ny"))
        assertEquals("1234,50", ExportarTudo.campo(1234.5))
        assertEquals("sim", ExportarTudo.campo(true))
        assertEquals("", ExportarTudo.campo(null))
    }

    @Test fun `csv tem BOM separador ponto e virgula e data dd mm aaaa`() {
        val ms = Calendar.getInstance(fuso).apply { clear(); set(2026, Calendar.MARCH, 5, 12, 0) }.timeInMillis
        val csv = ExportarTudo.csv(listOf("data", "valor"), listOf(listOf(Date(ms), 10.5)), fuso)
        assertTrue(csv.startsWith("﻿data;valor\r\n"))
        assertTrue(csv.contains("05/03/2026;10,50\r\n"))
    }

    @Test fun `zip contem um csv por colecao legivel pelo diretorio central`() {
        val b = BackupDto(
            contas = listOf(ContaSaldo(1, 99.9, "Banco; \"X\"", "p", "1", "111", "Eu")),
            despesas = listOf(Despesa(7, "Linha1\nLinha2", 12.0, Date(1_760_000_000_000), "Cat", "111", "p", TipoDespesa.DEBITO, 10, 2025))
        )
        val bytes = ExportarTudo.zipDoBackup(b, fuso)
        val itens = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) { itens[e.name] = z.readBytes().toString(Charsets.UTF_8); e = z.nextEntry }
        }
        assertTrue(itens.keys.containsAll(listOf("despesas.csv", "contas.csv", "cartoes.csv", "categorias.csv", "orcamentos.csv", "metas.csv",
            "investimentos.csv", "recorrencias.csv", "dividas.csv", "pessoas.csv", "acertos.csv", "projetos.csv", "regras.csv")))
        assertTrue(itens.getValue("contas.csv").contains("\"Banco; \"\"X\"\"\""))
        assertTrue(itens.getValue("despesas.csv").contains("\"Linha1\nLinha2\""))
        assertTrue(itens.values.all { it.startsWith("﻿") })
    }
}
