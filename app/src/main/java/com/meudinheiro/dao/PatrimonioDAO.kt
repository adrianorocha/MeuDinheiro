package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.PatrimonioHistorico
import kotlinx.coroutines.flow.Flow

@Dao
interface PatrimonioDao {

    // Salva o snapshot do mês
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarSnapshot(patrimonio: PatrimonioHistorico)

    // Pega os últimos 12 meses para o gráfico
    @Query("SELECT * FROM patrimonio_historico ORDER BY dataMillis ASC LIMIT 12")
    fun obterHistoricoPatrimonial(): Flow<List<PatrimonioHistorico>>

    // Limpa o histórico se necessário
    @Query("DELETE FROM patrimonio_historico")
    suspend fun limparHistorico()

    @Query("SELECT * FROM patrimonio_historico WHERE mesReferencia = :mes LIMIT 1")
    suspend fun buscarSnapshotPorMes(mes: String): PatrimonioHistorico?
}