package com.meudinheiro.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.Natureza
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

/** Ações dos lançamentos (excluir parcelas, antecipar, editar, alternar) de ponta a ponta contra SQLite em memória. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LancamentoAcoesRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository

    @Before fun abrir() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
    }

    @After fun fechar() = db.close()

    private fun ms(ano: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private fun conta(numero: String, saldo: Double = 0.0) =
        ContaSaldo(banco = "Banco $numero", pic = "p", agencia = "1", conta = numero, titular = "", saldo = saldo, id = 0)

    private fun lanc(
        valor: Double, conta: String, pago: Boolean = false, cartaoId: Int? = null,
        data: Long = System.currentTimeMillis() + 10 * 86_400_000L, tipo: TipoDespesa = TipoDespesa.DEBITO, desc: String = "Emprestimo"
    ) = Despesa(
        descricao = desc, valor = valor, data = Date(data), categoria = "Outros", conta = conta, pic = "p",
        tipo = tipo, mes = 0, ano = 0, cartaoId = cartaoId, pago = pago
    )

    private fun saldo(n: String) = runBlocking { db.contaSaldoDao().obterPorNumero(n)!!.saldo }
    private fun limite(id: Int) = runBlocking { db.cartaoDao().getCartaoPorId(id)!!.limiteDisponivel }
    private fun contaId(n: String) = runBlocking { db.contaSaldoDao().obterPorNumero(n)!!.id }
    private fun todas() = runBlocking { db.despesaDao().obterTodasStatic() }
    private fun lixeira() = runBlocking { db.lixeiraDao().obterTodasStatic() }

    private fun novoCartao(contaNumero: String, tipo: String = "CRÉDITO", limite: Double = 1000.0): Int = runBlocking {
        repo.salvarCartao(
            Cartao(nome = "Visa", finalCartao = "1234", tipo = tipo, limiteDisponivel = 0.0, limiteTotal = limite,
                diaFechamento = 25, diaVencimento = 5, contaId = contaId(contaNumero))
        )
        db.cartaoDao().obterTodasStatic().last().id
    }

    // ------------------------------------------------------------------ ids grandes (vindos do portal)

    @Test fun `lista de conta preserva ids acima de 32 bits e as acoes atingem o lancamento certo`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val idGrande = 1_760_000_123_456L // como os ids gerados pelo portal (epoch ms)
        db.despesaDao().inserirDespesa(lanc(50.0, "111", pago = false).copy(id = idGrande))

        val daLista = db.despesaDao().obterDespesas().first()
        assertEquals(idGrande, daLista.single().id)

        repo.alternarPago(daLista.single().id, true)
        assertTrue(todas().single { it.id == idGrande }.pago)
        assertEquals(1, repo.excluirLancamento(daLista.single().id))
        assertTrue(todas().none { it.id == idGrande })
    }

    // ------------------------------------------------------------------ excluir parcelas

    @Test fun `excluir so esta parcela mantem as outras e excluir todas leva o grupo para a lixeira`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarParcelado(lanc(300.0, "111", data = ms(2027, 1, 10)), 3)
        val parcelas = todas().filter { it.natureza == Natureza.NORMAL }.sortedBy { it.dataMs }
        assertEquals(3, parcelas.size)
        assertTrue(parcelas.all { it.grupoId!!.startsWith("parc:") })

        assertEquals(1, repo.excluirLancamento(parcelas[0].id, grupoParcelas = false))
        assertEquals(2, todas().count { it.natureza == Natureza.NORMAL })
        assertEquals(1, lixeira().size)

        assertEquals(2, repo.excluirLancamento(parcelas[1].id, grupoParcelas = true))
        assertEquals(0, todas().count { it.natureza == Natureza.NORMAL })
        assertEquals(3, lixeira().size)
    }

    @Test fun `excluir todas as parcelas de cartao devolve o limite`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val cartao = novoCartao("111", limite = 1000.0)
        repo.registrarParcelado(lanc(400.0, "111", cartaoId = cartao, data = ms(2027, 1, 10)), 4)
        assertEquals(600.0, limite(cartao), 0.0)
        val uma = todas().first { it.cartaoId == cartao }
        assertEquals(4, repo.excluirLancamento(uma.id, grupoParcelas = true))
        assertEquals(1000.0, limite(cartao), 0.0)
    }

    @Test fun `parcelas antigas com uuid puro tambem excluem em grupo`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val g = java.util.UUID.randomUUID().toString()
        repeat(2) { repo.registrarLancamento(lanc(50.0, "111").copy(grupoId = g)) }
        val id = todas().first { it.grupoId == g }.id
        assertEquals(2, repo.excluirLancamento(id, grupoParcelas = true))
    }

    @Test fun `fora de parcelamento grupoParcelas exclui so o item`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        repo.registrarLancamento(lanc(10.0, "111"))
        val original = todas().single { it.natureza == Natureza.NORMAL }
        repo.repetirLancamento(original.id, 2, 1, com.meudinheiro.domain.Analises.UnidadeRepeticao.MESES)
        val rep = todas().first { it.grupoId?.startsWith("rep:") == true }
        assertEquals(1, repo.excluirLancamento(rep.id, grupoParcelas = true))
        assertEquals(2, todas().count { it.natureza == Natureza.NORMAL })
    }

    @Test fun `aporte de meta e pagamento de fatura nao podem ser excluidos`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        db.despesaDao().inserirDespesa(lanc(10.0, "111", pago = true).copy(natureza = Natureza.APORTE_META, mes = 1, ano = 2026))
        val id = todas().single { it.natureza == Natureza.APORTE_META }.id
        val ex = runCatching { repo.excluirLancamento(id) }.exceptionOrNull()
        assertTrue(ex is RegraFinanceiraException)
        assertTrue(ex!!.message!!.contains("Metas"))
        assertEquals(1, todas().count { it.natureza == Natureza.APORTE_META })
    }

    // ------------------------------------------------------------------ editar / alternar

    @Test fun `editar valor e tipo de lancamento pago recalcula o saldo`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(100.0, "111", pago = true, data = ms(2026, 1, 5)))
        val d = todas().single { it.natureza == Natureza.NORMAL }
        assertEquals(900.0, saldo("111"), 0.0)
        repo.atualizarLancamento(d.copy(valor = 40.0))
        assertEquals(960.0, saldo("111"), 0.0)
        repo.atualizarLancamento(repo.obterDespesaPorId(d.id)!!.copy(tipo = TipoDespesa.CREDITO))
        assertEquals(1040.0, saldo("111"), 0.0)
    }

    @Test fun `editar compra de cartao troca de cartao e mantem limite coerente`() = runBlocking {
        repo.salvarConta(conta("111", 0.0))
        val a = novoCartao("111", limite = 1000.0)
        val b = novoCartao("111", limite = 500.0)
        repo.registrarLancamento(lanc(200.0, "111", cartaoId = a))
        val d = todas().single { it.cartaoId == a }
        repo.atualizarLancamento(d.copy(cartaoId = b, pago = true)) // pago é descartado: compra de cartão fica em aberto
        assertEquals(1000.0, limite(a), 0.0)
        assertEquals(300.0, limite(b), 0.0)
        assertFalse(repo.obterDespesaPorId(d.id)!!.pago)
    }

    @Test fun `editar de cartao multiplo para debito vira lancamento direto da conta`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val credito = novoCartao("111", tipo = "CRÉDITO")
        val multiplo = novoCartao("111", tipo = "MÚLTIPLO")
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = credito))
        val d = todas().single { it.cartaoId == credito }
        repo.atualizarLancamento(d.copy(cartaoId = multiplo, data = Date(ms(2026, 1, 5))), Financas.Modalidade.DEBITO)
        val novo = repo.obterDespesaPorId(d.id)!!
        assertNull(novo.cartaoId)
        assertTrue(novo.pago)
        assertEquals("debito:$multiplo", novo.grupoId)
        assertEquals(900.0, saldo("111"), 0.0)
    }

    @Test fun `mover debito vinculado para outra conta desfaz o vinculo`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        val debito = novoCartao("111", tipo = "DÉBITO")
        repo.registrarLancamento(lanc(50.0, "111", cartaoId = debito, data = ms(2026, 1, 5)))
        val d = todas().single { it.natureza == Natureza.NORMAL }
        assertEquals("debito:$debito", d.grupoId)
        repo.atualizarLancamento(d.copy(conta = "222"))
        val movido = repo.obterDespesaPorId(d.id)!!
        assertEquals("222", movido.conta)
        assertNull(movido.grupoId)
        assertEquals(1000.0, saldo("111"), 0.0)
        assertEquals(-50.0, saldo("222"), 0.0)
    }

    // ------------------------------------------------------------------ criação: o cartão escolhido chega ao banco

    @Test fun `lancamento criado para cartao de credito grava cartaoId e nao mexe no saldo da conta`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartao = novoCartao("111", limite = 1000.0)
        val id = repo.registrarLancamento(lanc(150.0, "111", cartaoId = cartao, data = ms(2026, 1, 5)))
        val d = repo.obterDespesaPorId(id)!!
        assertEquals(cartao, d.cartaoId)
        assertFalse(d.pago)
        assertNull(d.grupoId)
        assertEquals(1000.0, saldo("111"), 0.0)
        assertEquals(850.0, limite(cartao), 0.0)
    }

    @Test fun `cartao multiplo sem modalidade e credito e com DEBITO sai direto da conta (intencional R42)`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val multiplo = novoCartao("111", tipo = "MÚLTIPLO", limite = 1000.0)
        val credito = repo.registrarLancamento(lanc(100.0, "111", cartaoId = multiplo, data = ms(2026, 1, 5)))
        assertEquals(multiplo, repo.obterDespesaPorId(credito)!!.cartaoId)
        assertEquals(1000.0, saldo("111"), 0.0)
        val debito = repo.registrarLancamento(lanc(40.0, "111", cartaoId = multiplo, data = ms(2026, 1, 5)), Financas.Modalidade.DEBITO)
        repo.obterDespesaPorId(debito)!!.let { assertNull(it.cartaoId); assertTrue(it.pago); assertEquals("debito:$multiplo", it.grupoId) }
        assertEquals(960.0, saldo("111"), 0.0)
        assertEquals(900.0, limite(multiplo), 0.0)
    }

    // ------------------------------------------------------------------ R78: mudar o local ao editar

    @Test fun `conta para credito vai para a conta do cartao em aberto e consome limite`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 500.0))
        val cartao = novoCartao("222", limite = 1000.0)
        val id = repo.registrarLancamento(lanc(200.0, "111", pago = true, data = ms(2026, 1, 5)))
        assertEquals(800.0, saldo("111"), 0.0)
        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = cartao), Financas.Modalidade.CREDITO)
        val d = repo.obterDespesaPorId(id)!!
        assertEquals(cartao, d.cartaoId); assertEquals("222", d.conta); assertFalse(d.pago); assertNull(d.grupoId)
        assertEquals(1000.0, saldo("111"), 0.0)   // saiu da conta 111
        assertEquals(500.0, saldo("222"), 0.0)    // crédito não mexe no saldo
        assertEquals(800.0, limite(cartao), 0.0)
    }

    @Test fun `conta para debito vira lancamento da conta do cartao com vinculo e pago pela data`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 500.0))
        val debito = novoCartao("222", tipo = "DÉBITO")
        val passado = repo.registrarLancamento(lanc(100.0, "111", pago = true, data = ms(2026, 1, 5)))
        val futuro = repo.registrarLancamento(lanc(30.0, "111", pago = false, data = ms(2027, 1, 5)))
        repo.atualizarLancamento(repo.obterDespesaPorId(passado)!!.copy(cartaoId = debito), Financas.Modalidade.DEBITO)
        repo.atualizarLancamento(repo.obterDespesaPorId(futuro)!!.copy(cartaoId = debito), Financas.Modalidade.DEBITO)
        repo.obterDespesaPorId(passado)!!.let { assertNull(it.cartaoId); assertEquals("222", it.conta); assertEquals("debito:$debito", it.grupoId); assertTrue(it.pago) }
        assertFalse(repo.obterDespesaPorId(futuro)!!.pago)
        assertEquals(1000.0, saldo("111"), 0.0) // os 100 voltaram; o de 30 estava em aberto
        assertEquals(400.0, saldo("222"), 0.0)
    }

    @Test fun `credito para conta devolve o limite e entra no saldo da conta escolhida`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        val cartao = novoCartao("111", limite = 1000.0)
        val id = repo.registrarLancamento(lanc(250.0, "111", cartaoId = cartao, data = ms(2026, 1, 5)))
        assertEquals(750.0, limite(cartao), 0.0)
        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = null, conta = "222", pago = true))
        val d = repo.obterDespesaPorId(id)!!
        assertNull(d.cartaoId); assertEquals("222", d.conta); assertTrue(d.pago)
        assertEquals(1000.0, limite(cartao), 0.0)
        assertEquals(-250.0, saldo("222"), 0.0)
        assertEquals(1000.0, saldo("111"), 0.0)
    }

    @Test fun `debito para conta remove o vinculo mesmo na mesma conta`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val debito = novoCartao("111", tipo = "DÉBITO")
        val id = repo.registrarLancamento(lanc(60.0, "111", cartaoId = debito, data = ms(2026, 1, 5)))
        assertEquals("debito:$debito", repo.obterDespesaPorId(id)!!.grupoId)
        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = null), desvincularDebito = true)
        val d = repo.obterDespesaPorId(id)!!
        assertNull(d.grupoId); assertNull(d.cartaoId); assertEquals("111", d.conta); assertTrue(d.pago)
        assertEquals(940.0, saldo("111"), 0.0)
    }

    @Test fun `credito e debito do mesmo cartao multiplo alternam com saldo e limite coerentes`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val m = novoCartao("111", tipo = "MÚLTIPLO", limite = 1000.0)
        val id = repo.registrarLancamento(lanc(100.0, "111", cartaoId = m, data = ms(2026, 1, 5)))
        assertEquals(900.0, limite(m), 0.0); assertEquals(1000.0, saldo("111"), 0.0)

        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = m), Financas.Modalidade.DEBITO)
        repo.obterDespesaPorId(id)!!.let { assertNull(it.cartaoId); assertEquals("debito:$m", it.grupoId); assertTrue(it.pago) }
        assertEquals(1000.0, limite(m), 0.0); assertEquals(900.0, saldo("111"), 0.0)

        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = m), Financas.Modalidade.CREDITO)
        repo.obterDespesaPorId(id)!!.let { assertEquals(m, it.cartaoId); assertNull(it.grupoId); assertFalse(it.pago) }
        assertEquals(900.0, limite(m), 0.0); assertEquals(1000.0, saldo("111"), 0.0)
    }

    @Test fun `editar sem mudar o local de um debito de cartao preserva vinculo e pago`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val m = novoCartao("111", tipo = "MÚLTIPLO", limite = 1000.0)
        val id = repo.registrarLancamento(lanc(50.0, "111", cartaoId = m, data = ms(2027, 1, 5)), Financas.Modalidade.DEBITO)
        assertFalse(repo.obterDespesaPorId(id)!!.pago) // futuro
        repo.atualizarLancamento(repo.obterDespesaPorId(id)!!.copy(cartaoId = m, valor = 70.0), Financas.Modalidade.DEBITO)
        repo.obterDespesaPorId(id)!!.let { assertEquals("debito:$m", it.grupoId); assertEquals(70.0, it.valor, 0.0); assertNull(it.cartaoId) }
    }

    @Test fun `parcelas conta para credito e credito para conta valem para o grupo inteiro`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        val cartao = novoCartao("222", limite = 1000.0)
        // 3 parcelas de 100: a 1ª no passado (paga), as demais futuras (+1 e +2 meses).
        repo.registrarParcelado(lanc(300.0, "111", data = System.currentTimeMillis() - 5 * 86_400_000L), 3)
        val parcelas = todas().filter { it.natureza == Natureza.NORMAL }.sortedBy { it.dataMs }
        val grupo = parcelas.first().grupoId!!
        assertTrue(parcelas.all { it.grupoId == grupo })
        assertTrue(parcelas.first().pago)

        // conta -> crédito (editando a 2ª): todas vão ao cartão, em aberto
        repo.atualizarLancamento(parcelas[1].copy(cartaoId = cartao), Financas.Modalidade.CREDITO)
        val noCartao = todas().filter { it.grupoId == grupo }
        assertTrue(noCartao.all { it.cartaoId == cartao && it.conta == "222" && !it.pago })
        assertEquals(1000.0, saldo("111"), 0.0)
        assertEquals(700.0, limite(cartao), 0.0)

        // crédito -> conta 111 (editando a 3ª): todas voltam; pago coerente com a data de cada uma
        val ultima = todas().single { it.grupoId == grupo && it.descricao.endsWith("(3/3)") }
        repo.atualizarLancamento(ultima.copy(cartaoId = null, conta = "111", pago = false))
        val naConta = todas().filter { it.grupoId == grupo }.sortedBy { it.dataMs }
        assertTrue(naConta.all { it.cartaoId == null && it.conta == "111" })
        assertTrue(naConta.first().pago)                 // data passada
        assertTrue(naConta.drop(1).none { it.pago })      // as demais seguem a própria data (futuras)
        assertEquals(1000.0, limite(cartao), 0.0)
        assertEquals(900.0, saldo("111"), 0.0)
    }

    @Test fun `parcelas para debito ficam na conta do cartao sem perder o grupo de parcelas`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val m = novoCartao("111", tipo = "MÚLTIPLO", limite = 1000.0)
        repo.registrarParcelado(lanc(200.0, "111", cartaoId = m, data = ms(2027, 1, 5)), 2)
        val p = todas().filter { it.natureza == Natureza.NORMAL }
        assertEquals(800.0, limite(m), 0.0)
        repo.atualizarLancamento(p.first().copy(cartaoId = m), Financas.Modalidade.DEBITO)
        val depois = todas().filter { it.natureza == Natureza.NORMAL }
        assertTrue(depois.all { it.cartaoId == null && it.conta == "111" && it.grupoId!!.startsWith("parc:") })
        assertEquals(1000.0, limite(m), 0.0)
    }

    @Test fun `mudar o local de natureza bloqueada e recusado sem alterar nada`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        val cartao = novoCartao("111", limite = 500.0)
        repo.transferirEntreContas("111", "222", 10.0)
        val t = todas().first { it.natureza == Natureza.TRANSFERENCIA }
        val ex = runCatching { repo.atualizarLancamento(t.copy(cartaoId = cartao), Financas.Modalidade.CREDITO) }.exceptionOrNull()
        assertTrue(ex is RegraFinanceiraException)
        assertNull(repo.obterDespesaPorId(t.id)!!.cartaoId)
        assertEquals(500.0, limite(cartao), 0.0)
    }

    @Test fun `natureza gerada nao pode ser editada nem alternada`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0)); repo.salvarConta(conta("222", 0.0))
        repo.transferirEntreContas("111", "222", 10.0)
        val t = todas().first { it.natureza == Natureza.TRANSFERENCIA }
        assertTrue(runCatching { repo.atualizarLancamento(t.copy(valor = 99.0)) }.exceptionOrNull() is RegraFinanceiraException)
        assertTrue(runCatching { repo.alternarPago(t.id, false) }.exceptionOrNull() is RegraFinanceiraException)
    }

    @Test fun `saldo inicial editado continua pago`() = runBlocking {
        repo.salvarConta(conta("111", 500.0))
        val si = todas().single { it.natureza == Natureza.SALDO_INICIAL }
        repo.atualizarLancamento(si.copy(valor = 800.0, pago = false))
        assertTrue(repo.obterDespesaPorId(si.id)!!.pago)
        assertEquals(800.0, saldo("111"), 0.0)
    }

    // ------------------------------------------------------------------ antecipar

    @Test fun `antecipar quitando parcelas com desconto baixa so o que saiu da conta`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarParcelado(lanc(300.0, "111", data = ms(2027, 1, 10)), 3)
        val ids = repo.antecipaveisDoGrupo(todas().first { it.natureza == Natureza.NORMAL }.id).map { it.id }
        assertEquals(3, ids.size)
        val r = repo.anteciparPagamento(ids, valorPago = 270.0, desconto = 30.0, data = ms(2026, 10, 7))
        assertEquals(30.0, r.economia, 0.0)
        val pagas = todas().filter { it.natureza == Natureza.NORMAL }
        assertTrue(pagas.all { it.pago })
        assertEquals(270.0, pagas.sumOf { it.valor }, 0.0)
        assertEquals(730.0, saldo("111"), 0.0)
    }

    @Test fun `antecipar parcial divide o item e o saldo cai so pelo valor pago`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        repo.registrarLancamento(lanc(200.0, "111", data = ms(2027, 1, 10)))
        val id = todas().single { it.natureza == Natureza.NORMAL }.id
        val r = repo.anteciparPagamento(listOf(id), valorPago = 80.0)
        assertEquals(120.0, r.restante, 0.0)
        val itens = todas().filter { it.natureza == Natureza.NORMAL }
        assertEquals(2, itens.size)
        assertEquals(120.0, itens.single { !it.pago }.valor, 0.0)
        assertEquals(80.0, itens.single { it.pago }.valor, 0.0)
        assertEquals(920.0, saldo("111"), 0.0)
    }

    @Test fun `antecipar recusa compra de cartao e e atomico em caso de erro`() = runBlocking {
        repo.salvarConta(conta("111", 1000.0))
        val cartao = novoCartao("111")
        repo.registrarLancamento(lanc(100.0, "111", cartaoId = cartao))
        repo.registrarLancamento(lanc(50.0, "111"))
        val compra = todas().single { it.cartaoId == cartao }
        val comum = todas().single { it.natureza == Natureza.NORMAL && it.cartaoId == null }
        assertTrue(repo.antecipaveisDoGrupo(compra.id).isEmpty())
        val ex = runCatching { repo.anteciparPagamento(listOf(comum.id, compra.id), 150.0) }.exceptionOrNull()
        assertTrue(ex is RegraFinanceiraException)
        assertFalse(repo.obterDespesaPorId(comum.id)!!.pago)
        assertEquals(1000.0, saldo("111"), 0.0)
        assertTrue(runCatching { repo.anteciparPagamento(listOf(comum.id), 60.0) }.exceptionOrNull() is RegraFinanceiraException) // excede
    }
}
