package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Lançamento excluído, guardado por 30 dias (R31). [payload] = JSON do documento da despesa (contrato §2). */
@Entity(tableName = "lixeira")
data class Lixeira(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tipo: String = "DESPESA",
    val descricao: String,
    val valor: Double,
    val excluidoEm: Long,
    val payload: String
)

@Dao
interface LixeiraDao {
    @Query("SELECT * FROM lixeira ORDER BY excluidoEm DESC")
    fun observar(): Flow<List<Lixeira>>

    @Query("SELECT * FROM lixeira")
    suspend fun obterTodasStatic(): List<Lixeira>

    @Query("SELECT * FROM lixeira WHERE id = :id LIMIT 1")
    suspend fun obter(id: Int): Lixeira?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(item: Lixeira): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(itens: List<Lixeira>)

    @Query("DELETE FROM lixeira WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)

    @Query("DELETE FROM lixeira WHERE excluidoEm < :limite")
    suspend fun purgarAnteriores(limite: Long)

    @Query("DELETE FROM lixeira")
    suspend fun limpar()
}
