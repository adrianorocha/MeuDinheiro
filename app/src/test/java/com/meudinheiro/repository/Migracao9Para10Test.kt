package com.meudinheiro.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Divida
import com.meudinheiro.data.MIGRATION_10_11
import com.meudinheiro.data.MIGRATION_11_12
import com.meudinheiro.data.MIGRATION_12_13
import com.meudinheiro.data.MIGRATION_13_14
import com.meudinheiro.data.MIGRATION_9_10
import com.meudinheiro.data.Pessoa
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R58/R59 — migração 9 → 10 contra o schema real da v9, validada pelo Room (ver [MigracaoTestHelper]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migracao9Para10Test {

    @Test fun `migracao 9 para 10 cria as tabelas novas sem perder dados`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        MigracaoTestHelper.criarBancoNaVersao(ctx, "mig9_10.db", 9) { db ->
            db.execSQL(
                "INSERT INTO despesas (id, descricao, valor, data, categoria, conta, pic, tipo, mes, ano, cartaoId, valorOriginal, " +
                    "moedaOriginal, cotacaoNaData, pago, natureza, grupoId, autor, fitid, conciliadoEm, tags) VALUES " +
                    "(1760000000123, 'Mercado', 12.5, 1760000000000, 'Alimentação', '111', 'p', 'DEBITO', 10, 2025, NULL, 12.5, " +
                    "'BRL', 1.0, 1, 'NORMAL', NULL, 'Ana', NULL, NULL, '[\"casa\"]')"
            )
            db.execSQL("INSERT INTO categorias (id, nome, pic) VALUES (7, 'Casa', 'home')")
            db.execSQL(
                "INSERT INTO projetos (id, nome, tag, orcamento, inicio, fim, ativo, cor) VALUES (2, 'Reforma', 'reforma', 1000.0, NULL, NULL, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO regras_categoria (id, padrao, tipoMatch, categoria, conta, tipo, ativa, prioridade, criadaEm) " +
                    "VALUES (4, 'uber', 'CONTEM', 'Transporte', NULL, NULL, 1, 0, 5)"
            )
        }

        val db = MigracaoTestHelper.abrirComRoom(ctx, "mig9_10.db", MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
        try {
            // dados antigos intactos
            val d = db.despesaDao().obterTodasStatic().single()
            assertEquals(1760000000123L, d.id)
            assertEquals("Mercado", d.descricao)
            assertEquals(listOf("casa"), d.tags)
            assertEquals("Casa", db.categoriaDao().obterTodasStatic().single().nome)
            assertEquals("reforma", db.projetoDao().obterTodasStatic().single().tag)
            assertEquals("Transporte", db.regraCategoriaDao().obterTodasStatic().single().categoria)

            // tabelas novas começam vazias ("sem dívidas/pessoas/acertos") e funcionam
            assertTrue(db.dividaDao().obterTodasStatic().isEmpty())
            assertTrue(db.pessoaDao().obterTodasStatic().isEmpty())
            assertTrue(db.acertoDao().obterTodasStatic().isEmpty())

            val dividaId = db.dividaDao().salvar(
                Divida(nome = "Carro", tipo = "FINANCIAMENTO", credor = "Banco", saldoDevedor = 5000.0, valorOriginal = 6000.0, taxaJurosMensal = 1.5,
                    parcelaMinima = 600.0, parcelasRestantes = 10, diaVencimento = 5, conta = "111")
            ).toInt()
            assertEquals(5000.0, db.dividaDao().obterPorId(dividaId)!!.saldoDevedor, 0.0)
            val pessoaId = db.pessoaDao().salvar(Pessoa(nome = "Ana", contato = "11 9")).toInt()
            db.acertoDao().salvar(
                Acerto(pessoaId = pessoaId, tipo = "A_RECEBER", valor = 33.33, descricao = "Pizza", data = 1760000000000, despesaId = 1760000000123L, grupoRateio = "rateio:1760000000123")
            )
            val a = db.acertoDao().obterTodasStatic().single()
            assertEquals(1760000000123L, a.despesaId)
            assertEquals(false, a.quitado)
            assertEquals("Ana", db.pessoaDao().obterTodasStatic().single().nome)
        } finally {
            db.close()
        }
    }
}
