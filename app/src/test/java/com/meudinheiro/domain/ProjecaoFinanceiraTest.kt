package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.ProjecaoFinanceira.ContaRef
import com.meudinheiro.domain.ProjecaoFinanceira.EntradaLimiteDiario
import com.meudinheiro.domain.ProjecaoFinanceira.EventoProjecao
import com.meudinheiro.domain.ProjecaoFinanceira.GastoHoje
import com.meudinheiro.domain.ProjecaoFinanceira.StatusLimite
import com.meudinheiro.domain.ProjecaoFinanceira.TipoEvento
import com.meudinheiro.domain.ProjecaoFinanceira.brlCentavos
import com.meudinheiro.domain.ProjecaoFinanceira.calcularLimiteDiario
import com.meudinheiro.domain.ProjecaoFinanceira.calendarioDosDados
import com.meudinheiro.domain.ProjecaoFinanceira.dataDeSerial
import com.meudinheiro.domain.ProjecaoFinanceira.diaDaSemana
import com.meudinheiro.domain.ProjecaoFinanceira.diaSerial
import com.meudinheiro.domain.ProjecaoFinanceira.eventosPrevistos
import com.meudinheiro.domain.ProjecaoFinanceira.limiteDiarioDosDados
import com.meudinheiro.domain.ProjecaoFinanceira.projetarMes
import com.meudinheiro.domain.ProjecaoFinanceira.ultimoDiaSerial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R51/R52 — mesmos cenários do teste do portal (`projecao.test.ts`). */
class ProjecaoFinanceiraTest {

    private fun S(a: Int, m: Int, d: Int) = diaSerial(a, m, d)
    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis

    private fun ev(dia: Int, centavos: Long, tipo: TipoEvento? = null) = EventoProjecao(
        dia = dia, centavos = centavos, tipo = tipo ?: if (centavos >= 0) TipoEvento.RECEITA else TipoEvento.DESPESA,
        descricao = "x", origem = "Banco A"
    )

    private fun entrada(
        saldo: Long = 0, eventos: List<EventoProjecao> = emptyList(), hoje: Int = S(2025, 3, 10), fimDoMes: Int = S(2025, 3, 31),
        ate: Boolean = false, gastos: List<GastoHoje> = emptyList()
    ) = EntradaLimiteDiario(saldo, eventos, hoje, fimDoMes, ate, gastos)

    private val contas = listOf(ContaRef(1, "111", "Banco A"))

    private fun desp(
        valor: Double, data: Long, tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = true, cartaoId: Int? = null,
        natureza: String = Natureza.NORMAL, descricao: String = "Item", conta: String = "111", id: Long = 0
    ) = Despesa(
        id = id, descricao = descricao, valor = valor, data = Date(data), categoria = "Geral", conta = conta, pic = "",
        tipo = tipo, mes = 1, ano = 2025, cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    private fun cartao(id: Int = 10, fech: Int = 25, venc: Int = 5, nome: String = "Nubank", tipo: String = "CRÉDITO") = Cartao(
        id = id, nome = nome, finalCartao = "1234", tipo = tipo, limiteDisponivel = 1000.0, limiteTotal = 1000.0,
        diaFechamento = fech, diaVencimento = venc, contaId = 1
    )

    private fun regra(
        dia: Int = 20, valor: Double = 1000.0, tipo: TipoDespesa = TipoDespesa.DEBITO, ultima: Long? = null,
        pausada: Boolean = false, ate: Long? = null, cartaoId: Int? = null, id: Int = 1, descricao: String = "Aluguel"
    ) = DespesaFixa(
        id = id, descricao = descricao, valor = valor, conta = "111", categoria = "Moradia", pic = "", tipo = tipo,
        diaVencimento = dia, ultimaDataLancamento = ultima?.let { Date(it) }, cartaoId = cartaoId, pausada = pausada,
        pausadaAte = ate?.let { Date(it) }
    )

    private fun eventos(
        despesas: List<Despesa> = emptyList(), fixas: List<DespesaFixa> = emptyList(), cartoes: List<Cartao> = emptyList(),
        agora: Long, ate: Int, filtro: Set<String>? = null, cs: List<ContaRef> = contas
    ) = eventosPrevistos(cs, cartoes, despesas, fixas, agora, ate, filtro)

    // ------------------------------------------------------------ datas

    @Test fun `datas seriais`() {
        assertEquals(0, S(1970, 1, 1))
        assertEquals(ProjecaoFinanceira.DataCivil(2024, 2, 29), dataDeSerial(S(2024, 2, 29)))
        assertEquals(ProjecaoFinanceira.DataCivil(2026, 1, 1), dataDeSerial(S(2025, 12, 31) + 1))
        assertEquals(28, ultimoDiaSerial(2, 2025) - S(2025, 2, 1) + 1)
        assertEquals(29, ultimoDiaSerial(2, 2024) - S(2024, 2, 1) + 1)
        assertEquals(S(2025, 12, 31), ultimoDiaSerial(12, 2025))
        assertEquals(1, diaDaSemana(S(2025, 3, 10)))
    }

    @Test fun `formata reais`() {
        assertEquals("R$ 1.234,56", brlCentavos(123456))
        assertEquals("R$ 0,05", brlCentavos(5))
        assertEquals("-R$ 10,00", brlCentavos(-1000))
    }

    // ------------------------------------------------------------ R51 pura

    @Test fun `sem receitas - saldo menos contas dividido pelos dias`() {
        val r = calcularLimiteDiario(entrada(saldo = 100000, eventos = listOf(ev(S(2025, 3, 20), -30000))))
        assertEquals(70000L, r.disponivelCentavos)
        assertEquals(22, r.diasRestantes)
        assertEquals(3181L, r.limiteDiarioCentavos)
        assertEquals(3181L, r.restanteHojeCentavos)
        assertEquals(StatusLimite.VERDE, r.status)
        assertEquals("Hoje você pode gastar até R$ 31,81.", r.mensagem)
    }

    @Test fun `mes no vermelho explica quanto falta`() {
        val r = calcularLimiteDiario(entrada(saldo = 10000, eventos = listOf(ev(S(2025, 3, 15), -25000))))
        assertTrue(r.mesNoVermelho)
        assertEquals(0L, r.limiteDiarioCentavos)
        assertEquals(15000L, r.faltaCentavos)
        assertEquals(StatusLimite.VERMELHO, r.status)
        assertEquals("Mês no vermelho: faltam R$ 150,00 para cobrir tudo até 31/03.", r.mensagem)
    }

    @Test fun `disponivel zero tambem e mes no vermelho`() {
        val r = calcularLimiteDiario(entrada(saldo = 10000, eventos = listOf(ev(S(2025, 3, 15), -10000))))
        assertTrue(r.mesNoVermelho)
        assertEquals("Mês no vermelho: não sobra nada para gastar até 31/03.", r.mensagem)
    }

    @Test fun `receitas previstas entram e as de depois do fim do mes nao`() {
        val r = calcularLimiteDiario(entrada(eventos = listOf(ev(S(2025, 3, 31), 220000), ev(S(2025, 4, 1), 999999))))
        assertEquals(220000L, r.disponivelCentavos)
        assertEquals(10000L, r.limiteDiarioCentavos)
    }

    @Test fun `ate a proxima receita termina no dia anterior`() {
        val r = calcularLimiteDiario(
            entrada(saldo = 100000, ate = true, eventos = listOf(ev(S(2025, 3, 12), -20000), ev(S(2025, 3, 15), 500000), ev(S(2025, 3, 20), -1000)))
        )
        assertEquals(S(2025, 3, 14), r.fimPeriodo)
        assertEquals(5, r.diasRestantes)
        assertEquals(80000L, r.disponivelCentavos)
        assertEquals(16000L, r.limiteDiarioCentavos)
    }

    @Test fun `ate a proxima receita sem receita cai no fim do mes`() {
        assertEquals(S(2025, 3, 31), calcularLimiteDiario(entrada(saldo = 22000, ate = true)).fimPeriodo)
    }

    @Test fun `ultimo dia do mes`() {
        val r = calcularLimiteDiario(entrada(hoje = S(2025, 3, 31), saldo = 5000))
        assertEquals(1, r.diasRestantes)
        assertEquals(5000L, r.limiteDiarioCentavos)
    }

    @Test fun `fevereiro bissexto e nao bissexto`() {
        val a = calcularLimiteDiario(entrada(hoje = S(2024, 2, 15), fimDoMes = S(2024, 2, 29), saldo = 150000))
        assertEquals(15, a.diasRestantes); assertEquals(10000L, a.limiteDiarioCentavos)
        val b = calcularLimiteDiario(entrada(hoje = S(2025, 2, 15), fimDoMes = S(2025, 2, 28), saldo = 140000))
        assertEquals(14, b.diasRestantes); assertEquals(10000L, b.limiteDiarioCentavos)
    }

    @Test fun `virada de ano com proxima receita em janeiro`() {
        val r = calcularLimiteDiario(
            entrada(hoje = S(2025, 12, 28), fimDoMes = S(2025, 12, 31), saldo = 80000, ate = true, eventos = listOf(ev(S(2026, 1, 5), 300000)))
        )
        assertEquals(S(2026, 1, 4), r.fimPeriodo)
        assertEquals(8, r.diasRestantes)
        assertEquals(10000L, r.limiteDiarioCentavos)
        assertEquals("Hoje você pode gastar até R$ 100,00.", r.mensagem)
    }

    @Test fun `status amarelo verde e vermelho sem descontar duas vezes`() {
        val hoje = S(2025, 3, 22) // 10 dias
        val amarelo = calcularLimiteDiario(entrada(hoje = hoje, saldo = 92000, gastos = listOf(GastoHoje(8000, null))))
        assertEquals(10000L, amarelo.limiteDiarioCentavos)
        assertEquals(StatusLimite.AMARELO, amarelo.status)
        assertEquals(2000L, amarelo.restanteHojeCentavos)
        assertEquals("Atenção: restam R$ 20,00 do limite de hoje.", amarelo.mensagem)
        val verde = calcularLimiteDiario(entrada(hoje = hoje, saldo = 93000, gastos = listOf(GastoHoje(7000, null))))
        assertEquals(StatusLimite.VERDE, verde.status)
        val verm = calcularLimiteDiario(entrada(hoje = hoje, saldo = 88000, gastos = listOf(GastoHoje(12000, null))))
        assertEquals(StatusLimite.VERMELHO, verm.status)
        assertFalse(verm.mesNoVermelho)
        assertEquals(-2000L, verm.restanteHojeCentavos)
        assertEquals("Hoje você já passou do limite em R$ 20,00.", verm.mensagem)
    }

    @Test fun `compra de hoje no cartao so e ja deduzida se a fatura vence no periodo`() {
        val hoje = S(2025, 3, 22)
        val fim = S(2025, 3, 31)
        val fora = calcularLimiteDiario(entrada(hoje = hoje, saldo = 100000, gastos = listOf(GastoHoje(5000, S(2025, 4, 5)))))
        assertEquals(10000L, fora.limiteDiarioCentavos)
        assertEquals(5000L, fora.gastoHojeCentavos)
        assertEquals(StatusLimite.VERDE, fora.status)
        val dentro = calcularLimiteDiario(
            entrada(hoje = hoje, saldo = 100000, eventos = listOf(ev(fim, -5000, TipoEvento.FATURA)), gastos = listOf(GastoHoje(5000, fim)))
        )
        assertEquals(95000L, dentro.disponivelCentavos)
        assertEquals(10000L, dentro.limiteDiarioCentavos)
    }

    // ------------------------------------------------------------ R52 pura

    private val hoje = S(2025, 3, 10)

    @Test fun `calendario acumula movimentos, dias passados sem projecao, menor saldo e alerta`() {
        val r = projetarMes(
            100000, listOf(ev(S(2025, 3, 20), -150000), ev(S(2025, 3, 28), 300000), ev(S(2025, 3, 31), -10000)), hoje, 3, 2025
        )
        assertEquals(31, r.dias.size)
        assertNull(r.dias[8].saldoProjetadoCentavos)
        assertEquals(100000L, r.dias[9].saldoProjetadoCentavos)
        assertTrue(r.dias[9].ehHoje)
        assertEquals(-50000L, r.dias[19].saldoProjetadoCentavos)
        assertTrue(r.dias[19].negativo)
        assertEquals(250000L, r.dias[27].saldoProjetadoCentavos)
        assertEquals(240000L, r.saldoFimDoMesCentavos)
        assertEquals(ProjecaoFinanceira.MenorSaldo(-50000, S(2025, 3, 20)), r.menorSaldo)
        assertEquals(S(2025, 3, 20), r.primeiroDiaNegativo)
        assertEquals("Conta ficará negativa em 20/03 antes do próximo recebimento.", r.alerta)
    }

    @Test fun `sem receitas o alerta informa que nao ha recebimento`() {
        val r = projetarMes(1000, listOf(ev(S(2025, 3, 12), -5000)), hoje, 3, 2025)
        assertEquals("Conta ficará negativa em 12/03 e não há recebimento previsto.", r.alerta)
    }

    @Test fun `mes sem saldo negativo nao gera alerta`() {
        val r = projetarMes(50000, listOf(ev(S(2025, 3, 12), -5000)), hoje, 3, 2025)
        assertNull(r.alerta)
        assertEquals(ProjecaoFinanceira.MenorSaldo(45000, S(2025, 3, 12)), r.menorSaldo)
    }

    @Test fun `mes futuro considera o que acontece antes dele`() {
        val r = projetarMes(100000, listOf(ev(S(2025, 3, 20), -40000), ev(S(2025, 4, 5), -30000)), hoje, 4, 2025)
        assertEquals(60000L, r.dias[0].saldoProjetadoCentavos)
        assertEquals(30000L, r.dias[4].saldoProjetadoCentavos)
        assertFalse(r.dias[0].passado)
    }

    @Test fun `mes passado nao tem projecao`() {
        val r = projetarMes(100000, emptyList(), hoje, 2, 2025)
        assertEquals(28, r.dias.size)
        assertTrue(r.dias.all { it.saldoProjetadoCentavos == null && it.passado })
        assertNull(r.menorSaldo)
        assertNull(r.alerta)
    }

    @Test fun `virada de ano e fevereiro bissexto`() {
        val d = S(2025, 12, 20)
        val r = projetarMes(10000, listOf(ev(S(2026, 1, 1), -20000)), d, 1, 2026)
        assertEquals(31, r.dias.size)
        assertEquals(-10000L, r.dias[0].saldoProjetadoCentavos)
        assertEquals("Conta ficará negativa em 01/01 e não há recebimento previsto.", r.alerta)
        assertEquals(29, projetarMes(0, emptyList(), d, 2, 2028).dias.size)
    }

    // ------------------------------------------------------------ eventos a partir dos dados

    @Test fun `recorrencia pausada e ignorada - ativa vira evento com clamp no fim do mes`() {
        val agora = ms(2025, 2, 10)
        val ate = S(2025, 3, 31)
        val e1 = eventos(fixas = listOf(regra(dia = 31)), agora = agora, ate = ate)
        assertEquals(listOf(S(2025, 2, 28), S(2025, 3, 31)), e1.map { it.dia })
        assertEquals(listOf(-100000L, -100000L), e1.map { it.centavos })
        assertTrue(e1.all { it.tipo == TipoEvento.RECORRENCIA_DESPESA })
        assertTrue(eventos(fixas = listOf(regra(dia = 31, pausada = true)), agora = agora, ate = ate).isEmpty())
        val comPrazo = eventos(fixas = listOf(regra(dia = 31, pausada = true, ate = ms(2025, 3, 15))), agora = agora, ate = ate)
        assertEquals(listOf(S(2025, 3, 31)), comPrazo.map { it.dia })
    }

    @Test fun `recorrencia ja lancada no mes nao duplica e receita recorrente e recebimento`() {
        val fixas = listOf(
            regra(id = 1, dia = 5, ultima = ms(2025, 12, 5)),
            regra(id = 2, descricao = "Salário", valor = 3000.0, tipo = TipoDespesa.CREDITO, dia = 5)
        )
        val e = eventos(fixas = fixas, agora = ms(2025, 12, 20), ate = S(2026, 1, 31))
        assertEquals(
            listOf(
                Triple("Salário", S(2025, 12, 20), 300000L), Triple("Salário", S(2026, 1, 5), 300000L),
                Triple("Aluguel", S(2026, 1, 5), -100000L)
            ),
            e.map { Triple(it.descricao, it.dia, it.centavos) }
        )
        assertEquals(
            listOf(TipoEvento.RECORRENCIA_RECEITA, TipoEvento.RECORRENCIA_RECEITA, TipoEvento.RECORRENCIA_DESPESA), e.map { it.tipo }
        )
    }

    @Test fun `fatura de cartao entra no vencimento com o total`() {
        val despesas = listOf(
            desp(200.0, ms(2025, 3, 10), cartaoId = 10, pago = false, descricao = "Compra A"),
            desp(100.0, ms(2025, 3, 11), cartaoId = 10, pago = false, descricao = "Compra B"),
            desp(999.0, ms(2025, 1, 11), cartaoId = 10, pago = true, descricao = "Antiga")
        )
        val e = eventos(despesas = despesas, cartoes = listOf(cartao()), agora = ms(2025, 3, 12), ate = S(2025, 4, 30))
        assertEquals(1, e.size)
        assertEquals(S(2025, 4, 5), e[0].dia)
        assertEquals(-30000L, e[0].centavos)
        assertEquals(TipoEvento.FATURA, e[0].tipo)
        assertEquals("Fatura Nubank", e[0].descricao)
        assertEquals(10, e[0].cartaoId)
    }

    @Test fun `fatura vencida vira atrasada e cai em hoje - pendencia de conta idem`() {
        val despesas = listOf(
            desp(80.0, ms(2025, 2, 10), cartaoId = 10, pago = false),
            desp(50.0, ms(2025, 3, 1), pago = false, descricao = "Luz")
        )
        val e = eventos(despesas = despesas, cartoes = listOf(cartao()), agora = ms(2025, 3, 12), ate = S(2025, 4, 30))
        assertTrue(e.all { it.dia == S(2025, 3, 12) && it.atrasado })
        assertEquals(listOf(-8000L, -5000L), e.map { it.centavos }.sorted())
    }

    @Test fun `compra recorrente no credito soma na fatura e nao na conta`() {
        val e = eventos(
            fixas = listOf(regra(descricao = "Streaming", valor = 50.0, dia = 10, cartaoId = 10)),
            cartoes = listOf(cartao()), agora = ms(2025, 3, 1), ate = S(2025, 4, 30)
        )
        assertEquals(listOf(TipoEvento.FATURA, TipoEvento.FATURA), e.map { it.tipo })
        assertEquals(listOf(S(2025, 4, 5), S(2025, 5, 5)), e.map { it.dia })
        assertEquals(listOf(-5000L, -5000L), e.map { it.centavos })
    }

    @Test fun `ignora pendencias que nao sao normais`() {
        val despesas = listOf(desp(10.0, ms(2025, 3, 20), pago = false, natureza = Natureza.TRANSFERENCIA))
        assertTrue(eventos(despesas = despesas, agora = ms(2025, 3, 12), ate = S(2025, 4, 30)).isEmpty())
    }

    @Test fun `filtro de contas`() {
        val cs = listOf(ContaRef(1, "111", "Banco A"), ContaRef(2, "222", "Banco B"))
        val despesas = listOf(
            desp(10.0, ms(2025, 3, 20), pago = false, conta = "111"),
            desp(20.0, ms(2025, 3, 20), pago = false, conta = "222")
        )
        val e = eventos(despesas = despesas, agora = ms(2025, 3, 12), ate = S(2025, 4, 30), filtro = setOf("222"), cs = cs)
        assertEquals(listOf(-2000L), e.map { it.centavos })
    }

    // ------------------------------------------------------------ sobre os dados completos

    @Test fun `cenario completo de limite diario`() {
        val despesas = listOf(
            desp(1000.0, ms(2025, 3, 1), tipo = TipoDespesa.CREDITO, pago = true, natureza = Natureza.SALDO_INICIAL),
            desp(500.0, ms(2025, 3, 15), tipo = TipoDespesa.CREDITO, pago = false, descricao = "Freela"),
            desp(200.0, ms(2025, 3, 20), pago = false, descricao = "Luz"),
            desp(300.0, ms(2025, 3, 5), pago = false, cartaoId = 10, descricao = "Compra")
        )
        val cartoes = listOf(cartao(fech = 25, venc = 28))
        val fixas = listOf(regra(valor = 100.0, dia = 25))
        val r = limiteDiarioDosDados(contas, cartoes, despesas, fixas, ms(2025, 3, 10))
        assertEquals(90000L, r.disponivelCentavos)
        assertEquals(22, r.diasRestantes)
        assertEquals(4090L, r.limiteDiarioCentavos)
        val ate = limiteDiarioDosDados(contas, cartoes, despesas, fixas, ms(2025, 3, 10), ateProximaReceita = true)
        assertEquals(S(2025, 3, 14), ate.fimPeriodo)
        assertEquals(100000L, ate.disponivelCentavos)
    }

    @Test fun `calendario sobre os dados - fatura em abril e saldo projetado`() {
        val despesas = listOf(
            desp(1000.0, ms(2025, 3, 1), tipo = TipoDespesa.CREDITO, pago = true),
            desp(300.0, ms(2025, 3, 10), pago = false, cartaoId = 10)
        )
        val r = calendarioDosDados(contas, listOf(cartao()), despesas, emptyList(), ms(2025, 3, 12), 4, 2025)
        assertEquals(100000L, r.dias[3].saldoProjetadoCentavos)
        assertEquals(70000L, r.dias[4].saldoProjetadoCentavos)
        assertEquals(TipoEvento.FATURA, r.dias[4].eventos[0].tipo)
        assertEquals(70000L, r.saldoFimDoMesCentavos)
    }
}
