package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.dao.ContaSaldoDomain
import kotlinx.coroutines.flow.Flow

@Dao
interface ContaSaldoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo)

    @Query("SELECT * FROM contasaldo ORDER BY banco DESC")
    suspend fun obterContaSaldo(): Flow<List<ContaSaldoDomain>>

    @Query("DELETE FROM contasaldo WHERE id = :id")
    suspend fun excluirConta(id: Int): ContaSaldo?
}