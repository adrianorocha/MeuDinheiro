package com.meudinheiro.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.MIGRATION_13_14
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Migração 13 → 14 (orcamentos.escopo / pessoaId) contra o schema real da v13, validada pelo Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migracao13Para14Test {

    @Test fun `migracao 13 para 14 mantem orcamentos como TODOS`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        MigracaoTestHelper.criarBancoNaVersao(ctx, "mig13_14.db", 13) { db ->
            db.execSQL("INSERT INTO orcamentos (id, categoria, valorLimite) VALUES (1, 'Combustível', 500.0)")
        }
        val db = MigracaoTestHelper.abrirComRoom(ctx, "mig13_14.db", MIGRATION_13_14)
        try {
            val o = db.orcamentoDao().obterTodasStatic().single()
            assertEquals("Combustível", o.categoria)
            assertEquals(500.0, o.valorLimite, 0.0)
            assertNull(o.escopo)
            assertNull(o.pessoaId)
            db.orcamentoDao().salvarOrcamento(o.copy(id = 0, escopo = "PESSOA", pessoaId = 7))
            assertEquals(2, db.orcamentoDao().obterTodasStatic().size)
        } finally {
            db.close()
        }
    }
}
