package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "despesas_fixas")
data class DespesaFixa(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val descricao: String,
    val valor: Double,
    val conta: String,
    val categoria: String,
    val pic: String,
    val tipo: TipoDespesa,
    val diaVencimento: Int, // Dia do mês (1 a 31) que deve ser lançada
    val ultimaDataLancamento: Date? = null // Para saber se já lançamos neste mês
)