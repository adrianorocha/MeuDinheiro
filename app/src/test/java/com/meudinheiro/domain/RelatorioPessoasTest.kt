package com.meudinheiro.domain

import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Pessoa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.RelatorioPessoas.Agrupar
import com.meudinheiro.domain.RelatorioPessoas.Atalho
import com.meudinheiro.domain.RelatorioPessoas.Opcoes
import com.meudinheiro.domain.RelatorioPessoas.OrigemTipo
import com.meudinheiro.domain.RelatorioPessoas.Sentido
import com.meudinheiro.domain.RelatorioPessoas.Situacao
import com.meudinheiro.domain.RelatorioPessoas.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R69 — MESMOS cenários e números de portal/src/lib/finance/relatorioPessoas.test.ts. */
class RelatorioPessoasTest {

    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis

    private val agora = ms(2026, 9, 30, 15)
    private val pessoas = listOf(Pessoa(1, "Ana", "11 99999-0000"), Pessoa(2, "Bruno", null), Pessoa(3, "Carla", null))

    private fun desp(id: Long, descricao: String, valor: Double, data: Long) = Despesa(
        id = id, descricao = descricao, valor = valor, data = Date(data), categoria = "Mercado", conta = "1", pic = "p",
        tipo = TipoDespesa.DEBITO, mes = 1, ano = 2026
    )

    private val despesas = listOf(desp(100, "Jantar", 120.0, ms(2026, 9, 5)), desp(101, "Mercado", 80.0, ms(2026, 9, 12)))

    private fun ac(
        id: Int, pessoaId: Int, tipo: String, valor: Double, descricao: String, data: Long,
        despesaId: Long? = null, grupo: String? = null, quitado: Boolean = false, quitadoEm: Long? = null
    ) = Acerto(id, pessoaId, tipo, valor, descricao, data, despesaId, quitado, quitadoEm, grupo)

    private val acertos = listOf(
        ac(1, 1, "A_RECEBER", 40.0, "Jantar", ms(2026, 9, 5), 100, "rateio:100"),
        ac(2, 2, "A_RECEBER", 40.0, "Jantar", ms(2026, 9, 5), 100, "rateio:100", quitado = true, quitadoEm = ms(2026, 9, 20)),
        ac(3, 1, "A_PAGAR", 15.5, "Café", ms(2026, 9, 10)),
        ac(4, 2, "A_PAGAR", 100.0, "Empréstimo", ms(2026, 8, 20)),
        ac(5, 1, "A_RECEBER", 20.0, "Mercado", ms(2026, 9, 12), 101, "vinculo:101"),
        ac(6, 2, "A_RECEBER", 10.0, "Antigo", ms(2026, 9, 15), 999, "rateio:999")
    )

    private fun gerar(
        pessoaIds: Set<Int> = emptySet(), periodo: LongRange? = null, situacao: Situacao = Situacao.TODOS, detalhes: Boolean = true,
        agrupar: Agrupar = Agrupar.POR_PESSOA, rateios: Boolean = false
    ) = RelatorioPessoas.montar(pessoas, acertos, despesas, Opcoes(pessoaIds, periodo, situacao, detalhes, agrupar, rateios, agora))

    private val setembro = RelatorioPessoas.periodoPorAtalho(Atalho.ESTE_MES, agora)

    @Test fun `todas as pessoas sem filtro - subtotais, sentido e pessoa sem acertos omitida`() {
        val r = gerar()
        assertTrue(!r.vazio)
        assertEquals(listOf("Ana", "Bruno"), r.pessoas.map { it.nome })
        val (ana, bruno) = r.pessoas
        assertEquals(listOf(6000L, 1550L, 4450L, 3L), listOf(ana.aReceber, ana.aPagar, ana.liquido, ana.nAcertos.toLong()))
        assertEquals("te deve R\$ 44,50", ana.saldoTexto)
        assertEquals(Sentido.TE_DEVE, ana.sentido)
        assertEquals("11 99999-0000", ana.contato)
        assertEquals(listOf(1000L, 10000L, -9000L, 4000L, 3L), listOf(bruno.aReceber, bruno.aPagar, bruno.liquido, bruno.quitadoAReceber, bruno.nAcertos.toLong()))
        assertEquals("você deve R\$ 90,00", bruno.saldoTexto)
        assertNull(bruno.contato)
        assertEquals(RelatorioPessoas.Resumo(7000, 11550, -4550, 4000, 0, 6, 2, 2), r.resumo)
        assertEquals("Todo o período", r.periodoTexto)
        assertEquals("30/09/2026 15:00", r.emitidoEm)
    }

    @Test fun `subtotais das pessoas fecham com o resumo e com as linhas em qualquer filtro`() {
        val filtros = listOf(
            gerar(), gerar(periodo = setembro), gerar(situacao = Situacao.PENDENTES), gerar(situacao = Situacao.QUITADOS), gerar(pessoaIds = setOf(2))
        )
        for (r in filtros) {
            assertEquals(r.resumo.aReceberPendente, r.pessoas.sumOf { it.aReceber })
            assertEquals(r.resumo.aPagarPendente, r.pessoas.sumOf { it.aPagar })
            assertEquals(r.resumo.saldoLiquido, r.pessoas.sumOf { it.liquido })
            assertEquals(r.resumo.nAcertos, r.pessoas.sumOf { it.nAcertos })
            for (p in r.pessoas) {
                fun s(tipo: String, st: Status) = p.linhas.filter { it.tipo == tipo && it.status == st }.sumOf { it.centavos }
                assertEquals(p.aReceber, s("A_RECEBER", Status.PENDENTE))
                assertEquals(p.aPagar, s("A_PAGAR", Status.PENDENTE))
                assertEquals(p.quitadoAReceber, s("A_RECEBER", Status.QUITADO))
                assertEquals(p.quitadoAPagar, s("A_PAGAR", Status.QUITADO))
            }
        }
    }

    @Test fun `periodo inclusivo e atalhos`() {
        fun par(a: Atalho) = RelatorioPessoas.periodoPorAtalho(a, agora)!!.let { RelatorioPessoas.dataBR(it.first) to RelatorioPessoas.dataBR(it.last) }
        assertEquals("01/09/2026" to "30/09/2026", par(Atalho.ESTE_MES))
        assertEquals("01/08/2026" to "31/08/2026", par(Atalho.MES_PASSADO))
        assertEquals("01/07/2026" to "30/09/2026", par(Atalho.TRES_MESES))
        assertEquals("01/01/2026" to "31/12/2026", par(Atalho.ANO))
        assertNull(RelatorioPessoas.periodoPorAtalho(Atalho.TUDO, agora))

        val r = gerar(periodo = setembro)
        assertEquals("01/09/2026 a 30/09/2026", r.periodoTexto)
        assertEquals(RelatorioPessoas.Resumo(7000, 1550, 5450, 4000, 0, 5, 2, 2), r.resumo)
        val bruno = r.pessoas[1]
        assertEquals(listOf(0L, 1000L, 2L), listOf(bruno.aPagar, bruno.liquido, bruno.nAcertos.toLong()))
        assertEquals(2, gerar(periodo = ms(2026, 9, 5)..ms(2026, 9, 5)).resumo.nAcertos)
    }

    @Test fun `situacao pendentes quitados e todos`() {
        val p = gerar(situacao = Situacao.PENDENTES)
        assertEquals(RelatorioPessoas.Resumo(7000, 11550, -4550, 0, 0, 5, 2, 2), p.resumo)
        val q = gerar(situacao = Situacao.QUITADOS)
        assertEquals(listOf("Bruno"), q.pessoas.map { it.nome })
        assertEquals(RelatorioPessoas.Resumo(0, 0, 0, 4000, 0, 1, 0, 1), q.resumo)
        assertEquals("quites", q.pessoas[0].saldoTexto)
        assertEquals(Sentido.QUITES, q.pessoas[0].sentido)
        assertTrue("Situação: Somente quitados" in q.filtros)
    }

    @Test fun `escolher pessoas, pessoa sem acertos omitida e nada a mostrar = vazio`() {
        val r = gerar(pessoaIds = setOf(2))
        assertEquals(listOf("Bruno"), r.pessoas.map { it.nome })
        assertEquals("Pessoas: Bruno", r.filtros[0])
        assertEquals("Pessoas: todas", gerar().filtros[0])
        val v = gerar(pessoaIds = setOf(3))
        assertTrue(v.vazio)
        assertTrue(v.pessoas.isEmpty())
        assertEquals(0, v.resumo.nAcertos)
        assertTrue(gerar(periodo = ms(2020, 1, 1)..ms(2020, 1, 31)).vazio)
        assertTrue(RelatorioPessoas.montar(emptyList(), emptyList(), emptyList(), Opcoes(agora = agora)).vazio)
    }

    @Test fun `linhas detalhadas - ordem por data, origem, status, rateio e orfao`() {
        val (ana, bruno) = gerar().pessoas
        assertEquals(listOf(1, 3, 5), ana.linhas.map { it.acertoId })
        val l0 = ana.linhas[0]
        assertEquals("05/09/2026", l0.dataTexto)
        assertEquals("lançamento de 05/09 — Jantar", l0.origem)
        assertEquals(OrigemTipo.LANCAMENTO, l0.origemTipo)
        assertEquals("A receber", l0.tipoTexto)
        assertEquals(4000L, l0.centavos)
        assertEquals(Status.PENDENTE, l0.status)
        assertEquals("Pendente", l0.statusTexto)
        assertEquals(RelatorioPessoas.RateioLinha(12000, 4000, "Rateio: despesa R\$ 120,00 · cota R\$ 40,00"), l0.rateio)
        assertEquals("avulso", ana.linhas[1].origem)
        assertEquals(OrigemTipo.AVULSO, ana.linhas[1].origemTipo)
        assertEquals("A pagar", ana.linhas[1].tipoTexto)
        assertEquals(1550L, ana.linhas[1].centavos)
        assertNull(ana.linhas[1].rateio)
        assertEquals("Vinculado: despesa R\$ 80,00 · cota R\$ 20,00", ana.linhas[2].rateio?.texto)
        assertEquals(listOf(4, 2, 6), bruno.linhas.map { it.acertoId })
        assertEquals(Status.QUITADO, bruno.linhas[1].status)
        assertEquals("Quitado em 20/09", bruno.linhas[1].statusTexto)
        assertEquals("lançamento removido", bruno.linhas[2].origem)
        assertEquals(OrigemTipo.REMOVIDO, bruno.linhas[2].origemTipo)
        assertNull(bruno.linhas[2].rateio)
    }

    @Test fun `agrupar cronologico e sem detalhes`() {
        val c = gerar(agrupar = Agrupar.CRONOLOGICO)
        assertEquals(listOf(4, 1, 2, 3, 5, 6), c.cronologico.map { it.acertoId })
        assertEquals("Bruno", c.cronologico[0].pessoaNome)
        assertTrue(gerar().cronologico.isEmpty())
        val s = gerar(detalhes = false, agrupar = Agrupar.CRONOLOGICO)
        assertTrue(s.cronologico.isEmpty())
        assertTrue(s.pessoas.all { it.linhas.isEmpty() })
        assertEquals(6, s.resumo.nAcertos)
        assertTrue("Detalhes: apenas totais" in s.filtros)
    }

    @Test fun `bloco Rateios - quanto voce realmente gastou`() {
        assertNull(gerar().rateios)
        val r = gerar(rateios = true).rateios
        assertNotNull(r)
        assertEquals(listOf(999L, 101L, 100L), r!!.itens.map { it.despesaId })
        assertEquals(listOf(20000L, 10000L, 10000L), listOf(r.totalRateado, r.aReceberTerceiros, r.liquido))
        val d100 = r.itens.first { it.despesaId == 100L }
        assertEquals(listOf(12000L, 8000L, 4000L, 4000L), listOf(d100.total, d100.aReceber, d100.liquido, d100.pendente))
        assertTrue(r.itens.first { it.despesaId == 999L }.orfao)
        val a = gerar(pessoaIds = setOf(1), rateios = true).rateios!!
        assertEquals(listOf(101L, 100L), a.itens.map { it.despesaId })
        assertEquals(listOf(20000L, 10000L, 10000L), listOf(a.totalRateado, a.aReceberTerceiros, a.liquido))
        assertNull(gerar(pessoaIds = setOf(1), situacao = Situacao.PENDENTES, periodo = ms(2026, 9, 14)..ms(2026, 9, 30), rateios = true).rateios)
    }

    @Test fun `avatar estavel - iniciais e cor derivadas do nome`() {
        assertEquals("A", RelatorioPessoas.iniciaisDoNome("Ana"))
        assertEquals("AS", RelatorioPessoas.iniciaisDoNome("  ana maria souza "))
        assertEquals("?", RelatorioPessoas.iniciaisDoNome(""))
        assertEquals(5, RelatorioPessoas.corIndiceDaPessoa("Ana"))
        assertEquals(7, RelatorioPessoas.corIndiceDaPessoa("Bruno"))
        assertEquals(listOf("A" to 5, "B" to 7), gerar().pessoas.map { it.iniciais to it.corIndice })
    }

    @Test fun `textos de saldo e nome do arquivo`() {
        assertEquals(Sentido.TE_DEVE to "te deve R\$ 1.234,56", RelatorioPessoas.saldoTexto(123456))
        assertEquals(Sentido.VOCE_DEVE to "você deve R\$ 0,05", RelatorioPessoas.saldoTexto(-5))
        assertEquals("quites", RelatorioPessoas.saldoTexto(0).second)
        assertEquals("pessoas_2026-09-30.pdf", RelatorioPessoas.nomeArquivo(agora, "pdf"))
        assertEquals("pessoas_2026-09-30-2.png", RelatorioPessoas.nomeArquivo(agora, "png", 2))
    }
}
