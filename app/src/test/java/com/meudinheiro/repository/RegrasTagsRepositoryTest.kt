package com.meudinheiro.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Projeto
import com.meudinheiro.data.RegraCategoria
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Analises
import com.meudinheiro.domain.ConfigGeral
import com.meudinheiro.domain.Painel503020
import com.meudinheiro.domain.Regras
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.storage.ConfiguracaoLocal
import com.meudinheiro.storage.FirestoreMapper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/** R55/R56/R57 contra SQLite em memória: tags preservadas, regras, backup antigo/novo e mesclagem de configurações. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegrasTagsRepositoryTest {

    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository

    @Before fun abrir() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
        runBlocking { repo.salvarConta(ContaSaldo(banco = "B", pic = "p", agencia = "1", conta = "111", titular = "", saldo = 0.0, id = 0)) }
    }

    @After fun fechar() = db.close()

    private fun daqui(dias: Int) = System.currentTimeMillis() + dias * 86_400_000L

    private fun lanc(valor: Double, tags: List<String>, desc: String = "Item", pago: Boolean = false, categoria: String = "Outros") = Despesa(
        descricao = desc, valor = valor, data = Date(daqui(10)), categoria = categoria, conta = "111", pic = "p",
        tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = pago, tags = tags
    )

    private fun todas() = runBlocking { db.despesaDao().obterTodasStatic() }

    // ------------------------------------------------------------------ R56: tags preservadas

    @Test fun `parcelar preserva as tags da compra em todas as parcelas`() = runBlocking {
        repo.registrarParcelado(lanc(300.0, listOf("Casa Nova", "eletro"), desc = "TV"), 3)
        val parcelas = todas().filter { it.descricao.startsWith("TV") }
        assertEquals(3, parcelas.size)
        assertTrue(parcelas.all { it.tags == listOf("Casa Nova", "eletro") })
    }

    @Test fun `duplicar e repetir preservam as tags`() = runBlocking {
        val id = repo.registrarLancamento(lanc(50.0, listOf("viagem"), desc = "Hotel"))
        val copia = repo.duplicarLancamento(id)
        assertEquals(listOf("viagem"), db.despesaDao().obterDespesaPorId(copia)!!.tags)
        repo.repetirLancamento(id, 2, 1, Analises.UnidadeRepeticao.MESES)
        assertEquals(4, todas().size)
        assertTrue(todas().all { it.tags == listOf("viagem") })
    }

    @Test fun `antecipar (inclusive pagamento parcial que divide o lancamento) preserva as tags`() = runBlocking {
        val id = repo.registrarLancamento(lanc(200.0, listOf("divida")))
        repo.anteciparPagamento(listOf(id), 80.0)
        val itens = todas()
        assertEquals(2, itens.size)
        assertTrue(itens.all { it.tags == listOf("divida") })
    }

    @Test fun `editar troca e remove tags, e excluir + restaurar da lixeira mantem`() = runBlocking {
        val id = repo.registrarLancamento(lanc(20.0, listOf("a", "b"), pago = true))
        val original = db.despesaDao().obterDespesaPorId(id)!!
        repo.atualizarLancamento(original.copy(tags = listOf("nova")))
        assertEquals(listOf("nova"), db.despesaDao().obterDespesaPorId(id)!!.tags)
        repo.excluirLancamento(id)
        val lix = db.lixeiraDao().obterTodasStatic().single()
        repo.restaurarDaLixeira(lix.id)
        assertEquals(listOf("nova"), todas().single().tags)
        repo.atualizarLancamento(todas().single().copy(tags = emptyList()))
        assertTrue(todas().single().tags.isEmpty())
    }

    @Test fun `mapper do Firestore usa array nativo e tolera documento sem o campo`() {
        val d = lanc(10.0, listOf("viagem", "Hotel"))
        val doc = FirestoreMapper.toDoc(d)
        assertEquals(listOf("viagem", "Hotel"), doc["tags"])
        assertEquals(listOf("viagem", "Hotel"), FirestoreMapper.despesaFromDoc(doc).tags)
        assertTrue(FirestoreMapper.despesaFromDoc(doc - "tags").tags.isEmpty())
        assertEquals(listOf("a"), FirestoreMapper.despesaFromDoc(doc + ("tags" to listOf("#a", "A", "", null))).tags)
    }

    // ------------------------------------------------------------------ R55: regras

    @Test fun `CRUD de regras com validacao, alternar e prioridade`() = runBlocking {
        val id = repo.salvarRegra(RegraCategoria(padrao = "  Uber ", categoria = " Transporte ", conta = " ", tipo = "XYZ")).toInt()
        val r = repo.obterRegras().single()
        assertEquals("Uber", r.padrao); assertEquals("Transporte", r.categoria); assertNull(r.conta); assertNull(r.tipo)
        assertTrue(r.criadaEm > 0)
        repo.alternarRegra(id)
        assertFalse(repo.obterRegras().single().ativa)
        repo.ajustarPrioridadeRegra(id, 3)
        assertEquals(3, repo.obterRegras().single().prioridade)
        try { repo.salvarRegra(RegraCategoria(padrao = " ", categoria = "A")); fail() } catch (_: RegraFinanceiraException) { }
        repo.excluirRegra(id)
        assertTrue(repo.obterRegras().isEmpty())
    }

    @Test fun `aplicar as existentes so mexe em NORMAL, mostra previa antes e desfaz`() = runBlocking {
        repo.salvarRegra(RegraCategoria(padrao = "uber", categoria = "Transporte"))
        val a = repo.registrarLancamento(lanc(10.0, emptyList(), desc = "Uber trip", categoria = "Outros"))
        repo.registrarLancamento(lanc(10.0, emptyList(), desc = "Padaria", categoria = "Outros"))
        val previa = repo.previaRegrasExistentes()
        assertEquals(listOf(a), previa.map { it.id })
        assertEquals("Outros", db.despesaDao().obterDespesaPorId(a)!!.categoria) // prévia não altera
        val anteriores = repo.aplicarRegrasExistentes(previa)
        assertEquals("Transporte", db.despesaDao().obterDespesaPorId(a)!!.categoria)
        repo.desfazerRegrasExistentes(anteriores)
        assertEquals("Outros", db.despesaDao().obterDespesaPorId(a)!!.categoria)
        assertEquals("Transporte", repo.categoriaPorRegras("UBER *TRIP", "111", TipoDespesa.DEBITO))
        assertNull(repo.categoriaPorRegras("Outra coisa", "111", TipoDespesa.DEBITO))
    }

    // ------------------------------------------------------------------ projetos

    @Test fun `projeto - tag unica, normalizada, e validacoes`() = runBlocking {
        repo.salvarProjeto(Projeto(nome = " Reforma ", tag = "#Reforma Casa", orcamento = 1000.456))
        val p = db.projetoDao().obterTodasStatic().single()
        assertEquals("Reforma", p.nome); assertEquals("reforma casa", p.tag); assertEquals(1000.46, p.orcamento!!, 0.0)
        try { repo.salvarProjeto(Projeto(nome = "Outro", tag = "REFORMA CASA")); fail() } catch (_: RegraFinanceiraException) { }
        repo.salvarProjeto(p.copy(nome = "Reforma 2")) // editar o próprio não conflita
        assertEquals("Reforma 2", db.projetoDao().obterTodasStatic().single().nome)
    }

    // ------------------------------------------------------------------ backup

    @Test fun `backup antigo sem tags, regras, projetos e configuracoes restaura sem falha`() = runBlocking {
        val antigo = """
            {"versaoBackup":2,
             "contas":[{"id":3,"saldo":0.0,"banco":"X","pic":"p","agencia":"1","conta":"10","titular":""}],
             "despesas":[{"id":7,"descricao":"Antiga","valor":10.0,"data":1760000000000,"categoria":"A","conta":"10","pic":"p","tipo":"DEBITO","mes":10,"ano":2025,"pago":true,"natureza":"NORMAL","campoFuturo":123}],
             "colecaoFutura":[{"a":1}]}
        """.trimIndent()
        repo.restaurarBackupCompleto(antigo)
        val d = todas().single()
        assertEquals("Antiga", d.descricao)
        assertTrue(d.tags.isEmpty())
        assertTrue(repo.obterRegras().isEmpty())
        assertTrue(db.projetoDao().obterTodasStatic().isEmpty())
    }

    @Test fun `backup novo faz ida e volta com tags, regras, projetos e configuracoes`() = runBlocking {
        repo.registrarLancamento(lanc(10.0, listOf("Viagem", "hotel"), desc = "Hotel", pago = true))
        repo.salvarRegra(RegraCategoria(padrao = "uber", tipoMatch = "COMECA_COM", categoria = "Transporte", tipo = "DEBITO", prioridade = 2))
        repo.salvarProjeto(Projeto(nome = "Viagem", tag = "viagem", orcamento = 500.0, inicio = 10L, fim = 99L, cor = "#ff0000"))
        val prefs = UserPreferences(ctx)
        val local = ConfiguracaoLocal(db, prefs)
        prefs.saveLimiteDiarioAteReceita(true, carimbar = false)
        local.registrarAlteracao()

        val json = repo.gerarBackup()
        assertTrue(json.contains("\"tags\":[\"Viagem\",\"hotel\"]"))
        assertTrue(json.contains("\"regrasCategoria\""))
        assertTrue(json.contains("\"configuracoes\""))

        repo.limparBancoDeDadosCompleto()
        repo.restaurarBackupCompleto(json)
        assertEquals(listOf("Viagem", "hotel"), todas().single { it.descricao == "Hotel" }.tags)
        val r = repo.obterRegras().single()
        assertEquals("COMECA_COM", r.tipoMatch); assertEquals("DEBITO", r.tipo); assertEquals(2, r.prioridade)
        val p = db.projetoDao().obterTodasStatic().single()
        assertEquals(500.0, p.orcamento!!, 0.0); assertEquals(10L, p.inicio); assertEquals("#ff0000", p.cor)
    }

    @Test fun `restore de backup nao sobrescreve configuracao local mais nova (empate ou mais antiga)`() = runBlocking {
        val prefs = UserPreferences(ctx)
        val local = ConfiguracaoLocal(db, prefs)
        prefs.saveLimiteDiarioAteReceita(true, carimbar = false)
        prefs.salvarPainel503020(Painel503020.serializar(Painel503020.CONFIG_PADRAO.copy(mesesReserva = 4)), carimbar = false)
        local.registrarAlteracao()
        val carimboLocal = local.atual().carimbo
        assertTrue(carimboLocal > 0)

        val antigo = ConfigGeral(Painel503020.CONFIG_PADRAO.copy(mesesReserva = 20), false, carimboLocal - 1)
        assertEquals(ConfigGeral.Acao.ENVIAR_LOCAL, local.aplicar(antigo))
        assertEquals(4, local.atual().painel!!.mesesReserva)
        assertEquals(ConfigGeral.Acao.NADA, local.aplicar(ConfigGeral(Painel503020.CONFIG_PADRAO.copy(mesesReserva = 20), false, carimboLocal)))
        assertEquals(4, local.atual().painel!!.mesesReserva)
        assertEquals(true, local.atual().limiteDiarioAteReceita)
    }

    // ------------------------------------------------------------------ R57: configurações

    @Test fun `configuracao da nuvem mais nova e aplicada no DataStore e no Room - sem alteracao o carimbo e zero`() = runBlocking {
        val prefs = UserPreferences(ctx)
        val local = ConfiguracaoLocal(db, prefs)
        assertNull(local.atual().atualizadoEm)
        assertNull(local.documentoParaEnvio())

        val nuvem = ConfigGeral(Painel503020.CONFIG_PADRAO.copy(mesesReserva = 11), true, 5_000L)
        assertEquals(ConfigGeral.Acao.APLICAR_NUVEM, local.aplicar(nuvem))
        val depois = local.atual()
        assertEquals(11, depois.painel!!.mesesReserva)
        assertEquals(true, depois.limiteDiarioAteReceita)
        assertEquals(5_000L, depois.carimbo)
        assertEquals(5_000L, db.configuracaoDao().obterGeral()!!.atualizadoEm)

        // mesma configuração de novo (empate): nada a fazer
        assertEquals(ConfigGeral.Acao.NADA, local.aplicar(nuvem))
        // alterar localmente carimba acima do anterior e passa a valer sobre a nuvem
        local.registrarAlteracao()
        assertTrue(local.atual().carimbo > 5_000L)
        assertEquals(ConfigGeral.Acao.ENVIAR_LOCAL, local.aplicar(nuvem))
        assertTrue(Regras.TIPOS_MATCH.isNotEmpty())
    }
}
