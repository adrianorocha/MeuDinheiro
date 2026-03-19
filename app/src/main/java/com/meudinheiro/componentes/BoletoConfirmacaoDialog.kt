package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun BoletoConfirmacaoDialog(
    codigoLido: String,
    valorExtraido: Double?, // A IA tenta descobrir o valor dentro do código
    onConfirmar: (Double) -> Unit,
    onDescartar: () -> Unit
) {
    Dialog(onDismissRequest = onDescartar) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131E29)),
            border = BorderStroke(2.dp, Color(0xFF69F0AE).copy(alpha = 0.5f)) // Borda NeonGreen
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Ícone de Sucesso animado/destacado
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF69F0AE),
                    modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Boleto Identificado",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(24.dp))

                // Código lido (quebra a linha bonitinho)
                Surface(
                    color = Color.Black.copy(0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = codigoLido,
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Se a IA conseguiu ler o valor do código
                if (valorExtraido != null) {
                    Text("Valor Sugerido", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    Text(
                        text = "R$ ${String.format("%.2f", valorExtraido)}",
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                } else {
                    Text("Valor não identificado no código. Você poderá digitar a seguir.", color = Color(0xFFFFB74D), fontSize = 12.sp)
                }

                Spacer(Modifier.height(32.dp))

                // Botões de Ação
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDescartar,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(0.2f))
                    ) {
                        Text("Descartar")
                    }
                    Button(
                        onClick = { onConfirmar(valorExtraido ?: 0.0) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text("Avançar", color = Color(0xFF131E29), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}