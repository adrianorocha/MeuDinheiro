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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed

@Composable
fun RelatorioSaudeDialog(
    receitaAtual: Double,
    despesaAtual: Double,
    despesaAnterior: Double,
    isPrivate: Boolean,
    onDismiss: () -> Unit
) {
    // --- LÓGICA E MATEMÁTICA ---
    val variacaoGastos = if (despesaAnterior > 0) {
        ((despesaAtual - despesaAnterior) / despesaAnterior) * 100
    } else {
        0.0
    }

    val consumoReceita = if (receitaAtual > 0) (despesaAtual / receitaAtual).toFloat() else 0f
    val sobra = receitaAtual - despesaAtual

    // Definição do "Status" e Cores
    val (statusCor, statusIcone, statusTexto, statusMensagem) = when {
        consumoReceita >= 0.9f -> listOf(
            NeonRed, Icons.Default.WarningAmber, "PERIGO", "Quase no limite!"
        )

        consumoReceita >= 0.7f -> listOf(
            NeonOrange, Icons.AutoMirrored.Filled.TrendingUp, "ALERTA", "Atenção aos extras."
        )

        else -> listOf(
            NeonGreen, Icons.AutoMirrored.Filled.TrendingDown, "SAUDÁVEL", "Finanças sob controle."
        )
    }

    // Garantindo o tipo Color para o statusCor
    val corAtual = statusCor as Color

    // Animação da barra de progresso
    var startAnimation by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (startAnimation) consumoReceita.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "progress_anim"
    )

    LaunchedEffect(Unit) { startAnimation = true }

    // --- UI PREMIUM ---
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(2.dp, corAtual.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131E29)) // Fundo escuro profundo
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // 1. HEADER: Ícone + Título + Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Raio-X Financeiro",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }

                    // CHIP DE STATUS
                    Surface(
                        color = corAtual.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, corAtual.copy(alpha = 0.5f))
                    ) {
                        Text(
                            statusTexto.toString(),
                            color = corAtual,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // 2. TEXTO DE COMPARAÇÃO
                val gastouMais = variacaoGastos > 0
                val textoVariacao = if (variacaoGastos == 0.0) "Gastos iguais ao mês passado"
                else "Você gastou ${
                    String.format(
                        "%.1f",
                        Math.abs(variacaoGastos)
                    )
                }% ${if (gastouMais) "a mais" else "a menos"}."

                Text(
                    text = textoVariacao,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(32.dp))

                // 3. BARRA DE CONSUMO DE RECEITA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Consumo da Receita",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Track da Barra
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    // Barra Animada com Brilho/Sombra Neon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(8.dp)
                            .shadow(
                                if (animatedProgress > 0.05f) 6.dp else 0.dp,
                                CircleShape,
                                ambientColor = corAtual,
                                spotColor = corAtual
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(NeonCyan, corAtual)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // 4. INSIGHT FINAL / SOBRA PREVISTA
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.02f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                statusIcone as androidx.compose.ui.graphics.vector.ImageVector,
                                contentDescription = null,
                                tint = corAtual,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                statusMensagem.toString(),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Sobra Prev.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = formatarMoedaBR(sobra, isPrivate),
                                color = if (isPrivate) Color.Gray else corAtual,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 5. BOTÃO FECHAR
                TextButton(
                    onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "FECHAR",
                        color = Color.White.copy(alpha = 0.3f),
                        letterSpacing = 2.sp,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}