package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.Transacao
import kotlinx.coroutines.flow.Flow

@Dao
interface TransacaoDao {
    @Query("SELECT * FROM transacoes ORDER BY timestamp DESC LIMIT 5")
    fun getUltimasTransacoes(): Flow<List<Transacao>>

    @Insert
    suspend fun inserir(transacao: Transacao)

    @Query("DELETE FROM transacoes")
    suspend fun limparTudo()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(transacao: List<Transacao>)
}