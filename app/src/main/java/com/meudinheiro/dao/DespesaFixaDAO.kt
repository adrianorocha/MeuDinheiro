package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meudinheiro.data.DespesaFixa

@Dao
interface DespesaFixaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(despesaFixa: DespesaFixa)

    @Update
    suspend fun atualizar(despesaFixa: DespesaFixa)

    @Query("SELECT * FROM despesas_fixas")
    suspend fun obterTodas(): List<DespesaFixa>

    @Query("DELETE FROM despesas_fixas WHERE id = :id")
    suspend fun excluir(id: Int)
}