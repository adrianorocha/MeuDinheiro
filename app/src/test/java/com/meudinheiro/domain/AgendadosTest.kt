package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class AgendadosTest {
    private fun ms(m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(2026, m - 1, d, h, 0, 0) }.timeInMillis
    private val agora = ms(10, 15, 9)

    private fun l(desc: String, valor: Double, data: Long, tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = false,
                  cartao: Int? = null, natureza: String = Natureza.NORMAL, id: Long = 0) = Despesa(
        id = id, descricao = desc, valor = valor, data = Date(data), categoria = "c", conta = "1", pic = "p", tipo = tipo,
        mes = 1, ano = 2026, pago = pago, cartaoId = cartao, natureza = natureza
    )

    @Test fun `seleciona so futuros em aberto, normais e fora de cartao`() {
        val lista = listOf(
            l("futuro", 10.0, ms(10, 20)),
            l("passado aberto", 10.0, ms(10, 10)),
            l("futuro pago", 10.0, ms(10, 20), pago = true),
            l("cartao", 10.0, ms(10, 20), cartao = 3),
            l("transferencia", 10.0, ms(10, 20), natureza = Natureza.TRANSFERENCIA)
        )
        assertEquals(listOf("futuro"), Agendados.selecionar(lista, agora).map { it.descricao })
    }

    @Test fun `ordena por data e respeita o horizonte`() {
        val lista = listOf(l("b", 1.0, ms(12, 1)), l("a", 1.0, ms(10, 16)), l("c", 1.0, ms(11, 5)))
        assertEquals(listOf("a", "c", "b"), Agendados.selecionar(lista, agora).map { it.descricao })
        assertEquals(listOf("a", "c"), Agendados.selecionar(lista, agora, ate = ms(11, 30)).map { it.descricao })
    }

    @Test fun `resumo separa a pagar e a receber e agrupa por mes`() {
        val lista = listOf(
            l("luz", 100.10, ms(10, 20)),
            l("salario", 500.0, ms(10, 25), TipoDespesa.CREDITO),
            l("aluguel", 800.0, ms(12, 5))
        )
        val r = Agendados.resumir(lista, agora)
        assertEquals(3, r.quantidade)
        assertEquals(90010L, r.aPagarCentavos)
        assertEquals(50000L, r.aReceberCentavos)
        assertEquals(listOf(10, 12), r.meses.map { it.mes })
        assertEquals(2, r.meses[0].itens.size)
        assertEquals(10010L, r.meses[0].aPagarCentavos)
    }

    @Test fun `virada de ano agrupa em ordem`() {
        val lista = listOf(
            l("jan", 1.0, Calendar.getInstance().apply { clear(); set(2027, 0, 5) }.timeInMillis),
            l("dez", 1.0, ms(12, 5))
        )
        val r = Agendados.resumir(lista, agora)
        assertEquals(listOf(2026 to 12, 2027 to 1), r.meses.map { it.ano to it.mes })
    }

    @Test fun `sem futuros o resumo e vazio`() {
        assertTrue(Agendados.resumir(listOf(l("x", 1.0, ms(10, 1))), agora).itens.isEmpty())
    }
}
