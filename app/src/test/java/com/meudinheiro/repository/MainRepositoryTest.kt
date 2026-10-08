package com.meudinheiro.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.domain.Analises
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.Natureza
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

/** Regras financeiras de ponta a ponta contra um SQLite real (em memória). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository

    @Before fun abrir() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
    }

    @After fun fechar() = db.close()

    // ------------------------------------------------------------------ helpers

    private fun ms(ano: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private fun hoje() = System.currentTimeMillis()

    private fun conta(numero: String, saldo: Double = 0.0, banco: String = "Banco $numero") =
        ContaSaldo(banco = banco, pic = "p", agencia = "1", conta = numero, titular = "", saldo = saldo, id = 0)

    private fun lanc(
        valor: Double, conta: String, tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = true,
        cartaoId: Int? = null, data: Long = hoje(), categoria: String = "Alimentação"
    ) = Despesa(
        descricao = "x", valor = valor, data = Date(data), categoria = categoria, conta = conta, pic = "p",
        tipo = tipo, mes = 0, ano = 0, cartaoId = cartaoId, pago = pago
    )

    private fun saldo(numero: String) = runBlocking { db.contaSaldoDao().obterPorNumero(numero)!!.saldo }
    private fun limite(id: Int) = runBlocking { db.cartaoDao().getCartaoPorId(id)!!.limiteDisponivel }
    private fun contaId(numero: String) = runBlocking { db.contaSaldoDao().obterPorNumero(numero)!!.id }
    private fun todas() = runBlocking { db.despesaDao().obterTodasStatic() }

    private fun novoCartao(contaNumero: String, limite: Double = 1000.0, fecha: Int = 25, vence: Int = 5): Int = runBlocking {
        repo.salvarCartao(
            Cartao(nome = "Visa", finalCartao = "1234", tipo = "CRÉDITO", limiteDisponivel = 0.0, limiteTotal = limite,
                diaFechamento = fecha, diaVencimento = vence, contaId = contaId(contaNumero))
        )
        db.cartaoDao().obterTodasStatic().last().id
    }

    /** Falha o teste se [bloco] não lançar [T]; devolve a exceção. */
    private inline fun <reified T : Throwable> capturar(bloco: () -> Unit): T {
        try {
            bloco()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("esperava ${T::class.simpleName}")
        throw IllegalStateException()
    }

    /** Igual a [capturar], mas devolve Unit (o JUnit exige métodos de teste `void`). */
    private inline fun <reified T : Throwable> exige(bloco: () -> Unit) {
        capturar<T>(bloco)
    }

    // ------------------------------------------------------------------ contas

    @Test fun `saldo inicial vira lancamento e nao conta como receita`() = runBlocking {
        repo.salvarConta(conta("111", saldo = 1000.0))
        assertEquals(1000.0, saldo("111"), 0.0)
        val l = todas().single()
        assertEquals(Natureza.SALDO_INICIAL, l.natureza)
        assertTrue(l.pago)
        assertEquals(0.0, repo.obterKpis(null, null).receitasRealizadas, 0.0)
    }

    @Test fun `numero de conta duplicado e recusado`() = runBlocking {
        repo.salvarConta(conta("111"))
        val e = capturar<RegraFinanceiraException> { runBlocking { repo.salvarConta(conta("111", banco = "Outro")) } }
        assertTrue(e.message!!.contains("111"))
    }

    @Test fun `editar a conta nao apaga os cartoes vinculados`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val cartaoId = novoCartao("111")
        val existente = db.contaSaldoDao().obterPorNumero("111")!!
        repo.salvarConta(existente.copy(titular = "Maria")) // antes: REPLACE + CASCADE apagava o cartão
        assertNotNull(db.cartaoDao().getCartaoPorId(cartaoId))
        assertEquals("Maria", db.contaSaldoDao().obterPorNumero("111")!!.titular)
        assertEquals(500.0, saldo("111"), 0.0)
    }

    @Test fun `excluir conta remove lancamentos, cartoes, compras e agendamentos`() = runBlocking {
        repo.salvarConta(conta("111", 500.0)); repo.salvarConta(conta("222", 100.0))
        val cartaoId = novoCartao("111")
        repo.registrarLancamento(lanc(50.0, "111", cartaoId = cartaoId, pago = false))
        repo.inserirAgendamento(TransferenciaAgendada(dataAgendada = hoje(), contaOrigem = "111", contaDestino = "222", valor = 10.0))

        repo.excluirConta(contaId("111"))

        assertNull(db.contaSaldoDao().obterPorNumero("111"))
        assertTrue(db.cartaoDao().obterTodasStatic().isEmpty())
        assertTrue(todas().none { it.conta == "111" || it.cartaoId == cartaoId })
        assertTrue(db.contaSaldoDao().obterAgendamentosStatic().isEmpty())
        assertEquals(100.0, saldo("222"), 0.0)
    }

    // ------------------------------------------------------------------ lançamentos

    @Test fun `despesa paga debita o saldo e pendente nao`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(250.0, "111"))
        repo.registrarLancamento(lanc(100.0, "111", pago = false))
        assertEquals(750.0, saldo("111"), 0.0)

        val pendente = todas().first { !it.pago }
        repo.alternarPago(pendente.id, true)
        assertEquals(650.0, saldo("111"), 0.0)
        repo.alternarPago(pendente.id, false)
        assertEquals(750.0, saldo("111"), 0.0)
    }

    @Test fun `excluir despesa nao paga nao devolve saldo que nunca saiu`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(100.0, "111", pago = false))
        repo.excluirLancamento(todas().first { !it.pago }.id)
        assertEquals(1000.0, saldo("111"), 0.0) // antes: virava 1100
    }

    @Test fun `excluir despesa paga devolve o valor`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(100.0, "111"))
        repo.excluirLancamento(todas().first { it.natureza == Natureza.NORMAL }.id)
        assertEquals(1000.0, saldo("111"), 0.0)
    }

    @Test fun `lancamento grava mes e ano da data e arredonda centavos`() = runBlocking {
        repo.salvarConta(conta("111"))
        repo.registrarLancamento(lanc(10.005, "111", data = ms(2026, 3, 31)))
        val d = todas().single()
        assertEquals(3, d.mes); assertEquals(2026, d.ano)
        assertEquals(10.01, d.valor, 0.0)
    }

    @Test fun `valor zero ou conta inexistente sao recusados`() {
        exige<RegraFinanceiraException> { runBlocking { repo.registrarLancamento(lanc(0.0, "111")) } }
        exige<RegraFinanceiraException> { runBlocking { repo.registrarLancamento(lanc(10.0, "nao-existe")) } }
    }

    // ------------------------------------------------------------------ transferência

    @Test fun `transferencia move o dinheiro com lancamentos em par e nao vira receita nem despesa`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 50.0))
        repo.transferirEntreContas("111", "222", 300.0)

        assertEquals(700.0, saldo("111"), 0.0)
        assertEquals(350.0, saldo("222"), 0.0)
        val par = todas().filter { it.natureza == Natureza.TRANSFERENCIA }
        assertEquals(2, par.size)
        assertEquals(1, par.map { it.grupoId }.distinct().size)
        val k = repo.obterKpis(null, null)
        assertEquals(0.0, k.receitasRealizadas, 0.0); assertEquals(0.0, k.despesasTotal, 0.0)

        // um recálculo completo NÃO desfaz a transferência (antes ela só mexia no saldo e sumia)
        repo.recalcularTudo()
        assertEquals(700.0, saldo("111"), 0.0); assertEquals(350.0, saldo("222"), 0.0)
    }

    @Test fun `transferencia com saldo insuficiente e recusada sem alterar nada`() = runBlocking {
        repo.salvarConta(conta("111", 100.0)); repo.salvarConta(conta("222", 0.0))
        exige<RegraFinanceiraException> { runBlocking { repo.transferirEntreContas("111", "222", 100.01) } }
        assertEquals(100.0, saldo("111"), 0.0); assertEquals(0.0, saldo("222"), 0.0)
        assertTrue(todas().none { it.natureza == Natureza.TRANSFERENCIA })
    }

    @Test fun `transferencia exige valor positivo, contas distintas e existentes`() = runBlocking {
        repo.salvarConta(conta("111", 100.0)); repo.salvarConta(conta("222", 0.0))
        exige<RegraFinanceiraException> { runBlocking { repo.transferirEntreContas("111", "222", 0.0) } }
        exige<RegraFinanceiraException> { runBlocking { repo.transferirEntreContas("111", "111", 10.0) } }
        exige<RegraFinanceiraException> { runBlocking { repo.transferirEntreContas("111", "999", 10.0) } }
        assertEquals(100.0, saldo("111"), 0.0)
    }

    @Test fun `excluir um lado da transferencia exclui o par e restaura os saldos`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        repo.transferirEntreContas("111", "222", 300.0)
        repo.excluirLancamento(todas().first { it.natureza == Natureza.TRANSFERENCIA }.id)
        assertEquals(1000.0, saldo("111"), 0.0); assertEquals(0.0, saldo("222"), 0.0)
        assertTrue(todas().none { it.natureza == Natureza.TRANSFERENCIA })
    }

    @Test fun `agendamento sem saldo falha, continua pendente e executa quando houver saldo`() = runBlocking {
        repo.salvarConta(conta("111", 10.0)); repo.salvarConta(conta("222", 0.0))
        val id = repo.inserirAgendamento(TransferenciaAgendada(dataAgendada = hoje() - 1000, contaOrigem = "111", contaDestino = "222", valor = 50.0)).toInt()

        val agendamento = db.contaSaldoDao().obterAgendamento(id)!!
        assertTrue(repo.executarAgendamento(agendamento) is ResultadoAgendamento.Falhou)
        assertFalse(db.contaSaldoDao().obterAgendamento(id)!!.executada)

        repo.registrarLancamento(lanc(100.0, "111", TipoDespesa.CREDITO))
        assertEquals(ResultadoAgendamento.Executado, repo.executarAgendamento(agendamento))
        assertTrue(db.contaSaldoDao().obterAgendamento(id)!!.executada)
        assertEquals(60.0, saldo("111"), 0.0); assertEquals(50.0, saldo("222"), 0.0)
    }

    // ------------------------------------------------------------------ cartão

    @Test fun `compra no cartao consome limite uma unica vez e nao mexe no saldo`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111", limite = 1000.0)
        repo.registrarLancamento(lanc(300.0, "111", cartaoId = cartaoId, pago = false))
        assertEquals(700.0, limite(cartaoId), 0.0) // antes: o limite era abatido duas vezes (400)
        assertEquals(1000.0, saldo("111"), 0.0)
    }

    @Test fun `compra parcelada no cartao consome o limite total e gera parcelas mensais`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111", limite = 1000.0)
        repo.registrarParcelado(lanc(300.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 1, 10)), 3)
        assertEquals(700.0, limite(cartaoId), 0.0)
        val p = todas().filter { it.cartaoId == cartaoId }.sortedBy { it.data }
        assertEquals(listOf(100.0, 100.0, 100.0), p.map { it.valor })
        assertEquals(listOf(1, 2, 3), p.map { it.mes })
        assertEquals(3, p.map { it.cartaoId }.count { it == cartaoId })
    }

    @Test fun `pagar fatura debita a conta, libera o limite, quita as compras e registra o pagamento`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111", limite = 1000.0, fecha = 25, vence = 5)
        repo.registrarLancamento(lanc(300.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.registrarLancamento(lanc(120.5, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 20)))
        repo.registrarLancamento(lanc(80.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 28))) // fatura de novembro

        val pago = repo.pagarFatura(cartaoId, Financas.FaturaRef(10, 2026))

        assertEquals(420.5, pago, 0.0)
        assertEquals(579.5, saldo("111"), 0.0)
        assertEquals(920.0, limite(cartaoId), 0.0)                // só os 80 da fatura de novembro seguem consumindo
        val pagamento = todas().single { it.natureza == Natureza.PAGAMENTO_FATURA }
        assertEquals(420.5, pagamento.valor, 0.0)
        assertNull(pagamento.cartaoId)
        assertEquals(2, todas().count { it.cartaoId == cartaoId && it.pago })

        // pagar de novo a mesma fatura não é possível
        exige<RegraFinanceiraException> { runBlocking { repo.pagarFatura(cartaoId, Financas.FaturaRef(10, 2026)) } }
        assertEquals(579.5, saldo("111"), 0.0)

        // despesas do mês: o pagamento da fatura NÃO duplica o gasto (já contado nas compras)
        val k = repo.obterKpis(null, null)
        assertEquals(500.5, k.despesasTotal, 0.0)
    }

    @Test fun `compra 1200 em 12x consome todo o total e pagar uma fatura devolve so a parcela`() = runBlocking {
        repo.salvarConta(conta("111", 2000.0))
        val cartaoId = novoCartao("111", limite = 5000.0, fecha = 25, vence = 5)
        repo.registrarParcelado(lanc(1200.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)), 12)
        assertEquals(3800.0, limite(cartaoId), 0.0)
        repo.pagarFatura(cartaoId, Financas.FaturaRef(10, 2026))
        assertEquals(3900.0, limite(cartaoId), 0.0)
        assertEquals(1900.0, saldo("111"), 0.0)
    }

    @Test fun `parcela atual cria so as restantes`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val cartaoId = novoCartao("111", limite = 5000.0)
        val ids = repo.registrarParcelado(lanc(1200.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)), 12, parcelaAtual = 10)
        assertEquals(3, ids.size)
        assertEquals(listOf("x (10/12)", "x (11/12)", "x (12/12)"), todas().sortedBy { it.dataMs }.map { it.descricao })
        assertEquals(4700.0, limite(cartaoId), 0.0)
        exige<RegraFinanceiraException> { runBlocking { repo.registrarParcelado(lanc(100.0, "111", cartaoId = cartaoId, pago = false), 3, parcelaAtual = 4) } }
    }

    @Test fun `pagar itens seletivos debita liquido, devolve limite e quitar o resto fecha a fatura`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111", limite = 1000.0, fecha = 25, vence = 5)
        repo.registrarLancamento(lanc(100.10, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.registrarLancamento(lanc(50.20, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 12)))
        repo.registrarLancamento(lanc(20.05, "111", tipo = TipoDespesa.CREDITO, cartaoId = cartaoId, pago = false, data = ms(2026, 10, 14))) // estorno
        repo.registrarLancamento(lanc(80.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 11, 10)))
        val itens = todas().filter { it.cartaoId == cartaoId }.sortedBy { it.dataMs }
        val (a, b, estorno, nov) = itens

        // parcial: a + estorno
        val pago = repo.pagarItens(cartaoId, listOf(a.id, estorno.id))
        assertEquals(80.05, pago, 0.0)
        assertEquals(919.95, saldo("111"), 0.0)
        assertEquals(1000.0 - 50.20 - 80.0, limite(cartaoId), 0.0)
        assertTrue(todas().any { it.natureza == Natureza.PAGAMENTO_FATURA && it.descricao.endsWith("10/2026 (parcial)") })

        // quitar o restante da fatura de outubro fecha sem erro de centavos
        val resto = repo.pagarItens(cartaoId, listOf(b.id))
        assertEquals(50.20, resto, 0.0)
        assertTrue(todas().any { it.natureza == Natureza.PAGAMENTO_FATURA && it.descricao.endsWith("10/2026") })
        assertEquals(1000.0 - 80.0, limite(cartaoId), 0.0)
        exige<RegraFinanceiraException> { runBlocking { repo.pagarFatura(cartaoId, Financas.FaturaRef(10, 2026)) } }

        // várias faturas e validações
        exige<RegraFinanceiraException> { runBlocking { repo.pagarItens(cartaoId, listOf(a.id)) } }      // já pago
        exige<RegraFinanceiraException> { runBlocking { repo.pagarItens(cartaoId, listOf(999999L)) } }   // fora do grupo
        exige<RegraFinanceiraException> { runBlocking { repo.pagarItens(cartaoId, emptyList()) } }
        repo.pagarItens(cartaoId, listOf(nov.id))
        assertEquals(1000.0, limite(cartaoId), 0.0)
        assertEquals(1000.0 - 100.10 - 50.20 + 20.05 - 80.0, saldo("111"), 0.0001)
    }

    @Test fun `pagar itens so de estorno e recusado e varias faturas usam descricao de itens selecionados`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111", limite = 1000.0, fecha = 25, vence = 5)
        repo.registrarLancamento(lanc(10.0, "111", tipo = TipoDespesa.CREDITO, cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.registrarLancamento(lanc(60.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 11, 10)))
        val (est, nov) = todas().filter { it.cartaoId == cartaoId }.sortedBy { it.dataMs }
        exige<RegraFinanceiraException> { runBlocking { repo.pagarItens(cartaoId, listOf(est.id)) } }
        repo.pagarItens(cartaoId, listOf(est.id, nov.id))
        assertTrue(todas().any { it.natureza == Natureza.PAGAMENTO_FATURA && it.descricao.endsWith("(itens selecionados)") })
        assertEquals(950.0, saldo("111"), 0.0)
    }

    @Test fun `marcar itens como pagos libera limite sem mexer no saldo nem criar lancamento`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111", limite = 1000.0, fecha = 25, vence = 5)
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.registrarLancamento(lanc(50.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 12)))
        repo.registrarLancamento(lanc(20.0, "111", tipo = TipoDespesa.CREDITO, cartaoId = cartaoId, pago = false, data = ms(2026, 10, 14)))
        val (a, b, estorno) = todas().filter { it.cartaoId == cartaoId }.sortedBy { it.dataMs }
        val antes = todas().size
        val saldoAntes = saldo("111")
        assertEquals(1000.0 - 100.0 - 50.0 + 20.0, limite(cartaoId), 0.0)

        // parcial: a + estorno (líquido 80)
        assertEquals(2, repo.marcarItensComoPagos(cartaoId, listOf(a.id, estorno.id)))
        assertEquals(saldoAntes, saldo("111"), 0.0)
        assertEquals(antes, todas().size)
        assertTrue(todas().none { it.natureza == Natureza.PAGAMENTO_FATURA })
        assertEquals(1000.0 - 50.0, limite(cartaoId), 0.0)

        // validações
        exige<RegraFinanceiraException> { runBlocking { repo.marcarItensComoPagos(cartaoId, listOf(a.id)) } } // já pago
        exige<RegraFinanceiraException> { runBlocking { repo.marcarItensComoPagos(cartaoId, listOf(999999L)) } }
        exige<RegraFinanceiraException> { runBlocking { repo.marcarItensComoPagos(cartaoId, emptyList()) } }

        // o restante ainda pode ser pago normalmente (debita)
        assertEquals(50.0, repo.pagarItens(cartaoId, listOf(b.id)), 0.0)
        assertEquals(saldoAntes - 50.0, saldo("111"), 0.0)
        assertEquals(1000.0, limite(cartaoId), 0.0)
        exige<RegraFinanceiraException> { runBlocking { repo.pagarFatura(cartaoId, Financas.FaturaRef(10, 2026)) } }
    }

    @Test fun `marcar fatura inteira como paga impede pagarFatura e aceita liquido negativo`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val cartaoId = novoCartao("111", limite = 1000.0, fecha = 25, vence = 5)
        repo.registrarLancamento(lanc(30.0, "111", tipo = TipoDespesa.CREDITO, cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.registrarLancamento(lanc(200.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 11, 10)))
        val (est, nov) = todas().filter { it.cartaoId == cartaoId }.sortedBy { it.dataMs }
        // só estorno (líquido <= 0) é aceito aqui
        repo.marcarItensComoPagos(cartaoId, listOf(est.id))
        assertEquals(500.0, saldo("111"), 0.0)
        repo.marcarItensComoPagos(cartaoId, listOf(nov.id))
        assertEquals(500.0, saldo("111"), 0.0)
        assertEquals(1000.0, limite(cartaoId), 0.0)
        val msg = capturar<RegraFinanceiraException> { runBlocking { repo.pagarFatura(cartaoId, Financas.FaturaRef(10, 2026)) } }
        assertEquals("Esta fatura não possui valor pendente.", msg.message)
    }

    @Test fun `pagamento de fatura nao pode ser excluido`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111")
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.pagarFatura(cartaoId, Financas.FaturaRef(10, 2026))
        exige<RegraFinanceiraException> { runBlocking { repo.excluirLancamento(todas().single { it.natureza == Natureza.PAGAMENTO_FATURA }.id) } }
    }

    @Test fun `cartao com compras em aberto nao pode ser excluido`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111")
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = cartaoId, pago = false))
        exige<RegraFinanceiraException> { runBlocking { repo.excluirCartao(db.cartaoDao().getCartaoPorId(cartaoId)!!) } }
        assertNotNull(db.cartaoDao().getCartaoPorId(cartaoId))
    }

    @Test fun `baixar pendencia de cartao paga a fatura inteira`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartaoId = novoCartao("111")
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.registrarLancamento(lanc(50.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 12)))
        repo.baixarPendencia(todas().first { it.cartaoId == cartaoId })
        assertEquals(850.0, saldo("111"), 0.0)
        assertTrue(todas().filter { it.cartaoId == cartaoId }.all { it.pago })
    }

    // ------------------------------------------------------------------ metas

    @Test fun `aporte tira da conta, soma na meta e nao conta como despesa`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.salvarMeta(Meta(nome = "Viagem", valorObjetivo = 5000.0, valorGuardado = 0.0))
        val meta = db.metaDao().obterTodasStatic().single()
        repo.realizarAporte(meta, "111", 400.0)

        assertEquals(600.0, saldo("111"), 0.0)
        assertEquals(400.0, db.metaDao().obterTodasStatic().single().valorGuardado, 0.0)
        assertEquals(0.0, repo.obterKpis(null, null).despesasTotal, 0.0)
        assertEquals(1000.0, repo.patrimonioLiquidoFlow().first(), 0.0) // 600 em conta + 400 na meta
    }

    @Test fun `aporte acima do saldo e recusado e deposito rapido tambem sai da conta`() = runBlocking {
        repo.salvarConta(conta("111", 100.0))
        repo.salvarMeta(Meta(nome = "Viagem", valorObjetivo = 5000.0, valorGuardado = 0.0))
        val meta = db.metaDao().obterTodasStatic().single()
        exige<RegraFinanceiraException> { runBlocking { repo.realizarAporte(meta, "111", 100.01) } }
        assertEquals(0.0, db.metaDao().obterTodasStatic().single().valorGuardado, 0.0)

        repo.depositarNaMeta(meta.id, "111", 40.0)
        assertEquals(60.0, saldo("111"), 0.0)
        assertEquals(40.0, db.metaDao().obterTodasStatic().single().valorGuardado, 0.0)
    }

    @Test fun `excluir meta devolve o valor guardado a conta escolhida`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.salvarMeta(Meta(nome = "Viagem", valorObjetivo = 5000.0, valorGuardado = 0.0))
        val meta = db.metaDao().obterTodasStatic().single()
        repo.realizarAporte(meta, "111", 400.0)

        exige<RegraFinanceiraException> { runBlocking { repo.excluirMetaComRestituicao(meta, null) } } // sem conta: não some com o dinheiro
        assertEquals(1, db.metaDao().obterTodasStatic().size)

        repo.excluirMetaComRestituicao(meta, "111")
        assertEquals(1000.0, saldo("111"), 0.0)
        assertTrue(db.metaDao().obterTodasStatic().isEmpty())
        assertEquals(Natureza.RESGATE_META, todas().last().natureza)
    }

    // ------------------------------------------------------------------ recorrências / orçamento / patrimônio

    @Test fun `recorrencia recupera meses perdidos sem duplicar`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -3); set(Calendar.DAY_OF_MONTH, 1) }
        db.despesaFixaDao().inserir(
            DespesaFixa(descricao = "Aluguel", valor = 1000.0, conta = "111", categoria = "Casa", pic = "p",
                tipo = TipoDespesa.DEBITO, diaVencimento = 1, ultimaDataLancamento = cal.time)
        )
        repo.processarRecorrencias()
        assertEquals(3, todas().count { it.descricao == "Aluguel" })
        repo.processarRecorrencias()
        assertEquals(3, todas().count { it.descricao == "Aluguel" })
        assertTrue(todas().none { it.pago })
        assertEquals(0.0, saldo("111"), 0.0) // pendentes não mexem no saldo
    }

    @Test fun `orcamento unico por categoria e atualizado em vez de duplicado`() = runBlocking {
        repo.salvarOrcamento("Alimentação", 500.0)
        repo.salvarOrcamento(" alimentação ", 700.0)
        val o = db.orcamentoDao().obterTodasStatic().single()
        assertEquals(700.0, o.valorLimite, 0.0)
    }

    @Test fun `snapshot do patrimonio e unico por mes e atualizado`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(100.0, "111"))
        repo.registrarLancamento(lanc(50.0, "111"))
        val snaps = db.patrimonioDao().obterTodasStatic()
        assertEquals(1, snaps.size)
        assertEquals(850.0, snaps.single().valorTotal, 0.0)
    }

    @Test fun `kpis de despesas do mes anterior usam o mes correto`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        repo.registrarLancamento(lanc(40.0, "111", data = ms(2026, 9, 15)))
        repo.registrarLancamento(lanc(70.0, "111", data = ms(2026, 10, 15)))
        assertEquals(40.0, repo.getTotalDespesasPorPeriodo(9, 2026).first(), 0.0)
        assertEquals(70.0, repo.getTotalDespesasPorPeriodo(10, 2026).first(), 0.0)
    }

    // ------------------------------------------------------------------ backup

    @Test fun `backup e restauracao preservam ids, vinculos e saldos`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        val cartaoId = novoCartao("111")
        repo.registrarLancamento(lanc(200.0, "111", cartaoId = cartaoId, pago = false, data = ms(2026, 10, 10)))
        repo.transferirEntreContas("111", "222", 300.0)
        repo.salvarOrcamento("Alimentação", 500.0)
        val antes = todas().sortedBy { it.id }
        val json = repo.gerarBackup()

        repo.limparBancoDeDadosCompleto()
        assertTrue(todas().isEmpty())
        repo.restaurarBackupCompleto(json)

        val depois = todas().sortedBy { it.id }
        assertEquals(antes.map { it.id }, depois.map { it.id })
        assertEquals(antes.map { it.data.time }, depois.map { it.data.time })
        assertEquals(700.0, saldo("111"), 0.0); assertEquals(300.0, saldo("222"), 0.0)
        assertEquals(cartaoId, db.cartaoDao().obterTodasStatic().single().id)
        assertEquals(contaId("111"), db.cartaoDao().obterTodasStatic().single().contaId)
        assertEquals(800.0, limite(cartaoId), 0.0)
        assertEquals(1, db.orcamentoDao().obterTodasStatic().size)
    }

    @Test fun `restauracao invalida nao altera os dados existentes`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        exige<RegraFinanceiraException> { runBlocking { repo.restaurarBackupCompleto("{isso nao e json") } }
        assertEquals(1000.0, saldo("111"), 0.0)
    }

    @Test fun `restaurar backup com tabela vazia limpa a tabela (antes mantinha dados antigos)`() = runBlocking {
        repo.salvarConta(conta("111", 100.0))
        repo.salvarOrcamento("Lazer", 50.0)
        repo.restaurarBackupCompleto("""{"contas":[{"id":9,"saldo":0,"banco":"B","pic":"p","agencia":"1","conta":"999","titular":""}]}""")
        assertTrue(db.orcamentoDao().obterTodasStatic().isEmpty())
        assertEquals(listOf("999"), db.contaSaldoDao().obterTodasStatic().map { it.conta })
    }

    // ------------------------------------------------------------------ cartões virtuais (R18)

    private fun novoVirtual(principalId: Int, nome: String = "Virtual"): Int = runBlocking {
        repo.salvarCartao(
            Cartao(nome = nome, finalCartao = "9999", tipo = "QUALQUER", limiteDisponivel = 0.0, limiteTotal = 1.0,
                diaFechamento = 1, diaVencimento = 1, contaId = 0, cartaoPrincipalId = principalId)
        )
        db.cartaoDao().obterTodasStatic().last().id
    }

    @Test fun `virtual herda conta, limite e datas do fisico`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val fisico = novoCartao("111", limite = 1500.0, fecha = 20, vence = 28)
        val v = db.cartaoDao().getCartaoPorId(novoVirtual(fisico))!!
        assertEquals(fisico, v.cartaoPrincipalId)
        assertEquals(contaId("111"), v.contaId)
        assertEquals(1500.0, v.limiteTotal, 0.0); assertEquals(1500.0, v.limiteDisponivel, 0.0)
        assertEquals(20, v.diaFechamento); assertEquals(28, v.diaVencimento)
        assertTrue(v.ehVirtual)
    }

    @Test fun `limite e compartilhado entre o fisico e os virtuais`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val fisico = novoCartao("111", limite = 1000.0)
        val virtual = novoVirtual(fisico)
        repo.registrarLancamento(lanc(300.0, "111", cartaoId = virtual, pago = false))
        assertEquals(700.0, limite(fisico), 0.0); assertEquals(700.0, limite(virtual), 0.0)
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = fisico, pago = false))
        assertEquals(600.0, limite(fisico), 0.0); assertEquals(600.0, limite(virtual), 0.0)
        // a compra continua identificando qual cartão foi usado
        assertEquals(virtual, todas().first { it.valor == 300.0 }.cartaoId)
    }

    @Test fun `fatura e unica e pagar pelo virtual quita as compras de todo o grupo`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val fisico = novoCartao("111", limite = 1000.0)
        val virtual = novoVirtual(fisico)
        repo.registrarLancamento(lanc(300.0, "111", cartaoId = virtual, pago = false, data = ms(2026, 10, 10)))
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = fisico, pago = false, data = ms(2026, 10, 12)))

        val pago = repo.pagarFatura(virtual, Financas.FaturaRef(10, 2026))

        assertEquals(400.0, pago, 0.0)
        assertEquals(600.0, saldo("111"), 0.0)
        assertEquals(1000.0, limite(fisico), 0.0); assertEquals(1000.0, limite(virtual), 0.0)
        assertTrue(todas().filter { it.cartaoId != null }.all { it.pago })
        assertEquals("fatura:$fisico:2026-10", todas().single { it.natureza == Natureza.PAGAMENTO_FATURA }.grupoId)
        // despesas do período: 400 (não duplica com o pagamento)
        assertEquals(400.0, repo.obterKpis(null, null).despesasTotal, 0.0)
    }

    @Test fun `virtual de virtual e recusado`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val fisico = novoCartao("111")
        val virtual = novoVirtual(fisico)
        exige<RegraFinanceiraException> { runBlocking { novoVirtual(virtual, "Filho") } }
        exige<RegraFinanceiraException> { runBlocking { novoVirtual(9999, "Sem pai") } }
    }

    @Test fun `editar o limite do fisico propaga aos virtuais`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val fisico = novoCartao("111", limite = 1000.0)
        val virtual = novoVirtual(fisico)
        repo.registrarLancamento(lanc(250.0, "111", cartaoId = virtual, pago = false))
        repo.salvarCartao(db.cartaoDao().getCartaoPorId(fisico)!!.copy(limiteTotal = 2000.0, diaFechamento = 10))
        assertEquals(2000.0, db.cartaoDao().getCartaoPorId(virtual)!!.limiteTotal, 0.0)
        assertEquals(10, db.cartaoDao().getCartaoPorId(virtual)!!.diaFechamento)
        assertEquals(1750.0, limite(fisico), 0.0); assertEquals(1750.0, limite(virtual), 0.0)
    }

    @Test fun `excluir virtual com compras em aberto e recusado e depois do pagamento o historico vai para o fisico`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val fisico = novoCartao("111")
        val virtual = novoVirtual(fisico)
        repo.registrarLancamento(lanc(80.0, "111", cartaoId = virtual, pago = false, data = ms(2026, 10, 10)))
        exige<RegraFinanceiraException> { runBlocking { repo.excluirCartao(db.cartaoDao().getCartaoPorId(virtual)!!) } }

        repo.pagarFatura(fisico, Financas.FaturaRef(10, 2026))
        repo.excluirCartao(db.cartaoDao().getCartaoPorId(virtual)!!)

        assertNull(db.cartaoDao().getCartaoPorId(virtual))
        assertNotNull(db.cartaoDao().getCartaoPorId(fisico))
        assertEquals(fisico, todas().single { it.valor == 80.0 && it.natureza == Natureza.NORMAL }.cartaoId)
    }

    @Test fun `excluir o fisico remove os virtuais e exige grupo sem compras em aberto`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val fisico = novoCartao("111")
        val virtual = novoVirtual(fisico)
        repo.registrarLancamento(lanc(80.0, "111", cartaoId = virtual, pago = false, data = ms(2026, 10, 10)))
        exige<RegraFinanceiraException> { runBlocking { repo.excluirCartao(db.cartaoDao().getCartaoPorId(fisico)!!) } }

        repo.pagarFatura(virtual, Financas.FaturaRef(10, 2026))
        repo.excluirCartao(db.cartaoDao().getCartaoPorId(fisico)!!)
        assertTrue(db.cartaoDao().obterTodasStatic().isEmpty())
    }

    @Test fun `previsao nao duplica a fatura do grupo`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val fisico = novoCartao("111", fecha = 25, vence = 5)
        val virtual = novoVirtual(fisico)
        val d = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = virtual, pago = false, data = d))
        repo.registrarLancamento(lanc(50.0, "111", cartaoId = fisico, pago = false, data = d))
        val p = repo.previsaoFlow().first()
        assertEquals(150.0, p.faturasAteVencimento, 0.0)
    }

    // ------------------------------------------------------------------ lixeira (R31)

    private fun lixeira() = runBlocking { db.lixeiraDao().obterTodasStatic() }

    @Test fun `excluir vai para a lixeira e restaurar recompõe saldo e lançamento`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(100.0, "111"))
        val id = todas().first { it.natureza == Natureza.NORMAL }.id
        assertEquals(900.0, saldo("111"), 0.0)

        repo.excluirLancamento(id)
        assertEquals(1000.0, saldo("111"), 0.0)
        assertEquals(1, lixeira().size)

        repo.restaurarDaLixeira(lixeira().single().id)
        assertTrue(lixeira().isEmpty())
        assertEquals(900.0, saldo("111"), 0.0)
        assertEquals(id, todas().single { it.natureza == Natureza.NORMAL }.id) // mesmo id
    }

    @Test fun `saldo inicial nao vai para a lixeira`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        repo.excluirLancamento(todas().single().id)
        assertTrue(lixeira().isEmpty())
    }

    @Test fun `transferencia excluida vai em par e e restaurada em par`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        repo.transferirEntreContas("111", "222", 300.0)
        repo.excluirLancamento(todas().first { it.natureza == Natureza.TRANSFERENCIA }.id)
        assertEquals(2, lixeira().size)
        assertEquals(1000.0, saldo("111"), 0.0)

        repo.restaurarDaLixeira(lixeira().first().id)
        assertTrue(lixeira().isEmpty())
        assertEquals(700.0, saldo("111"), 0.0); assertEquals(300.0, saldo("222"), 0.0)
    }

    @Test fun `restaurar compra de cartao devolve o consumo de limite`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val cartaoId = novoCartao("111", limite = 1000.0)
        repo.registrarLancamento(lanc(250.0, "111", cartaoId = cartaoId, pago = false))
        repo.excluirLancamento(todas().single { it.cartaoId == cartaoId }.id)
        assertEquals(1000.0, limite(cartaoId), 0.0)
        repo.restaurarDaLixeira(lixeira().single().id)
        assertEquals(750.0, limite(cartaoId), 0.0)
    }

    @Test fun `restaurar lancamento de conta que nao existe mais e recusado e mantem o item`() = runBlocking {
        repo.salvarConta(conta("111", 100.0))
        repo.registrarLancamento(lanc(10.0, "111"))
        repo.excluirLancamento(todas().first { it.natureza == Natureza.NORMAL }.id)
        repo.excluirConta(contaId("111"))
        exige<RegraFinanceiraException> { runBlocking { repo.restaurarDaLixeira(lixeira().single().id) } }
        assertEquals(1, lixeira().size)
    }

    @Test fun `purga remove so o que passou de 30 dias e esvaziar limpa tudo`() = runBlocking {
        val agora = System.currentTimeMillis()
        db.lixeiraDao().inserir(com.meudinheiro.data.Lixeira(descricao = "velho", valor = 1.0, excluidoEm = agora - 31L * 86_400_000L, payload = "{}"))
        db.lixeiraDao().inserir(com.meudinheiro.data.Lixeira(descricao = "novo", valor = 1.0, excluidoEm = agora - 29L * 86_400_000L, payload = "{}"))
        repo.purgarLixeira()
        assertEquals(listOf("novo"), lixeira().map { it.descricao })
        repo.esvaziarLixeira()
        assertTrue(lixeira().isEmpty())
    }

    // ------------------------------------------------------------------ duplicar / repetir (R23)

    @Test fun `duplicar cria copia em aberto de hoje e repetir cria ocorrencias futuras`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(60.0, "111", data = ms(2026, 1, 31)))
        val original = todas().first { it.natureza == Natureza.NORMAL }

        repo.duplicarLancamento(original.id)
        val copia = todas().filter { it.descricao == "x" && it.id != original.id }.single()
        assertFalse(copia.pago); assertEquals(60.0, copia.valor, 0.0)
        assertEquals(940.0, saldo("111"), 0.0) // a cópia está em aberto: não debita

        val n = repo.repetirLancamento(original.id, 3, 1, Analises.UnidadeRepeticao.MESES)
        assertEquals(3, n)
        val grupo = todas().filter { it.grupoId?.startsWith("rep:") == true }
        assertEquals(listOf(2, 3, 4), grupo.sortedBy { it.dataMs }.map { it.mes })
        assertTrue(grupo.none { it.pago })
    }

    @Test fun `repetir compra de cartao consome limite e transferencia nao pode ser duplicada`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        val cartaoId = novoCartao("111", limite = 1000.0)
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = cartaoId, pago = false))
        repo.repetirLancamento(todas().single { it.cartaoId == cartaoId }.id, 2, 1, Analises.UnidadeRepeticao.SEMANAS)
        assertEquals(700.0, limite(cartaoId), 0.0)

        repo.transferirEntreContas("111", "222", 10.0)
        exige<RegraFinanceiraException> { runBlocking { repo.duplicarLancamento(todas().first { it.natureza == Natureza.TRANSFERENCIA }.id) } }
        exige<RegraFinanceiraException> { runBlocking { repo.repetirLancamento(todas().first { it.natureza == Natureza.TRANSFERENCIA }.id, 2, 1, Analises.UnidadeRepeticao.DIAS) } }
    }

    // ------------------------------------------------------------------ autor / backup com lixeira

    @Test fun `lancamento sem autor recebe o nome do perfil e autor informado e preservado`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        com.meudinheiro.funcoes.UserPreferences(ctx).saveUserName("Ana Souza")
        repo.salvarConta(conta("111", 100.0))
        repo.registrarLancamento(lanc(1.0, "111"))
        repo.registrarLancamento(lanc(2.0, "111").copy(autor = "Bruno"))
        val porValor = todas().filter { it.natureza == Natureza.NORMAL }.associateBy { it.valor }
        assertEquals("Ana Souza", porValor.getValue(1.0).autor)
        assertEquals("Bruno", porValor.getValue(2.0).autor)
    }

    @Test fun `backup preserva lixeira, autor e prazo da meta`() = runBlocking {
        repo.salvarConta(conta("111", 100.0))
        repo.registrarLancamento(lanc(5.0, "111").copy(autor = "Ana"))
        repo.excluirLancamento(todas().first { it.natureza == Natureza.NORMAL }.id)
        repo.salvarMeta(Meta(nome = "Viagem", valorObjetivo = 100.0, valorGuardado = 0.0, dataAlvo = 1_900_000_000_000))
        val json = repo.gerarBackup()
        repo.limparBancoDeDadosCompleto()
        repo.restaurarBackupCompleto(json)
        assertEquals(1, lixeira().size)
        assertEquals(1_900_000_000_000, db.metaDao().obterTodasStatic().single().dataAlvo)
        repo.restaurarDaLixeira(lixeira().single().id)
        assertEquals("Ana", todas().first { it.natureza == Natureza.NORMAL }.autor)
    }

    // ------------------------------------------------------------------ recorrências em conta ou cartão (R16)

    private fun regraFixa(conta: String, cartaoId: Int? = null) = DespesaFixa(
        descricao = "Streaming", valor = 40.0, conta = conta, categoria = "Lazer", pic = "p",
        tipo = TipoDespesa.DEBITO, diaVencimento = 1, cartaoId = cartaoId,
        ultimaDataLancamento = Calendar.getInstance().apply { add(Calendar.MONTH, -2); set(Calendar.DAY_OF_MONTH, 1) }.time
    )

    @Test fun `recorrencia em cartao gera compras no cartao que consomem o limite`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val fisico = novoCartao("111", limite = 1000.0)
        val virtual = novoVirtual(fisico)
        repo.salvarDespesaFixa(regraFixa(conta = "qualquer", cartaoId = virtual))   // conta é resolvida pelo cartão
        val compras = todas().filter { it.descricao == "Streaming" }
        assertEquals(2, compras.size)
        assertTrue(compras.all { it.cartaoId == virtual && it.conta == "111" && !it.pago })
        assertEquals(920.0, limite(fisico), 0.0)          // limite do grupo (físico + virtual)
        assertEquals(500.0, saldo("111"), 0.0)            // não debita a conta
        assertEquals(virtual, db.despesaFixaDao().obterTodas().single().cartaoId)
        assertEquals("111", db.despesaFixaDao().obterTodas().single().conta)

        repo.processarRecorrencias()                      // idempotente
        assertEquals(2, todas().count { it.descricao == "Streaming" })
    }

    @Test fun `recorrencia em conta continua gerando lancamentos na conta`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        repo.salvarDespesaFixa(regraFixa("111"))
        val l = todas().filter { it.descricao == "Streaming" }
        assertEquals(2, l.size)
        assertTrue(l.all { it.cartaoId == null && it.conta == "111" })
    }

    @Test fun `trocar a origem da recorrencia entre conta e cartao`() = runBlocking {
        repo.salvarConta(conta("111", 0.0)); repo.salvarConta(conta("222", 0.0))
        val cartao = novoCartao("222")
        db.despesaFixaDao().inserir(regraFixa("111"))
        val id = db.despesaFixaDao().obterTodas().single().id

        repo.alterarOrigemRecorrencia(id, null, cartao)
        db.despesaFixaDao().obterTodas().single().let { assertEquals(cartao, it.cartaoId); assertEquals("222", it.conta) }

        repo.alterarOrigemRecorrencia(id, "111", null)
        db.despesaFixaDao().obterTodas().single().let { assertNull(it.cartaoId); assertEquals("111", it.conta) }

        exige<RegraFinanceiraException> { runBlocking { repo.alterarOrigemRecorrencia(id, null, 9999) } }
        exige<RegraFinanceiraException> { runBlocking { repo.alterarOrigemRecorrencia(id, null, null) } }
    }

    @Test fun `excluir o cartao move a recorrencia para a conta e excluir a conta remove a regra`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val cartao = novoCartao("111")
        repo.salvarDespesaFixa(regraFixa("111", cartao))
        exige<RegraFinanceiraException> { runBlocking { repo.excluirCartao(db.cartaoDao().getCartaoPorId(cartao)!!) } }
        // havia compras em aberto da própria recorrência: exclusão é recusada e a regra permanece no cartão
        assertEquals(cartao, db.despesaFixaDao().obterTodas().single().cartaoId)

        todas().filter { it.cartaoId == cartao }.forEach { repo.excluirLancamento(it.id) }
        repo.excluirCartao(db.cartaoDao().getCartaoPorId(cartao)!!)
        db.despesaFixaDao().obterTodas().single().let { assertNull(it.cartaoId); assertEquals("111", it.conta) }

        repo.excluirConta(contaId("111"))
        assertTrue(db.despesaFixaDao().obterTodas().isEmpty())
    }

    // ------------------------------------------------------------------ R41 limite próprio

    private fun salvarVirtualComTeto(principalId: Int, teto: Double?): Unit = runBlocking {
        repo.salvarCartao(
            Cartao(nome = "V", finalCartao = "9999", tipo = "CRÉDITO", limiteDisponivel = 0.0, limiteTotal = 1.0,
                diaFechamento = 1, diaVencimento = 1, contaId = 0, cartaoPrincipalId = principalId, limiteProprio = teto)
        )
    }

    @Test fun `limite proprio e validado e zero vira nulo`() = runBlocking {
        repo.salvarConta(conta("111"))
        val fisico = novoCartao("111", limite = 1000.0)
        val e1 = capturar<com.meudinheiro.repository.RegraFinanceiraException> { runBlocking { salvarVirtualComTeto(fisico, 1000.01) } }
        assertEquals("O limite próprio deve ser maior que zero e não pode passar do limite total do cartão físico.", e1.message)
        capturar<com.meudinheiro.repository.RegraFinanceiraException> { runBlocking { salvarVirtualComTeto(fisico, -5.0) } }
        salvarVirtualComTeto(fisico, 0.0)
        assertNull(db.cartaoDao().obterTodasStatic().last().limiteProprio)
        salvarVirtualComTeto(fisico, 1000.0)
        assertEquals(1000.0, db.cartaoDao().obterTodasStatic().last().limiteProprio!!, 0.0)
    }

    @Test fun `propagacao do fisico preserva limite proprio e baixar limite abaixo do teto falha`() = runBlocking {
        repo.salvarConta(conta("111"))
        val fisico = novoCartao("111", limite = 1000.0)
        salvarVirtualComTeto(fisico, 600.0)
        val f = db.cartaoDao().getCartaoPorId(fisico)!!
        repo.salvarCartao(f.copy(limiteTotal = 800.0, diaFechamento = 10))
        val v = db.cartaoDao().obterTodasStatic().last()
        assertEquals(600.0, v.limiteProprio!!, 0.0); assertEquals(800.0, v.limiteTotal, 0.0); assertEquals(10, v.diaFechamento)
        capturar<com.meudinheiro.repository.RegraFinanceiraException> { runBlocking { repo.salvarCartao(f.copy(limiteTotal = 500.0)) } }
        assertEquals(800.0, db.cartaoDao().getCartaoPorId(fisico)!!.limiteTotal, 0.0)
    }

    // ------------------------------------------------------------------ R42 vínculo editável

    private fun novoCartaoTipo(contaNumero: String, tipo: String, limite: Double = 1000.0, nome: String = "Cartao"): Int = runBlocking {
        repo.salvarCartao(
            Cartao(nome = nome, finalCartao = "1111", tipo = tipo, limiteDisponivel = 0.0, limiteTotal = limite,
                diaFechamento = 25, diaVencimento = 5, contaId = contaId(contaNumero))
        )
        db.cartaoDao().obterTodasStatic().last().id
    }

    private fun cartao(id: Int) = runBlocking { db.cartaoDao().getCartaoPorId(id)!! }

    @Test fun `fisico independente vira virtual e herda tudo do fisico novo`() = runBlocking {
        repo.salvarConta(conta("111")); repo.salvarConta(conta("222"))
        val a = novoCartaoTipo("111", "CRÉDITO", 1000.0, "A")
        val b = novoCartaoTipo("222", "CRÉDITO", 500.0, "B")
        repo.registrarLancamento(lanc(100.0, "222", cartaoId = b, pago = false))     // compra em aberto no B (grupo antigo)
        repo.registrarLancamento(lanc(200.0, "111", cartaoId = a, pago = false))
        assertEquals(1000.0 - 200.0, limite(a), 0.0)

        repo.salvarCartao(cartao(b).copy(cartaoPrincipalId = a, limiteProprio = 300.0))

        val nb = cartao(b)
        assertEquals(a, nb.cartaoPrincipalId); assertEquals(contaId("111"), nb.contaId)
        assertEquals(1000.0, nb.limiteTotal, 0.0); assertEquals(25, nb.diaFechamento); assertEquals("CRÉDITO", nb.tipo)
        assertEquals(300.0, nb.limiteProprio!!, 0.0)
        assertEquals("111", todas().first { it.cartaoId == b }.conta)                // despesas passam para a conta do físico
        assertEquals(700.0, limite(a), 0.0); assertEquals(700.0, limite(b), 0.0)     // grupo: 1000 - 200 - 100
    }

    @Test fun `limite proprio e revalidado ao vincular e fisico com virtuais nao pode virar virtual`() = runBlocking {
        repo.salvarConta(conta("111"))
        val a = novoCartaoTipo("111", "CRÉDITO", 1000.0, "A")
        val b = novoCartaoTipo("111", "CRÉDITO", 5000.0, "B")
        exige<RegraFinanceiraException> { runBlocking { repo.salvarCartao(cartao(b).copy(cartaoPrincipalId = a, limiteProprio = 1000.01)) } }
        assertNull(cartao(b).cartaoPrincipalId)                                      // nada mudou
        exige<RegraFinanceiraException> { runBlocking { repo.salvarCartao(cartao(b).copy(cartaoPrincipalId = a, limiteProprio = -1.0)) } }

        val v = novoVirtual(b)
        exige<RegraFinanceiraException> { runBlocking { repo.salvarCartao(cartao(b).copy(cartaoPrincipalId = a)) } }  // B tem virtual
        assertNull(cartao(b).cartaoPrincipalId)
        exige<RegraFinanceiraException> { runBlocking { repo.salvarCartao(cartao(a).copy(cartaoPrincipalId = a)) } } // de si mesmo
        exige<RegraFinanceiraException> { runBlocking { repo.salvarCartao(cartao(a).copy(cartaoPrincipalId = v)) } } // de um virtual
        exige<RegraFinanceiraException> { runBlocking { repo.salvarCartao(cartao(a).copy(cartaoPrincipalId = 9999)) } }
    }

    @Test fun `virtual vira fisico independente mantendo valores e recalcula os dois grupos`() = runBlocking {
        repo.salvarConta(conta("111"))
        val a = novoCartaoTipo("111", "CRÉDITO", 1000.0, "A")
        val v = novoVirtual(a)
        repo.salvarCartao(cartao(v).copy(limiteProprio = 400.0))
        repo.registrarLancamento(lanc(300.0, "111", cartaoId = v, pago = false))
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = a, pago = false))
        assertEquals(600.0, limite(a), 0.0)

        repo.salvarCartao(cartao(v).copy(cartaoPrincipalId = null))

        val nv = cartao(v)
        assertNull(nv.cartaoPrincipalId); assertNull(nv.limiteProprio)
        assertEquals(1000.0, nv.limiteTotal, 0.0); assertEquals(contaId("111"), nv.contaId); assertEquals(25, nv.diaFechamento)
        assertEquals(700.0, limite(v), 0.0)      // grupo próprio: 1000 - 300 (compras do virtual vão com ele)
        assertEquals(900.0, limite(a), 0.0)      // grupo antigo: 1000 - 100
    }

    @Test fun `virtual orfao pode ser corrigido pela edicao`() = runBlocking {
        repo.salvarConta(conta("111"))
        val a = novoCartaoTipo("111", "CRÉDITO", 800.0, "A")
        db.cartaoDao().inserirCartao(Cartao(nome = "Orfao", finalCartao = "0001", tipo = "CRÉDITO", limiteDisponivel = 800.0, limiteTotal = 800.0,
            diaFechamento = 1, diaVencimento = 2, contaId = contaId("111"), cartaoPrincipalId = 9999))
        val orfao = db.cartaoDao().obterTodasStatic().last().id
        repo.salvarCartao(cartao(orfao).copy(cartaoPrincipalId = a))
        assertEquals(a, cartao(orfao).cartaoPrincipalId)
        assertEquals(25, cartao(orfao).diaFechamento)

        val orfao2 = run {
            db.cartaoDao().inserirCartao(Cartao(nome = "Orfao2", finalCartao = "0002", tipo = "CRÉDITO", limiteDisponivel = 10.0, limiteTotal = 10.0,
                diaFechamento = 1, diaVencimento = 2, contaId = contaId("111"), cartaoPrincipalId = 9998))
            db.cartaoDao().obterTodasStatic().last().id
        }
        repo.salvarCartao(cartao(orfao2).copy(cartaoPrincipalId = null))               // ou vira físico
        assertNull(cartao(orfao2).cartaoPrincipalId)
    }

    @Test fun `editar limite proprio de cartao existente`() = runBlocking {
        repo.salvarConta(conta("111"))
        val a = novoCartaoTipo("111", "CRÉDITO", 1000.0, "A")
        repo.salvarCartao(cartao(a).copy(limiteProprio = 250.0))
        assertEquals(250.0, cartao(a).limiteProprio!!, 0.0)
        repo.salvarCartao(cartao(a).copy(limiteProprio = null))
        assertNull(cartao(a).limiteProprio)
    }

    // ------------------------------------------------------------------ R42 modalidade da compra

    @Test fun `compra em cartao de debito sai direto da conta sem consumir limite`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val c = novoCartaoTipo("111", "DÉBITO", 0.0)
        repo.registrarLancamento(lanc(80.0, "xx", cartaoId = c, pago = false))
        val d = todas().single { it.valor == 80.0 }
        assertNull(d.cartaoId); assertEquals("111", d.conta); assertTrue(d.pago)
        assertEquals(420.0, saldo("111"), 0.0)
        assertEquals(0.0, limite(c), 0.0)

        repo.registrarLancamento(lanc(50.0, "111", cartaoId = c, pago = false, data = hoje() + 5 * 86_400_000L))
        assertFalse(todas().single { it.valor == 50.0 }.pago)                         // futuro: pago conforme a data
        assertEquals(420.0, saldo("111"), 0.0)

        val ids = repo.registrarParcelado(lanc(90.0, "111", cartaoId = c, pago = false), 3)  // parcelas forçadas a 1
        assertEquals(1, ids.size)
        assertEquals(90.0, todas().single { it.valor == 90.0 }.valor, 0.0)
        assertNull(todas().single { it.valor == 90.0 }.cartaoId)
        assertEquals(330.0, saldo("111"), 0.0)
    }

    @Test fun `cartao multiplo segue a escolha e padrao e credito`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val m = novoCartaoTipo("111", "MÚLTIPLO", 1000.0)
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = m, pago = false))
        assertEquals(m, todas().single { it.valor == 100.0 }.cartaoId)
        assertEquals(900.0, limite(m), 0.0); assertEquals(500.0, saldo("111"), 0.0)

        repo.registrarLancamento(lanc(40.0, "111", cartaoId = m, pago = false), Financas.Modalidade.DEBITO)
        assertNull(todas().single { it.valor == 40.0 }.cartaoId)
        assertEquals(460.0, saldo("111"), 0.0); assertEquals(900.0, limite(m), 0.0)

        repo.registrarLancamento(lanc(10.0, "111", cartaoId = m, pago = false), Financas.Modalidade.CREDITO)
        assertEquals(m, todas().single { it.valor == 10.0 }.cartaoId)
        val ids = repo.registrarParcelado(lanc(300.0, "111", cartaoId = m, pago = false), 3, Financas.Modalidade.CREDITO)
        assertEquals(3, ids.size)
    }

    @Test fun `cartao de credito ignora pedido de debito`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val c = novoCartaoTipo("111", "CRÉDITO", 1000.0)
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = c, pago = false), Financas.Modalidade.DEBITO)
        assertEquals(c, todas().single { it.valor == 100.0 }.cartaoId)
        assertEquals(900.0, limite(c), 0.0); assertEquals(500.0, saldo("111"), 0.0)
    }

    @Test fun `compra no debito pelo virtual cai na conta do fisico`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val f = novoCartaoTipo("111", "DÉBITO", 0.0)
        val v = novoVirtual(f)
        repo.registrarLancamento(lanc(60.0, "zzz", cartaoId = v, pago = false))
        todas().single { it.valor == 60.0 }.let { assertEquals("111", it.conta); assertNull(it.cartaoId) }
        assertEquals(440.0, saldo("111"), 0.0)
    }

    @Test fun `recorrencia em cartao de debito gera na conta e multiplo continua credito`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val deb = novoCartaoTipo("111", "DÉBITO", 0.0, "Deb")
        repo.salvarDespesaFixa(regraFixa("111", deb))
        val geradas = todas().filter { it.descricao == "Streaming" }
        assertEquals(2, geradas.size)
        assertTrue(geradas.all { it.cartaoId == null && it.conta == "111" })
        assertEquals(deb, db.despesaFixaDao().obterTodas().single().cartaoId)         // a regra continua apontando o cartão

        val mul = novoCartaoTipo("111", "MÚLTIPLO", 1000.0, "Mul")
        repo.salvarDespesaFixa(regraFixa("111", mul).copy(descricao = "Outra"))
        val outras = todas().filter { it.descricao == "Outra" }
        assertTrue(outras.isNotEmpty() && outras.all { it.cartaoId == mul })
    }

    @Test fun `trocar o cartao na edicao reaplica a modalidade`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val cred = novoCartaoTipo("111", "CRÉDITO", 1000.0, "Cred")
        val deb = novoCartaoTipo("111", "DÉBITO", 0.0, "Deb")
        val id = repo.registrarLancamento(lanc(100.0, "111", cartaoId = cred, pago = false))
        assertEquals(900.0, limite(cred), 0.0)
        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = deb))
        val d = repo.obterDespesaPorId(id)!!
        assertNull(d.cartaoId); assertTrue(d.pago)
        assertEquals(400.0, saldo("111"), 0.0); assertEquals(1000.0, limite(cred), 0.0)
    }

    // ------------------------------------------------------------------ vínculo de débito (debito:<cartao>)

    @Test fun `compra no debito fica vinculada ao cartao e excluir uma nao apaga as outras`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val c = novoCartaoTipo("111", "DÉBITO", 0.0)
        val a = repo.registrarLancamento(lanc(10.0, "111", cartaoId = c, pago = false))
        val b = repo.registrarLancamento(lanc(20.0, "111", cartaoId = c, pago = false))
        assertEquals("debito:$c", repo.obterDespesaPorId(a)!!.grupoId)
        assertEquals("debito:$c", repo.obterDespesaPorId(b)!!.grupoId)
        repo.excluirLancamento(a)                                // vínculo não é grupo de parcelas/transferência
        assertNull(repo.obterDespesaPorId(a)); assertNotNull(repo.obterDespesaPorId(b))
        assertEquals(480.0, saldo("111"), 0.0)
        // editar um débito vinculado (sem trocar de cartão) preserva o vínculo
        repo.atualizarLancamento(repo.obterDespesaPorId(b)!!.copy(valor = 25.0))
        assertEquals("debito:$c", repo.obterDespesaPorId(b)!!.grupoId)
        assertEquals(475.0, saldo("111"), 0.0)
    }

    @Test fun `debito vinculado aparece na tela do cartao fora da fatura e do limite`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val m = novoCartaoTipo("111", "MÚLTIPLO", 1000.0)
        val v = novoVirtual(m)
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = m, pago = false))                       // crédito
        repo.registrarLancamento(lanc(40.0, "111", cartaoId = v, pago = false), Financas.Modalidade.DEBITO) // débito pelo virtual
        repo.registrarLancamento(lanc(15.0, "111"))                                                      // lançamento comum

        val doFisico = repo.getComprasDoGrupoDe(m).first()
        val doVirtual = repo.getComprasDoGrupoDe(v).first()
        assertEquals(m, doFisico.principalId); assertEquals(m, doVirtual.principalId)  // virtual → grupo do físico
        assertEquals(listOf(100.0), doFisico.credito.map { it.valor })
        assertEquals(listOf(40.0), doFisico.debito.map { it.valor })
        assertEquals(doFisico, doVirtual)
        assertEquals(listOf(100.0), repo.getDespesasDoGrupoDe(v).first().map { it.valor })
        assertEquals(900.0, limite(m), 0.0)                                                              // débito não consome limite
        val fatura = Financas.resumoFatura(setOf(m, v), 25, 5, doFisico.credito, Financas.faturaDaCompra(hoje(), 25))
        assertEquals(100.0, fatura.total, 0.0)
    }

    @Test fun `excluir virtual leva o vinculo de debito para o fisico`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val f = novoCartaoTipo("111", "DÉBITO", 0.0)
        val v = novoVirtual(f)
        val id = repo.registrarLancamento(lanc(30.0, "111", cartaoId = v, pago = false))
        assertEquals("debito:$v", repo.obterDespesaPorId(id)!!.grupoId)
        repo.excluirCartao(db.cartaoDao().getCartaoPorId(v)!!)
        assertEquals("debito:$f", repo.obterDespesaPorId(id)!!.grupoId)
        assertEquals(listOf(30.0), repo.getComprasDoGrupoDe(f).first().debito.map { it.valor })
    }

    @Test fun `trocar para cartao de credito remove o vinculo de debito`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val deb = novoCartaoTipo("111", "DÉBITO", 0.0, "Deb")
        val cred = novoCartaoTipo("111", "CRÉDITO", 1000.0, "Cred")
        val id = repo.registrarLancamento(lanc(100.0, "111", cartaoId = deb, pago = false))
        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = cred, pago = false))
        val d = repo.obterDespesaPorId(id)!!
        assertEquals(cred, d.cartaoId); assertNull(d.grupoId)
    }

    // ------------------------------------------------------------------ R42 ajuste de saldo

    @Test fun `ajuste de saldo cria lancamento AJUSTE que entra no saldo mas nao em receita ou despesa`() = runBlocking {
        repo.salvarConta(conta("111", 100.0))
        val a = repo.ajustarSaldoConta("111", 150.55)!!
        assertEquals(TipoDespesa.CREDITO, a.tipo); assertEquals(5055L, a.centavos)
        assertEquals(150.55, saldo("111"), 0.0)
        val l = todas().single { it.natureza == Natureza.AJUSTE }
        assertEquals(50.55, l.valor, 0.0); assertEquals(TipoDespesa.CREDITO, l.tipo); assertTrue(l.pago); assertNull(l.cartaoId)
        assertEquals("Ajuste de saldo", l.categoria); assertEquals("Ajuste de saldo (conferido com o banco)", l.descricao)
        val k = Financas.kpisPeriodo(todas(), null, null)
        assertEquals(0.0, k.receitasRealizadas, 0.0); assertEquals(0.0, k.despesasTotal, 0.0)  // o saldo inicial também fica de fora

        assertNull(repo.ajustarSaldoConta("111", 150.55))                              // já igual: nada criado
        assertEquals(1, todas().count { it.natureza == Natureza.AJUSTE })

        val b = repo.ajustarSaldoConta("111", 100.0, "conferido")!!
        assertEquals(TipoDespesa.DEBITO, b.tipo); assertEquals(5055L, b.centavos)
        assertEquals(100.0, saldo("111"), 0.0)
        assertTrue(todas().any { it.natureza == Natureza.AJUSTE && it.descricao.endsWith("conferido") })
    }

    @Test fun `ajuste pode ser excluido para desfazer e conta inexistente falha`() = runBlocking {
        repo.salvarConta(conta("111", 100.0))
        repo.ajustarSaldoConta("111", -20.0)                                            // saldo real negativo
        assertEquals(-20.0, saldo("111"), 0.0)
        repo.excluirLancamento(todas().single { it.natureza == Natureza.AJUSTE }.id)
        assertEquals(100.0, saldo("111"), 0.0)
        exige<RegraFinanceiraException> { runBlocking { repo.ajustarSaldoConta("999", 10.0) } }
        exige<RegraFinanceiraException> { runBlocking { repo.ajustarSaldoConta("111", Double.NaN) } }
    }

    // ------------------------------------------------ trocar o vínculo físico <-> virtual (edição pelo app)

    @Test fun `trocar o fisico de um virtual move o cartao de grupo e herda os dados do novo fisico`() = runBlocking {
        repo.salvarConta(conta("111", 0.0)); repo.salvarConta(conta("222", 0.0))
        val a = novoCartao("111", limite = 1000.0)
        val b = novoCartao("222", limite = 3000.0, fecha = 10, vence = 20)
        val v = novoVirtual(a)
        val atual = db.cartaoDao().getCartaoPorId(v)!!
        repo.salvarCartao(atual.copy(cartaoPrincipalId = b))
        val novo = db.cartaoDao().getCartaoPorId(v)!!
        assertEquals(b, novo.cartaoPrincipalId)
        assertEquals(contaId("222"), novo.contaId)
        assertEquals(3000.0, novo.limiteTotal, 0.0)
        assertEquals(10, novo.diaFechamento)
        assertEquals(listOf(b, v).sorted(), db.cartaoDao().obterGrupo(b).map { it.id }.sorted())
        assertEquals(listOf(a), db.cartaoDao().obterGrupo(a).map { it.id })
    }

    @Test fun `virtual vira fisico independente e fisico com virtuais nao vira virtual`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val a = novoCartao("111", limite = 1000.0)
        val v = novoVirtual(a)
        val outro = novoCartao("111", limite = 500.0)
        // físico com virtuais não pode virar virtual de outro
        exige<RegraFinanceiraException> { runBlocking { repo.salvarCartao(db.cartaoDao().getCartaoPorId(a)!!.copy(cartaoPrincipalId = outro)) } }
        // virtual -> físico independente (sem limite próprio)
        repo.salvarCartao(db.cartaoDao().getCartaoPorId(v)!!.copy(cartaoPrincipalId = null, limiteProprio = 100.0))
        val liberado = db.cartaoDao().getCartaoPorId(v)!!
        assertNull(liberado.cartaoPrincipalId); assertNull(liberado.limiteProprio)
        assertEquals(listOf(v), db.cartaoDao().obterGrupo(v).map { it.id })
    }
}
