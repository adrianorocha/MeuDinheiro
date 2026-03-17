package com.meudinheiro.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "cartoes",
    foreignKeys = [
        ForeignKey(
            entity = ContaSaldo::class, // Sua tabela de contas atual
            parentColumns = ["id"],
            childColumns = ["contaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Cartao(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,               // Ex: "Nubank Platinum"
    val finalCartao: String,        // Ex: "4321" (Apenas os 4 últimos dígitos)
    val tipo: String,               // "CRÉDITO", "DÉBITO" ou "MÚLTIPLO"
    val limiteTotal: Double,        // Ex: 5000.00
    val diaFechamento: Int,         // Ex: 25
    val diaVencimento: Int,         // Ex: 5
    @ColumnInfo(index = true)
    val contaId: Int              // 📍 O VÍNCULO: ID da conta corrente associada
)

