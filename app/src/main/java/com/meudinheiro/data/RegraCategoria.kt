package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * R55 — regra de categorização automática (contrato: coleção `regrasCategoria`).
 * [tipoMatch]: CONTEM | COMECA_COM | IGUAL. [conta]/[tipo] restringem (nulo = qualquer; tipo = DEBITO | CREDITO).
 * Maior [prioridade] vence; empate = padrão mais longo (mais específico). Texto comparado sem acento nem caixa.
 */
@Entity(tableName = "regras_categoria")
data class RegraCategoria(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val padrao: String,
    val tipoMatch: String = "CONTEM",
    val categoria: String,
    val conta: String? = null,
    val tipo: String? = null,
    val ativa: Boolean = true,
    val prioridade: Int = 0,
    val criadaEm: Long = 0L
)

@Dao
interface RegraCategoriaDao {
    @Query("SELECT * FROM regras_categoria ORDER BY prioridade DESC, id ASC")
    fun observar(): Flow<List<RegraCategoria>>

    @Query("SELECT * FROM regras_categoria")
    suspend fun obterTodasStatic(): List<RegraCategoria>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(regra: RegraCategoria): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(regras: List<RegraCategoria>)

    @Query("DELETE FROM regras_categoria WHERE id = :id")
    suspend fun excluir(id: Int)

    @Query("DELETE FROM regras_categoria WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)

    @Query("DELETE FROM regras_categoria")
    suspend fun limpar()
}
