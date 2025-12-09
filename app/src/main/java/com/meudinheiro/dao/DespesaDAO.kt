package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import kotlinx.coroutines.flow.Flow

@Dao
interface DespesaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirDespesa(despesa: Despesa)

    @Query("SELECT * FROM despesas ORDER BY data DESC")
    fun obterDespesas(): Flow<List<DespesasDomain>>

    @Query("DELETE FROM despesas WHERE id = :id")
    suspend fun excluirDespesa(id: Int)
}