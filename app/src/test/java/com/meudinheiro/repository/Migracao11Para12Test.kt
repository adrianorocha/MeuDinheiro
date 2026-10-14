package com.meudinheiro.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.MIGRATION_11_12
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R67 — migração 11 → 12 (despesas.autorUid) contra o schema real da v11, validada pelo Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migracao11Para12Test {

    @Test fun `migracao 11 para 12 cria autorUid nulo sem perder dados`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        MigracaoTestHelper.criarBancoNaVersao(ctx, "mig11_12.db", 11) { db ->
            db.execSQL(
                "INSERT INTO despesas (id, descricao, valor, data, categoria, conta, pic, tipo, mes, ano, cartaoId, valorOriginal, " +
                    "moedaOriginal, cotacaoNaData, pago, natureza, grupoId, autor, fitid, conciliadoEm, tags, anexos) VALUES " +
                    "(1760000000123, 'Mercado', 12.5, 1760000000000, 'Alimentação', '111', 'p', 'DEBITO', 10, 2025, NULL, 12.5, " +
                    "'BRL', 1.0, 1, 'NORMAL', NULL, 'Ana', NULL, NULL, '[\"casa\"]', '[]')"
            )
        }
        val db = MigracaoTestHelper.abrirComRoom(ctx, "mig11_12.db", MIGRATION_11_12)
        try {
            val d = db.despesaDao().obterTodasStatic().single()
            assertEquals("Mercado", d.descricao)
            assertEquals("Ana", d.autor)
            assertEquals(listOf("casa"), d.tags)
            assertNull("lançamento antigo = sem autorUid", d.autorUid)

            db.despesaDao().inserirDespesa(d.copy(autorUid = "uid-ana"))
            assertEquals("uid-ana", db.despesaDao().obterTodasStatic().single().autorUid)
        } finally {
            db.close()
        }
    }
}
