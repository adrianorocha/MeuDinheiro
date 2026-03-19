package com.meudinheiro.componentes

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.meudinheiro.data.PatrimonioPonto
import com.meudinheiro.ui.theme.NeonCyan

@Composable
fun EvolucaoPatrimonialChart(
    pontos: List<PatrimonioPonto>,
    isPrivate: Boolean,
    modifier: Modifier = Modifier
) {
    if (pontos.isEmpty()) return

    val maxValor = pontos.maxOf { it.valor }.coerceAtLeast(1.0)
    val minValor = pontos.minOf { it.valor }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val spacing = width / (pontos.size - 1)

        // 1. Criar o Caminho da Linha (Path)
        val path = Path()
        pontos.forEachIndexed { index, ponto ->
            val x = index * spacing
            // Inverter o Y (0 é no topo) e normalizar o valor
            val y = height - ((ponto.valor / maxValor).toFloat() * height)

            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        // 2. Desenhar o Gradiente de Preenchimento (Sombra abaixo da linha)
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.2f), Color.Transparent)
            )
        )

        // 3. Desenhar a Linha com Brilho Neon (Usando Native Canvas para Glow)
        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                color = NeonCyan.toArgb()
                strokeWidth = 8f
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
                // O SEGREDO DO BRILHO:
                maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
            }
            // Desenha a "sombra" neon por trás
            drawPath(path.asAndroidPath(), paint)
        }

        // Desenha a linha principal sólida por cima
        drawPath(
            path = path,
            color = NeonCyan,
            style = Stroke(width = 4f)
        )

        // 4. Desenhar os Pontos (Bolinhas)
        pontos.forEachIndexed { index, ponto ->
            val x = index * spacing
            val y = height - ((ponto.valor / maxValor).toFloat() * height)

            drawCircle(
                color = Color(0xFF131E29),
                radius = 10f,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
            drawCircle(
                color = NeonCyan,
                radius = 6f,
                center = androidx.compose.ui.geometry.Offset(x, y),
                style = Stroke(width = 4f)
            )
        }
    }
}