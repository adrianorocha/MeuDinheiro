package com.meudinheiro.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Compra(
    val id: Int,
    val estabelecimento: String,
    val valor: Double,
    val data: String,
    val categoria: CategoriaCompra
)

enum class CategoriaCompra(val icone: ImageVector, val cor: Color) {
    ALIMENTACAO(Icons.Rounded.Restaurant, Color(0xFFFF9800)),
    TRANSPORTE(Icons.Rounded.DirectionsCar, Color(0xFF00E5FF)),
    LAZER(Icons.Rounded.Celebration, Color(0xFFFF007A)),
    SAUDE(Icons.Rounded.MedicalServices, Color(0xFF00FF95)),
    OUTROS(Icons.Rounded.ShoppingBag, Color(0xFF7000FF))
}
