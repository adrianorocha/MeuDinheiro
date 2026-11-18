package com.meudinheiro.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.MIGRATION_7_8
import com.meudinheiro.data.TipoDespesa
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
import java.util.Calendar
import java.util.Date

/** R47 — pausar/retomar recorrência contra um SQLite real (em memória) e a migração 7 → 8. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PausaRecorrenciaRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository

    @Before fun abrir() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
    }

    @After fun fechar() = db.close()

    private fun mesesAtras(n: Int) = Calendar.getInstance().apply { add(Calendar.MONTH, -n); set(Calendar.DAY_OF_MONTH, 1) }.time
    private fun daqui(dias: Int) = System.currentTimeMillis() + dias * 86_400_000L
    private fun lancamentos() = runBlocking { db.despesaDao().obterTodasStatic() }.filter { it.descricao == "Aluguel" }
    private fun regraSalva() = runBlocking { db.despesaFixaDao().obterTodas().single() }

    /** Regra do dia 1, com 3 meses sem lançar; salva direto no DAO para não disparar o processamento. */
    private fun criarRegra(pausada: Boolean = false, ate: Date? = null): Int = runBlocking {
        repo.salvarConta(ContaSaldo(banco = "B", pic = "p", agencia = "1", conta = "111", titular = "", saldo = 0.0, id = 0))
        db.despesaFixaDao().inserir(
            DespesaFixa(descricao = "Aluguel", valor = 1000.0, conta = "111", categoria = "Casa", pic = "p",
                tipo = TipoDespesa.DEBITO, diaVencimento = 1, ultimaDataLancamento = mesesAtras(3), pausada = pausada, pausadaAte = ate)
        )
        regraSalva().id
    }

    @Test fun `pausada nao lanca nem mexe na ultima data`() = runBlocking {
        val id = criarRegra()
        repo.pausarRecorrencia(id)
        val antes = regraSalva().ultimaDataLancamento
        repo.processarRecorrencias()
        assertEquals(0, lancamentos().size)
        assertEquals(antes, regraSalva().ultimaDataLancamento)
        assertTrue(regraSalva().pausada)
    }

    @Test fun `pausar nao altera o que ja foi lancado`() = runBlocking {
        val id = criarRegra()
        repo.processarRecorrencias()
        val antes = lancamentos().map { it.id to it.valor }
        assertEquals(3, antes.size)
        repo.pausarRecorrencia(id, daqui(30))
        repo.processarRecorrencias()
        assertEquals(antes, lancamentos().map { it.id to it.valor })
    }

    @Test fun `retomar nao recupera os meses pausados e limpa os campos`() = runBlocking {
        val id = criarRegra()
        repo.pausarRecorrencia(id, daqui(10))
        repo.retomarRecorrencia(id)
        val r = regraSalva()
        assertFalse(r.pausada); assertNull(r.pausadaAte)
        repo.processarRecorrencias()
        assertEquals(0, lancamentos().size)   // os 3 meses do período pausado não voltam
    }

    @Test fun `prazo vencido retoma sozinho sem catch-up e e idempotente`() = runBlocking {
        criarRegra(pausada = true, ate = Date(System.currentTimeMillis() - 1000))
        repo.processarRecorrencias()
        assertEquals(0, lancamentos().size)
        val r = regraSalva()
        assertFalse(r.pausada); assertNull(r.pausadaAte)
        repo.processarRecorrencias()
        assertEquals(0, lancamentos().size)
    }

    @Test fun `prazo no futuro continua pausada`() = runBlocking {
        criarRegra(pausada = true, ate = Date(daqui(5)))
        repo.processarRecorrencias()
        assertEquals(0, lancamentos().size)
        assertTrue(regraSalva().pausada)
    }

    @Test fun `regra nao pausada segue recuperando meses (comportamento antigo)`() = runBlocking {
        criarRegra()
        repo.processarRecorrencias()
        assertEquals(3, lancamentos().size)
    }

    @Test fun `validacoes de pausar e retomar`() = runBlocking {
        val id = criarRegra()
        fun falha(bloco: suspend () -> Unit) {
            try { runBlocking { bloco() }; fail("esperava RegraFinanceiraException") } catch (_: RegraFinanceiraException) { }
        }
        falha { repo.pausarRecorrencia(id, System.currentTimeMillis() - 1000) }
        falha { repo.pausarRecorrencia(9999) }
        falha { repo.retomarRecorrencia(id) }   // não está pausada
        falha { repo.retomarRecorrencia(9999) }
        // editar origem continua permitido com a regra pausada
        repo.pausarRecorrencia(id)
        repo.alterarOrigemRecorrencia(id, "111", null)
        assertTrue(regraSalva().pausada)
    }

    @Test fun `migracao 7 para 8 preserva linhas e define pausada = false`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE despesas_fixas (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, descricao TEXT NOT NULL, " +
                                "valor REAL NOT NULL, conta TEXT NOT NULL, categoria TEXT NOT NULL, pic TEXT NOT NULL, tipo TEXT NOT NULL, " +
                                "diaVencimento INTEGER NOT NULL, ultimaDataLancamento INTEGER, cartaoId INTEGER)"
                        )
                        db.execSQL("INSERT INTO despesas_fixas (descricao, valor, conta, categoria, pic, tipo, diaVencimento) VALUES ('Antiga', 9.5, '1', 'c', 'p', 'DEBITO', 5)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val sqlite = helper.writableDatabase
        MIGRATION_7_8.migrate(sqlite)
        sqlite.query("SELECT descricao, valor, pausada, pausadaAte FROM despesas_fixas").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Antiga", c.getString(0))
            assertEquals(9.5, c.getDouble(1), 0.0)
            assertEquals(0, c.getInt(2))
            assertTrue(c.isNull(3))
        }
        helper.close()
    }
}
