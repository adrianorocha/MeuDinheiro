package com.meudinheiro.componentes

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.meudinheiro.ui.theme.NeonCyan
import kotlinx.coroutines.delay

@Composable
fun DataStreamEffect(
    targetState: Int, // O índice da aba atual
    content: @Composable () -> Unit
) {
    // 🚀 MOTOR DE VARREDURA
    val scanProgress = remember { Animatable(0f) }

    // Dispara a varredura sempre que a aba mudar
    LaunchedEffect(targetState) {
        scanProgress.snapTo(0f)
        // O "flash" do laser é rápido: 400ms
        scanProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = LinearOutSlowInEasing)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // O conteúdo da tela (Aba)
        content()

        // 📟 A LINHA LASER (O "Data Stream")
        if (scanProgress.value < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp) // Largura da "nuvem" de luz
                    .graphicsLayer {
                        // Move a linha do topo (0%) até o final (100%) da tela
                        translationY = scanProgress.value * size.height
                        // Efeito de fade out conforme chega no final
                        alpha = (1f - scanProgress.value) * 0.8f
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                NeonCyan.copy(alpha = 0.1f),
                                NeonCyan, // O núcleo do laser
                                NeonCyan.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}