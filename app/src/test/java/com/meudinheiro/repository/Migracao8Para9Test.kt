package com.meudinheiro.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.Configuracao
import com.meudinheiro.data.MIGRATION_8_9
import com.meudinheiro.data.MIGRATION_10_11
import com.meudinheiro.data.MIGRATION_11_12
import com.meudinheiro.data.MIGRATION_12_13
import com.meudinheiro.data.MIGRATION_13_14
import com.meudinheiro.data.MIGRATION_9_10
import com.meudinheiro.data.Projeto
import com.meudinheiro.data.RegraCategoria
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R55/R56/R57 — migração 8 → 9 contra o schema real da v8, validada pelo Room (ver [MigracaoTestHelper]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migracao8Para9Test {

    @Test fun `migracao 8 para 9 cria as tabelas e a coluna sem perder dados`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        MigracaoTestHelper.criarBancoNaVersao(ctx, "mig8_9.db", 8) { db ->
            db.execSQL(
                "INSERT INTO despesas (id, descricao, valor, data, categoria, conta, pic, tipo, mes, ano, cartaoId, valorOriginal, " +
                    "moedaOriginal, cotacaoNaData, pago, natureza, grupoId, autor, fitid, conciliadoEm) VALUES " +
                    "(1760000000123, 'Mercado', 12.5, 1760000000000, 'Alimentação', '111', 'p', 'DEBITO', 10, 2025, NULL, 12.5, " +
                    "'BRL', 1.0, 1, 'NORMAL', NULL, 'Ana', NULL, NULL)"
            )
            db.execSQL("INSERT INTO categorias (id, nome, pic) VALUES (7, 'Casa', 'home')")
            db.execSQL(
                "INSERT INTO despesas_fixas (id, descricao, valor, conta, categoria, pic, tipo, diaVencimento, ultimaDataLancamento, cartaoId, pausada, pausadaAte) " +
                    "VALUES (3, 'Aluguel', 900.0, '111', 'Casa', 'p', 'DEBITO', 5, NULL, NULL, 0, NULL)"
            )
        }

        val db = MigracaoTestHelper.abrirComRoom(ctx, "mig8_9.db", MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
        try {
            // dados antigos intactos; coluna nova com o padrão (lista vazia)
            val d = db.despesaDao().obterTodasStatic().single()
            assertEquals(1760000000123L, d.id)
            assertEquals("Mercado", d.descricao)
            assertEquals(12.5, d.valor, 0.0)
            assertEquals("Ana", d.autor)
            assertTrue(d.tags.isEmpty())
            assertEquals("Casa", db.categoriaDao().obterTodasStatic().single().nome)
            assertEquals("Aluguel", db.despesaFixaDao().obterTodasStatic().single().descricao)

            // coluna e tabelas novas funcionam
            db.despesaDao().inserirDespesa(d.copy(tags = listOf("viagem", "Hotel")))
            assertEquals(listOf("viagem", "Hotel"), db.despesaDao().obterDespesaPorId(1760000000123L)!!.tags)

            val idRegra = db.regraCategoriaDao().salvar(RegraCategoria(padrao = "uber", categoria = "Transporte", prioridade = 2, criadaEm = 5L))
            assertEquals("Transporte", db.regraCategoriaDao().obterTodasStatic().single { it.id.toLong() == idRegra }.categoria)
            db.projetoDao().salvar(Projeto(nome = "Reforma", tag = "reforma", orcamento = 1000.0))
            assertEquals("reforma", db.projetoDao().obterTodasStatic().single().tag)
            db.configuracaoDao().salvar(Configuracao(json = "{}", atualizadoEm = 77L))
            assertEquals(77L, db.configuracaoDao().obterGeral()!!.atualizadoEm)
        } finally {
            db.close()
        }
    }
}
