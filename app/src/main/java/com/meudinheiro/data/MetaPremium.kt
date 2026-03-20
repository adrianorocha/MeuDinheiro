package com.meudinheiro.data

import androidx.compose.ui.graphics.Color

data class MetaPremium(
    val id: String,
    val nome: String,
    val valorAlvo: Double,
    val valorPoupado: Double,
    val iconePic: String, // String ID para o drawable
    val corDestaque: Color // Cada meta tem sua cor neon única
) {
    // Progresso de 0.0f a 1.0f
    val progresso: Float get() = if (valorAlvo > 0) (valorPoupado / valorAlvo).toFloat().coerceIn(0f, 1f) else 0f
    val concluida: Boolean get() = progresso >= 1f
}
