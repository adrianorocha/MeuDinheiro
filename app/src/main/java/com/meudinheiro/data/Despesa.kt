package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "despesas")
data class Despesa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val descricao: String,
    val valor: Double,
    val data: String,
    val categoria: String,
    val conta: String,
    val pic: String
)