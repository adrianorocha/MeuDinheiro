package com.meudinheiro.componentes

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ActionOrbital(
    icone: ImageVector,
    cor: Color,
    tooltip: String,
    badgeCount: Int = 0,
    animateIdleJump: Boolean = true,
    onClick: () -> Unit
) {
    val infiniteTransitionGlow = rememberInfiniteTransition(label = "orbital_glow")
    val glowAlpha by infiniteTransitionGlow.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    val idleOffset = if (animateIdleJump) {
        val infiniteTransitionJump = rememberInfiniteTransition(label = "idle_jump")
        infiniteTransitionJump.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idle_offset"
        ).value
    } else 0f

    // Animação do Badge
    var scaleFactor by remember { mutableFloatStateOf(0f) }
    val scaleAnim by animateFloatAsState(
        targetValue = scaleFactor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "BadgeScale"
    )

    LaunchedEffect(key1 = badgeCount) {
        if (badgeCount > 0) {
            scaleFactor = 0f
            delay(50)
            scaleFactor = 1f
        } else scaleFactor = 0f
    }

    Box(
        modifier = Modifier.size(50.dp), // Aumentamos o berço para o brilho respirar
        contentAlignment = Alignment.Center
    ) {
        // 🚀 O SEGREDO: Gradiente Radial em vez de Blur
        // Isso garante que o fundo seja circular e nunca "quadre"
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(cor.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset.Unspecified,
                        radius = Float.POSITIVE_INFINITY
                    )
                )
        )

        // O BOTÃO DE VIDRO
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    translationY = idleOffset.dp.toPx()
                }
                .clip(CircleShape) // 🚀 Garante que até o clique seja circular
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, cor.copy(alpha = 0.3f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icone,
                contentDescription = tooltip,
                tint = cor,
                modifier = Modifier.size(18.dp)
            )
        }

        // BADGE ANIMADO
        if (scaleAnim > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = ((-2) + idleOffset).dp)
                    .scale(scaleAnim)
                    .size(16.dp)
                    .background(Color(0xFFFF4B4B), CircleShape)
                    .border(1.5.dp, Color(0xFF0D1B2A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$badgeCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}