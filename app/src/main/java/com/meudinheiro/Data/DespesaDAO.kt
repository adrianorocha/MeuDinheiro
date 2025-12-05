package com.meudinheiro.Data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DespesaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirDespesa(despesa: Despesa)

    @Query("SELECT * FROM despesas")
    fun getDespesas(): Flow<List<Despesa>>
}