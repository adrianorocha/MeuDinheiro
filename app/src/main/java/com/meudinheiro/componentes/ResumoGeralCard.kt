package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.*
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.ChartLegendItem
import com.meudinheiro.funcoes.formatarMoedaBR

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ResumoGeralCard(
    receitaTotal: Double,
    despesaTotal: Double,
    metasTotal: Double, // Novo parâmetro: Total guardado em metas
    isPrivate: Boolean = false
) {
    // Patrimônio Líquido = O que sobrou (lucro) + O que já está poupado
    val saldoDisponivel = receitaTotal - despesaTotal
    val patrimonioTotal = saldoDisponivel + metasTotal

    // Proporção para as Barras (O maior valor entre os 3 define o 100% da largura)
    val maxVal = maxOf(receitaTotal, despesaTotal, metasTotal, 1.0)
    val propReceita = (receitaTotal / maxVal).toFloat()
    val propDespesa = (despesaTotal / maxVal).toFloat()
    val propMetas = (metasTotal / maxVal).toFloat()

    var animationPlayed by remember { mutableStateOf(false) }

    val modoEconomia = lembrarEstadoPerformance()

    // Se estiver em economia, o tempo de animação é ZERO (mudança instantânea)
    val duracaoAnim = if (modoEconomia) 0 else 600

    // Animações
    val animBarReceita by animateFloatAsState(
        targetValue = if (animationPlayed) propReceita else 0f,
        animationSpec = tween(1000, 100), label = "barR"
    )
    val animBarMetas by animateFloatAsState( // Nova animação
        targetValue = if (animationPlayed) propMetas else 0f,
        animationSpec = tween(1000, 300), label = "barM"
    )
    val animBarDespesa by animateFloatAsState(
        targetValue = if (animationPlayed) propDespesa else 0f,
        animationSpec = tween(1000, 500), label = "barD"
    )

    // Proporção do Donut (Despesa em relação ao que entrou)
    val sweepDespesa = if (receitaTotal > 0) (despesaTotal / receitaTotal).toFloat()
        .coerceIn(0f, 1f) * 360f else 0f
    val animSweep by animateFloatAsState(
        targetValue = if (animationPlayed) sweepDespesa else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "donut"
    )

    // 1. Condição de Alerta: Gastou mais do que ganhou?
    val estaNoVermelho = despesaTotal > receitaTotal && receitaTotal > 0

    // 2. Configuração da Animação de Pulsação
    val infiniteTransition = rememberInfiniteTransition(label = "alerta")
    val corAlerta by infiniteTransition.animateColor(
        initialValue = Color(0xFFEF5350).copy(alpha = 0.2f),
        targetValue = Color(0xFFEF5350).copy(alpha = 0.8f),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corPulso"
    )
    LaunchedEffect(Unit) { animationPlayed = true }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(135.dp), // Aumentado para acomodar 3 barras
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E).copy(alpha = 0.95f)),
        border = if (estaNoVermelho) {
            BorderStroke(2.dp, corAlerta)
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- LADO ESQUERDO: Donut ---
            Box(modifier = Modifier.size(75.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(65.dp)) {
                    val stroke = 7.dp.toPx()
                    // Trilho
                    drawArc(
                        color = Color.White.copy(alpha = 0.05f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // Despesas
                    drawArc(
                        brush = Brush.linearGradient(listOf(Color(0xFFFF8A80), Color(0xFFEF5350))),
                        startAngle = -90f, sweepAngle = animSweep, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                val percent =
                    if (receitaTotal > 0) ((despesaTotal / receitaTotal) * 100).toInt() else 0
                Text(
                    text = if (isPrivate) "**" else "$percent%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // --- LADO DIREITO: Barras e Info ---
            Column(modifier = Modifier.weight(1f)) {
                Text("Patrimônio Líquido", fontSize = 11.sp, color = Color.White.copy(0.6f))

                // --- TEXTO ANIMADO DO PATRIMÔNIO ---
                AnimatedContent(
                    targetState = patrimonioTotal,
                    transitionSpec = {
                        if (modoEconomia) {
                            // Sem animação de slide/fade para poupar CPU/GPU
                            fadeIn(tween(0)) with fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(duracaoAnim)) + slideInVertically { it / 2 }) with fadeOut(tween(duracaoAnim))
                        }
                    }
                ) { valor ->
                    Text(
                        text = formatarMoedaBR(valor, isPrivate),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (estaNoVermelho) Color(0xFFEF5350) else Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Barra 1: Entradas (Verde Claro)
                HorizontalBalanceBar(
                    label = "Entradas", value = receitaTotal, progress = animBarReceita,
                    color = Color(0xFF69F0AE), isPrivate = isPrivate
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Barra 2: Metas (Verde Neon - NOVO)
                HorizontalBalanceBar(
                    label = "Poupado", value = metasTotal, progress = animBarMetas,
                    color = Color(0xFF00E676), isPrivate = isPrivate
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Barra 3: Saídas (Vermelho)
                HorizontalBalanceBar(
                    label = "Saídas", value = despesaTotal, progress = animBarDespesa,
                    color = Color(0xFFEF5350), isPrivate = isPrivate
                )
            }
        }
    }
}

