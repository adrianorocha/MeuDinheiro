package com.meudinheiro.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.AcaoTrilha
import com.meudinheiro.domain.TrilhaArquivo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

/** R66 — a trilha acompanha criar/editar/pagar/excluir/restaurar sem mudar o comportamento das operações. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrilhaRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository
    private lateinit var ctx: Context

    @Before fun abrir() {
        ctx = ApplicationProvider.getApplicationContext()
        TrilhaArquivo(File(ctx.filesDir, "trilha/alteracoes.jsonl")).limpar()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
    }

    @After fun fechar() = db.close()

    @Test fun `ciclo de vida do lancamento fica na trilha`() = runBlocking {
        db.contaSaldoDao().inserirTodas(listOf(ContaSaldo(1, 0.0, "B", "p", "1", "111", "")))
        val base = Despesa(descricao = "Pizza", valor = 30.0, data = Date(System.currentTimeMillis() + 5 * 86_400_000L), categoria = "Lazer",
            conta = "111", pic = "p", tipo = TipoDespesa.DEBITO, mes = 0, ano = 0)
        val id = repo.registrarLancamento(base)
        val salvo = repo.obterDespesaPorId(id)!!
        repo.atualizarLancamento(salvo.copy(valor = 45.0))
        repo.alternarPago(id, true)
        repo.excluirLancamento(id)
        repo.restaurarDaLixeira(db.lixeiraDao().obterTodasStatic().single().id)

        val trilha = repo.lerTrilha()
        assertEquals(listOf(AcaoTrilha.CRIAR, AcaoTrilha.EDITAR, AcaoTrilha.PAGAR, AcaoTrilha.EXCLUIR, AcaoTrilha.RESTAURAR_LIXEIRA), trilha.map { it.acao }.reversed())
        val edicao = trilha.first { it.acao == AcaoTrilha.EDITAR }
        assertTrue(edicao.alteracoes.any { it.campo == "valor" && it.antigo == "30.0" && it.novo == "45.0" })
        assertEquals(1, db.despesaDao().obterTodasStatic().size) // comportamento das operações intacto
    }
}
