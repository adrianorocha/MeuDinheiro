package com.meudinheiro.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "despesas")
data class Despesa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val descricao: String,
    val valor: Double,
    val data: Date,
    val categoria: String,
    val conta: String,
    val pic: String,
    val tipo: TipoDespesa,

    val mes: Int,
    val ano: Int,

    //@ColumnInfo(name = "cartao_id")
    val cartaoId: Int? = null,
    val valorOriginal: Double = 0.0, // Ex: 50.00
    val moedaOriginal: String = "BRL", // Ex: "USD"
    val cotacaoNaData: Double = 1.0,    // Ex: 5.25

    @ColumnInfo(name = "pago", defaultValue = "0")
    val pago: Boolean = false
)

enum class TipoDespesa {
    DEBITO,
    CREDITO
}
