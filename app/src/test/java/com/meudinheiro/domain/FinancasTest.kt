package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Financas.FaturaRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class FinancasTest {

    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis

    private fun lanc(
        valor: Double,
        tipo: TipoDespesa = TipoDespesa.DEBITO,
        data: Long = ms(2026, 10, 10),
        conta: String = "111",
        pago: Boolean = true,
        cartaoId: Int? = null,
        natureza: String = Natureza.NORMAL,
        categoria: String = "Alimentação",
        id: Long = 0
    ) = Despesa(
        id = id, descricao = "x", valor = valor, data = Date(data), categoria = categoria, conta = conta,
        pic = "p", tipo = tipo, mes = Financas.mesDe(data), ano = Financas.anoDe(data),
        cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    private val cartao = Cartao(
        id = 1, nome = "Visa", finalCartao = "1234", tipo = "CRÉDITO",
        limiteDisponivel = 1000.0, limiteTotal = 1000.0, diaFechamento = 25, diaVencimento = 5, contaId = 1
    )

    // ------------------------------------------------------------------ R1

    @Test fun `centavos evitam erro de ponto flutuante`() {
        assertEquals(0.30, Dinheiro.somar(listOf(0.1, 0.2)), 0.0)
        assertEquals(10L, Dinheiro.centavos(0.1))
        assertEquals(1.01, Dinheiro.arredondar(1.005), 0.0) // meio para cima
        assertEquals(2.68, Dinheiro.arredondar(2.675), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valor nao finito e rejeitado`() { Dinheiro.centavos(Double.NaN) }

    // ------------------------------------------------------------------ R3

    @Test fun `saldo considera apenas pagos da conta fora de cartao`() {
        val l = listOf(
            lanc(1000.0, TipoDespesa.CREDITO),
            lanc(250.50),
            lanc(100.0, pago = false),                       // pendente: não debitou
            lanc(80.0, cartaoId = 1, pago = true),           // cartão: só a fatura debita
            lanc(500.0, TipoDespesa.CREDITO, conta = "222")  // outra conta
        )
        assertEquals(749.50, Financas.saldoConta("111", l), 0.0)
    }

    @Test fun `saldo soma 0,1 dez vezes sem erro`() {
        val l = List(10) { lanc(0.1, TipoDespesa.CREDITO) }
        assertEquals(1.0, Financas.saldoConta("111", l), 0.0)
    }

    // ------------------------------------------------------------------ R4

    @Test fun `limite do cartao desconta compras em aberto e devolve estornos`() {
        val l = listOf(
            lanc(300.0, cartaoId = 1, pago = false),
            lanc(100.0, cartaoId = 1, pago = false),
            lanc(40.0, TipoDespesa.CREDITO, cartaoId = 1, pago = false), // estorno
            lanc(500.0, cartaoId = 1, pago = true)                       // fatura já paga
        )
        assertEquals(640.0, Financas.limiteDisponivel(cartao, l), 0.0)
    }

    // ------------------------------------------------------------------ R5

    @Test fun `kpis ignoram transferencias aportes e fatura e separam realizado de previsto`() {
        val l = listOf(
            lanc(5000.0, TipoDespesa.CREDITO, categoria = "Salário"),
            lanc(1000.0, TipoDespesa.CREDITO, pago = false),                      // a receber
            lanc(300.0, pago = true),
            lanc(200.0, pago = false),
            lanc(150.0, cartaoId = 1, pago = false),                              // compra no cartão = despesa
            lanc(50.0, TipoDespesa.CREDITO, cartaoId = 1, pago = false),          // estorno abate
            lanc(2000.0, natureza = Natureza.TRANSFERENCIA),
            lanc(2000.0, TipoDespesa.CREDITO, natureza = Natureza.TRANSFERENCIA),
            lanc(400.0, natureza = Natureza.APORTE_META),
            lanc(900.0, natureza = Natureza.PAGAMENTO_FATURA),
            lanc(777.0, TipoDespesa.CREDITO, natureza = Natureza.SALDO_INICIAL)
        )
        val k = Financas.kpisPeriodo(l, null, null)
        assertEquals(5000.0, k.receitasRealizadas, 0.0)
        assertEquals(1000.0, k.receitasPrevistas, 0.0)
        assertEquals(600.0, k.despesasTotal, 0.0)       // 300 + 200 + 150 − 50
        assertEquals(300.0, k.despesasPagas, 0.0)
        assertEquals(300.0, k.despesasPendentes, 0.0)
        assertEquals(4400.0, k.resultado, 0.0)
        assertEquals(0.88, k.taxaPoupanca, 1e-9)
    }

    @Test fun `kpis respeitam o intervalo inclusive`() {
        val l = listOf(lanc(10.0, data = ms(2026, 10, 1, 0)), lanc(20.0, data = ms(2026, 10, 31, 23)), lanc(40.0, data = ms(2026, 11, 1, 0)))
        val k = Financas.kpisPeriodo(l, Financas.inicioDoMes(10, 2026), Financas.fimDoMes(10, 2026))
        assertEquals(30.0, k.despesasTotal, 0.0)
    }

    @Test fun `taxa de poupanca e zero sem receita`() {
        assertEquals(0.0, Financas.kpisPeriodo(listOf(lanc(10.0)), null, null).taxaPoupanca, 0.0)
    }

    // ------------------------------------------------------------------ R6

    @Test fun `compra ate o dia de fechamento entra na fatura do mes, depois vai para a seguinte`() {
        assertEquals(FaturaRef(10, 2026), Financas.faturaDaCompra(ms(2026, 10, 25, 23), 25))
        assertEquals(FaturaRef(11, 2026), Financas.faturaDaCompra(ms(2026, 10, 26, 0), 25))
    }

    @Test fun `fatura vira o ano`() {
        assertEquals(FaturaRef(1, 2027), Financas.faturaDaCompra(ms(2026, 12, 30), 25))
        assertEquals(FaturaRef(12, 2025), FaturaRef(1, 2026).anterior())
        assertEquals(FaturaRef(2, 2027), FaturaRef(11, 2026).deslocar(3))
    }

    @Test fun `fechamento 31 em mes curto usa o ultimo dia`() {
        assertEquals(28, Financas.diaDeFechamento(31, FaturaRef(2, 2027)))
        assertEquals(29, Financas.diaDeFechamento(31, FaturaRef(2, 2028)))
        // compra em 28/02 com fechamento 31 ainda cabe na fatura de fevereiro
        assertEquals(FaturaRef(2, 2027), Financas.faturaDaCompra(ms(2027, 2, 28), 31))
    }

    @Test fun `vencimento depois do fechamento fica no mesmo mes, antes vai para o proximo`() {
        // fecha 25, vence 5 → vence no mês seguinte
        val v1 = Calendar.getInstance().apply { timeInMillis = Financas.dataVencimento(25, 5, FaturaRef(10, 2026)) }
        assertEquals(11, v1.get(Calendar.MONTH) + 1)
        assertEquals(5, v1.get(Calendar.DAY_OF_MONTH))
        // fecha 5, vence 15 → mesmo mês
        val v2 = Calendar.getInstance().apply { timeInMillis = Financas.dataVencimento(5, 15, FaturaRef(10, 2026)) }
        assertEquals(10, v2.get(Calendar.MONTH) + 1)
        assertEquals(15, v2.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun `resumo da fatura separa pendente e identifica fatura paga`() {
        val l = listOf(
            lanc(100.0, cartaoId = 1, pago = false, data = ms(2026, 10, 10)),
            lanc(50.0, cartaoId = 1, pago = false, data = ms(2026, 10, 20)),
            lanc(30.0, TipoDespesa.CREDITO, cartaoId = 1, pago = false, data = ms(2026, 10, 21)), // estorno
            lanc(999.0, cartaoId = 1, pago = false, data = ms(2026, 10, 30))                     // fatura de novembro
        )
        val r = Financas.resumoFatura(cartao, l, FaturaRef(10, 2026))
        assertEquals(120.0, r.pendente, 0.0)
        assertEquals(3, r.itens.size)
        assertFalse(r.paga)
        val paga = Financas.resumoFatura(cartao, l.map { it.copy(pago = true) }, FaturaRef(10, 2026))
        assertTrue(paga.paga)
    }

    @Test fun `faturas em aberto ordenadas e sem faturas zeradas`() {
        val l = listOf(
            lanc(50.0, cartaoId = 1, pago = false, data = ms(2026, 11, 10)),
            lanc(70.0, cartaoId = 1, pago = false, data = ms(2026, 10, 10)),
            lanc(30.0, cartaoId = 1, pago = false, data = ms(2026, 9, 10)),
            lanc(30.0, TipoDespesa.CREDITO, cartaoId = 1, pago = false, data = ms(2026, 9, 11)) // zera setembro
        )
        val abertas = Financas.faturasEmAberto(cartao, l)
        assertEquals(listOf(FaturaRef(10, 2026), FaturaRef(11, 2026)), abertas.map { it.ref })
    }

    // ------------------------------------------------------------------ R8

    @Test fun `parcelamento de 100 em 3 soma exatamente 100 com o resto na ultima`() {
        val p = Financas.parcelar(lanc(100.0, data = ms(2026, 1, 15)), 3, ms(2026, 1, 15))
        assertEquals(listOf(33.33, 33.33, 33.34), p.map { it.valor })
        assertEquals(100.0, Dinheiro.somar(p.map { it.valor }), 0.0)
        assertEquals(listOf("x (1/3)", "x (2/3)", "x (3/3)"), p.map { it.descricao })
        assertEquals(1, p.map { it.grupoId }.distinct().size)
    }

    @Test fun `parcelas no dia 31 nao derivam para o dia 28`() {
        val p = Financas.parcelar(lanc(120.0, data = ms(2026, 1, 31)), 4, 0L)
        val dias = p.map { Calendar.getInstance().apply { timeInMillis = it.dataMs }.let { c -> c.get(Calendar.MONTH) + 1 to c.get(Calendar.DAY_OF_MONTH) } }
        assertEquals(listOf(1 to 31, 2 to 28, 3 to 31, 4 to 30), dias)
    }

    @Test fun `parcelas no cartao nascem em aberto, na conta so as ja vencidas sao pagas`() {
        val agora = ms(2026, 2, 20)
        val conta = Financas.parcelar(lanc(90.0, data = ms(2026, 1, 15)), 3, agora)
        assertEquals(listOf(true, true, false), conta.map { it.pago }) // jan e fev já passaram, mar não
        val cartaoP = Financas.parcelar(lanc(90.0, data = ms(2026, 1, 15), cartaoId = 1, pago = false), 3, agora)
        assertTrue(cartaoP.none { it.pago })
    }

    @Test fun `parcelas atualizam mes e ano de cada parcela`() {
        val p = Financas.parcelar(lanc(100.0, data = ms(2026, 11, 10)), 3, 0L)
        assertEquals(listOf(11 to 2026, 12 to 2026, 1 to 2027), p.map { it.mes to it.ano })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parcelamento exige valor positivo`() { Financas.parcelar(lanc(0.0), 3, 0L) }

    @Test fun `parcelas em andamento criam so k a n com os valores das posicoes originais`() {
        val p = Financas.parcelar(lanc(100.0, data = ms(2026, 5, 10), cartaoId = 1, pago = false), 3, 0L, aPartirDe = 2)
        assertEquals(listOf(33.33, 33.34), p.map { it.valor })
        assertEquals(listOf("x (2/3)", "x (3/3)"), p.map { it.descricao })
        assertEquals(listOf(5 to 2026, 6 to 2026), p.map { it.mes to it.ano })
        val ultima = Financas.parcelar(lanc(100.0, cartaoId = 1, pago = false), 3, 0L, aPartirDe = 3)
        assertEquals(listOf(33.34), ultima.map { it.valor })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parcela atual maior que o total e invalida`() { Financas.parcelar(lanc(100.0), 3, 0L, aPartirDe = 4) }

    @Test fun `limite desconta todas as parcelas e comprometimento separa atual e futuras`() {
        val compras = Financas.parcelar(
            lanc(1200.0, data = ms(2026, 10, 10), cartaoId = 1, pago = false), 12, 0L
        )
        val c = cartao.copy(limiteTotal = 5000.0, limiteDisponivel = 5000.0)
        assertEquals(3800.0, Financas.limiteDisponivel(c, compras), 0.0)
        val comp = Financas.comprometimentoCartao(c, compras, ms(2026, 10, 15))
        assertEquals(100.0, comp.faturaAtual, 0.0)
        assertEquals(1100.0, comp.parcelasFuturas, 0.0)
        assertEquals(1200.0, comp.emAbertoTotal, 0.0)
        assertEquals(12, comp.liberacaoPorFatura.size)
        assertEquals(FaturaRef(10, 2026), comp.liberacaoPorFatura.first().ref)
        assertTrue(comp.liberacaoPorFatura.zipWithNext().all { (a, b) -> a.vencimento < b.vencimento })
    }

    // ------------------------------------------------------------------ R16

    private fun regra(dia: Int, ultima: Long? = null) = DespesaFixa(
        id = 1, descricao = "Aluguel", valor = 1000.0, conta = "111", categoria = "Casa", pic = "p",
        tipo = TipoDespesa.DEBITO, diaVencimento = dia, ultimaDataLancamento = ultima?.let { Date(it) }
    )

    @Test fun `regra nunca lancada so gera o mes corrente e so depois do vencimento`() {
        assertEquals(1, Financas.ocorrenciasPendentes(regra(10), ms(2026, 10, 10, 8)).size)
        assertEquals(0, Financas.ocorrenciasPendentes(regra(10), ms(2026, 10, 9)).size)
    }

    @Test fun `regra ja lancada no mes nao duplica`() {
        assertEquals(0, Financas.ocorrenciasPendentes(regra(10, ms(2026, 10, 10)), ms(2026, 10, 28)).size)
    }

    @Test fun `meses perdidos sao recuperados`() {
        val datas = Financas.ocorrenciasPendentes(regra(10, ms(2026, 7, 10)), ms(2026, 10, 15))
        assertEquals(listOf(8, 9, 10), datas.map { Financas.mesDe(it) })
    }

    @Test fun `dia 31 em fevereiro vira ultimo dia do mes`() {
        val d = Financas.ocorrenciasPendentes(regra(31, ms(2027, 1, 31)), ms(2027, 3, 1)).first()
        val c = Calendar.getInstance().apply { timeInMillis = d }
        assertEquals(2, c.get(Calendar.MONTH) + 1)
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun `recuperacao e limitada a 12 meses`() {
        assertEquals(12, Financas.ocorrenciasPendentes(regra(1, ms(2020, 1, 1)), ms(2026, 10, 15)).size)
    }

    // ------------------------------------------------------------------ R13

    @Test fun `orcamento soma despesas do mes na categoria sem diferenciar caixa e inclui cartao`() {
        val l = listOf(
            lanc(100.0, categoria = "Alimentação"),
            lanc(60.0, categoria = " alimentação ", cartaoId = 1, pago = false),
            lanc(20.0, TipoDespesa.CREDITO, categoria = "Alimentação", cartaoId = 1, pago = false), // estorno
            lanc(500.0, TipoDespesa.CREDITO, categoria = "Alimentação"),                             // receita: fora
            lanc(40.0, categoria = "Lazer"),
            lanc(15.0, categoria = "Alimentação", data = ms(2026, 9, 30)),                           // outro mês
            lanc(70.0, categoria = "Alimentação", natureza = Natureza.APORTE_META)                    // não é despesa
        )
        val p = Financas.progressoOrcamento("Alimentação", 200.0, l, Financas.inicioDoMes(10, 2026), Financas.fimDoMes(10, 2026))
        assertEquals(140.0, p.gasto, 0.0)
        assertEquals(0.7, p.percentual, 1e-9)
        assertEquals(Financas.StatusOrcamento.OK, p.status)
    }

    @Test fun `status do orcamento passa de atencao para estourado`() {
        fun status(gasto: Double) = Financas.progressoOrcamento(
            "Alimentação", 100.0, listOf(lanc(gasto)), Financas.inicioDoMes(10, 2026), Financas.fimDoMes(10, 2026)
        ).status
        assertEquals(Financas.StatusOrcamento.ATENCAO, status(80.0))
        assertEquals(Financas.StatusOrcamento.ESTOURADO, status(100.0))
        assertEquals(Financas.StatusOrcamento.ESTOURADO, status(150.0))
    }

    // ------------------------------------------------------------- R14 / R15

    @Test fun `patrimonio liquido soma ativos e subtrai faturas abertas`() {
        val compras = listOf(
            lanc(300.0, cartaoId = 1, pago = false),
            lanc(100.0, TipoDespesa.CREDITO, cartaoId = 1, pago = false),
            lanc(900.0, cartaoId = 1, pago = true) // já paga
        )
        assertEquals(1000.0 + 500.0 + 200.0 - 200.0, Financas.patrimonioLiquido(listOf(400.0, 600.0), listOf(500.0), listOf(200.0), compras), 0.0)
    }

    @Test fun `previsao do mes considera a receber, a pagar e faturas vencendo ate o fim do mes`() {
        val fimOut = Financas.fimDoMes(10, 2026)
        val l = listOf(
            lanc(200.0, pago = false, data = ms(2026, 10, 20)),                         // a pagar
            lanc(50.0, pago = false, data = ms(2026, 9, 1)),                            // atrasada
            lanc(30.0, pago = false, data = ms(2026, 11, 3)),                           // mês que vem: fora
            lanc(300.0, TipoDespesa.CREDITO, pago = false, data = ms(2026, 10, 28)),    // a receber
            lanc(400.0, cartaoId = 1, pago = false, data = ms(2026, 9, 10)),            // fatura set: venc. 05/10
            lanc(600.0, cartaoId = 1, pago = false, data = ms(2026, 10, 10))            // fatura out: venc. 05/11 (fora)
        )
        val p = Financas.previsaoMes(1000.0, l, listOf(cartao), fimOut)
        assertEquals(300.0, p.receitasPrevistas, 0.0)
        assertEquals(250.0, p.despesasPendentes, 0.0)
        assertEquals(400.0, p.faturasAteVencimento, 0.0)
        assertEquals(650.0, p.contasAPagar, 0.0)
        assertEquals(650.0, p.saldoLivrePrevisto, 0.0) // 1000 + 300 − 250 − 400
        assertEquals(0.65, p.margem, 1e-9)
        assertEquals(Financas.StatusMes.SEGURO, p.status)
    }

    @Test fun `previsao com saldo zero e conta a pagar e risco`() {
        val p = Financas.previsaoMes(0.0, listOf(lanc(100.0, pago = false, data = ms(2026, 10, 20))), emptyList(), Financas.fimDoMes(10, 2026))
        assertEquals(-100.0, p.saldoLivrePrevisto, 0.0)
        assertEquals(Financas.StatusMes.RISCO, p.status)
    }

    // ------------------------------------------------------------------ R17

    @Test fun `saude financeira usa 70 e 90 por cento da receita`() {
        assertEquals(Financas.NivelSaude.SAUDAVEL, Financas.saudeFinanceira(1000.0, 690.0, 0.0).nivel)
        assertEquals(Financas.NivelSaude.ALERTA, Financas.saudeFinanceira(1000.0, 700.0, 0.0).nivel)
        assertEquals(Financas.NivelSaude.PERIGO, Financas.saudeFinanceira(1000.0, 900.0, 0.0).nivel)
    }

    @Test fun `despesa sem receita e perigo (antes aparecia como saudavel)`() {
        assertEquals(Financas.NivelSaude.PERIGO, Financas.saudeFinanceira(0.0, 50.0, 0.0).nivel)
        assertEquals(Financas.NivelSaude.SAUDAVEL, Financas.saudeFinanceira(0.0, 0.0, 0.0).nivel)
    }

    @Test fun `variacao de gastos contra o mes anterior`() {
        assertEquals(25.0, Financas.saudeFinanceira(1000.0, 500.0, 400.0).variacaoGastos, 1e-9)
        assertEquals(0.0, Financas.saudeFinanceira(1000.0, 500.0, 0.0).variacaoGastos, 0.0)
    }

    // ---------------------------------------------------------- investimentos

    @Test fun `rentabilidade de investimento`() {
        assertEquals(100.0, Financas.rendimento(1000.0, 1100.0), 0.0)
        assertEquals(10.0, Financas.rentabilidadePercentual(1000.0, 1100.0), 1e-9)
        assertEquals(0.0, Financas.rentabilidadePercentual(0.0, 50.0), 0.0)
    }

    // ------------------------------------------------------------------ R41

    private val virtual = Cartao(
        id = 2, nome = "Virtual", finalCartao = "9999", tipo = "CRÉDITO", limiteDisponivel = 1000.0, limiteTotal = 1000.0,
        diaFechamento = 25, diaVencimento = 5, contaId = 1, cartaoPrincipalId = 1
    )

    @Test fun `saldo do cartao separa uso do fisico e do virtual`() {
        val grupo = listOf(cartao, virtual)
        val l = listOf(
            lanc(300.0, cartaoId = 1, pago = false), lanc(100.0, cartaoId = 2, pago = false),
            lanc(40.0, TipoDespesa.CREDITO, cartaoId = 2, pago = false),   // estorno no virtual
            lanc(500.0, cartaoId = 1, pago = true)                           // pago: não conta
        )
        val f = Financas.saldoDoCartao(cartao, grupo, l)
        val v = Financas.saldoDoCartao(virtual, grupo, l)
        assertEquals(300.0, f.usado, 0.0); assertEquals(60.0, v.usado, 0.0)
        assertEquals(640.0, f.disponivelGrupo, 0.0); assertEquals(640.0, v.disponivel, 0.0)
        assertEquals(0.3, f.razao, 1e-9); assertEquals(0.06, v.razao, 1e-9)
    }

    @Test fun `disponivel respeita limite proprio e nunca passa do disponivel do grupo`() {
        val v = virtual.copy(limiteProprio = 200.0)
        val grupo = listOf(cartao, v)
        val l = listOf(lanc(50.0, cartaoId = 2, pago = false))
        val s = Financas.saldoDoCartao(v, grupo, l)
        assertEquals(150.0, s.disponivel, 0.0); assertEquals(0.25, s.razao, 1e-9); assertEquals(200.0, s.limiteProprio!!, 0.0)
        // grupo quase esgotado pelo físico: disponível do virtual = min(150, 80)
        val l2 = l + lanc(870.0, cartaoId = 1, pago = false)
        val s2 = Financas.saldoDoCartao(v, grupo, l2)
        assertEquals(80.0, s2.disponivelGrupo, 0.0); assertEquals(80.0, s2.disponivel, 0.0)
        // uso acima do teto próprio: disponível próprio negativo limitado pelo cálculo (não passa do grupo)
        assertEquals(0.0, Financas.saldoDoCartao(cartao, grupo, emptyList()).usado, 0.0)
    }

    @Test fun `estorno maior que compras nao gera uso negativo`() {
        val s = Financas.saldoDoCartao(cartao, listOf(cartao), listOf(lanc(30.0, TipoDespesa.CREDITO, cartaoId = 1, pago = false)))
        assertEquals(0.0, s.usado, 0.0)
    }

    // ------------------------------------------------------------------ R42

    private fun cartaoTipo(tipo: String) = cartao.copy(tipo = tipo)

    @Test fun `modalidade da compra depende do tipo do cartao`() {
        val c = Financas.Modalidade.CREDITO; val d = Financas.Modalidade.DEBITO
        assertEquals(c, Financas.modalidadeDaCompra(cartaoTipo("CRÉDITO"), d))   // crédito: sempre crédito
        assertEquals(c, Financas.modalidadeDaCompra(cartaoTipo("CRÉDITO")))
        assertEquals(d, Financas.modalidadeDaCompra(cartaoTipo("DÉBITO"), c))    // débito: sempre débito
        assertEquals(d, Financas.modalidadeDaCompra(cartaoTipo("DEBITO")))
        assertEquals(c, Financas.modalidadeDaCompra(cartaoTipo("MÚLTIPLO")))     // múltiplo: padrão crédito
        assertEquals(d, Financas.modalidadeDaCompra(cartaoTipo("MÚLTIPLO"), d))
        assertEquals(d, Financas.modalidadeDaCompra(cartaoTipo("MULTIPLO"), d))
        assertEquals(c, Financas.modalidadeDaCompra(cartaoTipo("???"), d))       // desconhecido: comportamento histórico
    }

    @Test fun `compra no debito vira lancamento na conta e credito fica como esta`() {
        val compra = lanc(50.0, cartaoId = 1, pago = false, conta = "outra", data = ms(2026, 10, 10))
        val agora = ms(2026, 10, 15)
        val deb = Financas.aplicarModalidade(compra, cartaoTipo("DÉBITO"), "111", null, agora)
        assertEquals(null, deb.cartaoId); assertEquals("111", deb.conta); assertTrue(deb.pago)
        val futura = Financas.aplicarModalidade(compra.copy(data = Date(ms(2026, 10, 20))), cartaoTipo("MÚLTIPLO"), "111", Financas.Modalidade.DEBITO, agora)
        assertEquals(null, futura.cartaoId); assertFalse(futura.pago)
        assertEquals(compra, Financas.aplicarModalidade(compra, cartaoTipo("CRÉDITO"), "111", Financas.Modalidade.DEBITO, agora))
        assertEquals(compra, Financas.aplicarModalidade(compra, cartaoTipo("MÚLTIPLO"), "111", null, agora))
    }

    // ------------------------------------------------ vínculo de débito (grupoId "debito:<cartao>")

    @Test fun `compra convertida em debito recebe grupoId debito do cartao usado`() {
        val compra = lanc(50.0, cartaoId = 1, pago = false, data = ms(2026, 10, 10))
        val agora = ms(2026, 10, 15)
        val deb = Financas.aplicarModalidade(compra, cartaoTipo("DÉBITO"), "111", null, agora)
        assertEquals("debito:1", deb.grupoId)
        assertEquals(1, Financas.cartaoDeDebito(deb))
        val mult = Financas.aplicarModalidade(compra, cartaoTipo("MÚLTIPLO").copy(id = 7), "111", Financas.Modalidade.DEBITO, agora)
        assertEquals("debito:7", mult.grupoId)
        // crédito: não ganha vínculo e perde um vínculo de débito herdado (edição trocando de cartão)
        assertEquals(null, Financas.aplicarModalidade(compra, cartaoTipo("CRÉDITO"), "111", null, agora).grupoId)
        val herdado = compra.copy(grupoId = "debito:9")
        assertEquals(null, Financas.aplicarModalidade(herdado, cartaoTipo("CRÉDITO"), "111", null, agora).grupoId)
        // grupo de parcelas não é tocado no crédito
        val parc = compra.copy(grupoId = "parc:abc")
        assertEquals("parc:abc", Financas.aplicarModalidade(parc, cartaoTipo("CRÉDITO"), "111", null, agora).grupoId)
    }

    @Test fun `debito nao e tratado como grupo de lancamentos`() {
        assertEquals(12, Financas.cartaoDeDebito("debito:12"))
        assertEquals(null, Financas.cartaoDeDebito("debito:x"))
        listOf(null, "parc:1", "fixa:2", "fatura:1:2026-10", "transf:abc", "rep:u", "uuid-solto").forEach {
            assertEquals(null, Financas.cartaoDeDebito(it)); assertEquals(it != null, Financas.ehGrupoDeLancamentos(it))
        }
        assertFalse(Financas.ehGrupoDeLancamentos("debito:3"))
    }

    @Test fun `compras do grupo separam credito do grupo e debitos vinculados de qualquer cartao do grupo`() {
        val fisico = cartao
        val virtual = cartao.copy(id = 2, cartaoPrincipalId = 1)
        val outro = cartao.copy(id = 3)
        val cartoes = listOf(fisico, virtual, outro)
        val credFisico = lanc(10.0, cartaoId = 1, pago = false).copy(id = 1)
        val credVirtual = lanc(20.0, cartaoId = 2, pago = false).copy(id = 2)
        val credOutro = lanc(30.0, cartaoId = 3, pago = false).copy(id = 3)
        val debFisico = lanc(1.0).copy(id = 4, grupoId = "debito:1")
        val debVirtual = lanc(2.0).copy(id = 5, grupoId = "debito:2")
        val debOutro = lanc(3.0).copy(id = 6, grupoId = "debito:3")
        val comum = lanc(4.0).copy(id = 7)
        val todas = listOf(credFisico, credVirtual, credOutro, debFisico, debVirtual, debOutro, comum)

        val foco = Financas.comprasDoGrupo(2, cartoes, todas)   // foco no virtual: fatura/débitos do grupo
        assertEquals(1, foco.principalId)
        assertEquals(setOf(1L, 2L), foco.credito.map { it.id }.toSet())
        assertEquals(setOf(4L, 5L), foco.debito.map { it.id }.toSet())
        val outroGrupo = Financas.comprasDoGrupo(3, cartoes, todas)
        assertEquals(3, outroGrupo.principalId)
        assertEquals(listOf(3L), outroGrupo.credito.map { it.id }); assertEquals(listOf(6L), outroGrupo.debito.map { it.id })

        // resumoFatura só enxerga o crédito: o débito vinculado não entra no total
        val ref = Financas.faturaDaCompra(ms(2026, 10, 10), 25)
        val r = Financas.resumoFatura(setOf(1, 2), 25, 5, foco.credito, ref)
        assertEquals(30.0, r.total, 0.0)
    }

    @Test fun `debitos da fatura sao do mes civil exibido`() {
        val a = lanc(5.0, data = ms(2026, 10, 1)).copy(id = 1, grupoId = "debito:1")
        val b = lanc(6.0, data = ms(2026, 10, 31)).copy(id = 2, grupoId = "debito:1")
        val c = lanc(7.0, data = ms(2026, 11, 2)).copy(id = 3, grupoId = "debito:1")
        assertEquals(listOf(2L, 1L), Financas.debitosDaFatura(listOf(a, b, c), Financas.FaturaRef(10, 2026)).map { it.id })
        assertEquals(listOf(3L), Financas.debitosDaFatura(listOf(a, b, c), Financas.FaturaRef(11, 2026)).map { it.id })
        assertTrue(Financas.debitosDaFatura(listOf(a, b, c), Financas.FaturaRef(12, 2026)).isEmpty())
    }

    @Test fun `outras faturas em aberto indicam o valor e a fatura mais proxima`() {
        val ids = setOf(1)
        val itens = listOf(
            lanc(100.0, cartaoId = 1, pago = false, data = ms(2026, 9, 10)),   // fatura 09
            lanc(40.0, cartaoId = 1, pago = false, data = ms(2026, 12, 10)),   // fatura 12
            lanc(70.0, cartaoId = 1, pago = false, data = ms(2027, 1, 10)),    // fatura 01/2027
            lanc(5.0, cartaoId = 1, pago = true, data = ms(2026, 11, 3)),      // paga: ignora
            lanc(9.0, cartaoId = 2, pago = false, data = ms(2026, 11, 3))      // outro grupo: ignora
        )
        val o = Financas.outrasFaturasEmAberto(ids, 25, itens, Financas.FaturaRef(10, 2026))
        assertEquals(210.0, o.emAberto, 0.0)
        assertEquals(Financas.FaturaRef(12, 2026), o.proxima)                  // a seguinte com itens
        val dez = Financas.outrasFaturasEmAberto(ids, 25, itens, Financas.FaturaRef(2, 2027))
        assertEquals(Financas.FaturaRef(1, 2027), dez.proxima)                 // só há anteriores: a mais recente
        val vazio = Financas.outrasFaturasEmAberto(ids, 25, emptyList(), Financas.FaturaRef(10, 2026))
        assertEquals(0.0, vazio.emAberto, 0.0); assertEquals(null, vazio.proxima)
    }

    @Test fun `ajuste de saldo calcula a diferenca em centavos`() {
        assertEquals(null, Financas.calcularAjusteSaldo(100.0, 100.0))
        assertEquals(null, Financas.calcularAjusteSaldo(0.1 + 0.2, 0.3))             // ruído de ponto flutuante
        Financas.calcularAjusteSaldo(100.0, 150.55)!!.let { assertEquals(TipoDespesa.CREDITO, it.tipo); assertEquals(5055L, it.centavos); assertEquals(50.55, it.valor, 0.0) }
        Financas.calcularAjusteSaldo(100.0, 40.0)!!.let { assertEquals(TipoDespesa.DEBITO, it.tipo); assertEquals(6000L, it.centavos) }
        Financas.calcularAjusteSaldo(-10.0, 5.0)!!.let { assertEquals(TipoDespesa.CREDITO, it.tipo); assertEquals(1500L, it.centavos) }
        Financas.calcularAjusteSaldo(5.0, -5.0)!!.let { assertEquals(TipoDespesa.DEBITO, it.tipo); assertEquals(1000L, it.centavos) }
        assertEquals(null, Financas.calcularAjusteSaldo(1.0, Double.NaN))
    }

    @Test fun `saldo informado aceita formatos brasileiros e negativo`() {
        assertEquals(1234.56, Financas.parseSaldoInformado("1.234,56")!!, 0.0)
        assertEquals(-50.0, Financas.parseSaldoInformado("-50,00")!!, 0.0)
        assertEquals(10.0, Financas.parseSaldoInformado("R$ 10")!!, 0.0)
        assertEquals(1234.5, Financas.parseSaldoInformado("1234.5")!!, 0.0)
        assertEquals(null, Financas.parseSaldoInformado("")); assertEquals(null, Financas.parseSaldoInformado("-")); assertEquals(null, Financas.parseSaldoInformado("abc"))
    }

    @Test fun `ajuste entra no saldo da conta mas nao em receita, despesa, orcamento nem previsao`() {
        val ini = ms(2026, 10, 1); val fim = ms(2026, 10, 31)
        val l = listOf(
            lanc(100.0, TipoDespesa.CREDITO, data = ms(2026, 10, 2)),
            lanc(30.0, TipoDespesa.DEBITO, data = ms(2026, 10, 3), categoria = "Ajuste de saldo", natureza = Natureza.AJUSTE),
            lanc(20.0, TipoDespesa.CREDITO, data = ms(2026, 10, 4), natureza = Natureza.AJUSTE)
        )
        assertEquals(90.0, Financas.saldoConta("111", l), 0.0)                       // saldo conta os dois ajustes
        val k = Financas.kpisPeriodo(l, ini, fim)
        assertEquals(100.0, k.receitasRealizadas, 0.0); assertEquals(0.0, k.despesasTotal, 0.0)
        assertEquals(0.0, Financas.progressoOrcamento("Ajuste de saldo", 100.0, l, ini, fim).gasto, 0.0)
        val p = Financas.previsaoMes(90.0, l.filter { it.natureza == Natureza.AJUSTE }.map { it.copy(pago = false) }, emptyList(), fim)
        assertEquals(0.0, p.despesasPendentes, 0.0); assertEquals(0.0, p.receitasPrevistas, 0.0)
        val w = WidgetResumo.montar(listOf(90.0), emptyList(), emptyList(), ms(2026, 10, 15), historico = l.filter { it.natureza == Natureza.AJUSTE }, orcamentos = emptyList())
        assertEquals(0.0, w.despesasMes, 0.0); assertEquals(0.0, w.receitasMes, 0.0)
    }

    // ------------------------------------------------------------------ últimas movimentações por conta

    @Test fun `ultimas movimentacoes seguem a conta selecionada e ignoram cartao e futuro`() {
        val hoje = ms(2026, 10, 15)
        val l = listOf(
            lanc(10.0, conta = "111", data = ms(2026, 10, 14), id = 1),
            lanc(20.0, conta = "222", data = ms(2026, 10, 14), id = 2),
            lanc(30.0, conta = "111", data = ms(2028, 5, 18), id = 3), // parcela futura
            lanc(40.0, conta = "111", data = ms(2026, 10, 13), cartaoId = 1, pago = false, id = 4), // compra de cartao
            lanc(50.0, conta = "111", data = ms(2026, 10, 15, 23), id = 5) // hoje, fim do dia
        )
        assertEquals(listOf(5L, 1L), Financas.ultimasDaConta(l, "111", hoje).map { it.id })
        assertEquals(listOf(2L), Financas.ultimasDaConta(l, " 222 ", hoje).map { it.id })
        assertEquals(setOf(5L, 1L, 2L), Financas.ultimasDaConta(l, "", hoje).map { it.id }.toSet())
        assertEquals(1, Financas.ultimasDaConta(l, "111", hoje, limite = 1).size)
    }
}
