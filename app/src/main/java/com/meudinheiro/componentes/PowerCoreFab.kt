package com.meudinheiro.componentes

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
@Composable
fun PowerCoreFab(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f, // Glow mais discreto
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .navigationBarsPadding() // Respeita a área do sistema
            .size(56.dp)             // 🚀 Box total menor
            .graphicsLayer {
                translationY = 25f   // 🚀 A "Mágica": Empurra o botão 25 pixels para baixo
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // Aura Neon (Glow)
        Surface(
            modifier = Modifier
                .size(40.dp)
                .alpha(glowAlpha)
                .blur(8.dp),
            shape = CircleShape,
            color = NeonCyan
        ) {}

        // O Núcleo do Botão
        Box(
            modifier = Modifier
                .size(42.dp)         // 🚀 Tamanho ultra compacto
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(NeonCyan, Color(0xFF00B8D4))
                    )
                )
                .border(1.dp, Color.White.copy(0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Menu",
                tint = DeepSpaceBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}