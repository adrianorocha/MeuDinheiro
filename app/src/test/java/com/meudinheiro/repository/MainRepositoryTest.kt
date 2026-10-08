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
}

