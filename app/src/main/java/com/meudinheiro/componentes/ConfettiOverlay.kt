package com.meudinheiro.componentes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import com.meudinheiro.data.ConfeteState

@Composable
fun ConfettiOverlay(state: ConfeteState) {
    val tempo = rememberInfiniteTransition(label = "").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing))
    )

    LaunchedEffect(state.partículas) {
        while (state.partículas.isNotEmpty()) {
            withFrameNanos { state.atualizar() }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        state.partículas.forEachIndexed { index, c ->
            rotate(degrees = tempo.value * (if(index % 2 == 0) 1f else -1f), pivot = Offset(c.x, c.y)) {
                drawRoundRect(
                    color = c.cor,
                    topLeft = Offset(c.x, c.y),
                    size = Size(c.tamanho, c.tamanho * 0.6f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}