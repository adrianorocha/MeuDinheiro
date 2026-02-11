package com.meudinheiro.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.formatarMoedaBR

@Composable
fun ResumoGeralCard(
    receitaTotal: Double,
    despesaTotal: Double,
    isPrivate: Boolean = false
) {
    val saldoTotal = receitaTotal - despesaTotal
    val totalFinanceiro = receitaTotal + despesaTotal

    // Proporção para o Donut (Círculo)
    val targetSweepDespesa = if (totalFinanceiro > 0) (despesaTotal / totalFinanceiro).toFloat() * 360f else 0f

    // Proporções para as Barras Horizontais (O maior valor ocupa 100% da largura)
    val maxVal = maxOf(receitaTotal, despesaTotal, 1.0)
    val propReceita = (receitaTotal / maxVal).toFloat()
    val propDespesa = (despesaTotal / maxVal).toFloat()

    var animationPlayed by remember { mutableStateOf(false) }

    // Animação do Donut
    val animSweep by animateFloatAsState(
        targetValue = if (animationPlayed) targetSweepDespesa else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "donut"
    )

    // Animações das Barras Horizontais
    val animBarReceita by animateFloatAsState(
        targetValue = if (animationPlayed) propReceita else 0f,
        animationSpec = tween(1000, 200),
        label = "barR"
    )
    val animBarDespesa by animateFloatAsState(
        targetValue = if (animationPlayed) propDespesa else 0f,
        animationSpec = tween(1000, 400),
        label = "barD"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(110.dp), // Altura otimizada para integrar as barras
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E).copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- LADO ESQUERDO: Gráfico Donut Animado ---
            Box(
                modifier = Modifier.size(70.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(60.dp)) {
                    val stroke = 7.dp.toPx()

                    // Fundo Verde (Receita/Total)
                    drawArc(
                        color = Color(0xFF69F0AE).copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    // Saídas (Vermelho) com Gradiente
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFFFF8A80), Color(0xFFEF5350)),
                            center = center
                        ),
                        startAngle = -90f,
                        sweepAngle = animSweep,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                // Texto de porcentagem central (Contador Animado)
                val percent = if (totalFinanceiro > 0) ((animSweep / 360f) * 100).toInt() else 0
                Text(
                    text = if (isPrivate) "**" else "$percent%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // --- LADO DIREITO: Info e Balanço de Barras ---
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = "Patrimônio Líquido",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.6f)
                )
                Text(
                    text = formatarMoedaBR(saldoTotal, isPrivate),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (saldoTotal >= 0) Color.White else Color(0xFFEF5350)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Barra Horizontal: Entradas
                HorizontalBalanceBar(
                    label = "Entradas",
                    value = receitaTotal,
                    progress = animBarReceita,
                    color = Color(0xFF69F0AE),
                    isPrivate = isPrivate
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Barra Horizontal: Saídas
                HorizontalBalanceBar(
                    label = "Saídas",
                    value = despesaTotal,
                    progress = animBarDespesa,
                    color = Color(0xFFEF5350),
                    isPrivate = isPrivate
                )
            }
        }
    }
}

@Composable
private fun HorizontalBalanceBar(
    label: String,
    value: Double,
    progress: Float,
    color: Color,
    isPrivate: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f))
            Text(formatarMoedaBR(value, isPrivate), fontSize = 9.sp, color = Color.White.copy(0.8f))
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(0.05f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

// Componente para pontos indicadores (se ainda usar em outros lugares)
@Composable
private fun IndicatorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(color, RoundedCornerShape(2.dp))
    )
}

// --- Gráfico de Pizza (Distribuicao de Gastos) mantido e organizado ---
@Composable
fun PremiumPieChart(
    despesas: List<DespesasDomain>,
    isPrivate: Boolean = false
) {
    val gastosPorCategoria = despesas
        .filter { it.tipo == TipoDespesa.DEBITO }
        .groupBy { it.categoria }
        .mapValues { it.value.sumOf { d -> d.valor } }

    val totalGeral = gastosPorCategoria.values.sum()
    val listaCores = listOf(
        Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFFFD54F),
        Color(0xFFFF8A80), Color(0xFFB388FF), Color(0xFF80D8FF)
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Distribuição de Gastos", style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(180.dp)) {
                var startAngle = -90f
                gastosPorCategoria.entries.forEachIndexed { index, entry ->
                    val sweepAngle = (entry.value / totalGeral).toFloat() * 360f
                    drawArc(color = listaCores[index % listaCores.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true)
                    startAngle += sweepAngle
                }
                drawCircle(color = PremiumDarkBlue, radius = size.minDimension / 4)
            }
        }

        Spacer(Modifier.height(24.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = 3
        ) {
            gastosPorCategoria.entries.forEachIndexed { index, entry ->
                ChartLegendItem(color = listaCores[index % listaCores.size], label = entry.key, value = entry.value, isPrivate = isPrivate)
            }
        }
    }
}

@Composable
fun ChartLegendItem(color: Color, label: String, value: Double, isPrivate: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextWhite.copy(0.6f))
            Text(formatarMoedaBR(value, isPrivate), fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.Bold)
        }
    }
}