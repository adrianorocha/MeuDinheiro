package com.meudinheiro.domain

import com.meudinheiro.domain.IndicesEconomicos.Origem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R100 — só semente + edições MANUAIS são gravadas; valores do BCB vivem apenas no cache (paridade com `paraSalvar` do portal). */
class IndicesBcbPersistenciaTest {

    private val snap = Bcb.SnapshotIndices(
        1,
        mapOf("selic" to Bcb.IndiceBcb(listOf(1.09, 1.08, 0.92), "10/2026", true), "ipca" to Bcb.IndiceBcb(listOf(0.07, -0.32, 0.82), "09/2026", false)),
        Bcb.SelicMetaBcb(13.75, "2026-10-27"), Bcb.SalarioBcb(162100, "10/2026")
    )

    @Test fun `valores do BCB nunca entram no texto salvo`() {
        val ef = Bcb.aplicarBcb(IndicesEconomicos.padrao(), snap)
        // mesmo que o conjunto efetivo fosse serializado por engano, nada do BCB seria gravado
        assertEquals("", IndicesEconomicos.serializar(ef))
        assertEquals(IndicesEconomicos.padrao(), IndicesEconomicos.desserializar(IndicesEconomicos.serializar(ef)))
    }

    @Test fun `base recarregada traz a semente e o BCB so aparece ao mesclar`() {
        val salvo = IndicesEconomicos.desserializar(IndicesEconomicos.serializar(IndicesEconomicos.padrao()))
        assertEquals(listOf(1.09, 1.08), salvo.itens.first { it.id == "selic" }.serie)
        val ef = Bcb.aplicarBcb(salvo, snap)
        assertEquals(listOf(1.09, 1.08, 0.92), ef.itens.first { it.id == "selic" }.serie)
    }

    @Test fun `edicao manual e gravada com a marca e volta como manual`() {
        val c0 = IndicesEconomicos.padrao()
        val c = c0.copy(itens = c0.itens.map { if (it.id == "ipca") it.copy(serie = listOf(0.5), competencia = "10/2026", origem = Origem.MANUAL) else it })
        val txt = IndicesEconomicos.serializar(c)
        assertTrue(txt.contains("manual\tipca"))
        val lido = IndicesEconomicos.desserializar(txt)
        assertEquals(Origem.MANUAL, lido.itens.first { it.id == "ipca" }.origem)
        // o BCB nao sobrescreve
        assertEquals(listOf(0.5), Bcb.aplicarBcb(lido, snap).itens.first { it.id == "ipca" }.serie)
    }

    @Test fun `manual com os mesmos valores da semente continua manual`() {
        val c0 = IndicesEconomicos.padrao()
        val c = c0.copy(itens = c0.itens.map { if (it.id == "selic") it.copy(origem = Origem.MANUAL) else it })
        val lido = IndicesEconomicos.desserializar(IndicesEconomicos.serializar(c))
        assertEquals(Origem.MANUAL, lido.itens.first { it.id == "selic" }.origem)
        assertEquals(listOf(1.09, 1.08), Bcb.aplicarBcb(lido, snap).itens.first { it.id == "selic" }.serie)
    }

    @Test fun `dados da R98 ja editados viram manuais ao recarregar e sem edicao nao`() {
        val antigo = "serie\tcdi\t1.2\ncomp\tcdi\t11/2026\nsal\t170000\t2027\nextra\tselicMeta\t12.5\n"
        val r = IndicesEconomicos.desserializar(antigo)
        assertEquals(Origem.MANUAL, r.itens.first { it.id == "cdi" }.origem)
        assertEquals(Origem.SEMENTE, r.itens.first { it.id == "selic" }.origem)
        assertEquals(Origem.MANUAL, r.salarioMinimo.origem)
        assertEquals(Origem.MANUAL, r.extras.selicMetaOrigem)
        val ef = Bcb.aplicarBcb(r, snap)
        assertEquals(listOf(1.2), ef.itens.first { it.id == "cdi" }.serie)
        assertEquals(170000L, ef.salarioMinimo.centavos)
        assertEquals(12.5, ef.extras.selicMetaAnualPct!!, 0.0)
    }

    @Test fun `usar automatico volta o campo a semente e o BCB passa a valer`() {
        val c0 = IndicesEconomicos.padrao()
        val editado = c0.copy(itens = c0.itens.map { if (it.id == "ipca") it.copy(serie = listOf(0.5), origem = Origem.MANUAL) else it })
        val auto = editado.copy(itens = editado.itens.map { if (it.id == "ipca") IndicesEconomicos.padrao().itens.first { x -> x.id == "ipca" } else it })
        assertFalse(IndicesEconomicos.serializar(auto).contains("ipca"))
        assertEquals(listOf(0.07, -0.32, 0.82), Bcb.aplicarBcb(auto, snap).itens.first { it.id == "ipca" }.serie)
        assertEquals(Origem.BCB, Bcb.aplicarBcb(auto, snap).itens.first { it.id == "ipca" }.origem)
    }

    @Test fun `restaurar padrao descarta edicoes e nao apaga o cache (o cache e outro armazem)`() {
        // restaurar = gravar texto vazio na base; o snapshot do BCB continua e volta a valer
        val restaurada = IndicesEconomicos.desserializar("")
        val ef = Bcb.aplicarBcb(restaurada, snap)
        assertEquals(Origem.BCB, ef.itens.first { it.id == "selic" }.origem)
        assertEquals(Origem.BCB, ef.salarioMinimo.origem)
    }

    @Test fun `salario e meta manuais sobrevivem e o salario do BCB nao e gravado`() {
        val c0 = IndicesEconomicos.padrao()
        val c = c0.copy(salarioMinimo = IndicesEconomicos.SalarioMinimo(162100, "2026", Origem.MANUAL), extras = c0.extras.copy(selicMetaAnualPct = 14.0, selicMetaOrigem = Origem.MANUAL))
        val lido = IndicesEconomicos.desserializar(IndicesEconomicos.serializar(c))
        assertEquals(Origem.MANUAL, lido.salarioMinimo.origem) // manual mesmo igual a semente
        assertEquals(14.0, Bcb.aplicarBcb(lido, snap).extras.selicMetaAnualPct!!, 0.0)
        assertEquals("", IndicesEconomicos.serializar(Bcb.aplicarBcb(IndicesEconomicos.padrao(), snap)))
    }
}
