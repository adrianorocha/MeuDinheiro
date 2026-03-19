package com.meudinheiro.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed

// Cores locais de apoio (ajuste se já tiver no seu tema)
/*val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF69F0AE)
val NeonRed = Color(0xFFFF5252)
val NeonOrange = Color(0xFFFFB74D)*/

@Composable
fun RelatorioSaudeFinanceiraCard(
    receitaAtual: Double,
    despesaAtual: Double,
    despesaAnterior: Double,
    isPrivate: Boolean
) {
    // --- LÓGICA E MATEMÁTICA ---
    // 1. Variação de Gastos (Mês atual vs Passado)
    val variacaoGastos = if (despesaAnterior > 0) {
        ((despesaAtual - despesaAnterior) / despesaAnterior) * 100
    } else {
        0.0
    }

    // 2. Nível de Consumo da Receita (Burn Rate)
    val consumoReceita = if (receitaAtual > 0) (despesaAtual / receitaAtual).toFloat() else 0f

    // Animação da barra de progresso
    var startAnimation by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (startAnimation) consumoReceita.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progress_anim"
    )

    LaunchedEffect(Unit) { startAnimation = true }

    // 3. Definição do "Status"
    val (statusCor, statusIcone, statusTexto, statusMensagem) = when {
        consumoReceita >= 0.9f -> listOf(NeonRed, Icons.Default.WarningAmber, "PERIGO", "Quase no limite da receita!")
        consumoReceita >= 0.7f -> listOf(NeonOrange, Icons.Default.TrendingUp, "ALERTA", "Atenção aos gastos extras.")
        else -> listOf(NeonGreen, Icons.Default.TrendingDown, "SAUDÁVEL", "Finanças sob controle.")
    }

    // --- UI PREMIUM ---
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B).copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, (statusCor as Color).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // HEADER DO RELATÓRIO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoGraph, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Raio-X Financeiro", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                // CHIP DE STATUS
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusCor.copy(alpha = 0.15f))
                        .border(1.dp, statusCor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(statusTexto.toString(), color = statusCor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // COMPARAÇÃO COM MÊS ANTERIOR
            val gastouMais = variacaoGastos > 0
            val corVariacao = if (gastouMais) NeonRed else NeonGreen
            val textoVariacao = if (variacaoGastos == 0.0) "Gastos iguais ao mês passado"
            else "Você gastou ${String.format("%.1f", Math.abs(variacaoGastos))}% ${if (gastouMais) "a mais" else "a menos"} que no mês passado."

            Text(
                text = textoVariacao,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // BARRA DE CONSUMO DE RECEITA (O Show Visual)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Consumo da Receita", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // A Barra Animada
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonCyan, statusCor)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // INSIGHT FINAL / SOBRA PREVISTA
            val sobra = receitaAtual - despesaAtual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcone as androidx.compose.ui.graphics.vector.ImageVector, contentDescription = null, tint = statusCor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(statusMensagem.toString(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Sobra Prevista", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                    Text(
                        text = formatarMoedaBR(sobra, isPrivate), // Respeita a privacidade!
                        color = if (isPrivate) Color.Gray else if (sobra >= 0) NeonGreen else NeonRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}