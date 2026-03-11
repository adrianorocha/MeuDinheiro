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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.PieChartData
import com.meudinheiro.funcoes.HorizontalBalanceBarSlim
import com.meudinheiro.funcoes.PremiumPieChart
import com.meudinheiro.funcoes.TrendIndicator
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.funcoes.lembrarEstadoPerformance

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ResumoGeralCard(
    receitaTotal: Double,
    despesaMesAnterior: Double,
    despesaTotal: Double,
    metasTotal: Double,
    isPrivate: Boolean = false,
    dadosGrafico: List<PieChartData>
) {
    // --- LÓGICA DE DADOS ---
    val saldoDisponivel = receitaTotal - despesaTotal
    val patrimonioTotal = saldoDisponivel + metasTotal

    // Define o 100% da largura das barras baseado no maior valor
    val maxVal = maxOf(receitaTotal, despesaTotal, metasTotal, 1.0)

    // --- ANIMAÇÕES E PERFORMANCE ---
    val modoEconomia = lembrarEstadoPerformance()
    var animationPlayed by remember { mutableStateOf(false) }
    val duracaoAnim = if (modoEconomia) 0 else 800

    LaunchedEffect(Unit) { animationPlayed = true }

    // Animação das Barras
    val animBarReceita by animateFloatAsState(
        targetValue = if (animationPlayed) (receitaTotal / maxVal).toFloat() else 0f,
        animationSpec = tween(duracaoAnim, 0, FastOutSlowInEasing), label = "barR"
    )
    val animBarMetas by animateFloatAsState(
        targetValue = if (animationPlayed) (metasTotal / maxVal).toFloat() else 0f,
        animationSpec = tween(duracaoAnim, 150, FastOutSlowInEasing), label = "barM"
    )
    val animBarDespesa by animateFloatAsState(
        targetValue = if (animationPlayed) (despesaTotal / maxVal).toFloat() else 0f,
        animationSpec = tween(duracaoAnim, 300, FastOutSlowInEasing), label = "barD"
    )

    // Animação do Indicador Circular (Donut Esquerdo)
    val sweepDespesa = if (receitaTotal > 0) (despesaTotal / receitaTotal).toFloat()
        .coerceIn(0f, 1f) * 360f else 0f
    val animSweep by animateFloatAsState(
        targetValue = if (animationPlayed) sweepDespesa else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "donut"
    )

    // --- ALERTA VISUAL (Pulso Vermelho) ---
    val estaNoVermelho = despesaTotal > receitaTotal && receitaTotal > 0
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

    // --- UI DO CARD ---
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        // Removemos a altura fixa para deixar o layout responsivo
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E).copy(alpha = 0.98f)),
        border = if (estaNoVermelho) BorderStroke(2.dp, corAlerta) else BorderStroke(
            1.dp,
            Color.White.copy(0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            // =================================================
            // SEÇÃO 1: ESQUERDA - Indicador de Consumo (Donut)
            // =================================================
            Box(
                modifier = Modifier.size(70.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(60.dp)) {
                    val stroke = 6.dp.toPx()
                    // Fundo do trilho
                    drawArc(
                        color = Color.White.copy(alpha = 0.05f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // Arco de despesas
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
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            // =================================================
            // SEÇÃO 2: CENTRO - Dados Financeiros e Barras
            // =================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text("Patrimônio Líquido", fontSize = 10.sp, color = Color.White.copy(0.6f))

                AnimatedContent(
                    targetState = patrimonioTotal,
                    transitionSpec = {
                        if (modoEconomia) fadeIn(tween(0)) with fadeOut(tween(0))
                        else (fadeIn(tween(duracaoAnim)) + slideInVertically { it / 2 }) with fadeOut(
                            tween(duracaoAnim)
                        )
                    },
                    label = "animSaldo"
                ) { valor ->
                    Text(
                        text = formatarMoedaBR(valor, isPrivate),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (estaNoVermelho) Color(0xFFEF5350) else Color.White
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Barra 1: Entradas
                HorizontalBalanceBarSlim(
                    label = "Entradas", value = receitaTotal, progress = animBarReceita,
                    color = Color(0xFF69F0AE), isPrivate = isPrivate, isLoading = !animationPlayed
                )

                // Barra 2: Poupado (Metas)
                HorizontalBalanceBarSlim(
                    label = "Poupado",
                    value = metasTotal,
                    progress = animBarMetas,
                    color = Color(0xFF00E676),
                    isPrivate = isPrivate,
                    isLoading = !animationPlayed // Verde um pouco mais forte
                )

                // Barra 3: Saídas
                HorizontalBalanceBarSlim(
                    label = "Saídas", value = despesaTotal, progress = animBarDespesa,
                    color = Color(0xFFEF5350), isPrivate = isPrivate, isLoading = !animationPlayed
                )

                TrendIndicator(
                    valorAtual = despesaTotal,
                    valorAnterior = despesaMesAnterior, // Você precisará passar esse valor via parâmetro
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

// =================================================
// SEÇÃO 3: DIREITA - Gráfico de Pizza Animado
// =================================================
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(80.dp) // Espaço reservado para o gráfico
                    .padding(start = 8.dp)
            ) {
                if (dadosGrafico.isNotEmpty()) {
                    PremiumPieChart(
                        dados = dadosGrafico,
                        modifier = Modifier.size(70.dp) // Tamanho compacto para o Card
                    )
                } else {
                    // Círculo vazio caso não haja despesas
                    Canvas(modifier = Modifier.size(65.dp)) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            style = Stroke(width = 20f)
                        )
                    }
                }
            }
        }
    }
}