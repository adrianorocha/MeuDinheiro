package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.ResumoFinanceiroDto
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

    @Query("""
    SELECT conta, tipo, pago, SUM(valor) as valorTotal
    FROM despesas
    WHERE data BETWEEN :inicio AND :fim
    GROUP BY conta, tipo, pago
""")
    suspend fun obterResumoPorPeriodo(inicio: Date, fim: Date): List<ResumoFinanceiroDto>

    // No DespesasDao.kt
    @Query("""
    SELECT conta, tipo, SUM(valor) as valorTotal 
    FROM despesas 
    GROUP BY conta, tipo
""")
    fun obterResumoGlobalPorConta(): List<ResumoFinanceiroDto>
@Query("UPDATE Despesas SET pago = :status WHERE id = :id")
suspend fun atualizarStatusPago(id: Long, status: Boolean)

    @Query("SELECT * FROM despesas")
    suspend fun obterTodasStatic(): List<Despesa>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirLista(despesas: List<Despesa>)

    // Opcional: Limpar antes de restaurar para evitar lixo de dados antigos
    @Query("DELETE FROM despesas")
    suspend fun limparTudo()

    @Query("SELECT * FROM despesas") // Atenção: verifique se o nome da sua tabela é "despesas" mesmo
    fun obterTodasFlow(): Flow<List<Despesa>>

    // Soma tudo por período
    @Query("SELECT SUM(valor) FROM despesas WHERE data >= :inicio AND data <= :fim AND tipo = :tipo AND pago = 1")
    suspend fun somarPorPeriodo(inicio: Long, fim: Long, tipo: TipoDespesa): Double?

    // Soma tudo (Global)
    @Query("SELECT SUM(valor) FROM despesas WHERE tipo = :tipo AND pago = 1")
    suspend fun somarGlobal(tipo: TipoDespesa): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(despesas: List<Despesa>) // <--- O nome que o Repository procura

    @Query("""
    SELECT SUM(valor) FROM despesas 
    WHERE strftime('%m', data / 1000, 'unixepoch') = :mes 
    AND strftime('%Y', data / 1000, 'unixepoch') = :ano
""")
    fun getTotalPorMesEAno(mes: String, ano: String): Flow<Double?>

    @Query("SELECT * FROM despesas WHERE mes = :mes AND ano = :ano AND (conta = :contaId OR :contaId = '')")
    fun getDespesasPorMes(mes: Int, ano: Int, contaId: String): Flow<List<DespesasDomain>>

    @Query("SELECT * FROM despesas WHERE (conta = :contaId OR :contaId = '')")
    fun getTodasDespesas(contaId: String): Flow<List<DespesasDomain>>
}
