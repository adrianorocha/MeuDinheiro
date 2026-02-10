package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Orcamento
import kotlinx.coroutines.flow.Flow

// 2. DAO
@Dao
interface OrcamentoDao {
    @Query("SELECT * FROM orcamentos")
    fun obterTodosFlow(): Flow<List<Orcamento>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarOrcamento(orcamento: Orcamento)

    @Delete
    suspend fun excluirOrcamento(orcamento: Orcamento)
}
