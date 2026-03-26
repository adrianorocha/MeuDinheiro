package com.meudinheiro.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.meudinheiro.data.random

@Composable
fun GlitchAnimation(
    visible: Boolean, // Se o modo privado está ativo
    content: @Composable () -> Unit
) {
    // 🚀 MOTOR DE GLITCH
    val glitchProgress = remember { Animatable(0f) }

    // Dispara a interferência sempre que o modo privado for ativado
    LaunchedEffect(visible) {
        if (visible) {
            glitchProgress.snapTo(0f)
            // A interferência é rápida: 300ms
            glitchProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = LinearOutSlowInEasing)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // O conteúdo (saldo)
        content()

        // 👻 O EFEITO DE INTERFERÊNCIA
        if (glitchProgress.value < 1f && visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Deslocamento horizontal e vertical aleatório para simular glitch
                        translationX = ((-1f..1f).random() * glitchProgress.value * 20).dp.toPx()
                        translationY = ((-1f..1f).random() * glitchProgress.value * 20).dp.toPx()
                        // Efeito de fade out conforme chega no final
                        alpha = (1f - glitchProgress.value) * 0.8f
                    }
                    .background(Color.Transparent)
            )
        }
    }
}