package com.meudinheiro.componentes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meudinheiro.funcoes.Haptics
import com.meudinheiro.ui.theme.NeonCyan

@Composable
fun BotaGlassmorphic(
    texto: String,
    icone: ImageVector? = null,
    corAcento: Color = NeonCyan,
    modifier: Modifier = Modifier,
    hapticType: String = "energia",
    animateIdleJump: Boolean = true, // 🚀 NOVA FLAG: Ativa/desativa o pulo idle
    onClick: () -> Unit
) {
    // 🚀 Contexto para vibração
    val context = LocalContext.current

    // --- 💡 MOTOR DE PULO IDLE (Flutuação Magnética) ---
    val idleOffset = if (animateIdleJump) {
        val infiniteTransitionJump = rememberInfiniteTransition(label = "idle_jump_bota")
        infiniteTransitionJump.animateFloat(
            initialValue = 0f,
            targetValue = -5f, // Pula sutilmente 5dp para cima
            animationSpec = infiniteRepeatable(
                // 1800ms para ir, 1800ms para voltar. Linear para um float suave.
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse // Vai e volta
            ),
            label = "idle_offset"
        ).value
    } else {
        0f // Sem pulo
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            // 🚀 A MÁGICA DO PULO ACONTECE AQUI!
            // graphicsLayer move o pixel sem alterar o layout dos vizinhos
            .graphicsLayer {
                translationY = idleOffset.dp.toPx() // Aplica o pulo
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                // Efeito de "Scanline" no fundo
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.03f),
                    0.5f to Color.White.copy(alpha = 0.07f),
                    1.0f to Color.White.copy(alpha = 0.03f)
                )
            )
            .border(
                width = 1.dp,
                color = corAcento.copy(alpha = 0.25f), // Borda neon sutil
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Tira o ripple feio
            ) {
                // VIBRAÇÃO AUTOMÁTICA
                Haptics.vibrar(context, hapticType)
                onClick()
            },
        contentAlignment = Alignment.Center

    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icone != null) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = corAcento,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = texto,
                style = MaterialTheme.typography.labelLarge, // 🚀 Usa o estilo Cyberpunk
                color = corAcento,
                textAlign = TextAlign.Center
            )
        }
    }
}