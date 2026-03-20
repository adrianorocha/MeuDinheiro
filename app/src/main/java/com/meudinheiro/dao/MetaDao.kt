package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Meta
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaDao {
    @Query("SELECT * FROM metas")
    fun getTodasMetas(): Flow<List<Meta>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarMeta(meta: Meta)

    @Delete
    suspend fun excluirMeta(meta: Meta)

    @Query("UPDATE metas SET valorGuardado = valorGuardado + :valor WHERE id = :id")
    suspend fun adicionarAporte(id: Int, valor: Double)

    @Query("SELECT SUM(valorGuardado) FROM metas")
    fun getTotalPoupado(): Flow<Double?>

    @Query("SELECT * FROM metas")
    suspend fun obterTodasStatic(): List<Meta>

    @Query("SELECT * FROM metas ORDER BY valorObjetivo DESC")
    suspend fun obterTodasAsMetasSync(): List<Meta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirLista(contas: List<Meta>)

    @Query("DELETE FROM metas")
    suspend fun limparTudo()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(contas: List<Meta>)

    @Query("UPDATE metas SET valorGuardado = valorGuardado + :valor WHERE id = :id")
    suspend fun adicionarValorMeta(id: Long, valor: Double)
}