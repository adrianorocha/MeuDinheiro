package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Meta
import com.meudinheiro.data.Orcamento
import kotlinx.coroutines.flow.Flow

// 2. DAO
@Dao
interface OrcamentoDao {
    @Query("SELECT * FROM orcamentos")
    fun obterTodosFlow(): Flow<List<Orcamento>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarOrcamento(orcamento: Orcamento)

    @Query("DELETE FROM orcamentos WHERE categoria = :categoria") // Verifique se o nome da sua tabela é esse mesmo
    suspend fun excluirPorCategoria(categoria: String)

    @Query("UPDATE orcamentos SET valorLimite = :novoValor WHERE categoria = :categoria")
    suspend fun atualizarPorCategoria(categoria: String, novoValor: Double)

    @Query("SELECT * FROM orcamentos")
    suspend fun obterTodasStatic(): List<Orcamento>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirLista(orcamentos: List<Orcamento>)

    @Query("DELETE FROM orcamentos")
    suspend fun limparTudo()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(orcamentos: List<Orcamento>)
}
