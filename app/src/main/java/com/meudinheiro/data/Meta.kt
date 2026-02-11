package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metas")
data class Meta(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val valorObjetivo: Double,
    val valorGuardado: Double,
    val icone: String = "ic_savings" // Podemos usar para ícones diferentes
)
