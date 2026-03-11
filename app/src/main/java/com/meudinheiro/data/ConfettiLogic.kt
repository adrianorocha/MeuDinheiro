package com.meudinheiro.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class Confete(
    var x: Float,
    var y: Float,
    var vx: Float, // Velocidade horizontal
    var vy: Float, // Velocidade vertical
    val cor: Color,
    val tamanho: Float,
    val peso: Float // Para dar variação na queda
)

class ConfeteState {
    var partículas by mutableStateOf<List<Confete>>(emptyList())
    private val gravidade = 0.8f

    fun disparar(origemX: Float, origemY: Float) {
        val novasParticulas = List(80) {
            // Gera um ângulo aleatório para a explosão (para cima)
            val angulo = (-Math.PI).toFloat() * (0.2f..0.8f).random()
            val forca = (15f..35f).random()

            Confete(
                x = origemX,
                y = origemY,
                vx = Math.cos(angulo.toDouble()).toFloat() * forca,
                vy = Math.sin(angulo.toDouble()).toFloat() * forca,
                cor = listOf(
                    Color(0xFF69F0AE),
                    Color(0xFF00E676),
                    Color.White,
                    Color.Yellow
                ).random(),
                tamanho = (10f..20f).random(),
                peso = (0.5f..1.2f).random()
            )
        }
        partículas = partículas + novasParticulas
    }

    fun atualizar() {
        partículas = partículas.map { c ->
            c.vy += gravidade * c.peso // Gravidade puxando para baixo
            c.copy(
                x = c.x + c.vx,
                y = c.y + c.vy,
                vx = c.vx * 0.95f // Arrastre (vai parando pros lados)
            )
        }.filter { it.y < 3000f && it.x > -100f && it.x < 2000f }
    }
}

// Extensão útil para gerar números aleatórios em Float
fun ClosedFloatingPointRange<Float>.random() =
    start + (java.util.Random().nextFloat() * (endInclusive - start))
