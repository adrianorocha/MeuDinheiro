package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import kotlinx.coroutines.flow.Flow

@Dao
interface ContaSaldoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo)

    @Query("SELECT * FROM contasaldo ORDER BY banco DESC")
    fun obterContaSaldo(): Flow<List<ContaSaldoDomain>>

    @Query("SELECT saldo FROM contasaldo WHERE conta = :conta LIMIT 1")
    suspend fun obterSaldoPorConta(conta: String): Double?

    @Query("DELETE FROM contasaldo WHERE id = :id")
    suspend fun excluirConta(id: Int)

    @Query("UPDATE contasaldo SET saldo = :novoSaldo WHERE conta = :conta")
    suspend fun atualizarSaldo(conta: String, novoSaldo: Double)

    @Query("SELECT * FROM contasaldo")
    suspend fun obterTodasStatic(): List<ContaSaldo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirLista(contas: List<ContaSaldo>)

    @Query("UPDATE contasaldo SET saldo = saldo - :valor WHERE conta = :contaId")
    suspend fun subtrairSaldo(contaId: String, valor: Double)

    @Query("SELECT * FROM contasaldo")
    fun getTodasContas(): Flow<List<ContaSaldo>>
}
