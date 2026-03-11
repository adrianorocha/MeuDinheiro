package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meudinheiro.data.Investimento
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestimentoDao {
    // Traz a lista sempre ordenada do maior valor para o menor
    @Query("SELECT * FROM investimentos ORDER BY valorAtual DESC")
    fun getTodosInvestimentos(): Flow<List<Investimento>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(investimento: Investimento)

    @Update
    suspend fun atualizar(investimento: Investimento)

    @Delete
    suspend fun deletar(investimento: Investimento)

    // O SQLite já faz a soma do seu patrimônio direto no motor do banco!
    @Query("SELECT SUM(valorAtual) FROM investimentos")
    fun getPatrimonioTotal(): Flow<Double?>
}