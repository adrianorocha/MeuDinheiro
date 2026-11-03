package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * R58 — dívida em quitação (contrato: coleção `dividas`). [saldoDevedor] é controle próprio (não é saldo de conta);
 * valores em reais com 2 casas (padrão do app), a lógica trabalha em centavos. [taxaJurosMensal] em % ao mês (0 = sem juros).
 * [tipo]: EMPRESTIMO | FINANCIAMENTO | CHEQUE_ESPECIAL | CARTAO_ROTATIVO | CONSIGNADO | OUTRA.
 * [valorOriginal] só serve ao progresso de quitação (null = o saldo atual). [parcelaMinima] = parcela fixa ou mínimo mensal.
 */
@Entity(tableName = "dividas")
data class Divida(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val tipo: String = "OUTRA",
    val credor: String? = null,
    val saldoDevedor: Double,
    val valorOriginal: Double? = null,
    val taxaJurosMensal: Double = 0.0,
    val parcelaMinima: Double? = null,
    val parcelasRestantes: Int? = null,
    val diaVencimento: Int? = null,
    /** Número da conta de onde costuma sair o pagamento. */
    val conta: String? = null,
    val ativa: Boolean = true
)

@Dao
interface DividaDao {
    @Query("SELECT * FROM dividas ORDER BY ativa DESC, nome COLLATE NOCASE ASC")
    fun observar(): Flow<List<Divida>>

    @Query("SELECT * FROM dividas")
    suspend fun obterTodasStatic(): List<Divida>

    @Query("SELECT * FROM dividas WHERE id = :id")
    suspend fun obterPorId(id: Int): Divida?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(divida: Divida): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(dividas: List<Divida>)

    @Query("DELETE FROM dividas WHERE id = :id")
    suspend fun excluir(id: Int)

    @Query("DELETE FROM dividas WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)

    @Query("DELETE FROM dividas")
    suspend fun limpar()
}
