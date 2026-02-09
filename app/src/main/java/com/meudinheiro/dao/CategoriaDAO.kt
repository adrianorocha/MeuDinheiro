package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Categoria
import com.meudinheiro.data.CategoriaDomain
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(categoria: Categoria)

    // CORREÇÃO AQUI: "nome AS title" faz a mágica acontecer
    @Query("SELECT pic, nome AS title FROM categorias ORDER BY nome ASC")
    fun obterTodas(): Flow<List<CategoriaDomain>>

    @Delete
    suspend fun excluir(categoria: Categoria)

    @Query("DELETE FROM categorias WHERE nome = :nome")
    suspend fun excluirPorNome(nome: String)
}