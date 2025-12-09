package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contasaldo")
data class ContaSaldo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saldo: Double,
    val banco: String,
    val agencia: String,
    val conta: String,
    val titular: String
)