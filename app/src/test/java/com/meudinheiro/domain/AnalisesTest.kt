package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Investimento
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Analises.StatusMeta
import com.meudinheiro.domain.Analises.StatusReserva
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class AnalisesTest {

    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis

    private fun lanc(
        valor: Double, desc: String = "x", categoria: String = "Alimentação", data: Long = ms(2026, 10, 10),
        tipo: TipoDespesa = TipoDespesa.DEBITO, conta: String = "111", pago: Boolean = true, cartaoId: Int? = null,
        natureza: String = Natureza.NORMAL
    ) = Despesa(
        descricao = desc, valor = valor, data = Date(data), categoria = categoria, conta = conta, pic = "p", tipo = tipo,
        mes = Financas.mesDe(data), ano = Financas.anoDe(data), cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    private val hoje = ms(2026, 10, 15)

    // ------------------------------------------------------------- R21

    @Test fun `sugere a categoria do historico para descricoes parecidas`() {
        val h = listOf(
            lanc(200.0, "Posto Shell Av Brasil", "Combustível", ms(2026, 9, 1)),
            lanc(180.0, "POSTO SHELL", "Combustível", ms(2026, 9, 15)),
            lanc(40.0, "Padaria Central", "Alimentação")
        )
        assertEquals("Combustível", Analises.sugerirCategoria("posto shell 123", h))
        assertEquals("Alimentação", Analises.sugerirCategoria("Padaria  Central!", h))
    }

    @Test fun `sem semelhanca nao sugere nada, e ignora lancamentos internos`() {
        val h = listOf(lanc(10.0, "Transferência para Itaú", "Transferência", natureza = Natureza.TRANSFERENCIA))
        assertNull(Analises.sugerirCategoria("Cinema", h))
        assertNull(Analises.sugerirCategoria("Transferência para Itaú", h))
        assertNull(Analises.sugerirCategoria("", h))
    }

    @Test fun `acentos e caixa nao importam, categoria mais frequente vence`() {
        val h = listOf(
            lanc(1.0, "Café da Manhã", "Lanche", ms(2026, 8, 1)),
            lanc(1.0, "Cafe da manha", "Lanche", ms(2026, 8, 2)),
            lanc(1.0, "Café da manhã", "Alimentação", ms(2026, 9, 1))
        )
        assertEquals("Lanche", Analises.sugerirCategoria("CAFE DA MANHA", h))
    }

    // ------------------------------------------------------------- R22

    @Test fun `alerta de 80 e de 100 so uma vez por mes`() {
        val a1 = Analises.alertasOrcamento(listOf("Lazer" to 0.85), "2026-10", emptySet())
        assertEquals(80, a1.single().limiar)
        assertTrue(Analises.alertasOrcamento(listOf("Lazer" to 0.9), "2026-10", Analises.chavesParaMarcar(a1.single())).isEmpty())

        val a2 = Analises.alertasOrcamento(listOf("Lazer" to 1.2), "2026-10", Analises.chavesParaMarcar(a1.single()))
        assertEquals(100, a2.single().limiar)
        val marcadas = Analises.chavesParaMarcar(a2.single())
        assertTrue("100 marca também o de 80", marcadas.any { it.endsWith("|80") })
        assertTrue(Analises.alertasOrcamento(listOf("Lazer" to 0.85), "2026-10", marcadas).isEmpty())
        assertEquals(1, Analises.alertasOrcamento(listOf("Lazer" to 0.85), "2026-11", marcadas).size) // outro mês avisa de novo
        assertTrue(Analises.alertasOrcamento(listOf("Lazer" to 0.79), "2026-10", emptySet()).isEmpty())
    }

    // ------------------------------------------------------------- R23

    @Test fun `duplicar cria copia em aberto de hoje e recusa lancamentos internos`() {
        val c = Analises.duplicar(lanc(50.0, "Luz", data = ms(2026, 9, 5)), hoje)!!
        assertEquals(0L, c.id); assertFalse(c.pago); assertEquals(hoje, c.dataMs); assertEquals(10, c.mes)
        assertNull(Analises.duplicar(lanc(50.0, natureza = Natureza.TRANSFERENCIA), hoje))
    }

    @Test fun `repetir mensal no dia 31 nao deriva e semanal soma 7 dias`() {
        val mensal = Analises.repetir(lanc(10.0, data = ms(2026, 1, 31)), 4, 1, Analises.UnidadeRepeticao.MESES)
        assertEquals(listOf(2 to 28, 3 to 31, 4 to 30, 5 to 31), mensal.map { Financas.mesDe(it.dataMs) to Calendar.getInstance().apply { timeInMillis = it.dataMs }.get(Calendar.DAY_OF_MONTH) })
        assertTrue(mensal.none { it.pago }); assertEquals(1, mensal.map { it.grupoId }.distinct().size)
        assertTrue(mensal.first().grupoId!!.startsWith("rep:"))

        val semanal = Analises.repetir(lanc(10.0, data = ms(2026, 10, 1)), 3, 2, Analises.UnidadeRepeticao.SEMANAS)
        assertEquals(listOf(15, 29, 12), semanal.map { Calendar.getInstance().apply { timeInMillis = it.dataMs }.get(Calendar.DAY_OF_MONTH) })
        assertEquals(listOf(10, 10, 11), semanal.map { it.mes })

        val diario = Analises.repetir(lanc(10.0, data = ms(2026, 10, 30)), 2, 1, Analises.UnidadeRepeticao.DIAS)
        assertEquals(listOf(10, 11), diario.map { it.mes })
    }

    @Test(expected = IllegalArgumentException::class) fun `repetir recusa n invalido`() {
        Analises.repetir(lanc(10.0), 0, 1, Analises.UnidadeRepeticao.DIAS)
    }

    @Test(expected = IllegalArgumentException::class) fun `repetir recusa lancamento interno`() {
        Analises.repetir(lanc(10.0, natureza = Natureza.APORTE_META), 2, 1, Analises.UnidadeRepeticao.DIAS)
    }

    // ------------------------------------------------------------- R24

    private val conta = ContaSaldo(id = 1, saldo = 0.0, banco = "Nubank", pic = "p", agencia = "0001", conta = "111", titular = "Ana")
    private val cartao = Cartao(id = 1, nome = "Visa Platinum", finalCartao = "9734", tipo = "CRÉDITO", limiteDisponivel = 1.0, limiteTotal = 1.0,
        diaFechamento = 1, diaVencimento = 1, contaId = 1)
    private val meta = Meta(id = 1, nome = "Viagem Europa", valorObjetivo = 1.0, valorGuardado = 0.0)

    @Test fun `busca por texto valor e data`() {
        val l = listOf(
            lanc(123.45, "Mercado Pão de Açúcar", "Supermercado", ms(2026, 10, 3)),
            lanc(10.0, "Uber", "Transporte", ms(2026, 9, 20))
        )
        fun b(t: String) = Analises.buscar(t, l, listOf(conta), listOf(cartao), listOf(meta))
        assertEquals(1, b("pao de acucar").lancamentos.size)
        assertEquals("Mercado Pão de Açúcar", b("123,45").lancamentos.single().descricao)
        assertEquals("Mercado Pão de Açúcar", b("123.45").lancamentos.single().descricao)
        assertEquals("Uber", b("20/09").lancamentos.single().descricao)
        assertEquals("Uber", b("20/09/2026").lancamentos.single().descricao)
        assertTrue(b("20/09/2025").lancamentos.isEmpty())
        assertEquals(2, b("nubank").lancamentos.size) // pelo banco da conta
        assertEquals(1, b("nubank").contas.size)
        assertEquals(1, b("9734").cartoes.size)
        assertEquals(1, b("europa").metas.size)
        assertTrue(b("").vazio); assertTrue(b("zzzz").vazio)
    }

    // ------------------------------------------------------------- R25

    @Test fun `reserva de emergencia em meses de despesa`() {
        val d = listOf(
            lanc(1000.0, data = ms(2026, 7, 10)), lanc(2000.0, data = ms(2026, 8, 10)), lanc(3000.0, data = ms(2026, 9, 10)),
            lanc(99999.0, data = ms(2026, 10, 10)) // mês corrente não entra na média
        )
        val inv = listOf(
            Investimento(nome = "CDB", tipo = "Renda Fixa", valorInvestido = 1.0, valorAtual = 5000.0),
            Investimento(nome = "PETR4", tipo = "Ações", valorInvestido = 1.0, valorAtual = 100000.0)
        )
        val r = Analises.reservaEmergencia(hoje, d, listOf(7000.0), inv)
        assertEquals(2000.0, r.mediaDespesas3m!!, 0.0)
        assertEquals(12000.0, r.liquidez, 0.0)
        assertEquals(6.0, r.meses!!, 1e-9)
        assertEquals(0.0, r.faltante, 0.0)
        assertEquals(StatusReserva.OK, r.status)

        val crit = Analises.reservaEmergencia(hoje, d, listOf(3000.0), emptyList())
        assertEquals(StatusReserva.CRITICO, crit.status); assertEquals(9000.0, crit.faltante, 0.0)
        assertEquals(StatusReserva.ATENCAO, Analises.reservaEmergencia(hoje, d, listOf(8000.0), emptyList()).status)
    }

    @Test fun `reserva ignora meses vazios e sem historico nao calcula`() {
        val d = listOf(lanc(600.0, data = ms(2026, 9, 10)))
        assertEquals(600.0, Analises.reservaEmergencia(hoje, d, listOf(1200.0), emptyList()).mediaDespesas3m!!, 0.0)
        val sem = Analises.reservaEmergencia(hoje, emptyList(), listOf(1200.0), emptyList())
        assertNull(sem.meses); assertEquals(StatusReserva.SEM_DADOS, sem.status)
    }

    // ------------------------------------------------------------- R26

    private fun mensal(desc: String, valor: Double, dia: Int, meses: List<Int>) =
        meses.map { lanc(valor, desc, "Lazer", ms(2026, it, dia)) }

    @Test fun `detecta assinatura mensal e ignora parcelas e gastos irregulares`() {
        val d = mensal("Netflix", 55.9, 8, listOf(6, 7, 8, 9, 10)) +
            mensal("Spotify 2026", 21.9, 12, listOf(8, 9, 10)) +
            mensal("Loja X (1/3)", 100.0, 5, listOf(8, 9, 10)) +
            listOf(lanc(30.0, "Mercado", "Alimentação", ms(2026, 8, 3)), lanc(300.0, "Mercado", "Alimentação", ms(2026, 9, 20)), lanc(90.0, "Mercado", "Alimentação", ms(2026, 10, 7))) +
            mensal("Revista antiga", 20.0, 5, listOf(3, 4, 5)) // última ocorrência há muito tempo
        val a = Analises.assinaturas(hoje, d, emptyList())
        assertEquals(setOf("Netflix", "Spotify 2026"), a.map { it.nome }.toSet())
        val n = a.first { it.nome == "Netflix" }
        assertEquals(Analises.OrigemAssinatura.DETECTADA, n.origem)
        assertEquals(55.9, n.valorMedio, 0.0); assertEquals(670.8, n.totalAnual, 0.0)
    }

    @Test fun `inclui despesas fixas e nao duplica a detectada coberta por regra`() {
        val d = mensal("Academia", 100.0, 5, listOf(8, 9, 10))
        val fixa = DespesaFixa(id = 1, descricao = "Academia", valor = 100.0, conta = "111", categoria = "Saúde", pic = "p",
            tipo = TipoDespesa.DEBITO, diaVencimento = 5)
        val a = Analises.assinaturas(hoje, d, listOf(fixa))
        assertEquals(1, a.size); assertEquals(Analises.OrigemAssinatura.FIXA, a.single().origem)
    }

    // ------------------------------------------------------------- R27

    @Test fun `meta com prazo calcula aporte mensal e ritmo`() {
        val alvo = ms(2027, 4, 15) // ~6 meses
        val m = Meta(id = 1, nome = "Viagem", valorObjetivo = 6000.0, valorGuardado = 3000.0, dataAlvo = alvo)
        val aportes = listOf(
            lanc(500.0, "Aporte: Viagem", "Reserva", ms(2026, 8, 10), natureza = Natureza.APORTE_META),
            lanc(500.0, "Aporte: Viagem", "Reserva", ms(2026, 9, 10), natureza = Natureza.APORTE_META),
            lanc(500.0, "Aporte: Viagem", "Reserva", ms(2026, 10, 10), natureza = Natureza.APORTE_META),
            lanc(9999.0, "Aporte: Outra", "Reserva", ms(2026, 10, 10), natureza = Natureza.APORTE_META)
        )
        val p = Analises.prazoDaMeta(m, hoje, aportes)
        assertEquals(3000.0, p.restante, 0.0)
        assertEquals(6, p.mesesRestantes)
        assertEquals(500.0, p.aporteMensalNecessario!!, 0.0)
        assertEquals(500.0, p.ritmoMensal, 0.0)
        assertEquals(StatusMeta.NO_RITMO, p.status)
        assertEquals(StatusMeta.ABAIXO, Analises.prazoDaMeta(m, hoje, emptyList()).status)
    }

    @Test fun `meta concluida atrasada e sem prazo`() {
        assertEquals(StatusMeta.CONCLUIDA, Analises.prazoDaMeta(Meta(nome = "a", valorObjetivo = 100.0, valorGuardado = 100.0), hoje, emptyList()).status)
        assertEquals(StatusMeta.ATRASADA, Analises.prazoDaMeta(Meta(nome = "a", valorObjetivo = 100.0, valorGuardado = 10.0, dataAlvo = ms(2026, 1, 1)), hoje, emptyList()).status)
        val sem = Analises.prazoDaMeta(Meta(nome = "a", valorObjetivo = 100.0, valorGuardado = 10.0), hoje, emptyList())
        assertEquals(StatusMeta.SEM_PRAZO, sem.status); assertEquals(90.0, sem.restante, 0.0); assertNull(sem.aporteMensalNecessario)
    }

    @Test fun `prazo curto conta no minimo um mes`() {
        val p = Analises.prazoDaMeta(Meta(nome = "a", valorObjetivo = 100.0, valorGuardado = 0.0, dataAlvo = hoje + 3 * 86_400_000L), hoje, emptyList())
        assertEquals(1, p.mesesRestantes); assertEquals(100.0, p.aporteMensalNecessario!!, 0.0)
    }

    // ------------------------------------------------------------- R28

    @Test fun `parcelar vale a pena quando o rendimento supera os juros embutidos`() {
        // 1000 à vista ou 10x de 100 (sem juros): com 1% a.m. o valor presente é < 1000
        val s = Analises.simularParcelamento(1000.0, 10, 100.0, 0.01)
        assertTrue(s.parcelarVale)
        assertEquals(947.13, s.valorPresente, 0.01)
        assertEquals(52.87, s.diferenca, 0.01)
        assertEquals(0.0, s.jurosImplicitosMensais, 0.0)
        assertEquals(1000.0, s.totalParcelado, 0.0)
    }

    @Test fun `parcelamento com juros altos nao vale e informa a taxa implicita`() {
        val s = Analises.simularParcelamento(1000.0, 10, 125.0, 0.01)
        assertFalse(s.parcelarVale)
        assertTrue(s.diferenca < 0)
        // 10x de 125 por 1000 => ~4,28% a.m. (fator de anuidade 8)
        assertEquals(0.0428, s.jurosImplicitosMensais, 0.0005)
        assertEquals(1250.0, s.totalParcelado, 0.0)
    }

    @Test fun `entrada reduz o valor financiado`() {
        val s = Analises.simularParcelamento(1000.0, 4, 200.0, 0.0, entrada = 200.0)
        assertEquals(1000.0, s.valorPresente, 0.0)
        assertFalse(s.parcelarVale) // empate não "vale a pena"
        assertEquals(0.0, s.jurosImplicitosMensais, 0.0)
    }

    // ------------------------------------------------------------- R29

    @Test fun `regra 50 30 20 classifica por grupo e aportes contam como poupanca`() {
        val mes = listOf(
            lanc(2500.0, "Mercado", "Supermercado"), lanc(500.0, "Plano", "Saúde"),
            lanc(1000.0, "Cinema", "Cinema"), lanc(200.0, "Jantar", "Alimentação"),
            lanc(800.0, "Aporte: Viagem", "Reserva", natureza = Natureza.APORTE_META),
            lanc(100.0, "Resgate: x", "Reserva", tipo = TipoDespesa.CREDITO, natureza = Natureza.RESGATE_META),
            lanc(1000.0, "Transf", "Transferência", natureza = Natureza.TRANSFERENCIA) // ignorada
        )
        val r = Analises.regra503020(6000.0, mes)
        assertEquals(3000.0, r.necessidades.valor, 0.0); assertEquals(50.0, r.necessidades.percentual, 1e-9)
        assertEquals(Analises.StatusGrupo.OK, r.necessidades.status)
        assertEquals(1200.0, r.desejos.valor, 0.0); assertEquals(20.0, r.desejos.percentual, 1e-9)
        assertEquals(700.0, r.poupanca.valor, 0.0)
        assertEquals(Analises.StatusGrupo.ABAIXO, r.poupanca.status) // 11,67% < 20%
    }

    @Test fun `regra 50 30 20 sem receita nao divide por zero`() {
        val r = Analises.regra503020(0.0, listOf(lanc(100.0, "x", "Cinema")))
        assertEquals(0.0, r.desejos.percentual, 0.0)
    }

    // ------------------------------------------------------------- R30

    @Test fun `melhor dia de compra e o dia seguinte ao fechamento`() {
        val m = Analises.melhorDiaDeCompra(diaFechamento = 25, diaVencimento = 5, hoje = hoje)
        assertEquals(26, m.dia)
        assertTrue("vence ~40 dias depois", m.prazoMaximoDias in 38..41)
        // fecha 5, vence 15 (mesmo mês): melhor dia 6, prazo ~40 dias
        val n = Analises.melhorDiaDeCompra(5, 15, hoje)
        assertEquals(6, n.dia); assertTrue(n.prazoMaximoDias in 38..41)
    }

    @Test fun `fechamento no ultimo dia do mes leva ao dia 1 do mes seguinte`() {
        val m = Analises.melhorDiaDeCompra(31, 10, ms(2026, 9, 15)) // setembro tem 30 dias
        assertEquals(1, m.dia)
        assertNotNull(m.prazoMaximoDias)
    }
}
