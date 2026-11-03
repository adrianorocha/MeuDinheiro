package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.QuemLancou.Selecao
import com.meudinheiro.repository.BackupJson
import com.meudinheiro.storage.FirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/** R67 — autor do lançamento (autor = nome, autorUid = uid), filtro "Quem lançou", documento Firestore e backup antigo. */
class AutoriaTest {

    private val base = Despesa(
        id = 1, descricao = "Mercado", valor = 10.0, data = Date(1_760_000_000_000), categoria = "Alimentação", conta = "111",
        pic = "p", tipo = TipoDespesa.DEBITO, mes = 10, ano = 2025
    )

    @Test fun `completa autor e uid quando vazios e mantem Importacao`() {
        val novo = Autoria.completar(base, "Ana", "uid-ana")
        assertEquals("Ana", novo.autor); assertEquals("uid-ana", novo.autorUid)
        val imp = Autoria.completar(base.copy(autor = "Importação"), "Ana", "uid-ana")
        assertEquals("Importação", imp.autor); assertEquals("uid-ana", imp.autorUid)
        // local-only: sem uid e sem nome continua vazio
        val local = Autoria.completar(base, " ", null)
        assertNull(local.autor); assertNull(local.autorUid)
    }

    @Test fun `duplicar repetir parcelar - o autor e quem executa`() {
        val deBia = base.copy(autor = "Bia", autorUid = "uid-bia")
        val copia = Autoria.paraExecutor(deBia, "Ana", "uid-ana")
        assertEquals("Ana", copia.autor); assertEquals("uid-ana", copia.autorUid)
        // parcelas geradas a partir do modelo herdam o autor do modelo (já do executor)
        val parcelas = com.meudinheiro.domain.Financas.parcelar(copia.copy(valor = 90.0), 3, 1_760_000_000_000)
        assertEquals(setOf("uid-ana"), parcelas.map { it.autorUid }.toSet())
        assertEquals(setOf("Ana"), parcelas.map { it.autor }.toSet())
    }

    @Test fun `filtro quem lancou - conjunta, minhas e membro`() {
        val l = listOf(
            base.copy(id = 1, autorUid = "a", autor = "Ana"), base.copy(id = 2, autorUid = "b", autor = "Bia"),
            base.copy(id = 3, autorUid = null, autor = null)
        )
        assertEquals(3, QuemLancou.filtrar(l, Selecao.Todos, "a") { it.autorUid }.size)
        assertEquals(listOf(1L), QuemLancou.filtrar(l, Selecao.Eu, "a") { it.autorUid }.map { it.id })
        assertEquals(listOf(2L), QuemLancou.filtrar(l, Selecao.Membro("b"), "a") { it.autorUid }.map { it.id })
        assertEquals(emptyList<Despesa>(), QuemLancou.filtrar(l, Selecao.Eu, null) { it.autorUid })
        assertEquals(listOf("Bia"), QuemLancou.autoresDistintos(l, "a", { it.autorUid }, { it.autor }).map { it.nome })
        assertNull(QuemLancou.rotuloAutor("Ana", false))
        assertEquals("por Ana", QuemLancou.rotuloAutor("Ana", true))
        assertNull(QuemLancou.rotuloAutor(null, true))
    }

    @Test fun `documento so leva autorUid quando preenchido e le documento antigo`() {
        assertFalse(FirestoreMapper.toDoc(base).containsKey("autorUid"))
        val doc = FirestoreMapper.toDoc(base.copy(autor = "Ana", autorUid = "uid-ana"))
        assertEquals("uid-ana", doc["autorUid"])
        assertEquals("uid-ana", FirestoreMapper.despesaFromDoc(doc).autorUid)
        assertNull(FirestoreMapper.despesaFromDoc(FirestoreMapper.toDoc(base)).autorUid)
    }

    @Test fun `backup antigo sem autorUid restaura com autorUid nulo e ida e volta preserva`() {
        val antigo = """{"despesas":[{"id":1,"descricao":"x","valor":5.0,"data":1760000000000,"categoria":"A","conta":"1","pic":"p","tipo":"DEBITO","mes":10,"ano":2025,"autor":"Ana"}]}"""
        val d = BackupJson.fromJson(antigo).despesas!!.single()
        assertNull(d.autorUid); assertEquals("Ana", d.autor)
        val json = BackupJson.toJson(com.meudinheiro.data.BackupDto(despesas = listOf(base.copy(autor = "Ana", autorUid = "uid-ana"))))
        assertEquals("uid-ana", BackupJson.fromJson(json).despesas!!.single().autorUid)
        val vazio = BackupJson.fromJson(json.replace("uid-ana", "  ")).despesas!!.single()
        assertNull("autorUid em branco é saneado", vazio.autorUid)
    }
}
