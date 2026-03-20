package com.meudinheiro.componentes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meudinheiro.R
import com.meudinheiro.data.MetaPremium
import com.meudinheiro.funcoes.obterResIdPelaPic
import com.meudinheiro.ui.theme.NeonCyan

@Composable
fun OrbeHolograficoMeta(
    meta: MetaPremium,
    isPrivate: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val haptic = LocalHapticFeedback.current

    // Animação de pulso sutil para metas quase concluídas
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Cores baseadas no status
    val corStatus = if (meta.concluida) NeonCyan else meta.corDestaque
    val corGlow = if (meta.progresso > 0.8f) corStatus.copy(alpha = pulseAlpha) else corStatus.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .size(100.dp)
            .padding(8.dp)
            .combinedClickable(
                onClick = { /* Clique simples pode abrir detalhes */ },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress) // 📳 Vibração física
                    onLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. O Canvas que desenha o Orbe e o Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val centro = center
            val raio = (size.minDimension / 2) - (strokeWidth / 2)

            // TRILHO DO ORBE (Fundo Escuro)
            drawCircle(
                color = Color.White.copy(alpha = 0.03f),
                radius = raio,
                center = centro,
                style = Stroke(width = strokeWidth)
            )

            // PREENCHIMENTO LÍQUIDO (O Progresso)
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to corStatus.copy(alpha = 0.1f),
                    meta.progresso to corStatus,
                    1.0f to corStatus.copy(alpha = 0.1f),
                    center = centro
                ),
                startAngle = -90f,
                sweepAngle = meta.progresso * 360f,
                useCenter = false,
                topLeft = Offset(centro.x - raio, centro.y - raio),
                size = Size(raio * 2, raio * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 💡 EFEITO GLOW NEON EXTERNO
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(corGlow, Color.Transparent),
                    center = centro,
                    radius = raio * 1.4f
                ),
                radius = raio * 1.4f,
                center = centro
            )
        }

        // 2. O ÍCONE E O TEXTO CENTRAL
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(
                    id = if (meta.concluida) R.drawable.ic_rocket_launch // Ícone de decolagem
                    else obterResIdPelaPic(meta.iconePic)
                ),
                contentDescription = meta.nome,
                tint = corStatus,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPrivate) "**" else "${(meta.progresso * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
        }
    }
}