package com.meudinheiro.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class Confete(
    val x: Float,
    var y: Float,
    val cor: Color,
    val velocidade: Float,
    val angulo: Float,
    val tamanho: Float = (8..15).random().toFloat()
)

class ConfeteState {
    var partículas by mutableStateOf<List<Confete>>(emptyList())

    fun disparar(largura: Float) {
        val novasParticulas = List(100) {
            Confete(
                x = (0f..largura).random(),
                y = -50f,
                cor = listOf(Color(0xFF69F0AE), Color(0xFF00E676), Color(0xFFB2FF59), Color.White).random(),
                velocidade = (10f..25f).random(),
                angulo = (-0.5f..0.5f).random()
            )
        }
        partículas = novasParticulas
    }

    fun atualizar() {
        partículas = partículas.map { it.copy(y = it.y + it.velocidade, x = it.x + it.angulo) }
            .filter { it.y < 2500f } // Remove confetes que saíram da tela
    }
}

// Extensão útil para gerar números aleatórios em Float
fun ClosedFloatingPointRange<Float>.random() =
    start + (java.util.Random().nextFloat() * (endInclusive - start))
