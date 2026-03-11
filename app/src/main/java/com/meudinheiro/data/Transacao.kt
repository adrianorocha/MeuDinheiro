package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transacoes")
data class Transacao(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val descricao: String,
    val valor: Double,
    val bancoNome: String,
    val categoriaNome: String,
    val categoriaCorHex: String, // Salvamos a cor como Hex (ex: "#69F0AE")
    val timestamp: Long = System.currentTimeMillis()
)
