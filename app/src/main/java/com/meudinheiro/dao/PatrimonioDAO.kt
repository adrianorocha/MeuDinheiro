package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.PatrimonioHistorico
import kotlinx.coroutines.flow.Flow

@Dao
interface PatrimonioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarSnapshot(patrimonio: PatrimonioHistorico)

    /** Últimos 12 registros, em ordem cronológica (o LIMIT antigo devolvia os 12 mais ANTIGOS). */
    @Query(
        """
        SELECT * FROM (SELECT * FROM patrimonio_historico ORDER BY dataMillis DESC LIMIT 12)
        ORDER BY dataMillis ASC
        """
    )
    fun obterHistoricoPatrimonial(): Flow<List<PatrimonioHistorico>>

    @Query("SELECT * FROM patrimonio_historico WHERE dataMillis BETWEEN :inicio AND :fim LIMIT 1")
    suspend fun buscarSnapshotNoPeriodo(inicio: Long, fim: Long): PatrimonioHistorico?

    @Query("DELETE FROM patrimonio_historico")
    suspend fun limparTudo()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(patrimoniohistorico: List<PatrimonioHistorico>)

    @Query("SELECT * FROM patrimonio_historico")
    suspend fun obterTodasStatic(): List<PatrimonioHistorico>

    @Query("DELETE FROM patrimonio_historico WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)
}
