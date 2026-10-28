package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date

class WidgetResumoTest {
    private fun ms(d: Int) = Calendar.getInstance().apply { clear(); set(2026, 9, d, 12, 0, 0) }.timeInMillis
    private val agora = ms(15)

    private fun conta(valor: Double, dia: Int, pago: Boolean = false) = Despesa(
        descricao = "x", valor = valor, data = Date(ms(dia)), categoria = "c", conta = "1", pic = "p", tipo = TipoDespesa.DEBITO,
        mes = 10, ano = 2026, pago = pago
    )

    private fun meta(nome: String, objetivo: Double, guardado: Double) = Meta(nome = nome, valorObjetivo = objetivo, valorGuardado = guardado)

    @Test fun `soma saldos em centavos sem erro de ponto flutuante`() {
        val r = WidgetResumo.montar(listOf(0.1, 0.2, 1234567.89), emptyList(), emptyList(), agora)
        assertEquals(1234568.19, r.saldoTotal, 0.0)
    }

    @Test fun `separa atrasadas de a vencer e soma o valor`() {
        val r = WidgetResumo.montar(emptyList(), emptyList(), listOf(conta(100.0, 10), conta(50.5, 15), conta(20.0, 18), conta(999.0, 12, pago = true)), agora)
        assertEquals(1, r.atrasadas)
        assertEquals(2, r.aVencer) // hoje conta como a vencer
        assertEquals(170.5, r.valorPendente, 0.0)
    }

    @Test fun `meta com objetivo zero nao quebra e percentual fica entre 0 e 100`() {
        assertEquals(0, WidgetResumo.percentual(10.0, 0.0))
        assertEquals(100, WidgetResumo.percentual(500.0, 100.0))
        assertEquals(33, WidgetResumo.percentual(1.0, 3.0))
    }

    @Test fun `metas em andamento vem primeiro das mais adiantadas e limita a tres`() {
        val metas = listOf(meta("Concluída", 100.0, 100.0), meta("A", 100.0, 10.0), meta("B", 100.0, 80.0), meta("C", 100.0, 50.0), meta("D", 100.0, 20.0))
        val r = WidgetResumo.montar(emptyList(), metas, emptyList(), agora)
        assertEquals(listOf("B", "C", "D"), r.metas.map { it.nome })
    }
}
