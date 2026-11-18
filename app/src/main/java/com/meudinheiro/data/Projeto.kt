package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * R56 — projeto = agrupador de lançamentos por tag (contrato: coleção `projetos`).
 * [tag] é a CHAVE normalizada (minúsculas, sem acento, sem `#`). [inicio]/[fim] em epoch ms (opcionais).
 */
@Entity(tableName = "projetos")
data class Projeto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val tag: String,
    val orcamento: Double? = null,
    val inicio: Long? = null,
    val fim: Long? = null,
    val ativo: Boolean = true,
    val cor: String? = null
)

@Dao
interface ProjetoDao {
    @Query("SELECT * FROM projetos ORDER BY ativo DESC, nome COLLATE NOCASE ASC")
    fun observar(): Flow<List<Projeto>>

    @Query("SELECT * FROM projetos")
    suspend fun obterTodasStatic(): List<Projeto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(projeto: Projeto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(projetos: List<Projeto>)

    @Query("DELETE FROM projetos WHERE id = :id")
    suspend fun excluir(id: Int)

    @Query("DELETE FROM projetos WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)

    @Query("DELETE FROM projetos")
    suspend fun limpar()
}
