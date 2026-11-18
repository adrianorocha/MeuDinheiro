package com.meudinheiro.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecalculoSaldosRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: MainRepository

    @Before fun abrir() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MainRepository(ctx, db)
    }

    @After fun fechar() = db.close()

    private fun lanc(valor: Double, pago: Boolean, cartaoId: Int? = null) = Despesa(
        descricao = "x", valor = valor, data = Date(), categoria = "Outros", conta = "111", pic = "p",
        tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, cartaoId = cartaoId, pago = pago
    )

    @Test fun `recalcula cache adulterado, devolve antes e depois e e idempotente`() = runBlocking {
        repo.salvarConta(ContaSaldo(banco = "B", pic = "p", agencia = "1", conta = "111", titular = "", saldo = 1000.0))
        val contaId = db.contaSaldoDao().obterPorNumero("111")!!.id
        repo.salvarCartao(
            Cartao(nome = "V", finalCartao = "1", tipo = "CRÉDITO", limiteDisponivel = 0.0, limiteTotal = 500.0,
                diaFechamento = 25, diaVencimento = 5, contaId = contaId)
        )
        val cartaoId = db.cartaoDao().obterTodasStatic().single().id
        repo.registrarLancamento(lanc(100.0, pago = true))
        repo.registrarLancamento(lanc(80.0, pago = false, cartaoId = cartaoId))
        assertTrue(repo.conferirSaldos().tudoConfere)

        // adultera os caches
        db.contaSaldoDao().atualizarSaldo("111", 123.45)
        db.cartaoDao().atualizarLimite(cartaoId, 1.0)
        val rel = repo.conferirSaldos()
        assertFalse(rel.tudoConfere)
        assertEquals(1, rel.saldosDivergentes)
        assertEquals(1, rel.limitesDivergentes)
        // conferir não altera nada
        assertEquals(123.45, db.contaSaldoDao().obterPorNumero("111")!!.saldo, 0.0)

        val r1 = repo.recalcularSaldos()
        assertEquals(1, r1.saldosCorrigidos)
        assertEquals(1, r1.limitesCorrigidos)
        assertEquals(123.45, r1.contas.single().antes, 0.0)
        assertEquals(900.0, r1.contas.single().depois, 0.0)
        assertEquals(1.0, r1.cartoes.single().antes, 0.0)
        assertEquals(420.0, r1.cartoes.single().depois, 0.0)
        assertEquals(900.0, db.contaSaldoDao().obterPorNumero("111")!!.saldo, 0.0)
        assertEquals(420.0, db.cartaoDao().getCartaoPorId(cartaoId)!!.limiteDisponivel, 0.0)
        assertEquals(3, db.despesaDao().obterTodasStatic().size) // saldo inicial + 2; nada apagado

        val r2 = repo.recalcularSaldos()
        assertEquals(0, r2.totalCorrecoes)
        assertTrue(r2.nenhumaDiferenca)
        assertTrue(repo.conferirSaldos().tudoConfere)
    }
}
