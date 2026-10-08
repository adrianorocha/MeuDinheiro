package com.meudinheiro.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meudinheiro.domain.Movimento
import com.meudinheiro.domain.Natureza
import java.util.Date

/**
 * Lançamento do extrato.
 *
 * - [tipo]: direção do dinheiro — CREDITO = entrada, DEBITO = saída (não confundir com cartão de crédito).
 * - Compra no cartão = tipo DEBITO + [cartaoId]; [pago] significa "fatura quitada".
 * - [natureza] separa receita/despesa real de movimentações internas (transferência, aporte, fatura…).
 */
@Entity(
    tableName = "despesas",
    indices = [Index("conta"), Index("cartaoId"), Index("data")]
)
data class Despesa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val descricao: String,
    override val valor: Double,
    val data: Date,
    override val categoria: String,
    override val conta: String,
    val pic: String,
    override val tipo: TipoDespesa,

    val mes: Int,
    val ano: Int,

    override val cartaoId: Int? = null,
    val valorOriginal: Double = 0.0,
    val moedaOriginal: String = "BRL",
    val cotacaoNaData: Double = 1.0,

    @ColumnInfo(name = "pago", defaultValue = "0")
    override val pago: Boolean = false,

    // Sem defaultValue no Room de propósito: a migração 1→2 cria as colunas com DEFAULT.
    override val natureza: String = Natureza.NORMAL,
    val grupoId: String? = null,
    /** Quem fez o lançamento (conta compartilhada, R32). Nulo em dados antigos. */
    val autor: String? = null,
    /** Identificador da transação no extrato do banco (conciliação feita no portal — R35/R38). */
    val fitid: String? = null,
    /** Quando foi conciliada com o extrato (epoch ms); nulo = não conciliada. */
    val conciliadoEm: Long? = null
) : Movimento {
    override val dataMs: Long get() = data.time
}

enum class TipoDespesa {
    DEBITO,
    CREDITO
}
