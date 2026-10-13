package com.meudinheiro.repository

import com.google.gson.Gson
import com.meudinheiro.data.Anexo
import com.meudinheiro.data.BackupDto
import com.meudinheiro.data.Converters
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.storage.FirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** R60 — anexos em documento Firestore, backup (ida/volta e backup antigo), lixeira e conversor Room. */
class AnexosSerializacaoTest {

    private val a1 = Anexo("11111111-aaaa", "cupom.jpg", "image/jpeg", 2048, "usuarios/uid1/anexos/42/11111111-aaaa.jpg", 1_760_000_000_000)
    private val a2 = Anexo("22222222-bbbb", "nota.pdf", "application/pdf", 90_000, "local:anexos/42/22222222-bbbb.pdf", 1_760_000_000_001)
    private val base = Despesa(
        id = 42, descricao = "Mercado", valor = 10.0, data = Date(1_760_000_000_000), categoria = "Alimentação",
        conta = "111", pic = "p", tipo = TipoDespesa.DEBITO, mes = 10, ano = 2025
    )

    @Test fun `documento so leva anexos quando ha anexos`() {
        assertFalse(FirestoreMapper.toDoc(base).containsKey("anexos"))
        val doc = FirestoreMapper.toDoc(base.copy(anexos = listOf(a1, a2)))
        @Suppress("UNCHECKED_CAST") val lista = doc["anexos"] as List<Map<String, Any?>>
        assertEquals(2, lista.size)
        assertEquals("usuarios/uid1/anexos/42/11111111-aaaa.jpg", lista[0]["caminho"])
        assertEquals(2048L, lista[0]["tamanho"])
    }

    @Test fun `documento ida e volta e documento antigo sem anexos`() {
        val volta = FirestoreMapper.despesaFromDoc(FirestoreMapper.toDoc(base.copy(anexos = listOf(a1, a2))))
        assertEquals(listOf(a1, a2), volta.anexos)
        assertTrue(FirestoreMapper.despesaFromDoc(FirestoreMapper.toDoc(base)).anexos.isEmpty())
        // doc do portal: números como Double, itens inválidos descartados
        val doc = FirestoreMapper.toDoc(base) + ("anexos" to listOf(
            mapOf("id" to "x1", "nome" to "a.png", "tipo" to "image/png", "tamanho" to 10.0, "caminho" to "usuarios/u/anexos/42/x1.png", "criadoEm" to 5.0),
            mapOf("id" to "x2", "tipo" to "image/gif", "caminho" to "usuarios/u/anexos/42/x2.gif"),
            "lixo", null
        ))
        val d = FirestoreMapper.despesaFromDoc(doc)
        assertEquals(listOf("x1"), d.anexos.map { it.id })
        assertEquals(10L, d.anexos.single().tamanho)
    }

    @Test fun `lixeira (json do documento) mantem os metadados dos anexos`() {
        val g = Gson()
        val json = g.toJson(FirestoreMapper.toDoc(base.copy(anexos = listOf(a1))))
        @Suppress("UNCHECKED_CAST") val doc = g.fromJson(json, Map::class.java) as Map<String, Any?>
        assertEquals(listOf(a1), FirestoreMapper.despesaFromDoc(doc).anexos)
    }

    @Test fun `backup ida e volta preserva anexos`() {
        val volta = BackupJson.fromJson(BackupJson.toJson(BackupDto(despesas = listOf(base.copy(anexos = listOf(a1, a2))))))
        assertEquals(listOf(a1, a2), volta.despesas!!.single().anexos)
    }

    @Test fun `backup antigo sem anexos vira lista vazia e anexos invalidos sao saneados`() {
        val antigo = """{"despesas":[{"id":1,"descricao":"x","valor":1.0,"data":1760000000000,"categoria":"c","conta":"1","pic":"p","tipo":"DEBITO","mes":10,"ano":2025,"pago":true}]}"""
        assertTrue(BackupJson.fromJson(antigo).despesas!!.single().anexos.isEmpty())
        val sujo = """{"despesas":[{"id":1,"descricao":"x","valor":1.0,"data":1760000000000,"categoria":"c","conta":"1","pic":"p","tipo":"DEBITO","mes":10,"ano":2025,"pago":true,
            "anexos":[{"id":"ok","nome":"a.pdf","tipo":"application/pdf","tamanho":5,"caminho":"usuarios/u/anexos/1/ok.pdf","criadoEm":1},{"id":"ruim","tipo":"text/html","caminho":"x"},{"nome":"sem id"}]}]}"""
        assertEquals(listOf("ok"), BackupJson.fromJson(sujo).despesas!!.single().anexos.map { it.id })
    }

    @Test fun `conversor room ida e volta e tolera texto invalido`() {
        val c = Converters()
        assertEquals("[]", c.fromAnexos(emptyList()))
        assertEquals(listOf(a1, a2), c.toAnexos(c.fromAnexos(listOf(a1, a2))))
        assertTrue(c.toAnexos("").isEmpty())
        assertTrue(c.toAnexos("não é json").isEmpty())
    }
}
