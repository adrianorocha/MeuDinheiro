package com.meudinheiro.componentes

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.PatrimonioPonto
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.*

@Composable
fun PatrimonioHistoricoDialog(
    historico: List<PatrimonioHistorico>,
    isPrivate: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(2.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131E29))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Evolução Patrimonial", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                Spacer(Modifier.height(32.dp))

                // Gráfico Customizado (Canvas)
                if (historico.size < 2) {
                    Box(Modifier.height(150.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Dados insuficientes para gerar o gráfico", color = Color.White.copy(0.3f), fontSize = 12.sp)
                    }
                } else {
                    // Aqui entra o componente EvolucaoPatrimonialChart que criamos antes
                    EvolucaoPatrimonialChart(
                        pontos = historico.map { PatrimonioPonto(it.mesReferencia, it.valorTotal) },
                        isPrivate = isPrivate,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Legenda de Valor Atual
                val ultimoValor = historico.lastOrNull()?.valorTotal ?: 0.0
                Surface(
                    color = Color.White.copy(0.02f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Patrimônio Atual", color = Color.White.copy(0.5f), fontSize = 13.sp)
                        Text(
                            text = formatarMoedaBR(ultimoValor, isPrivate),
                            color = NeonCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("VOLTAR", color = Color.White.copy(0.3f), letterSpacing = 2.sp)
                }
            }
        }
    }
}