package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Date

class TrilhaAlteracoesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun d(id: Long = 1, desc: String = "Mercado", valor: Double = 10.0, pago: Boolean = false) = Despesa(
        id = id, descricao = desc, valor = valor, data = Date(1_760_000_000_000), categoria = "Alimentação", conta = "111", pic = "p",
        tipo = TipoDespesa.DEBITO, mes = 10, ano = 2025, pago = pago
    )

    @Test fun `limite de 2000 descarta as mais antigas`() {
        val t = TrilhaArquivo(File(tmp.root, "t/a.jsonl"))
        (1..2005).forEach { t.registrar(EntradaTrilha(it.toLong(), AcaoTrilha.CRIAR, it.toLong(), "d$it", 1.0)) }
        val lida = t.ler()
        assertEquals(2000, lida.size)
        assertEquals(2005L, lida.first().quando) // mais recente primeiro
        assertEquals(6L, lida.last().quando)
    }

    @Test fun `falha de log nao propaga`() {
        val arquivoComum = tmp.newFile("bloqueio")
        val t = TrilhaArquivo(File(arquivoComum, "filho/a.jsonl")) // o pai é um arquivo: não dá para criar a pasta
        assertFalse(t.registrarSeguro(EntradaTrilha(1, AcaoTrilha.CRIAR)))
        assertTrue(t.ler().isEmpty())
    }

    @Test fun `editar registra campos com antigo e novo`() {
        val e = Trilha.editar(5, d(valor = 10.0), d(desc = "Mercado X", valor = 12.5, pago = true))
        assertEquals(AcaoTrilha.EDITAR, e.acao)
        assertEquals(setOf("descricao", "valor", "pago"), e.alteracoes.map { it.campo }.toSet())
        val v = e.alteracoes.first { it.campo == "valor" }
        assertEquals("10.0", v.antigo); assertEquals("12.5", v.novo)
        assertTrue(Trilha.diferencas(d(), d()).isEmpty())
    }

    @Test fun `filtro por acao periodo e busca`() {
        val l = listOf(
            Trilha.criar(100, d(1, "Pizza")), Trilha.excluir(200, d(2, "Cinema")), Trilha.pago(300, d(3, "Luz"), true),
            Trilha.editar(400, d(4, "Aluguel", 10.0), d(4, "Aluguel", 20.0))
        )
        assertEquals(1, Trilha.filtrar(l, acao = AcaoTrilha.EXCLUIR).size)
        assertEquals(2, Trilha.filtrar(l, de = 300).size)
        assertEquals(1, Trilha.filtrar(l, de = 100, ate = 150).size)
        assertEquals("Pizza", Trilha.filtrar(l, busca = " PIZZ ").single().descricao)
        assertEquals(1, Trilha.filtrar(l, busca = "valor").size) // por nome de campo alterado
    }

    @Test fun `linha corrompida e ignorada`() {
        val f = File(tmp.root, "x.jsonl")
        f.writeText("lixo{{\n")
        val t = TrilhaArquivo(f)
        t.registrar(EntradaTrilha(1, AcaoTrilha.CRIAR, 1, "ok"))
        assertEquals(1, t.ler().size)
    }
}
