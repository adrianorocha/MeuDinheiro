package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patrimonio_historico")
data class PatrimonioHistorico(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dataMillis: Long, // Data do registro
    val valorTotal: Double, // Soma de todas as contas no momento
    val mesReferencia: String // Ex: "JAN", "FEV"
)