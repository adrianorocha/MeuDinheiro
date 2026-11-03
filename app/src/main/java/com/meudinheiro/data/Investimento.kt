package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meudinheiro.domain.Financas

@Entity(tableName = "investimentos")
data class Investimento(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val tipo: String, // "Renda Fixa", "Ações", "FIIs", "Cripto"
    val valorInvestido: Double, // Quanto dinheiro saiu do seu bolso
    val valorAtual: Double // Quanto o ativo vale no mercado hoje
) {
    /** Lucro/prejuízo em R$ (centavos exatos). */
    val rendimentoReal: Double
        get() = Financas.rendimento(valorInvestido, valorAtual)

    /** Variação percentual sobre o valor investido (0 quando nada foi investido). */
    val rentabilidadePercentual: Double
        get() = Financas.rentabilidadePercentual(valorInvestido, valorAtual)
}
