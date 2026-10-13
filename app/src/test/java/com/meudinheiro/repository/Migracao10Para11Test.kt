package com.meudinheiro.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.Anexo
import com.meudinheiro.data.MIGRATION_10_11
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R60 — migração 10 → 11 (despesas.anexos) contra o schema real da v10, validada pelo Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migracao10Para11Test {

    @Test fun `migracao 10 para 11 cria a coluna anexos vazia sem perder dados`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        MigracaoTestHelper.criarBancoNaVersao(ctx, "mig10_11.db", 10) { db ->
            db.execSQL(
                "INSERT INTO despesas (id, descricao, valor, data, categoria, conta, pic, tipo, mes, ano, cartaoId, valorOriginal, " +
                    "moedaOriginal, cotacaoNaData, pago, natureza, grupoId, autor, fitid, conciliadoEm, tags) VALUES " +
                    "(1760000000123, 'Mercado', 12.5, 1760000000000, 'Alimentação', '111', 'p', 'DEBITO', 10, 2025, NULL, 12.5, " +
                    "'BRL', 1.0, 1, 'NORMAL', NULL, 'Ana', NULL, NULL, '[\"casa\"]')"
            )
        }
        val db = MigracaoTestHelper.abrirComRoom(ctx, "mig10_11.db", MIGRATION_10_11)
        try {
            val d = db.despesaDao().obterTodasStatic().single()
            assertEquals("Mercado", d.descricao)
            assertEquals(listOf("casa"), d.tags)
            assertTrue("lançamento antigo = sem anexos", d.anexos.isEmpty())

            val a = Anexo("u-1", "cupom.jpg", "image/jpeg", 2048, "usuarios/uid/anexos/1760000000123/u-1.jpg", 5)
            db.despesaDao().inserirDespesa(d.copy(anexos = listOf(a)))
            assertEquals(listOf(a), db.despesaDao().obterTodasStatic().single().anexos)
        } finally {
            db.close()
        }
    }
}
