package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Projeto
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R56 — MESMOS cenários de portal/src/lib/finance/tags.test.ts. */
class TagsProjetosTest {

    private fun ms(ano: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private fun d(
        id: Long, valor: Double = 10.0, tags: List<String> = emptyList(), categoria: String = "Casa", data: Long = ms(2025, 3, 10),
        tipo: TipoDespesa = TipoDespesa.DEBITO, cartaoId: Int? = null, natureza: String = Natureza.NORMAL
    ) = Despesa(
        id = id, descricao = "Item", valor = valor, data = Date(data), categoria = categoria, conta = "111", pic = "p", tipo = tipo,
        mes = Financas.mesDe(data), ano = Financas.anoDe(data), cartaoId = cartaoId, pago = true, natureza = natureza, tags = tags
    )

    @Test fun `normaliza tags - limpa, deduplica pela chave, limita a 5 e a 30 caracteres`() {
        assertEquals(
            listOf("Viagem", "Reforma Casa", "ÁRVORE", "a", "b"),
            Tags.normalizar(listOf("  #Viagem ", "viagem", "Reforma  Casa", "ÁRVORE", "a", "b", "c"))
        )
        assertEquals(30, Tags.rotulo("x".repeat(40)).length)
        assertEquals(emptyList<String>(), Tags.normalizar(listOf("", "#", "   ")))
        assertEquals(emptyList<String>(), Tags.normalizar(null))
    }

    @Test fun `chave minuscula sem acento e sem hash`() {
        assertEquals("cafe bom", Tags.chave("#Café  Bom"))
        assertTrue(Tags.tem(listOf("Árvore"), "#ARVORE"))
        assertFalse(Tags.tem(emptyList(), "a"))
    }

    @Test fun `tags usadas por uso`() {
        val u = Tags.usadas(listOf(d(1, tags = listOf("Viagem", "Hotel")), d(2, tags = listOf("viagem")), d(3)))
        assertEquals(listOf(Triple("viagem", "Viagem", 2), Triple("hotel", "Hotel", 1)), u.map { Triple(it.chave, it.rotulo, it.usos) })
    }

    private val despesas = listOf(
        d(1, 100.0, listOf("Reforma"), data = ms(2025, 3, 10)),
        d(2, 50.5, listOf("reforma", "extra"), data = ms(2025, 4, 5)),
        d(3, 10.0, listOf("REFORMA"), data = ms(2025, 4, 6), tipo = TipoDespesa.CREDITO, cartaoId = 10), // estorno abate
        d(4, 77.0, listOf("reforma"), data = ms(2025, 4, 7), tipo = TipoDespesa.CREDITO),                 // receita comum: fora
        d(5, 33.0, listOf("reforma"), data = ms(2025, 4, 8), natureza = Natureza.TRANSFERENCIA),           // interno: fora
        d(6, 999.0, listOf("viagem")),
        d(7, 5.0)
    )

    private fun proj(orcamento: Double? = null, inicio: Long? = null, fim: Long? = null) =
        Projeto(id = 1, nome = "Reforma", tag = "reforma", orcamento = orcamento, inicio = inicio, fim = fim)

    @Test fun `resumo soma NORMAL com a tag, estorno abate, por categoria e por mes`() {
        val r = Projetos.resumo(proj(orcamento = 200.0), despesas)
        assertEquals(14_050L, r.gastoCentavos)
        assertEquals(3, r.quantidade)
        assertEquals(listOf("Casa" to 14_050L), r.porCategoria)
        assertEquals(listOf("2025-03" to 10_000L, "2025-04" to 4_050L), r.porMes)
        assertEquals(listOf(3L, 2L, 1L), r.itens.map { it.id })
        assertEquals(0.7025, r.percentual!!, 0.0001)
        assertEquals(StatusProjeto.NO_ORCAMENTO, r.status)
    }

    @Test fun `status do orcamento`() {
        assertEquals(StatusProjeto.ATENCAO, Projetos.resumo(proj(orcamento = 150.0), despesas).status)
        assertEquals(StatusProjeto.ESTOUROU, Projetos.resumo(proj(orcamento = 100.0), despesas).status)
        assertEquals(StatusProjeto.SEM_ORCAMENTO, Projetos.resumo(proj(orcamento = null), despesas).status)
        assertNull(Projetos.resumo(proj(), despesas).percentual)
    }

    @Test fun `periodo do projeto restringe os lancamentos`() {
        val r = Projetos.resumo(proj(inicio = ms(2025, 4, 1), fim = ms(2025, 4, 30)), despesas)
        assertEquals(4_050L, r.gastoCentavos)
        assertEquals(2, r.quantidade)
    }

    @Test fun `validacao de projeto`() {
        assertEquals("Informe o nome do projeto.", Projetos.validar(" ", "a", null, null, null, emptyList()))
        assertEquals("Informe a tag do projeto.", Projetos.validar("A", "#", null, null, null, emptyList()))
        val outros = listOf(Projeto(id = 9, nome = "X", tag = "reforma"))
        assertNotNull(Projetos.validar("A", "Reforma", null, null, null, outros))
        assertNull(Projetos.validar("A", "Reforma", null, null, null, outros, idAtual = 9))
        assertNotNull(Projetos.validar("A", "x", null, 10L, 5L, emptyList()))
    }

    @Test fun `relatorio sem filtro de tag nao muda - com tag filtra por qualquer uma e agrupa`() {
        val base = listOf(
            d(1, 100.0, listOf("viagem", "hotel")),
            d(2, 50.0, listOf("Viagem")),
            d(3, 30.0)
        )
        val f = FiltroRelatorio(inicio = ms(2025, 3, 1), fim = ms(2025, 3, 31), tipo = TipoRelatorio.DESPESA)
        val sem = Relatorios.gerar(f, base)
        assertEquals(180.0, sem.total, 0.0)
        assertEquals(sem, Relatorios.gerar(f.copy(tags = emptySet()), base))
        assertEquals(150.0, Relatorios.gerar(f.copy(tags = setOf("#VIAGEM")), base).total, 0.0)
        assertEquals(100.0, Relatorios.gerar(f.copy(tags = setOf("hotel")), base).total, 0.0)
        val g = Relatorios.totaisPorTag(sem)
        assertEquals(listOf(Triple("viagem", 15_000L, 2), Triple("hotel", 10_000L, 1), Triple("", 3_000L, 1)), g.map { Triple(it.tag, it.total, it.quantidade) })
        assertEquals("Sem tag", g.last().rotulo)
    }
}
