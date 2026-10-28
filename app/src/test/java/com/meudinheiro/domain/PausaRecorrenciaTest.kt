package com.meudinheiro.domain

import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.storage.FirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R47 — pausar recorrência (regras puras). */
class PausaRecorrenciaTest {

    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis

    private fun regra(
        dia: Int = 5, ultima: Long? = null, pausada: Boolean = false, ate: Long? = null, valor: Double = 100.0,
        tipo: TipoDespesa = TipoDespesa.DEBITO
    ) = DespesaFixa(
        id = 1, descricao = "Aluguel", valor = valor, conta = "111", categoria = "Casa", pic = "p", tipo = tipo,
        diaVencimento = dia, ultimaDataLancamento = ultima?.let { Date(it) }, pausada = pausada, pausadaAte = ate?.let { Date(it) }
    )

    @Test fun `pausada de fato exige a marca e prazo nao vencido`() {
        val hoje = ms(2026, 10, 15)
        assertFalse(Financas.recorrenciaPausada(regra(), hoje))
        assertTrue(Financas.recorrenciaPausada(regra(pausada = true), hoje))
        assertTrue(Financas.recorrenciaPausada(regra(pausada = true, ate = ms(2026, 11, 1)), hoje))
        assertFalse(Financas.recorrenciaPausada(regra(pausada = true, ate = ms(2026, 10, 1)), hoje))
        assertFalse(Financas.recorrenciaPausada(regra(pausada = false, ate = ms(2026, 12, 1)), hoje))
    }

    @Test fun `pausada nao gera ocorrencias`() {
        val r = regra(ultima = ms(2026, 7, 5), pausada = true)
        assertEquals(emptyList<Long>(), Financas.ocorrenciasPendentes(r, ms(2026, 10, 15)))
    }

    @Test fun `retomar nao recupera meses pausados`() {
        val retomada = Financas.retomar(regra(ultima = ms(2026, 7, 5), pausada = true), ms(2026, 10, 15))
        assertFalse(retomada.pausada); assertNull(retomada.pausadaAte)
        assertEquals(ms(2026, 10, 5), retomada.ultimaDataLancamento!!.time)
        assertEquals(0, Financas.ocorrenciasPendentes(retomada, ms(2026, 10, 20)).size)
        assertEquals(listOf(11), Financas.ocorrenciasPendentes(retomada, ms(2026, 11, 6)).map { Financas.mesDe(it) })
    }

    @Test fun `retomar antes do vencimento do mes usa a ocorrencia do mes anterior`() {
        val retomada = Financas.retomar(regra(dia = 20, ultima = ms(2026, 7, 20), pausada = true), ms(2026, 10, 15))
        assertEquals(ms(2026, 9, 20), retomada.ultimaDataLancamento!!.time)
        assertEquals(listOf(10), Financas.ocorrenciasPendentes(retomada, ms(2026, 10, 21)).map { Financas.mesDe(it) })
    }

    @Test fun `retomar nunca anda para tras e funciona sem lancamento anterior`() {
        val futuro = regra(ultima = ms(2026, 12, 5), pausada = true)
        assertEquals(ms(2026, 12, 5), Financas.retomar(futuro, ms(2026, 10, 15)).ultimaDataLancamento!!.time)
        assertEquals(ms(2026, 10, 5), Financas.retomar(regra(pausada = true), ms(2026, 10, 15)).ultimaDataLancamento!!.time)
    }

    @Test fun `dia 31 em mes curto retoma no ultimo dia`() {
        val r = Financas.retomar(regra(dia = 31, pausada = true), ms(2026, 3, 10))
        assertEquals(ms(2026, 2, 28), r.ultimaDataLancamento!!.time)
    }

    @Test fun `regra antiga sem os campos funciona como nao pausada`() {
        val antiga = DespesaFixa(id = 1, descricao = "x", valor = 1.0, conta = "1", categoria = "c", pic = "p",
            tipo = TipoDespesa.DEBITO, diaVencimento = 5, ultimaDataLancamento = Date(ms(2026, 8, 5)))
        assertFalse(antiga.pausada); assertNull(antiga.pausadaAte)
        assertEquals(listOf(9, 10), Financas.ocorrenciasPendentes(antiga, ms(2026, 10, 15)).map { Financas.mesDe(it) })
    }

    @Test fun `previsao de assinaturas ignora pausadas e nao altera as demais`() {
        val hoje = ms(2026, 10, 15)
        val ativa = regra(valor = 50.0)
        val pausada = regra(pausada = true, valor = 70.0).copy(id = 2, descricao = "Streaming")
        val vencida = regra(pausada = true, ate = ms(2026, 10, 1), valor = 30.0).copy(id = 3, descricao = "Curso")
        val a = Analises.assinaturas(hoje, emptyList(), listOf(ativa, pausada, vencida))
        assertEquals(listOf("Aluguel", "Curso"), a.map { it.nome }.sorted())
        assertEquals(Analises.assinaturas(hoje, emptyList(), listOf(ativa)).single().totalMensal, a.first { it.nome == "Aluguel" }.totalMensal, 0.0)
    }

    @Test fun `firestore preserva os campos e documento antigo vira nao pausada`() {
        val r = regra(pausada = true, ate = ms(2026, 12, 1))
        val volta = FirestoreMapper.fixaFromDoc(FirestoreMapper.toDoc(r))
        assertTrue(volta.pausada); assertEquals(ms(2026, 12, 1), volta.pausadaAte!!.time)
        val antigo = FirestoreMapper.toDoc(regra()).filterKeys { it != "pausada" && it != "pausadaAte" }
        val lida = FirestoreMapper.fixaFromDoc(antigo)
        assertFalse(lida.pausada); assertNull(lida.pausadaAte)
    }
}
