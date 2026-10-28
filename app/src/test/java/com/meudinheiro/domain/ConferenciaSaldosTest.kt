package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.ConferenciaSaldos.InconsistenciaTipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class ConferenciaSaldosTest {
    private val agora = Calendar.getInstance().apply { clear(); set(2026, 5, 15, 12, 0, 0) }.timeInMillis
    private val dia = 86_400_000L

    private fun conta(id: Int, numero: String, saldo: Double) =
        ContaSaldo(id = id, saldo = saldo, banco = "Banco$id", pic = "p", agencia = "1", conta = numero, titular = "")

    private fun cartao(id: Int, contaId: Int, limite: Double, gravado: Double, principal: Int? = null) = Cartao(
        id = id, nome = "Cartao$id", finalCartao = "000$id", tipo = "CRÉDITO", limiteDisponivel = gravado, limiteTotal = limite,
        diaFechamento = 25, diaVencimento = 5, contaId = contaId, cartaoPrincipalId = principal
    )

    private fun d(
        id: Long, valor: Double, tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = true, conta: String = "111",
        natureza: String = Natureza.NORMAL, cartao: Int? = null, data: Long = agora - dia, desc: String = "x", grupo: String? = null
    ) = Despesa(
        id = id, descricao = desc, valor = valor, data = Date(data), categoria = "Outros", conta = conta, pic = "p", tipo = tipo,
        mes = 1, ano = 2026, cartaoId = cartao, pago = pago, natureza = natureza, grupoId = grupo
    )

    private val C = TipoDespesa.CREDITO

    private fun cenario(): List<Despesa> = listOf(
        d(1, 1000.0, C, natureza = Natureza.SALDO_INICIAL),
        d(2, 5000.0, C),                                   // receita
        d(3, 300.5),                                       // despesa
        d(4, 200.0, natureza = Natureza.PAGAMENTO_FATURA),
        d(5, 100.0, natureza = Natureza.TRANSFERENCIA),
        d(6, 40.0, C, natureza = Natureza.TRANSFERENCIA),
        d(7, 50.0, natureza = Natureza.APORTE_META),
        d(8, 10.0, C, natureza = Natureza.RESGATE_META),
        d(9, 7.25, natureza = Natureza.AJUSTE),
        // pendências
        d(10, 800.0, C, pago = false),                     // receita prevista
        d(11, 100.0, pago = false, data = agora - 5 * dia),     // atrasada
        d(12, 200.0, pago = false, data = agora + 10 * dia),    // a vencer
        d(13, 400.0, pago = false, data = agora + 90 * dia),    // futura
        d(14, 150.0, cartao = 1, pago = false),            // compra de cartão em aberto
        d(15, 30.0, C, cartao = 1, pago = false)           // estorno no cartão
    )

    private val contas = listOf(conta(1, "111", 5000.0))
    private val cartoes = listOf(cartao(1, 1, 1000.0, 1000.0))

    @Test fun `decomposicao fecha com o saldo calculado R3`() {
        val rel = ConferenciaSaldos.auditar(contas, cartoes, cenario(), agora)
        val c = rel.contas.single()
        val esperado = Financas.saldoConta("111", cenario())
        assertEquals(esperado, c.saldoCalculado, 0.0)
        assertEquals(Dinheiro.centavos(esperado), c.somaDecomposicaoC)
        // 1000 + 5000 - 300,50 - 200 - 100 + 40 - 50 + 10 - 7,25
        assertEquals(5392.25, c.saldoCalculado, 0.0)
        assertEquals(1000.0, c.decomposicao.first { it.tipo == ConferenciaSaldos.LinhaTipo.SALDO_INICIAL }.valor, 0.0)
        assertEquals(-300.5, c.decomposicao.first { it.tipo == ConferenciaSaldos.LinhaTipo.DESPESAS }.valor, 0.0)
    }

    @Test fun `pendencias da conta separam atrasadas a vencer futuras e cartao`() {
        val p = ConferenciaSaldos.auditar(contas, cartoes, cenario(), agora).contas.single().pendencias
        assertEquals(80000L, p.receitasPrevistasC)
        assertEquals(10000L, p.despesasAtrasadasC)
        assertEquals(20000L, p.despesasAVencerC)
        assertEquals(40000L, p.despesasFuturasC)
        assertEquals(12000L, p.comprasCartaoEmAbertoC) // 150 - 30
    }

    @Test fun `detecta divergencia de cache de saldo e de limite`() {
        val rel = ConferenciaSaldos.auditar(contas, cartoes, cenario(), agora)
        assertTrue(rel.contas.single().divergente)            // gravado 5000 x calculado 5392,25
        assertEquals(-392.25, rel.contas.single().diferenca, 0.0)
        val g = rel.cartoes.single()
        assertEquals(880.0, g.limiteCalculado, 0.0)            // 1000 - 150 + 30
        assertTrue(g.divergente)
        assertFalse(rel.tudoConfere)
        val ok = ConferenciaSaldos.auditar(listOf(conta(1, "111", 5392.25)), listOf(cartao(1, 1, 1000.0, 880.0)), cenario(), agora)
        assertTrue(ok.tudoConfere)
    }

    @Test fun `limite considera o grupo fisico mais virtual`() {
        val cs = listOf(cartao(1, 1, 1000.0, 0.0), cartao(2, 1, 0.0, 0.0, principal = 1))
        val ds = listOf(d(1, 100.0, cartao = 1, pago = false), d(2, 50.0, cartao = 2, pago = false))
        val g = ConferenciaSaldos.auditar(contas, cs, ds, agora).cartoes.single()
        assertEquals(850.0, g.limiteCalculado, 0.0)
        assertEquals(2, g.gravados.size)
        assertEquals(150.0, g.emAbertoTotal, 0.0)
    }

    @Test fun `totais batem com KPIs R5 e a explicacao diz porque entradas menos saidas difere do saldo`() {
        val ds = cenario()
        val rel = ConferenciaSaldos.auditar(contas, cartoes, ds, agora)
        val k = Financas.kpisPeriodo(ds, null, null)
        assertEquals(k.receitasRealizadas, rel.totais.entradasRealizadas, 0.0)
        assertEquals(k.receitasPrevistas, rel.totais.entradasPrevistas, 0.0)
        assertEquals(k.despesasPagas, rel.totais.saidasPagas, 0.0)
        assertEquals(k.despesasPendentes, rel.totais.saidasPendentes, 0.0)
        assertEquals(k.despesasTotal, rel.totais.saidasTotal, 0.0)
        assertEquals(k.resultado, rel.totais.entradasMenosSaidas, 0.0)
        assertTrue(rel.totais.saidasPendentes > 0)
        assertTrue(rel.totais.entradasMenosSaidas != rel.totais.saldoCalculado)
        val frase = rel.explicacao { "R$ $it" }
        assertTrue(frase.contains("Por isso Entradas"))
        assertTrue(frase.contains("pendentes"))
    }

    @Test fun `totais do periodo respeitam o intervalo`() {
        val ds = listOf(d(1, 100.0, C, data = agora), d(2, 999.0, C, data = agora - 60 * dia), d(3, 40.0, pago = false, data = agora))
        val rel = ConferenciaSaldos.auditar(contas, cartoes, ds, agora, inicio = agora - dia, fim = agora + dia)
        assertEquals(100.0, rel.totaisPeriodo!!.entradasRealizadas, 0.0)
        assertEquals(40.0, rel.totaisPeriodo!!.saidasPendentes, 0.0)
        assertEquals(1099.0, rel.totais.entradasRealizadas, 0.0)
    }

    @Test fun `detecta inconsistencias de dados sem lancar excecao`() {
        val cs = listOf(cartao(1, 1, 1000.0, 1000.0), cartao(5, 1, 0.0, 0.0, principal = 99))
        val ds = listOf(
            d(1, 10.0, conta = "999"),
            d(2, 10.0, cartao = 77),
            d(3, 0.0),
            d(4, Double.NaN),
            d(5, 10.0, natureza = "XYZ"),
            d(6, 10.0), d(6, 20.0)
        )
        val rel = ConferenciaSaldos.auditar(contas, cs, ds, agora)
        val tipos = rel.inconsistencias.map { it.tipo }.toSet()
        assertTrue(tipos.containsAll(setOf(
            InconsistenciaTipo.CONTA_INEXISTENTE, InconsistenciaTipo.CARTAO_INEXISTENTE, InconsistenciaTipo.VALOR_INVALIDO,
            InconsistenciaTipo.NATUREZA_DESCONHECIDA, InconsistenciaTipo.ID_DUPLICADO, InconsistenciaTipo.VIRTUAL_SEM_PRINCIPAL
        )))
        assertEquals(2, rel.inconsistencias.count { it.tipo == InconsistenciaTipo.VALOR_INVALIDO })
        assertTrue(rel.inconsistencias.all { it.id.isNotBlank() && it.descricao.isNotBlank() })
    }

    @Test fun `parcelas com buraco e pagas fora de ordem sao informativas`() {
        val g = "parc:abc"
        val ds = listOf(
            d(1, 10.0, desc = "TV (1/4)", grupo = g, pago = true),
            d(2, 10.0, desc = "TV (3/4)", grupo = g, pago = false),
            d(3, 10.0, desc = "TV (4/4)", grupo = g, pago = true)
        )
        val rel = ConferenciaSaldos.auditar(contas, cartoes, ds, agora)
        val tipos = rel.inconsistencias.map { it.tipo }
        assertTrue(InconsistenciaTipo.PARCELAS_INCOMPLETAS in tipos)
        assertTrue(InconsistenciaTipo.PARCELAS_FORA_DE_ORDEM in tipos)
        assertTrue(rel.inconsistencias.all { it.severidade == ConferenciaSaldos.Severidade.INFO })
        // parcelamento íntegro não gera nada
        val ok = listOf(d(1, 10.0, desc = "TV (1/2)", grupo = g), d(2, 10.0, desc = "TV (2/2)", grupo = g, pago = false))
        assertTrue(ConferenciaSaldos.auditar(contas, cartoes, ok, agora).inconsistencias.isEmpty())
    }
}
