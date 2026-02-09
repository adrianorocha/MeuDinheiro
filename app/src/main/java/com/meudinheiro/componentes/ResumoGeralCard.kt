package com.meudinheiro.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.formatarMoedaBR

@Composable
fun ResumoGeralCard(
    receitaTotal: Double,
    despesaTotal: Double,
    isPrivate: Boolean = false
) {
    val saldoTotal = receitaTotal - despesaTotal

    // Proporção para o gráfico
    val totalFinanceiro = receitaTotal + despesaTotal
    // Calculamos o ângulo da despesa em relação ao total (máximo 360 graus)
    val sweepDespesa = if (totalFinanceiro > 0) (despesaTotal / totalFinanceiro).toFloat() * 360f else 0f

    val animSweepDespesa by animateFloatAsState(
        targetValue = sweepDespesa,
        animationSpec = tween(durationMillis = 1000),
        label = "animDespesa"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp) // Padding externo mínimo
            .height(85.dp), // Altura compacta fixa
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E).copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp), // Padding interno reduzido
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- LADO ESQUERDO: Gráfico de Donut Compacto ---
            Box(
                modifier = Modifier.size(60.dp), // Tamanho reduzido
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(50.dp)) {
                    val espessura = 6.dp.toPx() // Traço mais fino

                    // Fundo/Receita (Verde)
                    drawArc(
                        color = Color(0xFF69F0AE).copy(alpha = 0.3f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = espessura, cap = StrokeCap.Round)
                    )

                    // Saídas (Vermelho) - Sobrepõe
                    drawArc(
                        color = Color(0xFFEF5350),
                        startAngle = -90f,
                        sweepAngle = animSweepDespesa,
                        useCenter = false,
                        style = Stroke(width = espessura, cap = StrokeCap.Round)
                    )
                }

                // Porcentagem no centro (somente se não for privado)
                if (!isPrivate) {
                    val porcentagem = if (totalFinanceiro > 0) (despesaTotal / totalFinanceiro * 100).toInt() else 0
                    Text(
                        text = "$porcentagem%",
                        fontSize = 10.sp,
                        color = Color.White.copy(0.7f),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    // Ícone de cadeado ou asteriscos se for privado
                    Text(
                        text = "**",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // --- LADO DIREITO: Info Compacta ---
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Patrimônio Líquido",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )

                Text(
                    text = formatarMoedaBR(saldoTotal, isPrivate),
                    style = MaterialTheme.typography.titleMedium.copy( // Fonte média em vez de Large
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = if (saldoTotal >= 0) Color.White else Color(0xFFEF5350),
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Legendas de Entradas/Saídas em linha única
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IndicatorDot(Color(0xFF69F0AE))
                    Text(
                        text = formatarMoedaBR(receitaTotal, isPrivate),
                        fontSize = 10.sp,
                        color = Color.White.copy(0.8f),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    IndicatorDot(Color(0xFFEF5350))
                    Text(
                        text = formatarMoedaBR(despesaTotal, isPrivate),
                        fontSize = 10.sp,
                        color = Color.White.copy(0.8f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(color, RoundedCornerShape(2.dp))
    )
}