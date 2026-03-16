package com.meudinheiro.componentes

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.funcoes.formatarMoedaBR

@Composable
fun PrevisaoFechamentoDialog(
    saldoAtual: Double,
    contasAVencer: Double,
    isPrivate: Boolean,
    onDismiss: () -> Unit
) {
    val saldoFinalPrevisto = saldoAtual - contasAVencer
    val margemSeguranca = if (saldoAtual > 0) (saldoFinalPrevisto / saldoAtual).toFloat() else 0f

    // Motor de Cores Inteligente
    val (alertColor, statusTexto) = when {
        margemSeguranca > 0.4f -> Color(0xFF69F0AE) to "Mês Seguro" // Verde Neon
        margemSeguranca > 0.05f -> Color(0xFFFFB74D) to "Atenção ao Caixa" // Laranja Neon
        else -> Color(0xFFFF5252) to "Alerta de Risco!" // Vermelho Neon
    }
            Dialog(onDismissRequest = onDismiss) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .border(2.dp, alertColor.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131E29))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Insights,
                                null,
                                tint = alertColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Previsão do Mês",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        // Bloco de Matemática
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Saldo em Conta", color = Color.White.copy(0.5f), fontSize = 13.sp)
                            Text(
                                formatarMoedaBR(saldoAtual, isPrivate),
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Contas Pendentes",
                                color = Color.White.copy(0.5f),
                                fontSize = 13.sp
                            )
                            Text(
                                "- ${formatarMoedaBR(contasAVencer, isPrivate)}",
                                color = Color(0xFFFF5252),
                                fontSize = 13.sp
                            )
                        }

                        Divider(
                            color = Color.White.copy(0.1f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        // O GRANDE RESULTADO
                        Text(
                            "Saldo Livre Previsto",
                            color = Color.White.copy(0.6f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatarMoedaBR(saldoFinalPrevisto, isPrivate),
                            color = alertColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(Modifier.height(24.dp))

                        // Barra de Estresse
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Margem de Segurança",
                                color = Color.White.copy(0.5f),
                                fontSize = 12.sp
                            )
                            Text(
                                statusTexto,
                                color = alertColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = margemSeguranca.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = alertColor,
                            trackColor = Color.Black.copy(0.3f)
                        )

                        Spacer(Modifier.height(32.dp))

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("FECHAR", color = Color.White.copy(0.3f), letterSpacing = 2.sp)
                        }
                    }
                }
            }
        }