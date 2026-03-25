package com.meudinheiro.componentes

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MiniFabOrbital(
    iconeRes: Int,
    cor: Color,
    offsetX: Dp,
    offsetY: Dp,
    progresso: Float, // 0f a 1f
    onClick: () -> Unit
) {
    // --- 1. MOTOR DE PULSO DO GLOW ---
    val infiniteTransition = rememberInfiniteTransition(label = "mini_orbital_motor")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // --- 2. MOTOR DE PULO IDLE (Flutuação) ---
    // Os satélites pulam com um tempo diferente (2200ms) para parecerem independentes
    val idleOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f, // Pulo sutil de 5dp
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_jump"
    )

    if (progresso > 0f) {
        Box(
            modifier = Modifier
                // Posicionamento orbital baseado no progresso da abertura
                .offset(x = offsetX * progresso, y = offsetY * progresso)
                .scale(progresso)
                .alpha(progresso)
                .size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // 🚀 GLOW RADIAL (Plasma)
            // O brilho pula junto com o botão para manter a aura colada nele
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        alpha = glowAlpha * progresso
                        // Aplicamos o pulo apenas quando o menu está quase aberto (> 80%)
                        translationY = if (progresso > 0.8f) idleOffset.dp.toPx() else 0f
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(cor.copy(alpha = 0.5f), Color.Transparent),
                            center = Offset.Unspecified,
                            radius = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // O BOTÃO SATÉLITE (Vidro)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .graphicsLayer {
                        // Aplica a flutuação magnética
                        translationY = if (progresso > 0.8f) idleOffset.dp.toPx() else 0f
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, cor.copy(alpha = 0.4f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconeRes),
                    contentDescription = null,
                    tint = cor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}