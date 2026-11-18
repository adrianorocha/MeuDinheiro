package com.meudinheiro.domain

import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Cortes.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R103 — "Onde cortar". Mesmos casos e números de `portal/src/lib/finance/cortes.test.ts`. */
class CortesTest {

    private fun ms(ano: Int, mes: Int, dia: Int) = Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis
    private val agora = ms(2025, 6, 15) // janela: mar, abr, mai/2025
    private val conta = ContaSaldo(id = 1, saldo = 0.0, banco = "Banco A", pic = "", agencia = "1", conta = "111", titular = "T")
    private var seq = 1L

    private fun d(
        descricao: String, valor: Double, categoria: String, data: Long, tipo: TipoDespesa = TipoDespesa.DEBITO,
        natureza: String = Natureza.NORMAL, cartaoId: Int? = null
    ) = Despesa(
        id = seq++, descricao = descricao, valor = valor, data = Date(data), categoria = categoria, conta = "111", pic = "", tipo = tipo,
        mes = 1, ano = 2025, cartaoId = cartaoId, pago = true, natureza = natureza
    )

    /** Mesmo gasto nos 3 meses da janela, valor variando (x0,8 / x1 / x1,2: a média se mantém e não vira "assinatura"). */
    private fun trimestre(descricao: String, valor: Double, categoria: String, dia: Int = 10, fixo: Boolean = false, natureza: String = Natureza.NORMAL) =
        listOf(3, 4, 5).mapIndexed { i, m ->
            val v = if (fixo) valor else Math.round(valor * doubleArrayOf(0.8, 1.0, 1.2)[i] * 100) / 100.0
            d(descricao, v, categoria, ms(2025, m, dia), natureza = natureza)
        }

    private fun calcular(despesas: List<Despesa>, par: Cortes.Parametros = Cortes.Parametros()) = Cortes.calcular(despesas, emptyList(), agora, par)

    @Test fun `categoria flexivel 20 por cento, essencial 5, reserva e aportes ficam de fora`() {
        val ds = trimestre("Cinema", 300.0, "Lazer") + trimestre("Mercado", 1000.0, "Supermercado") +
            trimestre("Poupar", 500.0, "Reserva") + trimestre("Meta", 500.0, "Lazer", natureza = Natureza.APORTE_META)
        val r = calcular(ds)
        val lazer = r.cortes.first { it.id == "corte:categoria:lazer" }
        val mercado = r.cortes.first { it.id == "corte:categoria:supermercado" }
        assertEquals(Tipo.FLEXIVEL, lazer.tipo); assertEquals(30000L, lazer.mensalAtual); assertEquals(6000L, lazer.economiaMensal)
        assertEquals(Tipo.ESSENCIAL, mercado.tipo); assertEquals(100000L, mercado.mensalAtual); assertEquals(5000L, mercado.economiaMensal)
        assertFalse(r.cortes.any { it.categoria == "Reserva" })
        assertEquals(listOf(Tipo.FLEXIVEL, Tipo.ESSENCIAL), r.cortes.map { it.tipo })
        assertEquals(11000L, r.totalMensal)
        assertEquals(132000L, r.totalAnual)
    }

    @Test fun `percentuais ajustaveis`() {
        val r = calcular(trimestre("Cinema", 300.0, "Lazer"), Cortes.Parametros(pctFlexivel = 0.5))
        assertEquals(15000L, r.totalMensal)
    }

    @Test fun `juros e tarifas sao 100 por cento evitaveis e nao contam de novo na categoria`() {
        val r = calcular(trimestre("Juros do rotativo", 90.0, "Banco") + trimestre("Padaria", 200.0, "Banco"))
        assertEquals(9000L, r.cortes.first { it.tipo == Tipo.EVITAVEL }.economiaMensal)
        assertEquals(20000L, r.cortes.first { it.tipo == Tipo.FLEXIVEL }.mensalAtual) // so a padaria
    }

    @Test fun `assinatura detectada e um corte proprio e sai da categoria`() {
        val r = calcular(trimestre("Streaming Max", 50.0, "Lazer", dia = 5, fixo = true) + trimestre("Cinema", 200.0, "Lazer", dia = 20))
        assertEquals(5000L, r.cortes.first { it.tipo == Tipo.ASSINATURA }.economiaMensal)
        assertEquals(20000L, r.cortes.first { it.tipo == Tipo.FLEXIVEL }.mensalAtual)
    }

    @Test fun `compras pequenas e frequentes 30 por cento do mensal, nao vale para essencial`() {
        val dias = listOf(2, 6, 9, 13, 17, 21)
        val cafes = listOf(3, 4, 5).flatMap { m -> dias.map { dia -> d("Cafeteria", 15.0, "Lazer", ms(2025, m, dia)) } }
        val r = calcular(cafes)
        val f = r.cortes.first { it.tipo == Tipo.FREQUENTE }
        assertEquals(9000L, f.mensalAtual)
        assertEquals(2700L, f.economiaMensal)
        assertFalse(r.cortes.any { it.tipo == Tipo.FLEXIVEL }) // tudo ja esta no bloco frequente
        val ess = listOf(3, 4, 5).flatMap { m -> dias.map { dia -> d("Feira", 15.0, "Supermercado", ms(2025, m, dia)) } }
        assertFalse(calcular(ess).cortes.any { it.tipo == Tipo.FREQUENTE })
    }

    @Test fun `estorno de cartao abate, blocos pequenos nao aparecem, fora da janela nao conta`() {
        val ds = trimestre("Roupas", 400.0, "Compras") + listOf(
            d("Estorno roupas", 300.0, "Compras", ms(2025, 4, 12), tipo = TipoDespesa.CREDITO, cartaoId = 10),
            d("Bala", 5.0, "Doces", ms(2025, 4, 1)),
            d("Viagem", 9000.0, "Viagem", ms(2025, 6, 2)),
            d("Viagem antiga", 9000.0, "Viagem", ms(2024, 12, 2))
        )
        val r = calcular(ds)
        assertEquals(1, r.cortes.size)
        assertEquals("Compras", r.cortes[0].categoria)
        assertEquals(Math.round(90000 / 3.0), r.cortes[0].mensalAtual)
    }

    @Test fun `dispensar tira o corte do total`() {
        val r = calcular(trimestre("Cinema", 300.0, "Lazer"), Cortes.Parametros(dispensados = setOf("corte:categoria:lazer")))
        assertTrue(r.cortes.isEmpty())
        assertEquals(0L, r.totalMensal)
    }

    @Test fun `maiores gastos ranking por media mensal`() {
        val r = calcular(trimestre("Restaurante A", 600.0, "Lazer") + trimestre("Cinema", 90.0, "Lazer"))
        assertEquals(listOf("Restaurante A", "Cinema"), r.maioresGastos.map { it.nome })
        assertEquals(60000L, r.maioresGastos[0].mensal)
    }

    @Test fun `plano soma na ordem de prioridade ate o alvo e diz quanto falta`() {
        val ds = trimestre("Juros", 40.0, "Banco") + trimestre("Cinema", 300.0, "Lazer") + trimestre("Mercado", 1000.0, "Supermercado")
        val todos = calcular(ds).cortes
        assertEquals(listOf(4000L, 6000L, 5000L), todos.map { it.economiaMensal })
        assertNull(Cortes.montarPlano(todos, 0))
        Cortes.montarPlano(todos, 3000)!!.let { assertEquals(4000L, it.economia); assertTrue(it.atinge); assertEquals(0L, it.falta) }
        assertEquals(2, Cortes.montarPlano(todos, 9000)!!.selecionados.size)
        Cortes.montarPlano(todos, 20000)!!.let { assertEquals(15000L, it.economia); assertFalse(it.atinge); assertEquals(5000L, it.falta) }
        assertEquals(2, calcular(ds, Cortes.Parametros(alvoMensal = 9000)).plano?.selecionados?.size)
    }

    @Test fun `aplicar o corte no fluxo saldo mais economia vezes j+1 e meses negativos somem`() {
        val ds = trimestre("Cinema", 800.0, "Lazer")
        val fluxo = FluxoCaixa.projetar(listOf(conta), emptyList(), ds, emptyList(), agora, 6, 0.0)
        assertNotNull(fluxo.primeiroNegativo)
        val f = Cortes.aplicarCorteNoFluxo(fluxo, fluxo.cortePorMes)
        assertTrue(f.mesesNegativosAntes > 0)
        assertEquals(0, f.mesesNegativosDepois)
        assertEquals(fluxo.meses[2].saldoFinal + fluxo.cortePorMes * 3, f.saldos[2])
        assertEquals(f.saldos[5], f.saldoFinalDepois)
    }
}
