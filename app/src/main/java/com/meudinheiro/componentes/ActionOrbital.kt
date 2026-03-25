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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun ActionOrbital(
    iconeRes: Int,
    cor: Color,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbital_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = InfiniteRepeatableSpec(
            tween(2000, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(45.dp) // Tamanho discreto para o topo
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // Aura Neon de Fundo (O brilho orbital)
        Surface(
            modifier = Modifier
                .size(32.dp)
                .alpha(glowAlpha)
                .blur(8.dp),
            shape = CircleShape,
            color = cor
        ) {}

        // O Botão em si (Glassmorphism sutil)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, cor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconeRes),
                contentDescription = null,
                tint = cor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}