package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transferencias_agendadas") // O nome aqui deve ser igual ao da Query no DAO
data class TransferenciaAgendada(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dataAgendada: Long,
    val contaOrigem: String,
    val contaDestino: String,
    val valor: Double,
    val executada: Boolean = false,
)