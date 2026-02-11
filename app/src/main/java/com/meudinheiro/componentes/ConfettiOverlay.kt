package com.meudinheiro.componentes

import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.meudinheiro.data.ConfeteState

@Composable
fun ConfettiOverlay(state: ConfeteState) {
    // Loop de animação que roda a cada quadro (frame)
    LaunchedEffect(state.partículas) {
        while (state.partículas.isNotEmpty()) {
            withFrameNanos {
                state.atualizar()
            }
        }
    }

    // Este é o Canvas do COMPOSE (androidx.compose.foundation.Canvas)
    Canvas(modifier = Modifier.fillMaxSize()) {
        state.partículas.forEach { confete ->
            // No Compose, o drawRoundRect já está disponível no escopo do Canvas
            drawRoundRect(
                color = confete.cor,
                topLeft = Offset(confete.x, confete.y),
                size = Size(confete.tamanho, confete.tamanho * 0.6f),
                // CornerRadius corrigido para o pacote androidx.compose.ui.geometry
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}