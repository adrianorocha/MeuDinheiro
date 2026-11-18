package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class RelatoriosTest {

    private fun ms(ano: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private fun lanc(
        valor: Double, categoria: String, data: Long, tipo: TipoDespesa = TipoDespesa.DEBITO, conta: String = "111",
        cartaoId: Int? = null, pago: Boolean = true, desc: String = "x", natureza: String = Natureza.NORMAL
    ) = Despesa(
        descricao = desc, valor = valor, data = Date(data), categoria = categoria, conta = conta, pic = "p", tipo = tipo,
        mes = Financas.mesDe(data), ano = Financas.anoDe(data), cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    private fun cartao(id: Int, principal: Int? = null) = Cartao(
        id = id, nome = "C$id", finalCartao = "000$id", tipo = "CRÉDITO", limiteDisponivel = 1.0, limiteTotal = 1.0,
        diaFechamento = 25, diaVencimento = 5, contaId = 1, cartaoPrincipalId = principal
    )

    private val setembro = ms(2026, 9, 1) to ms(2026, 9, 30)

    private val base = listOf(
        lanc(200.0, "Combustível", ms(2026, 9, 5), cartaoId = 1, pago = false, desc = "Posto A"),
        lanc(150.0, "Combustível", ms(2026, 9, 20), cartaoId = 2, pago = false, desc = "Posto B"),   // virtual do 1
        lanc(90.0, "Combustível", ms(2026, 9, 25), cartaoId = 3, pago = false, desc = "Posto C"),    // outro cartão
        lanc(100.0, "Alimentação", ms(2026, 9, 7), cartaoId = 1, pago = false),
        lanc(80.0, "Combustível", ms(2026, 9, 12)),                                                    // débito em conta
        lanc(30.0, "Combustível", ms(2026, 8, 20), cartaoId = 1, pago = true),                         // mês anterior
        lanc(5000.0, "Salário", ms(2026, 9, 5), tipo = TipoDespesa.CREDITO),
        lanc(1000.0, "Transferência", ms(2026, 9, 6), natureza = Natureza.TRANSFERENCIA)
    )
    private val cartoes = listOf(cartao(1), cartao(2, principal = 1), cartao(3))

    @Test fun `combustivel de um cartao em um periodo (exemplo do pedido)`() {
        val r = Relatorios.gerar(
            FiltroRelatorio(setembro.first, setembro.second, cartoes = setOf(1), categorias = setOf("combustível"), tipo = TipoRelatorio.DESPESA),
            base, cartoes
        )
        // cartão físico 1 inclui o virtual 2; cartão 3 e a compra em conta ficam de fora
        assertEquals(setOf("Posto A", "Posto B"), r.itens.map { it.descricao }.toSet())
        assertEquals(350.0, r.total, 0.0)
        assertEquals(2, r.quantidade); assertEquals(175.0, r.media, 0.0); assertEquals(200.0, r.maior, 0.0)
        assertEquals(30.0, r.anterior.total, 0.0)
        assertEquals((350.0 - 30.0) / 30.0 * 100, r.variacaoPercentual!!, 1e-9)
    }

    @Test fun `escolher so o virtual filtra apenas ele`() {
        val r = Relatorios.gerar(FiltroRelatorio(setembro.first, setembro.second, cartoes = setOf(2), tipo = TipoRelatorio.DESPESA), base, cartoes)
        assertEquals(listOf("Posto B"), r.itens.map { it.descricao })
    }

    @Test fun `despesas do mes excluem receitas e lancamentos internos, por categoria em ordem`() {
        val r = Relatorios.gerar(FiltroRelatorio(setembro.first, setembro.second, tipo = TipoRelatorio.DESPESA), base, cartoes)
        assertEquals(620.0, r.total, 0.0) // 200+150+90+100+80
        assertEquals(listOf("Combustível", "Alimentação"), r.porCategoria.map { it.nome })
        assertEquals(520.0, r.porCategoria.first().total, 0.0)
        assertEquals(100.0, r.porCategoria.sumOf { it.percentual }, 1e-9)
        assertEquals(listOf("2026-09"), r.porMes.map { it.mes })
    }

    @Test fun `receitas e saldo do periodo`() {
        val rec = Relatorios.gerar(FiltroRelatorio(setembro.first, setembro.second, tipo = TipoRelatorio.RECEITA), base, cartoes)
        assertEquals(5000.0, rec.total, 0.0); assertEquals(listOf("Salário"), rec.porCategoria.map { it.nome })
        val todos = Relatorios.gerar(FiltroRelatorio(setembro.first, setembro.second, tipo = TipoRelatorio.TODOS), base, cartoes)
        assertEquals(5000.0 - 620.0, todos.total, 0.0)
        assertEquals("categorias do relatório TODOS mostram só despesas", listOf("Combustível", "Alimentação"), todos.porCategoria.map { it.nome })
    }

    @Test fun `estorno no cartao abate a despesa`() {
        val l = listOf(
            lanc(100.0, "Compras", ms(2026, 9, 5), cartaoId = 1, pago = false),
            lanc(40.0, "Compras", ms(2026, 9, 6), tipo = TipoDespesa.CREDITO, cartaoId = 1, pago = false)
        )
        val r = Relatorios.gerar(FiltroRelatorio(setembro.first, setembro.second, tipo = TipoRelatorio.DESPESA), l, cartoes)
        assertEquals(60.0, r.total, 0.0)
        val receita = Relatorios.gerar(FiltroRelatorio(setembro.first, setembro.second, tipo = TipoRelatorio.RECEITA), l, cartoes)
        assertEquals(0.0, receita.total, 0.0)
    }

    @Test fun `filtros de conta, pago, texto e internos`() {
        val l = base + lanc(10.0, "Lazer", ms(2026, 9, 9), conta = "222", desc = "Pão de Açúcar")
        val f = FiltroRelatorio(setembro.first, setembro.second, tipo = TipoRelatorio.DESPESA)
        assertEquals(10.0, Relatorios.gerar(f.copy(contas = setOf("222")), l, cartoes).total, 0.0)
        assertEquals(80.0 + 10.0, Relatorios.gerar(f.copy(pago = true), l, cartoes).total, 0.0)
        assertEquals(10.0, Relatorios.gerar(f.copy(texto = "pao de acucar"), l, cartoes).total, 0.0)
        assertEquals(1000.0, Relatorios.gerar(f.copy(incluirInternos = true, categorias = setOf("transferencia")), l, cartoes).total, 0.0)
    }

    @Test fun `intervalo e inclusivo e sem dados nao divide por zero`() {
        val f = FiltroRelatorio(ms(2026, 9, 5), ms(2026, 9, 5), tipo = TipoRelatorio.DESPESA)
        assertEquals(listOf("Posto A"), Relatorios.gerar(f, base, cartoes).itens.map { it.descricao }) // só o dia 5, 12h
        val vazio = Relatorios.gerar(FiltroRelatorio(ms(2020, 1, 1), ms(2020, 1, 31)), base, cartoes)
        assertEquals(0, vazio.quantidade); assertEquals(0.0, vazio.media, 0.0); assertNull(vazio.variacaoPercentual)
        assertTrue(vazio.porCategoria.isEmpty())
    }

    @Test fun `modelo anual de IR pega saude e educacao do ano inteiro`() {
        val l = listOf(
            lanc(300.0, "Saúde", ms(2026, 2, 1)), lanc(500.0, "Educação", ms(2026, 12, 31)),
            lanc(50.0, "Lazer", ms(2026, 5, 1)), lanc(70.0, "Saúde", ms(2025, 12, 31))
        )
        val f = Relatorios.filtroDoModelo(Relatorios.Modelo.ANUAL_IR, 10, 2026)
        val r = Relatorios.gerar(f, l)
        assertEquals(800.0, r.total, 0.0)
        assertEquals(listOf("2026-02", "2026-12"), r.porMes.map { it.mes })
    }

    @Test fun `modelos mensais`() {
        val extrato = Relatorios.filtroDoModelo(Relatorios.Modelo.EXTRATO_CONTA, 9, 2026, conta = "111")
        assertTrue(extrato.incluirInternos); assertEquals(setOf("111"), extrato.contas)
        assertEquals(setOf(7), Relatorios.filtroDoModelo(Relatorios.Modelo.FATURA_CARTAO, 9, 2026, cartaoId = 7).cartoes)
        assertEquals(TipoRelatorio.TODOS, Relatorios.filtroDoModelo(Relatorios.Modelo.RECEITAS_DESPESAS, 9, 2026).tipo)
    }

    @Test fun `csv usa ponto e virgula, virgula decimal e aspas escapadas`() {
        val l = listOf(lanc(12.5, "Lazer", ms(2026, 9, 9), desc = "Cinema \"IMAX\"; sessão"))
        val csv = Relatorios.csv(Relatorios.gerar(FiltroRelatorio(setembro.first, setembro.second), l), conta = { "Nubank" })
        val linhas = csv.trim().lines()
        assertEquals("Data;Descrição;Categoria;Conta;Tipo;Pago;Valor", linhas[0])
        assertEquals("09/09/2026;\"Cinema \"\"IMAX\"\"; sessão\";\"Lazer\";\"Nubank\";Despesa;Sim;-12,50", linhas[1])
    }
}
