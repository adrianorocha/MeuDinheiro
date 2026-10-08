package com.meudinheiro.storage

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Categoria
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Investimento
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.Transacao
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.domain.Natureza
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/** Garante que Room ⇄ documentos Firestore é lossless para todas as coleções do contrato. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreMapperTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository

    @Before fun abrir() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
    }

    @After fun fechar() = db.close()

    private fun popular() = runBlocking {
        repo.salvarConta(ContaSaldo(banco = "Nubank", pic = "nubank", agencia = "1", conta = "111", titular = "Ana", saldo = 1000.0))
        repo.salvarConta(ContaSaldo(banco = "Itaú", pic = "itau", agencia = "2", conta = "222", titular = "Ana", saldo = 0.0))
        val contaId = db.contaSaldoDao().obterPorNumero("111")!!.id
        repo.salvarCartao(Cartao(nome = "Visa", finalCartao = "1234", tipo = "CRÉDITO", limiteDisponivel = 0.0, limiteTotal = 2000.0, diaFechamento = 25, diaVencimento = 5, contaId = contaId))
        val cartaoId = db.cartaoDao().obterTodasStatic().single().id
        repo.salvarCartao(Cartao(nome = "Virtual", finalCartao = "9999", tipo = "CRÉDITO", limiteDisponivel = 0.0, limiteTotal = 0.0,
            diaFechamento = 1, diaVencimento = 1, contaId = contaId, cartaoPrincipalId = cartaoId))
        repo.registrarLancamento(
            Despesa(descricao = "Mercado ✓", valor = 123.45, data = Date(1_760_000_000_000), categoria = "Alimentação", conta = "111", pic = "p",
                tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, cartaoId = cartaoId, pago = false, valorOriginal = 25.0, moedaOriginal = "USD", cotacaoNaData = 4.938)
        )
        repo.transferirEntreContas("111", "222", 10.0)
        repo.salvarMeta(Meta(nome = "Viagem", valorObjetivo = 5000.0, valorGuardado = 0.0, icone = "ic_x", dataAlvo = 1_800_000_000_000))
        repo.salvarOrcamento("Alimentação", 800.0)
        repo.salvarCategoria(Categoria(nome = "Pets", pic = "pets"))
        repo.inserirAgendamento(TransferenciaAgendada(dataAgendada = 1_760_000_000_000, contaOrigem = "111", contaDestino = "222", valor = 5.0))
        db.investimentoDao().inserir(Investimento(nome = "Tesouro", tipo = "Renda Fixa", valorInvestido = 100.0, valorAtual = 110.5))
        db.despesaFixaDao().inserir(DespesaFixa(descricao = "Aluguel", valor = 900.0, conta = "111", categoria = "Casa", pic = "p", tipo = TipoDespesa.DEBITO, diaVencimento = 31, ultimaDataLancamento = Date(1_750_000_000_000), cartaoId = cartaoId))
        db.transacaoDao().inserir(Transacao(descricao = "legado", valor = -3.0, bancoNome = "Nubank", categoriaNome = "Outros", categoriaCorHex = "#FFFFFF", timestamp = 1_700_000_000_000))
        repo.atualizarSnapshotPatrimonial()
        // um lançamento excluído vai para a lixeira (R31)
        val extra = repo.registrarLancamento(
            Despesa(descricao = "Excluída", valor = 7.0, data = Date(1_760_000_000_000), categoria = "Lazer", conta = "111", pic = "p",
                tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = true, autor = "Ana", fitid = "F123", conciliadoEm = 1_760_000_000_000)
        )
        repo.excluirLancamento(extra)
    }

    @Test fun `todas as colecoes dao ida e volta sem perda`() = runBlocking {
        popular()
        val catalogo = FirestoreMapper.catalogo(db)
        val antes = catalogo.associate { it.nome to it.lerLocal() }
        assertEquals(12, catalogo.size)
        assertTrue("todas as coleções do contrato têm dados no teste", antes.values.all { it.isNotEmpty() })

        // apaga tudo pela porta que o sync usa (ordem inversa) e regrava pelos documentos
        db.withTransaction {
            catalogo.asReversed().forEach { c -> c.apagarLocal(antes.getValue(c.nome).keys.toList()) }
        }
        assertTrue(catalogo.all { it.lerLocal().isEmpty() })
        db.withTransaction {
            catalogo.forEach { c -> c.gravarLocal(antes.getValue(c.nome).values.toList()) }
        }

        catalogo.forEach { c -> assertEquals("coleção ${c.nome}", antes.getValue(c.nome), c.lerLocal()) }
    }

    @Test fun `nomes das colecoes seguem o contrato`() {
        assertEquals(
            setOf("contas", "cartoes", "categorias", "despesas", "despesasFixas", "orcamentos", "metas", "investimentos",
                "transferenciasAgendadas", "patrimonio", "transacoes", "lixeira"),
            FirestoreMapper.catalogo(db).map { it.nome }.toSet()
        )
    }

    @Test fun `documento da despesa expoe os campos do contrato com data em milissegundos`() {
        val d = Despesa(id = 5, descricao = "x", valor = 10.0, data = Date(1_760_000_000_000), categoria = "c", conta = "1", pic = "p",
            tipo = TipoDespesa.CREDITO, mes = 10, ano = 2025, natureza = Natureza.TRANSFERENCIA, grupoId = "transf:1")
        val doc = FirestoreMapper.toDoc(d)
        assertEquals(
            setOf("id", "descricao", "valor", "data", "categoria", "conta", "pic", "tipo", "mes", "ano", "cartaoId", "valorOriginal",
                "moedaOriginal", "cotacaoNaData", "pago", "natureza", "grupoId", "autor", "fitid", "conciliadoEm"),
            doc.keys
        )
        assertEquals(1_760_000_000_000, doc["data"])
        assertEquals("CREDITO", doc["tipo"])
    }

    @Test fun `leitura tolera tipos numericos do firestore e campos ausentes`() {
        // O Firestore devolve inteiros como Long e pode omitir campos: valores padrão seguros, sem exceção.
        val d = FirestoreMapper.despesaFromDoc(
            mapOf("id" to 7L, "descricao" to "Portal", "valor" to 50L, "data" to 1_760_000_000_000, "categoria" to "Lazer", "conta" to "111",
                "tipo" to "DEBITO", "cartaoId" to 0L, "pago" to true, "natureza" to "INVALIDA")
        )
        assertEquals(7L, d.id); assertEquals(50.0, d.valor, 0.0); assertEquals(true, d.pago)
        assertNull("cartaoId 0 é tratado como nulo", d.cartaoId)
        assertEquals("natureza inválida cai para NORMAL", Natureza.NORMAL, d.natureza)
        assertEquals("BRL", d.moedaOriginal); assertEquals(1.0, d.cotacaoNaData, 0.0)
        assertNotNull(d.data)
        assertEquals(10, d.mes); assertEquals(2025, d.ano)

        val c = FirestoreMapper.cartaoFromDoc(mapOf("id" to 1L, "nome" to "Visa", "limiteTotal" to 1000L, "diaFechamento" to 99L, "diaVencimento" to 0L, "contaId" to 3L))
        assertEquals(31, c.diaFechamento); assertEquals(1, c.diaVencimento)
    }
}
