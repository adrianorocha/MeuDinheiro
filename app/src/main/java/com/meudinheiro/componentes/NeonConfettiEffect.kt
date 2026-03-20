package com.meudinheiro.componentes

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

// Modelo da Partícula
data class NeonParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1f,
    val color: Color,
    val size: Float = Random.nextFloat() * 10f + 5f
)

@Composable
fun NeonConfettiEffect(corDestaque: Color, onFinished: () -> Unit) {
    val particles = remember {
        List(100) { // Criamos 100 partículas
            NeonParticle(
                x = 0.5f, // Começa no centro (proporcional)
                y = 0.5f,
                vx = (Random.nextFloat() - 0.5f) * 0.1f, // Direção aleatória
                vy = (Random.nextFloat() - 0.7f) * 0.1f, // Mais força para cima
                color = if (Random.nextBoolean()) corDestaque else Color.White
            )
        }
    }

    val animatable = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
        )
        onFinished() // Limpa o efeito quando acabar
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val t = animatable.value

        particles.forEach { p ->
            // Física básica: posição = posição + velocidade * tempo
            val currentX = (p.x + p.vx * t * 15) * width
            val currentY = (p.y + p.vy * t * 15 + (0.1f * t * t * 10)) * height // Gravidade
            val currentAlpha = 1f - t // Vai sumindo

            drawCircle(
                color = p.color.copy(alpha = currentAlpha),
                radius = p.size,
                center = Offset(currentX, currentY)
            )
        }
    }
}