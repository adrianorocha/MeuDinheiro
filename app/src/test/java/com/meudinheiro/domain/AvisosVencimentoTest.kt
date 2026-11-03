package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class AvisosVencimentoTest {
    private fun ms(d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(2026, 9, d, h, 0, 0) }.timeInMillis
    private val agora = ms(15, 9)

    private fun conta(desc: String, valor: Double, dia: Int, pago: Boolean = false) = Despesa(
        descricao = desc, valor = valor, data = Date(ms(dia)), categoria = "c", conta = "1", pic = "p", tipo = TipoDespesa.DEBITO,
        mes = 10, ano = 2026, pago = pago
    )

    @Test fun `quando descreve a distancia em dias`() {
        assertEquals("hoje", AvisosVencimento.quando(ms(15, 23), agora))
        assertEquals("amanhã", AvisosVencimento.quando(ms(16), agora))
        assertEquals("em 3 dias", AvisosVencimento.quando(ms(18), agora))
        assertEquals("venceu ontem", AvisosVencimento.quando(ms(14), agora))
        assertEquals("venceu há 5 dias", AvisosVencimento.quando(ms(10), agora))
    }

    @Test fun `sem pendencias nao ha aviso e pagas sao ignoradas`() {
        assertNull(AvisosVencimento.montar(emptyList(), agora))
        assertNull(AvisosVencimento.montar(listOf(conta("Luz", 100.0, 16, pago = true)), agora))
    }

    @Test fun `uma conta a vencer usa o nome no titulo e o valor no resumo`() {
        val r = AvisosVencimento.montar(listOf(conta("Luz", 150.5, 16)), agora)!!
        assertEquals("Luz vence amanhã", r.titulo)
        assertEquals("R$ 150,50", r.resumo)
        assertFalse(r.atrasado)
    }

    @Test fun `atrasadas mudam o titulo e marcam o aviso como atrasado`() {
        val r = AvisosVencimento.montar(listOf(conta("Aluguel", 1500.0, 10), conta("Luz", 150.0, 16)), agora)!!
        assertEquals("1 atrasada(s) e 1 a vencer", r.titulo)
        assertTrue(r.atrasado)
        assertEquals("Aluguel", r.maisUrgente.descricao)
        assertEquals("R$ 1.650,00 no total · próxima: Aluguel, venceu há 5 dias", r.resumo)
    }

    @Test fun `so atrasadas`() {
        assertEquals("2 contas atrasadas", AvisosVencimento.montar(listOf(conta("a", 1.0, 1), conta("b", 1.0, 2)), agora)!!.titulo)
    }

    @Test fun `lista limita as linhas e informa o restante`() {
        val muitas = (16..23).map { conta("C$it", 10.0, it) }
        val r = AvisosVencimento.montar(muitas, agora)!!
        assertEquals(AvisosVencimento.MAX_LINHAS + 1, r.linhas.size)
        assertEquals("+ 3 outras no app", r.linhas.last())
        assertEquals("Amanhã · C16 · R$ 10,00", r.linhas.first())
        assertEquals("8 contas vencem em breve", r.titulo)
    }
}
