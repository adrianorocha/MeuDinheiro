package com.meudinheiro.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Divida
import com.meudinheiro.data.Pessoa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Natureza
import com.meudinheiro.domain.Rateios
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
import java.util.Date

/** R58/R59 contra SQLite em memória: pagamento abatendo saldo devedor, rateio sem mexer no lançamento, acertos, backup antigo/novo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DividasRateiosRepositoryTest {

    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository

    @Before fun abrir() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
        runBlocking { repo.salvarConta(ContaSaldo(banco = "B", pic = "p", agencia = "1", conta = "111", titular = "", saldo = 1000.0)) }
    }

    @After fun fechar() = db.close()

    private fun saldo() = runBlocking { db.contaSaldoDao().obterPorNumero("111")!!.saldo }
    private fun dividas() = runBlocking { db.dividaDao().obterTodasStatic() }
    private fun acertos() = runBlocking { db.acertoDao().obterTodasStatic() }
    private fun despesas() = runBlocking { db.despesaDao().obterTodasStatic() }

    private fun novaDivida(saldo: Double = 1000.0) = runBlocking {
        repo.salvarDivida(Divida(nome = "Empréstimo Banco", tipo = "EMPRESTIMO", saldoDevedor = saldo, taxaJurosMensal = 2.0, parcelaMinima = 200.0)).toInt()
    }

    private fun exige(bloco: () -> Unit): RegraFinanceiraException {
        try {
            bloco()
        } catch (e: RegraFinanceiraException) {
            return e
        }
        fail("esperava RegraFinanceiraException")
        throw IllegalStateException()
    }

    private fun pizza(valor: Double = 100.0) = runBlocking {
        repo.registrarLancamento(
            Despesa(descricao = "Pizza", valor = valor, data = Date(1_760_000_000_000), categoria = "Alimentação", conta = "111", pic = "p",
                tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = true)
        )
    }

    // ------------------------------------------------------------------ R58

    @Test fun `cadastro guarda o valor original e a edicao preserva`() {
        val id = novaDivida(5000.0)
        val d = dividas().single()
        assertEquals(5000.0, d.valorOriginal!!, 0.0)
        runBlocking { repo.salvarDivida(d.copy(saldoDevedor = 4000.0, valorOriginal = null)) }
        assertEquals(5000.0, dividas().single { it.id == id }.valorOriginal!!, 0.0)
        assertEquals(4000.0, dividas().single().saldoDevedor, 0.0)
        exige { runBlocking { repo.salvarDivida(Divida(nome = " ", saldoDevedor = 1.0)) } }
        exige { runBlocking { repo.salvarDivida(Divida(nome = "X", saldoDevedor = 1.0, taxaJurosMensal = 101.0)) } }
    }

    @Test fun `pagamento cria despesa NORMAL em Dividas com grupoId e abate o saldo devedor sem duplicar o saldo da conta`() = runBlocking {
        val id = novaDivida()
        val lanc = repo.pagarDivida(id, 300.0, "111")
        val d = db.despesaDao().obterDespesaPorId(lanc)!!
        assertEquals(Natureza.NORMAL, d.natureza)
        assertEquals(TipoDespesa.DEBITO, d.tipo)
        assertEquals("Dívidas", d.categoria)
        assertEquals("divida:$id", d.grupoId)
        assertTrue(d.pago)
        assertEquals(300.0, d.valor, 0.0)
        assertEquals(700.0, dividas().single().saldoDevedor, 0.0)
        assertTrue(dividas().single().ativa)
        assertEquals(700.0, saldo(), 0.0) // 1000 - 300, uma vez só
    }

    @Test fun `juros do mes acrescentam ao saldo devedor antes de abater`() = runBlocking {
        val id = novaDivida()
        repo.pagarDivida(id, 200.0, "111", juros = 20.0)
        assertEquals(820.0, dividas().single().saldoDevedor, 0.0)
        assertEquals(800.0, saldo(), 0.0)
        repo.registrarJurosDivida(id, 33.33)
        assertEquals(853.33, dividas().single().saldoDevedor, 0.0)
        assertEquals(800.0, saldo(), 0.0)
    }

    @Test fun `quitacao total inativa a divida e aceita categoria escolhida`() = runBlocking {
        val id = novaDivida()
        val lanc = repo.pagarDivida(id, 1000.0, "111", categoria = "Banco")
        assertEquals("Banco", db.despesaDao().obterDespesaPorId(lanc)!!.categoria)
        assertEquals(0.0, dividas().single().saldoDevedor, 0.0)
        assertFalse(dividas().single().ativa)
    }

    @Test fun `recusa pagamento acima do devido, conta e divida inexistentes, sem alterar nada`() {
        val id = novaDivida()
        val antes = despesas().size
        exige { runBlocking { repo.pagarDivida(id, 1000.01, "111") } }
        exige { runBlocking { repo.pagarDivida(id, 10.0, "999") } }
        exige { runBlocking { repo.pagarDivida(id + 50, 10.0, "111") } }
        exige { runBlocking { repo.pagarDivida(id, 0.0, "111") } }
        assertEquals(antes, despesas().size)
        assertEquals(1000.0, dividas().single().saldoDevedor, 0.0)
        assertEquals(1000.0, saldo(), 0.0)
    }

    // ------------------------------------------------------------------ R59

    private fun duasPessoas(): Pair<Int, Int> = runBlocking {
        repo.salvarPessoa(Pessoa(nome = "Ana")).toInt() to repo.salvarPessoa(Pessoa(nome = "Bia", contato = " 11 99 ")).toInt()
    }

    @Test fun `pessoas - nome unico e exclusao so sem acertos`() = runBlocking {
        val (ana, _) = duasPessoas()
        assertEquals("11 99", db.pessoaDao().obterTodasStatic().single { it.nome == "Bia" }.contato)
        exige { runBlocking { repo.salvarPessoa(Pessoa(nome = " ana ")) } }
        exige { runBlocking { repo.salvarPessoa(Pessoa(nome = " ")) } }
        repo.criarAcertoAvulso(ana, Rateios.A_RECEBER, 10.0, null)
        exige { runBlocking { repo.excluirPessoa(ana) } }
        repo.excluirAcerto(acertos().single().id)
        repo.excluirPessoa(ana)
        assertEquals(1, db.pessoaDao().obterTodasStatic().size)
    }

    @Test fun `dividir cria acertos A_RECEBER sem alterar lancamento nem saldo`() = runBlocking {
        val (ana, bia) = duasPessoas()
        val id = pizza()
        val antes = db.despesaDao().obterDespesaPorId(id)!!
        val saldoAntes = saldo()
        val voce = repo.dividirDespesa(id, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana), Rateios.Participante(bia)))
        assertEquals(3334L, voce)
        assertEquals(antes, db.despesaDao().obterDespesaPorId(id))
        assertEquals(saldoAntes, saldo(), 0.0)
        val a = acertos().sortedBy { it.pessoaId }
        assertEquals(2, a.size)
        assertEquals(33.33, a[0].valor, 0.0)
        assertEquals(Rateios.A_RECEBER, a[0].tipo)
        assertEquals(id, a[0].despesaId)
        assertEquals("rateio:$id", a[0].grupoRateio)
        assertFalse(a[0].quitado)
        val r = Rateios.resumo(despesas(), acertos())
        assertEquals(3334L, r.liquido)
        // não pode dividir de novo, nem dividir lançamento que não é despesa comum
        exige { runBlocking { repo.dividirDespesa(id, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana))) } }
        exige { runBlocking { repo.dividirDespesa(999L, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana))) } }
        exige { runBlocking { repo.dividirDespesa(pizza(), Rateios.Modo.PERCENTUAL, listOf(Rateios.Participante(ana, percentual = 500)), 400) } }
        exige { runBlocking { repo.dividirDespesa(pizza(), Rateios.Modo.IGUAL, listOf(Rateios.Participante(9999))) } }
        Unit
    }

    @Test fun `desfazer rateio remove os acertos, mas recusa se algum foi quitado`() = runBlocking {
        val (ana, bia) = duasPessoas()
        val id = pizza()
        repo.dividirDespesa(id, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana), Rateios.Participante(bia)))
        val primeiro = acertos().first().id
        repo.quitarAcerto(primeiro)
        exige { runBlocking { repo.desfazerRateio(id) } }
        repo.excluirAcerto(primeiro)
        assertEquals(1, acertos().size)
        // recria sem quitado
        repo.excluirAcerto(acertos().single().id)
        repo.dividirDespesa(id, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana)))
        assertEquals(1, repo.desfazerRateio(id))
        assertTrue(acertos().isEmpty())
    }

    @Test fun `receber acerto com lancamento cria receita Reembolso paga e o saldo bate`() = runBlocking {
        val (ana, bia) = duasPessoas()
        val id = pizza()
        repo.dividirDespesa(id, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana), Rateios.Participante(bia)))
        assertEquals(900.0, saldo(), 0.0)
        val a = acertos().first { it.pessoaId == ana }
        val sem = repo.quitarAcerto(acertos().first { it.pessoaId == bia }.id)
        assertNull(sem)
        assertEquals(900.0, saldo(), 0.0)
        val lanc = repo.quitarAcerto(a.id, contaLancamento = "111")
        val d = db.despesaDao().obterDespesaPorId(lanc!!)!!
        assertEquals(TipoDespesa.CREDITO, d.tipo)
        assertEquals("Reembolso", d.categoria)
        assertTrue(d.pago)
        assertEquals(933.33, saldo(), 0.0)
        assertTrue(acertos().first { it.id == a.id }.quitado)
        assertNotNull(acertos().first { it.id == a.id }.quitadoEm)
        exige { runBlocking { repo.quitarAcerto(a.id) } }
        // o lançamento original continua com o valor TOTAL
        assertEquals(100.0, db.despesaDao().obterDespesaPorId(id)!!.valor, 0.0)
    }

    @Test fun `emprestei e devo - com e sem lancamento`() = runBlocking {
        val (ana, bia) = duasPessoas()
        repo.criarAcertoAvulso(ana, Rateios.A_RECEBER, 50.0, null)
        assertEquals(1000.0, saldo(), 0.0)
        assertNull(acertos().single().despesaId)
        repo.criarAcertoAvulso(ana, Rateios.A_RECEBER, 50.0, "Almoço", contaLancamento = "111")
        assertEquals(950.0, saldo(), 0.0)
        val comLanc = acertos().first { it.descricao == "Almoço" }
        val l = db.despesaDao().obterDespesaPorId(comLanc.despesaId!!)!!
        assertEquals(TipoDespesa.DEBITO, l.tipo)
        assertEquals("Empréstimos", l.categoria)
        repo.criarAcertoAvulso(bia, Rateios.A_PAGAR, 20.0, "Emprestado", contaLancamento = "111")
        assertEquals(970.0, saldo(), 0.0)
        exige { runBlocking { repo.criarAcertoAvulso(bia, Rateios.A_PAGAR, 0.0, null) } }
        exige { runBlocking { repo.criarAcertoAvulso(9999, Rateios.A_PAGAR, 5.0, null) } }
        exige { runBlocking { repo.criarAcertoAvulso(bia, Rateios.A_PAGAR, 5.0, null, contaLancamento = "999") } }
        // pagar o que devo gera despesa "Acertos"
        val devo = acertos().first { it.tipo == Rateios.A_PAGAR }
        val lanc = repo.quitarAcerto(devo.id, contaLancamento = "111")
        assertEquals("Acertos", db.despesaDao().obterDespesaPorId(lanc!!)!!.categoria)
        assertEquals(950.0, saldo(), 0.0)
    }

    @Test fun `excluir o lancamento de origem deixa o acerto orfao e tolerado`() = runBlocking {
        val (ana, _) = duasPessoas()
        val id = pizza()
        repo.dividirDespesa(id, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana)))
        assertEquals(1, repo.excluirLancamento(id))
        assertEquals(1, acertos().size)
        val r = Rateios.resumo(despesas(), acertos())
        assertTrue(r.itens.single().orfao)
        assertEquals(0L, r.totalRateado)
        assertEquals(5000L, Rateios.saldosPorPessoa(db.pessoaDao().obterTodasStatic(), acertos()).first { it.pessoa.id == ana }.aReceber)
    }

    // ------------------------------------------------------------------ R68 - vincular lançamentos existentes

    private fun cinema() = runBlocking {
        repo.registrarLancamento(
            Despesa(descricao = "Cinema", valor = 60.0, data = Date(1_760_100_000_000), categoria = "Lazer", conta = "111", pic = "p",
                tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = true)
        )
    }

    @Test fun `R68 vincular cria so acertos - lancamentos e saldo identicos`() = runBlocking {
        val (ana, bia) = duasPessoas()
        val p = pizza()
        val c = cinema()
        val antes = despesas().sortedBy { it.id }
        val saldoAntes = saldo()
        val n = repo.vincularLancamentos(ana, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(p, 40.0), Rateios.ItemVinculo(c)))
        assertEquals(2, n)
        assertEquals(antes, despesas().sortedBy { it.id })
        assertEquals(saldoAntes, saldo(), 0.0)
        val a = acertos().first { it.despesaId == p }
        assertEquals("vinculo:$p", a.grupoRateio)
        assertEquals(40.0, a.valor, 0.0)
        assertEquals("Pizza", a.descricao)
        assertEquals(3L, Rateios.saldosPorPessoa(db.pessoaDao().obterTodasStatic(), acertos()).first { it.pessoa.id == ana }.aReceber / 3333L)
        // duplicidade e teto
        exige { runBlocking { repo.vincularLancamentos(ana, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(p, 5.0))) } }
        exige { runBlocking { repo.vincularLancamentos(bia, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(p, 60.01))) } }
        repo.vincularLancamentos(bia, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(p, 60.0)))
        assertEquals(0L, Rateios.gastoLiquido(despesas().first { it.id == p }, acertos()))
    }

    @Test fun `R68 atomico - item invalido nao cria nenhum acerto`() = runBlocking {
        val (ana, _) = duasPessoas()
        val p = pizza()
        val saldoInicial = runBlocking { repo.registrarLancamento(Despesa(descricao = "Transf", valor = 10.0, data = Date(1_760_000_000_000), categoria = "T", conta = "111", pic = "p", tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = true, natureza = Natureza.TRANSFERENCIA)) }
        exige { runBlocking { repo.vincularLancamentos(ana, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(p), Rateios.ItemVinculo(saldoInicial))) } }
        assertTrue(acertos().isEmpty())
    }

    @Test fun `R68 desfazer a divisao nao apaga o vinculo e desvincular e excluir o acerto`() = runBlocking {
        val (ana, bia) = duasPessoas()
        val p = pizza()
        repo.vincularLancamentos(ana, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(p, 20.0)))
        repo.dividirDespesa(p, Rateios.Modo.VALOR, listOf(Rateios.Participante(bia, valor = 3000)))
        exige { runBlocking { repo.dividirDespesa(p + 0, Rateios.Modo.VALOR, listOf(Rateios.Participante(bia, valor = 100))) } } // já dividido
        assertEquals(2, acertos().size)
        repo.desfazerRateio(p)
        val resta = acertos().single()
        assertEquals("vinculo:$p", resta.grupoRateio)
        repo.excluirAcerto(resta.id)
        assertTrue(acertos().isEmpty())
    }

    @Test fun `R68 divisao que passaria do total com vinculo existente e recusada`() = runBlocking {
        val (ana, bia) = duasPessoas()
        val p = pizza()
        repo.vincularLancamentos(ana, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(p, 70.0)))
        exige { runBlocking { repo.dividirDespesa(p, Rateios.Modo.IGUAL, listOf(Rateios.Participante(bia))) } }
        repo.dividirDespesa(p, Rateios.Modo.VALOR, listOf(Rateios.Participante(bia, valor = 3000)))
        assertEquals(0L, Rateios.gastoLiquido(despesas().single { it.id == p }, acertos()))
    }

    @Test fun `R68 receita so A_PAGAR e backup ida e volta com vinculo`() = runBlocking {
        val (ana, _) = duasPessoas()
        val salario = repo.registrarLancamento(
            Despesa(descricao = "Salário", valor = 500.0, data = Date(1_760_000_000_000), categoria = "Salário", conta = "111", pic = "p",
                tipo = TipoDespesa.CREDITO, mes = 0, ano = 0, pago = true)
        )
        exige { runBlocking { repo.vincularLancamentos(ana, Rateios.A_RECEBER, listOf(Rateios.ItemVinculo(salario))) } }
        repo.vincularLancamentos(ana, Rateios.A_PAGAR, listOf(Rateios.ItemVinculo(salario, 250.0)))
        val a0 = acertos()
        val json = repo.gerarBackup()
        repo.limparBancoDeDadosCompleto()
        repo.restaurarBackupCompleto(json)
        assertEquals(a0, acertos())
    }

    // ------------------------------------------------------------------ backup

    @Test fun `backup ida e volta inclui dividas, pessoas e acertos`() = runBlocking {
        novaDivida()
        val (ana, _) = duasPessoas()
        val id = pizza()
        repo.dividirDespesa(id, Rateios.Modo.IGUAL, listOf(Rateios.Participante(ana)))
        val json = repo.gerarBackup()
        val d0 = dividas()
        val p0 = db.pessoaDao().obterTodasStatic().sortedBy { it.id }
        val a0 = acertos()
        repo.limparBancoDeDadosCompleto()
        assertTrue(dividas().isEmpty() && acertos().isEmpty() && db.pessoaDao().obterTodasStatic().isEmpty())
        repo.restaurarBackupCompleto(json)
        assertEquals(d0, dividas())
        assertEquals(p0, db.pessoaDao().obterTodasStatic().sortedBy { it.id })
        assertEquals(a0, acertos())
    }

    @Test fun `backup antigo sem as colecoes novas restaura como sem dividas, pessoas e acertos`() = runBlocking {
        novaDivida()
        repo.restaurarBackupCompleto(
            """{"versaoBackup":2,"contas":[{"id":9,"saldo":0,"banco":"B","pic":"p","agencia":"1","conta":"999","titular":""}],"despesas":[],"projetos":[]}"""
        )
        assertTrue(dividas().isEmpty())
        assertTrue(acertos().isEmpty())
        assertTrue(db.pessoaDao().obterTodasStatic().isEmpty())
        assertEquals("999", db.contaSaldoDao().obterTodasStatic().single().conta)
    }

    @Test fun `backup parcial com campos ausentes e tipo desconhecido e sanitizado`() = runBlocking {
        repo.restaurarBackupCompleto(
            """{"dividas":[{"id":7,"nome":"Y","tipo":"XYZ","saldoDevedor":50}],"pessoas":[{"id":3,"nome":"Z"}],
               "acertos":[{"id":4,"pessoaId":3,"valor":12.345,"descricao":"d","data":1760000000000,"despesaId":777}]}"""
        )
        val d = dividas().single()
        assertEquals("OUTRA", d.tipo)
        assertEquals(0.0, d.taxaJurosMensal, 0.0)
        val a = acertos().single()
        assertEquals("A_RECEBER", a.tipo)
        assertEquals(12.35, a.valor, 0.0)
        assertEquals(777L, a.despesaId)
        assertFalse(a.quitado)
    }
}
