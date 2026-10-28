package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** R59 — pessoa com quem há acertos (contrato: coleção `pessoas`). */
@Entity(tableName = "pessoas")
data class Pessoa(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val contato: String? = null
)

/**
 * R59 — valor a receber/pagar entre pessoas (contrato: coleção `acertos`). [tipo] = A_RECEBER | A_PAGAR; [data] e [quitadoEm]
 * em epoch ms. [despesaId] = lançamento de origem (pode ficar órfão se ele for excluído: tolerado, nunca quebra).
 * [grupoRateio] = `rateio:<despesaId>` quando nasceu de "Dividir esta despesa".
 */
@Entity(tableName = "acertos")
data class Acerto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pessoaId: Int,
    val tipo: String,
    val valor: Double,
    val descricao: String,
    val data: Long,
    val despesaId: Long? = null,
    val quitado: Boolean = false,
    val quitadoEm: Long? = null,
    val grupoRateio: String? = null
)

@Dao
interface PessoaDao {
    @Query("SELECT * FROM pessoas ORDER BY nome COLLATE NOCASE ASC")
    fun observar(): Flow<List<Pessoa>>

    @Query("SELECT * FROM pessoas")
    suspend fun obterTodasStatic(): List<Pessoa>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(pessoa: Pessoa): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(pessoas: List<Pessoa>)

    @Query("DELETE FROM pessoas WHERE id = :id")
    suspend fun excluir(id: Int)

    @Query("DELETE FROM pessoas WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)

    @Query("DELETE FROM pessoas")
    suspend fun limpar()
}

@Dao
interface AcertoDao {
    @Query("SELECT * FROM acertos ORDER BY data DESC, id DESC")
    fun observar(): Flow<List<Acerto>>

    @Query("SELECT * FROM acertos")
    suspend fun obterTodasStatic(): List<Acerto>

    @Query("SELECT * FROM acertos WHERE despesaId = :despesaId")
    fun observarDaDespesa(despesaId: Long): Flow<List<Acerto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(acerto: Acerto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(acertos: List<Acerto>)

    @Query("DELETE FROM acertos WHERE id = :id")
    suspend fun excluir(id: Int)

    @Query("DELETE FROM acertos WHERE grupoRateio = :grupo")
    suspend fun excluirDoGrupo(grupo: String)

    @Query("DELETE FROM acertos WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)

    @Query("DELETE FROM acertos")
    suspend fun limpar()
}
