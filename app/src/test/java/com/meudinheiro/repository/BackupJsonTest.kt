package com.meudinheiro.repository

import com.meudinheiro.data.BackupDto
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Natureza
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class BackupJsonTest {

    private val despesa = Despesa(
        id = 42, descricao = "Mercado", valor = 123.45, data = Date(1_760_000_000_000), categoria = "Alimentação",
        conta = "111", pic = "supermarket", tipo = TipoDespesa.DEBITO, mes = 10, ano = 2025, cartaoId = 7,
        pago = false, natureza = Natureza.NORMAL, grupoId = "g1"
    )

    @Test fun `ida e volta v2 preserva ids, datas em milissegundos e vinculos`() {
        val json = BackupJson.toJson(
            BackupDto(
                contas = listOf(ContaSaldo(id = 3, saldo = 10.0, banco = "Nubank", pic = "nubank", agencia = "1", conta = "111", titular = "")),
                despesas = listOf(despesa)
            )
        )
        assertTrue("data deve ser número (ms), não texto: $json", json.contains("\"data\":1760000000000"))
        assertTrue(json.contains("\"versaoBackup\":2"))

        val volta = BackupJson.fromJson(json)
        assertEquals(3, volta.contas!!.single().id)
        val d = volta.despesas!!.single()
        assertEquals(42L, d.id)
        assertEquals(1_760_000_000_000, d.data.time)
        assertEquals(7, d.cartaoId)
        assertEquals("g1", d.grupoId)
        assertEquals(Natureza.NORMAL, d.natureza)
    }

    @Test fun `backup v1 sem natureza classifica lancamentos internos pela descricao`() {
        val v1 = """
            {"despesas":[
              {"id":1,"descricao":"Saldo Inicial","valor":100.0,"data":1760000000000,"categoria":"Outros","conta":"111","pic":"deposit","tipo":"CREDITO","mes":0,"ano":0,"pago":true},
              {"id":2,"descricao":"Pagamento Fatura: Visa","valor":50.0,"data":1760000000000,"categoria":"CARTÃO","conta":"111","pic":"p","tipo":"DEBITO","mes":9,"ano":125,"cartaoId":0,"pago":true},
              {"id":3,"descricao":"Aporte: Viagem","valor":20.0,"data":1760000000000,"categoria":"Reserva","conta":"111","pic":"p","tipo":"DEBITO","mes":10,"ano":2025,"pago":true},
              {"id":4,"descricao":"Estorno: Meta Viagem","valor":20.0,"data":1760000000000,"categoria":"Reserva","conta":"111","pic":"p","tipo":"CREDITO","mes":10,"ano":2025,"pago":true},
              {"id":5,"descricao":"Padaria","valor":9.9,"data":1760000000000,"categoria":"Alimentação","conta":"111","pic":"p","tipo":"DEBITO","mes":10,"ano":2025,"pago":true}
            ]}
        """.trimIndent()
        val d = BackupJson.fromJson(v1).despesas!!.associateBy { it.id }
        assertEquals(Natureza.SALDO_INICIAL, d.getValue(1).natureza)
        assertEquals(Natureza.PAGAMENTO_FATURA, d.getValue(2).natureza)
        assertNull("cartaoId 0 vira nulo", d.getValue(2).cartaoId)
        assertEquals(Natureza.APORTE_META, d.getValue(3).natureza)
        assertEquals(Natureza.RESGATE_META, d.getValue(4).natureza)
        assertEquals(Natureza.NORMAL, d.getValue(5).natureza)
        assertEquals("BRL", d.getValue(5).moedaOriginal)
        assertEquals(1.0, d.getValue(5).cotacaoNaData, 0.0)
    }

    @Test fun `datas antigas em texto sao lidas e nao derrubam a restauracao`() {
        val v1 = """{"despesas":[{"id":1,"descricao":"x","valor":1.0,"data":"Oct 6, 2025 3:00:00 PM","categoria":"c","conta":"1","pic":"p","tipo":"DEBITO","mes":10,"ano":2025,"pago":true}]}"""
        val d = BackupJson.fromJson(v1).despesas!!.single()
        assertTrue(d.data.time > 1_700_000_000_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json invalido e rejeitado com mensagem clara`() {
        BackupJson.fromJson("isto não é json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `arquivo vazio e rejeitado`() {
        BackupJson.fromJson("")
    }
}
