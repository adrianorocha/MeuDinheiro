package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface DespesaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirDespesa(despesa: Despesa): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(despesas: List<Despesa>)

    // ---------------------------------------------------------------- leitura

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM despesas ORDER BY data DESC")
    fun obterDespesas(): Flow<List<DespesasDomain>>

    @Query("SELECT * FROM despesas")
    fun obterTodasFlow(): Flow<List<Despesa>>

    @Query("SELECT * FROM despesas")
    suspend fun obterTodasStatic(): List<Despesa>

    @Query("SELECT * FROM despesas WHERE id = :id LIMIT 1")
    suspend fun obterDespesaPorId(id: Long): Despesa?

    @Query("SELECT * FROM despesas WHERE conta = :conta")
    suspend fun obterDaConta(conta: String): List<Despesa>

    @Query("SELECT * FROM despesas WHERE cartaoId = :cartaoId")
    suspend fun obterDoCartao(cartaoId: Int): List<Despesa>

    @Query("SELECT * FROM despesas WHERE grupoId = :grupoId")
    suspend fun obterDoGrupo(grupoId: String): List<Despesa>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM despesas WHERE conta = :contaId ORDER BY data DESC")
    fun obterDespesasPorContaFlow(contaId: String): Flow<List<DespesasDomain>>

    @Query("SELECT * FROM despesas WHERE cartaoId = :id ORDER BY data DESC")
    fun getDespesasPorCartao(id: Int): Flow<List<Despesa>>

    // Pendências (contas a pagar). Compras de cartão entram pela fatura, não aqui.
    @Query(
        """
        SELECT * FROM despesas
        WHERE pago = 0 AND tipo = :tipo AND (cartaoId IS NULL OR cartaoId = 0)
          AND natureza = 'NORMAL' AND data BETWEEN :inicio AND :fim
        ORDER BY data ASC
        """
    )
    suspend fun obterPendentesVencendoDatePorTipo(inicio: Date, fim: Date, tipo: TipoDespesa): List<Despesa>

    @Query(
        """
        SELECT * FROM despesas
        WHERE pago = 0 AND tipo = 'DEBITO' AND (cartaoId IS NULL OR cartaoId = 0)
          AND natureza = 'NORMAL' AND data BETWEEN :inicio AND :fim
        ORDER BY data ASC
        """
    )
    suspend fun obterPendentesVencendoDate(inicio: Date, fim: Date): List<Despesa>

    @Query(
        """
        SELECT * FROM despesas
        WHERE pago = 0 AND tipo = :tipo AND (cartaoId IS NULL OR cartaoId = 0)
          AND natureza = 'NORMAL' AND data < :inicio
        ORDER BY data ASC
        """
    )
    suspend fun obterPendentesAtrasadasPorTipo(inicio: Date, tipo: TipoDespesa): List<Despesa>

    // ----------------------------------------------------------------- escrita

    @Query("UPDATE despesas SET pago = :pago WHERE id = :id")
    suspend fun atualizarPago(id: Long, pago: Boolean)

    @Query("UPDATE despesas SET pago = 1 WHERE id IN (:ids)")
    suspend fun marcarComoPagas(ids: List<Long>)

    @Query("DELETE FROM despesas WHERE id = :id")
    suspend fun excluirPorId(id: Long)

    @Query("DELETE FROM despesas WHERE grupoId = :grupoId")
    suspend fun excluirPorGrupo(grupoId: String)

    @Query("DELETE FROM despesas WHERE conta = :conta OR cartaoId IN (:cartoes)")
    suspend fun excluirDaContaECartoes(conta: String, cartoes: List<Int>)

    @Query("DELETE FROM despesas")
    suspend fun limparTudo()

    @Query("SELECT * FROM despesas WHERE cartaoId IN (:cartoes)")
    suspend fun obterDosCartoes(cartoes: List<Int>): List<Despesa>

    @Query("UPDATE despesas SET cartaoId = :para WHERE cartaoId = :de")
    suspend fun reatribuirCartao(de: Int, para: Int)

    /** Move o vínculo de débito (`debito:<id>`) de um cartão excluído para outro. */
    @Query("UPDATE despesas SET grupoId = :para WHERE grupoId = :de")
    suspend fun reatribuirGrupo(de: String, para: String)

    /** R42: compras do cartão acompanham o número de conta do cartão. */
    @Query("UPDATE despesas SET conta = :conta WHERE cartaoId = :cartaoId AND conta != :conta")
    suspend fun atualizarContaDoCartao(cartaoId: Int, conta: String)

    @Query("DELETE FROM despesas WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Long>)
}
