package com.meudinheiro.componentes

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

@Composable
fun ScannerNeonOverlay(
    onClose: () -> Unit,
    onToggleFlash: () -> Unit
) {
    // Animação do Laser de Leitura
    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // A Mágica do Canvas (Máscara escura + Laser)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Tamanho da janela do leitor (Padrão boleto)
            val rectWidth = canvasWidth * 0.85f
            val rectHeight = 120.dp.toPx()
            val left = (canvasWidth - rectWidth) / 2
            val top = (canvasHeight - rectHeight) / 2

            // 1. Escurecer tudo ao redor (Fundo semi-transparente)
            drawRect(color = Color.Black.copy(alpha = 0.7f))

            // 2. "Recortar" a janela central (Deixa a câmera vazar)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(24.dp.toPx()),
                blendMode = BlendMode.Clear // Isso apaga o fundo escuro aqui!
            )

            // 3. Desenhar a Borda Neon da Mira
            drawRoundRect(
                color = Color(0xFF00E5FF), // NeonCyan
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(width = 4.dp.toPx())
            )

            // 4. O Laser Animado com Brilho (Glow)
            val laserCurrentY = top + (rectHeight * laserY)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#00E5FF")
                    strokeWidth = 8f
                    style = android.graphics.Paint.Style.STROKE
                    maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL) // Efeito Brilho
                }
                drawLine(left + 20f, laserCurrentY, left + rectWidth - 20f, laserCurrentY, paint)
            }

            // Linha central sólida do laser
            drawLine(
                color = Color.White,
                start = Offset(left + 20f, laserCurrentY),
                end = Offset(left + rectWidth - 20f, laserCurrentY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // --- BOTÕES DE CONTROLE (Sobrepostos à Câmera) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(0.4f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(Icons.Default.Close, "Fechar Scanner", tint = Color.White)
            }

            IconButton(
                onClick = onToggleFlash,
                modifier = Modifier.background(Color.Black.copy(0.4f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(Icons.Default.FlashlightOn, "Lanterna", tint = Color.White)
            }
        }

        // Texto de instrução
        Text(
            text = "Alinhe o código de barras na linha",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 100.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}