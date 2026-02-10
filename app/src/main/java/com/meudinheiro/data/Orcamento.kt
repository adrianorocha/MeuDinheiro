package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1. Entidade
@Entity(tableName = "orcamentos")
data class Orcamento(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoria: String,
    val valorLimite: Double
)

