package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.meudinheiro.data.Transacao
import kotlinx.coroutines.flow.Flow

@Dao
interface TransacaoDao {
    @Query("SELECT * FROM transacoes ORDER BY timestamp DESC LIMIT 20")
    fun getUltimasTransacoes(): Flow<List<Transacao>>

    @Insert
    suspend fun inserir(transacao: Transacao)
}