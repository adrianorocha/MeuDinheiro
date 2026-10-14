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

    // ---------------------------------------------------------------- R68 (mesmos cenários de rateios.test.ts)

    private fun desp(id: Long, desc: String, valor: Double, tipo: TipoDespesa = TipoDespesa.DEBITO, natureza: String = Natureza.NORMAL) =
        Despesa(id = id, descricao = desc, valor = valor, data = Date(ms(2025, 6, 10)), categoria = "Cat", conta = "111", pic = "p", tipo = tipo, mes = 6, ano = 2025, pago = true, natureza = natureza)

    private val lanc = listOf(
        desp(100, "Saldo inicial", 1000.0, TipoDespesa.CREDITO, Natureza.SALDO_INICIAL),
        desp(200, "Pizza", 100.0), desp(201, "Cinema", 60.0),
        desp(202, "Transferência", 30.0, natureza = Natureza.TRANSFERENCIA), desp(203, "Pagamento de fatura", 30.0, natureza = Natureza.PAGAMENTO_FATURA),
        desp(204, "Aporte meta", 30.0, natureza = Natureza.APORTE_META), desp(205, "Resgate meta", 30.0, TipoDespesa.CREDITO, Natureza.RESGATE_META),
        desp(206, "Ajuste", 30.0, natureza = Natureza.AJUSTE), desp(300, "Salário", 500.0, TipoDespesa.CREDITO)
    )
    private val gente = listOf(Pessoa(1, "Ana"), Pessoa(2, "Bia"))

    private fun vincular(acertos: List<Acerto>, pessoa: Int, tipo: String, vararg itens: Rateios.ItemVinculo) =
        Rateios.vincular({ id -> lanc.firstOrNull { it.id == id } }, acertos, gente, pessoa, tipo, itens.toList())

    private fun ok(r: Rateios.ResultadoVinculo) = (r as Rateios.ResultadoVinculo.Ok).acertos
    private fun item(id: Long, valor: Double? = null) = Rateios.ItemVinculo(id, valor)
    private fun naoOk(r: Rateios.ResultadoVinculo) = r is Rateios.ResultadoVinculo.Erro
    private fun comIds(l: List<Acerto>) = l.mapIndexed { i, a -> a.copy(id = i + 1) }

    @Test fun `R68 vincula 1 lancamento - so cria acerto com dados do lancamento`() {
        val r = ok(vincular(emptyList(), 1, "A_RECEBER", item(200)))
        assertEquals(1, r.size)
        assertEquals(Acerto(id = 0, pessoaId = 1, tipo = "A_RECEBER", valor = 100.0, descricao = "Pizza", data = ms(2025, 6, 10), despesaId = 200, quitado = false, quitadoEm = null, grupoRateio = "vinculo:200"), r[0])
        assertEquals(10000L, Rateios.saldosPorPessoa(gente, comIds(r))[0].liquido)
    }

    @Test fun `R68 vincula varios com valor parcial e A_PAGAR sem o teto de A_RECEBER`() {
        val r = ok(vincular(emptyList(), 1, "A_RECEBER", item(200, 40.5), item(201)))
        assertEquals(listOf(40.5, 60.0), r.map { it.valor })
        assertTrue(vincular(comIds(r), 2, "A_PAGAR", item(200)) is Rateios.ResultadoVinculo.Ok)
        assertTrue(naoOk(vincular(emptyList(), 1, "A_RECEBER", item(200, 100.01))))
        assertTrue(naoOk(vincular(emptyList(), 1, "A_RECEBER", item(200, 0.0))))
        assertTrue(naoOk(vincular(emptyList(), 1, "A_PAGAR", item(200, 100.01))))
    }

    @Test fun `R68 teto - A_RECEBER de outras pessoas conta e o indicador nunca fica negativo`() {
        val a = comIds(ok(vincular(emptyList(), 1, "A_RECEBER", item(200, 70.0))))
        assertEquals(7000L, Rateios.comprometidoAReceber(a, 200))
        assertTrue(naoOk(vincular(a, 2, "A_RECEBER", item(200, 30.01))))
        val cabe = a + comIds(ok(vincular(a, 2, "A_RECEBER", item(200, 30.0)))).map { it.copy(id = 9) }
        assertEquals(0L, Rateios.gastoLiquido(lanc[1], cabe))
    }

    @Test fun `R68 recusa naturezas internas, inexistentes e repetidos`() {
        for (id in listOf(100L, 202L, 203L, 204L, 205L, 206L)) assertTrue(naoOk(vincular(emptyList(), 1, "A_PAGAR", item(id))))
        assertEquals(Rateios.ResultadoVinculo.Erro("Lançamento não encontrado."), vincular(emptyList(), 1, "A_RECEBER", item(999)))
        assertEquals(Rateios.ResultadoVinculo.Erro("Pessoa não encontrada."), vincular(emptyList(), 9, "A_RECEBER", item(200)))
        assertTrue(naoOk(vincular(emptyList(), 1, "A_RECEBER")))
        assertTrue(naoOk(vincular(emptyList(), 1, "A_RECEBER", item(200), item(200))))
    }

    @Test fun `R68 duplicidade - mesma pessoa e tipo recusa, outro tipo ou pessoa passa`() {
        val a = comIds(ok(vincular(emptyList(), 1, "A_RECEBER", item(200, 10.0))))
        val dup = vincular(a, 1, "A_RECEBER", item(200, 5.0))
        assertTrue((dup as Rateios.ResultadoVinculo.Erro).mensagem.contains("já vinculado"))
        assertTrue(vincular(a, 1, "A_PAGAR", item(200, 5.0)) is Rateios.ResultadoVinculo.Ok)
        assertTrue(vincular(a, 2, "A_RECEBER", item(200, 5.0)) is Rateios.ResultadoVinculo.Ok)
    }

    @Test fun `R68 atomicidade - item invalido nao devolve nenhum acerto`() {
        assertTrue(naoOk(vincular(emptyList(), 1, "A_RECEBER", item(200), item(202), item(201))))
        assertTrue(naoOk(vincular(emptyList(), 1, "A_RECEBER", item(200), item(201, 999.0))))
    }

    @Test fun `R68 receita so aceita A_PAGAR`() {
        assertTrue(naoOk(vincular(emptyList(), 1, "A_RECEBER", item(300))))
        val r = ok(vincular(emptyList(), 1, "A_PAGAR", item(300, 250.0)))
        assertEquals(-25000L, Rateios.saldosPorPessoa(gente, comIds(r))[0].liquido)
    }

    @Test fun `R68 indicador reflete o vinculo`() {
        val a = comIds(ok(vincular(emptyList(), 1, "A_RECEBER", item(200, 35.5))))
        assertEquals(6450L, Rateios.gastoLiquido(lanc[1], a))
        val r = Rateios.resumo(lanc, a)
        assertEquals(10000L, r.totalRateado)
        assertEquals(3550L, r.aReceberTerceiros)
        assertEquals(6450L, r.liquido)
        assertEquals(3550L, r.itens[0].pendente)
    }
}
