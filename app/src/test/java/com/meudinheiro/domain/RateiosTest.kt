package com.meudinheiro.domain

import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Pessoa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Rateios.Cota
import com.meudinheiro.domain.Rateios.Modo
import com.meudinheiro.domain.Rateios.Participante
import com.meudinheiro.domain.Rateios.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R59 — MESMOS cenários e números de portal/src/lib/finance/rateios.test.ts. */
class RateiosTest {

    private fun ms(ano: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private fun p(id: Int, valor: Long? = null, pct: Long? = null) = Participante(id, valor, pct)

    private fun erro(r: Resultado) = r is Resultado.Erro

    @Test fun `divisao igual - sobra fica com voce`() {
        assertEquals(Resultado.Ok(listOf(Cota(1, 3333), Cota(2, 3333)), 3334), Rateios.ratear(10000, Modo.IGUAL, listOf(p(1), p(2))))
        assertEquals(Resultado.Ok(listOf(Cota(1, 5000)), 5001), Rateios.ratear(10001, Modo.IGUAL, listOf(p(1))))
    }

    @Test fun `por valor - voce fica com o resto, recusa soma acima do total e valor zerado`() {
        assertEquals(
            Resultado.Ok(listOf(Cota(1, 3000), Cota(2, 2500)), 4500),
            Rateios.ratear(10000, Modo.VALOR, listOf(p(1, valor = 3000), p(2, valor = 2500)))
        )
        assertTrue(erro(Rateios.ratear(10000, Modo.VALOR, listOf(p(1, valor = 6000), p(2, valor = 5000)))))
        assertEquals(Resultado.Ok(listOf(Cota(1, 10000)), 0), Rateios.ratear(10000, Modo.VALOR, listOf(p(1, valor = 10000))))
        assertTrue(erro(Rateios.ratear(10000, Modo.VALOR, listOf(p(1, valor = 0)))))
    }

    @Test fun `por percentual em decimos - soma exata, piso por pessoa, sobra no pagador`() {
        val r = Rateios.ratear(10001, Modo.PERCENTUAL, listOf(p(1, pct = 333), p(2, pct = 333)), 334)
        assertEquals(Resultado.Ok(listOf(Cota(1, 3330), Cota(2, 3330)), 3341), r)
        val ok = r as Resultado.Ok
        assertEquals(10001L, ok.cotas.sumOf { it.centavos } + ok.voce)
    }

    @Test fun `recusa percentuais que nao fecham 100`() {
        fun duas(a: Long, b: Long, voce: Long) = Rateios.ratear(10000, Modo.PERCENTUAL, listOf(p(1, pct = a), p(2, pct = b)), voce)
        assertEquals(Resultado.Erro("Os percentuais precisam somar 100% (somam 90,0%)."), duas(500, 400, 0))
        assertTrue(erro(duas(600, 500, 0)))
        assertEquals(1000L, (duas(500, 400, 100) as Resultado.Ok).voce)
        assertTrue(erro(duas(600, 600, -200)))
        assertTrue(erro(Rateios.ratear(10000, Modo.PERCENTUAL, listOf(p(1, pct = 500)))))
    }

    @Test fun `recusa entradas invalidas`() {
        assertTrue(erro(Rateios.ratear(0, Modo.IGUAL, listOf(p(1)))))
        assertTrue(erro(Rateios.ratear(100, Modo.IGUAL, emptyList())))
        assertTrue(erro(Rateios.ratear(100, Modo.IGUAL, listOf(p(1), p(1)))))
        assertTrue(erro(Rateios.ratear(2, Modo.IGUAL, listOf(p(1), p(2))))) // cota zerada
    }

    private fun acerto(id: Int, pessoa: Int, tipo: String, valor: Double, desc: String, data: Long, quitado: Boolean = false, despesaId: Long? = null, grupo: String? = null) =
        Acerto(id = id, pessoaId = pessoa, tipo = tipo, valor = valor, descricao = desc, data = data, despesaId = despesaId, quitado = quitado, grupoRateio = grupo)

    @Test fun `saldo liquido por pessoa - a receber menos a pagar, so pendentes`() {
        val acertos = listOf(
            acerto(1, 1, "A_RECEBER", 33.33, "x", 1), acerto(2, 1, "A_PAGAR", 10.0, "y", 2),
            acerto(3, 1, "A_RECEBER", 99.0, "z", 3, quitado = true), acerto(4, 2, "A_PAGAR", 5.0, "w", 5)
        )
        val s = Rateios.saldosPorPessoa(listOf(Pessoa(1, "Ana"), Pessoa(2, "Bia")), acertos)
        assertEquals(3333L, s[0].aReceber)
        assertEquals(1000L, s[0].aPagar)
        assertEquals(2333L, s[0].liquido)
        assertEquals(2, s[0].pendentes.size)
        assertEquals(1, s[0].quitados.size)
        assertEquals(-500L, s[1].liquido)
    }

    @Test fun `texto de cobranca - itens por data, total e PIX so se informado`() {
        val acertos = listOf(
            acerto(1, 1, "A_RECEBER", 33.33, "Pizza", ms(2025, 6, 10)),
            acerto(2, 1, "A_RECEBER", 20.0, "Uber", ms(2025, 6, 2)),
            acerto(3, 1, "A_RECEBER", 50.0, "Pago", ms(2025, 6, 3), quitado = true),
            acerto(4, 1, "A_PAGAR", 7.0, "Eu devo", ms(2025, 6, 4))
        )
        val base = "Olá, Ana! Segue o que ficou pendente entre nós:\n- 02/06 Uber: R$ 20,00\n- 10/06 Pizza: R$ 33,33\n\nTotal: R$ 53,33"
        assertEquals("$base\nChave PIX: ana@pix.com", Rateios.textoCobranca("Ana", acertos, "ana@pix.com"))
        assertEquals(base, Rateios.textoCobranca("Ana", acertos, "  "))
        assertNull(Rateios.textoCobranca("Ana", listOf(acertos[2], acertos[3])))
    }

    @Test fun `resumo e gasto liquido - total menos a receber de terceiros, orfao fora dos totais`() {
        val d = Despesa(
            id = 200, descricao = "Pizza", valor = 100.0, data = Date(ms(2025, 6, 10)), categoria = "Alimentação", conta = "111", pic = "p",
            tipo = TipoDespesa.DEBITO, mes = 6, ano = 2025, pago = true
        )
        val acertos = listOf(
            acerto(1, 1, "A_RECEBER", 33.33, "Pizza", d.data.time, despesaId = 200, grupo = "rateio:200"),
            acerto(2, 2, "A_RECEBER", 33.33, "Pizza", d.data.time, despesaId = 200, grupo = "rateio:200", quitado = true)
        )
        val r = Rateios.resumo(listOf(d), acertos)
        assertEquals(10000L, r.totalRateado)
        assertEquals(6666L, r.aReceberTerceiros)
        assertEquals(3334L, r.liquido)
        assertEquals(3333L, r.itens[0].pendente)
        assertEquals(3334L, Rateios.gastoLiquido(d, acertos))
        // lançamento removido: acerto tolerado, listado como órfão e fora dos totais
        val orfao = Rateios.resumo(emptyList(), acertos)
        assertTrue(orfao.itens.single().orfao)
        assertEquals(0L, orfao.totalRateado)
        assertEquals(0L, orfao.liquido)
        // período
        assertEquals(1, Rateios.resumo(listOf(d), acertos, ms(2025, 6, 1)..ms(2025, 6, 30)).itens.size)
        assertEquals(0, Rateios.resumo(listOf(d), acertos, ms(2025, 7, 1)..ms(2025, 7, 31)).itens.size)
    }
}
