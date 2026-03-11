package com.meudinheiro.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class TransacaoModel(
    val id: Int,
    val descricao: String,
    val valor: Double,
    val bancoNome: String,
    val dataHora: String,
    val categoriaNome: String,
    val categoriaCor: Color
) {
    // Função inteligente que escolhe o ícone baseado no nome da categoria
    fun getIcon(): ImageVector {
        return when (categoriaNome.lowercase()) {
            "alimentação", "comida", "restaurante" -> Icons.Default.Fastfood
            "transporte", "uber", "gasolina" -> Icons.Default.DirectionsCar
            "lazer", "viagem" -> Icons.Default.Celebration
            "saúde", "farmácia" -> Icons.Default.MedicalServices
            "casa", "aluguel" -> Icons.Default.Home
            "educação" -> Icons.Default.School
            "salário", "depósito" -> Icons.Default.Payments
            "compras", "mercado" -> Icons.Default.ShoppingCart
            else -> Icons.Default.Category
        }
    }
}