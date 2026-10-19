package com.meudinheiro.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.MIGRATION_12_13
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Migração 12 → 13 (cartoes.fechamentoMelhorDia) contra o schema real da v12, validada pelo Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migracao12Para13Test {

    @Test fun `migracao 12 para 13 cria fechamentoMelhorDia falso sem perder dados`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        MigracaoTestHelper.criarBancoNaVersao(ctx, "mig12_13.db", 12) { db ->
            db.execSQL("INSERT INTO contasaldo (id, saldo, banco, pic, agencia, conta, titular) VALUES (1, 0.0, 'Banco', 'p', '1', '111', 'Ana')")
            db.execSQL(
                "INSERT INTO cartoes (id, nome, finalCartao, tipo, limiteDisponivel, limiteTotal, diaFechamento, diaVencimento, contaId, " +
                    "cartaoPrincipalId, limiteProprio) VALUES (1, 'Visa', '1234', 'CRÉDITO', 900.0, 1000.0, 25, 5, 1, NULL, NULL)"
            )
        }
        val db = MigracaoTestHelper.abrirComRoom(ctx, "mig12_13.db", MIGRATION_12_13)
        try {
            val c = db.cartaoDao().obterTodasStatic().single()
            assertEquals("Visa", c.nome)
            assertEquals(25, c.diaFechamento)
            assertFalse("cartão antigo = comportamento anterior", c.fechamentoMelhorDia)

            db.cartaoDao().inserirCartao(c.copy(fechamentoMelhorDia = true))
            assertEquals(true, db.cartaoDao().obterTodasStatic().single().fechamentoMelhorDia)
        } finally {
            db.close()
        }
    }
}
