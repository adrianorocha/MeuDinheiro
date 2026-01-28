package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface DespesaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirDespesa(despesa: Despesa)

    @Query("SELECT * FROM despesas ORDER BY data DESC")
    fun obterDespesas(): Flow<List<DespesasDomain>>

    @Query("SELECT * FROM despesas WHERE conta = :contaId")
    suspend fun obterDespesasPorConta(contaId: String): List<DespesasDomain>

    @Query("DELETE FROM despesas WHERE id = :id")
    suspend fun excluirDespesa(id: Int)

    @Query("SELECT * FROM despesas WHERE id = :id LIMIT 1")
    suspend fun obterDespesaPorId(id: Int): Despesa?

    @Query("SELECT * FROM despesas WHERE conta = :contaId ORDER BY data DESC")
    fun obterDespesasPorContaFlow(contaId: String): Flow<List<DespesasDomain>>

    @Query("SELECT * FROM despesas WHERE data BETWEEN :inicioMillis AND :fimMillis ORDER BY data ASC")
    suspend fun obterDespesasVencendo(inicioMillis: Date, fimMillis: Date): List<Despesa>

    @Query("""
    SELECT * FROM despesas 
    WHERE conta = :contaId AND data BETWEEN :inicioMillis AND :fimMillis
    ORDER BY data ASC
""")
    suspend fun obterDespesasVencendoPorConta(contaId: String, inicioMillis: Long, fimMillis: Long): List<Despesa>

    @Query("""
    SELECT * FROM despesas
    WHERE conta = :contaId
      AND pago = 0
      AND data BETWEEN :inicioMillis AND :fimMillis
    ORDER BY data ASC
""")
    suspend fun obterPendentesVencendoPorConta(contaId: String, inicioMillis: Long, fimMillis: Long): List<Despesa>

    @Query("UPDATE despesas SET pago = :pago WHERE id = :id")
    suspend fun setPago(id: Int, pago: Boolean)

    @Query("""
    SELECT * FROM despesas
    WHERE pago = 0
      AND data BETWEEN :inicioMillis AND :fimMillis
    ORDER BY data ASC
""")
    suspend fun obterPendentesVencendo(inicioMillis: Long, fimMillis: Long): List<Despesa>

    @Query("""
SELECT * FROM despesas
WHERE pago = 0
AND data BETWEEN :inicio AND :fim
ORDER BY data ASC
""")
    suspend fun obterPendentesVencendoDate(inicio: Date, fim: Date): List<Despesa>

    @Query("""
SELECT * FROM despesas
WHERE pago = 0
AND tipo = :tipo
AND data BETWEEN :inicio AND :fim
ORDER BY data ASC
""")
    suspend fun obterPendentesVencendoDatePorTipo(inicio: Date, fim: Date, tipo: TipoDespesa): List<Despesa>

    @Query("""
SELECT * FROM despesas
WHERE pago = 0
AND data < :inicio
ORDER BY data ASC
""")
    suspend fun obterPendentesAtrasadas(inicio: Date): List<Despesa>

    @Query("""
SELECT * FROM despesas
WHERE pago = 0
AND tipo = :tipo
AND data < :inicio
ORDER BY data ASC
""")
    suspend fun obterPendentesAtrasadasPorTipo(inicio: Date, tipo: TipoDespesa): List<Despesa>
}
