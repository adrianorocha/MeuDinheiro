package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "investimentos")
data class Investimento(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val tipo: String, // "Renda Fixa", "Ações", "FIIs", "Cripto"
    val valorInvestido: Double, // Quanto dinheiro saiu do seu bolso
    val valorAtual: Double // Quanto o ativo vale no mercado hoje
) {
    // Calcula o lucro ou prejuízo em Reais (R$)
    val rendimentoReal: Double
        get() = valorAtual - valorInvestido

    // Calcula a porcentagem de crescimento ou queda (%)
    val rentabilidadePercentual: Double
        get() = if (valorInvestido > 0) {
            ((valorAtual - valorInvestido) / valorInvestido) * 100
        } else {
            0.0
        }
}