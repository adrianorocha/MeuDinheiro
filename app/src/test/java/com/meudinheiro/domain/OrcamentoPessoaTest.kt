package com.meudinheiro.domain

import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.EscopoOrcamento
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.storage.FirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** Orçamento por pessoa: gasto conforme o escopo (TODOS / EU / PESSOA), alertas por escopo e mapper. */
class OrcamentoPessoaTest {

    private fun ms(ano: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private val ini = Financas.inicioDoMes(10, 2026)
    private val fim = Financas.fimDoMes(10, 2026)

    private fun lanc(
        id: Long, valor: Double, tipo: TipoDespesa = TipoDespesa.DEBITO, categoria: String = "Combustível",
        cartaoId: Int? = null, data: Long = ms(2026, 10, 10)
    ) = Despesa(
        id = id, descricao = "x", valor = valor, data = Date(data), categoria = categoria, conta = "111", pic = "p",
        tipo = tipo, mes = 10, ano = 2026, cartaoId = cartaoId, pago = true, natureza = Natureza.NORMAL
    )

    private fun acerto(despesaId: Long?, pessoaId: Int, valor: Double, tipo: String = "A_RECEBER", quitado: Boolean = false) =
        Acerto(pessoaId = pessoaId, tipo = tipo, valor = valor, descricao = "a", data = 1L, despesaId = despesaId, quitado = quitado)

    private fun gasto(l: List<Despesa>, escopo: String?, pessoaId: Int?, acertos: List<Acerto>) =
        Financas.progressoOrcamento("Combustível", 1000.0, l, ini, fim, escopo, pessoaId, acertos).gasto

    @Test fun `TODOS fica inalterado mesmo com acertos`() {
        val l = listOf(lanc(1, 100.0), lanc(2, 50.0))
        val a = listOf(acerto(1, 7, 60.0))
        assertEquals(150.0, gasto(l, null, null, a), 0.0)
        assertEquals(150.0, gasto(l, null, null, emptyList()), 0.0)
        // escopo desconhecido / PESSOA sem pessoa cai em TODOS
        assertEquals(150.0, gasto(l, "XYZ", null, a), 0.0)
        assertEquals(150.0, gasto(l, EscopoOrcamento.PESSOA, null, a), 0.0)
    }

    @Test fun `EU desconta parcela A_RECEBER inclusive rateio parcial e acerto quitado`() {
        val l = listOf(lanc(1, 100.0), lanc(2, 80.0), lanc(3, 30.0))
        val a = listOf(
            acerto(1, 7, 30.0),                    // parcial
            acerto(1, 8, 20.0, quitado = true),    // quitado também desconta
            acerto(2, 7, 80.0),                    // tudo de terceiros
            acerto(3, 7, 10.0, tipo = "A_PAGAR")   // A_PAGAR não desconta
        )
        // meu(1)=50, meu(2)=0, meu(3)=30
        assertEquals(80.0, gasto(l, EscopoOrcamento.EU, null, a), 0.0)
    }

    @Test fun `PESSOA soma so os acertos dela`() {
        val l = listOf(lanc(1, 100.0), lanc(2, 80.0))
        val a = listOf(acerto(1, 7, 30.0), acerto(1, 8, 20.0), acerto(2, 7, 25.5), acerto(2, 7, 5.0, quitado = true), acerto(2, 7, 9.0, tipo = "A_PAGAR"))
        assertEquals(60.5, gasto(l, EscopoOrcamento.PESSOA, 7, a), 0.0)
        assertEquals(20.0, gasto(l, EscopoOrcamento.PESSOA, 8, a), 0.0)
        assertEquals(0.0, gasto(l, EscopoOrcamento.PESSOA, 99, a), 0.0)
    }

    @Test fun `acerto e limitado ao valor do lancamento`() {
        val l = listOf(lanc(1, 100.0))
        val a = listOf(acerto(1, 7, 70.0), acerto(1, 8, 70.0))
        assertEquals(0.0, gasto(l, EscopoOrcamento.EU, null, a), 0.0)          // terceiros = min(140, 100)
        assertEquals(70.0, gasto(l, EscopoOrcamento.PESSOA, 7, a), 0.0)
        assertEquals(100.0, gasto(l, EscopoOrcamento.PESSOA, 7, listOf(acerto(1, 7, 250.0))), 0.0)
    }

    @Test fun `estorno de cartao abate no EU e nao entra no PESSOA`() {
        val l = listOf(lanc(1, 100.0, cartaoId = 1), lanc(2, 20.0, TipoDespesa.CREDITO, cartaoId = 1), lanc(3, 500.0, TipoDespesa.CREDITO))
        val a = listOf(acerto(1, 7, 40.0))
        assertEquals(40.0, gasto(l, EscopoOrcamento.EU, null, a), 0.0)          // 60 - 20
        assertEquals(40.0, gasto(l, EscopoOrcamento.PESSOA, 7, a), 0.0)
        assertEquals(80.0, gasto(l, null, null, a), 0.0)                        // TODOS: 100 - 20
    }

    @Test fun `ignora outro mes, outra categoria e acertos orfaos ou avulsos`() {
        val l = listOf(lanc(1, 100.0), lanc(2, 100.0, data = ms(2026, 9, 30)), lanc(3, 100.0, categoria = "Lazer"))
        val a = listOf(acerto(2, 7, 50.0), acerto(null, 7, 50.0), acerto(999, 7, 50.0), acerto(3, 7, 50.0))
        assertEquals(100.0, gasto(l, EscopoOrcamento.EU, null, a), 0.0)
        assertEquals(0.0, gasto(l, EscopoOrcamento.PESSOA, 7, a), 0.0)
    }

    @Test fun `percentual e status seguem o gasto do escopo`() {
        val l = listOf(lanc(1, 100.0))
        val a = listOf(acerto(1, 7, 60.0))
        val p = Financas.progressoOrcamento("Combustível", 50.0, l, ini, fim, EscopoOrcamento.EU, null, a)
        assertEquals(40.0, p.gasto, 0.0)
        assertEquals(0.8, p.percentual, 1e-9)
        assertEquals(Financas.StatusOrcamento.ATENCAO, p.status)
    }

    @Test fun `alerta usa chave por escopo e pessoa sem colidir`() {
        val todos = Analises.ProgressoAlerta("Combustível", 0.85)
        val eu = Analises.ProgressoAlerta("Combustível", 0.85, EscopoOrcamento.EU, null, "Só eu")
        val joao = Analises.ProgressoAlerta("Combustível", 0.85, EscopoOrcamento.PESSOA, 7, "Só João")
        val maria = Analises.ProgressoAlerta("Combustível", 0.85, EscopoOrcamento.PESSOA, 8, "Só Maria")
        val quatro = Analises.alertasOrcamentoEscopo(listOf(todos, eu, joao, maria), "2026-10", emptySet())
        assertEquals(4, quatro.size)
        assertEquals(4, quatro.map { it.chave }.toSet().size)
        assertEquals("combustivel|2026-10|80", quatro[0].chave) // TODOS mantém o formato antigo
        // avisar o do João não silencia os outros
        val marcadas = Analises.chavesParaMarcar(quatro[2])
        val deNovo = Analises.alertasOrcamentoEscopo(listOf(todos, eu, joao, maria), "2026-10", marcadas)
        assertEquals(3, deNovo.size)
        assertFalse(deNovo.any { it.rotulo == "Só João" })
        // 100% marca o 80% do mesmo escopo
        val a100 = Analises.alertasOrcamentoEscopo(listOf(joao.copy(percentual = 1.1)), "2026-10", emptySet()).single()
        assertTrue(Analises.chavesParaMarcar(a100).any { it.endsWith("@P7|2026-10|80") })
    }

    @Test fun `mapper ida e volta com escopo e leitura sem os campos`() {
        val pessoa = Orcamento(id = 3, categoria = "Combustível", valorLimite = 300.0, escopo = EscopoOrcamento.PESSOA, pessoaId = 7)
        assertEquals(pessoa, FirestoreMapper.orcamentoFromDoc(FirestoreMapper.toDoc(pessoa)))
        val eu = Orcamento(id = 4, categoria = "Combustível", valorLimite = 100.0, escopo = EscopoOrcamento.EU)
        val docEu = FirestoreMapper.toDoc(eu)
        assertEquals("EU", docEu["escopo"])
        assertFalse(docEu.containsKey("pessoaId"))
        assertEquals(eu, FirestoreMapper.orcamentoFromDoc(docEu))

        val docTodos = FirestoreMapper.toDoc(Orcamento(id = 5, categoria = "Lazer", valorLimite = 50.0))
        assertFalse(docTodos.containsKey("escopo"))
        assertFalse(docTodos.containsKey("pessoaId"))

        // documento antigo (sem as chaves) e valores inconsistentes toleram
        val antigo = FirestoreMapper.orcamentoFromDoc(mapOf("id" to 9L, "categoria" to "Lazer", "valorLimite" to 10.0))
        assertNull(antigo.escopo); assertNull(antigo.pessoaId)
        val semPessoa = FirestoreMapper.orcamentoFromDoc(mapOf("id" to 9L, "categoria" to "Lazer", "valorLimite" to 10.0, "escopo" to "PESSOA"))
        assertNull(semPessoa.escopo); assertNull(semPessoa.pessoaId) // PESSOA sem pessoaId = TODOS
        val pessoaNum = FirestoreMapper.orcamentoFromDoc(mapOf("id" to 9L, "categoria" to "Lazer", "valorLimite" to 10.0, "escopo" to "PESSOA", "pessoaId" to 7.0))
        assertEquals(7, pessoaNum.pessoaId)
    }
}
