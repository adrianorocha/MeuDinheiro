package com.meudinheiro.componentes

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.ContaSaldoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecaoAgendamentosAtivos(viewModel: ContaSaldoViewModel) {
    val agendamentos by viewModel.agendamentosAtivos.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .animateContentSize()
    ) {
        // Título da Secção
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Transferências Agendadas",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (agendamentos.isEmpty()) {
            // Estado Vazio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B263B).copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum agendamento futuro.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            }
        } else {
            // Lista de Agendamentos
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                agendamentos.forEach { agendamento ->
                    ItemAgendamento(
                        agendamento = agendamento,
                        onCancelar = { viewModel.cancelarAgendamento(agendamento.id, context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemAgendamento(
    agendamento: TransferenciaAgendada,
    onCancelar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Rota do Dinheiro
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(agendamento.contaOrigem, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 8.dp).size(16.dp)
                    )
                    Text(agendamento.contaDestino, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Data Formatada
                val dataFormatada = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date(agendamento.dataAgendada))
                Text(dataFormatada, color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            // Valor e Botão de Cancelar
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatarMoedaBR(agendamento.valor, false ),
                    color = Color(0xFF69F0AE),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Botão de Cancelar Elegante
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFFF8A80).copy(alpha = 0.15f))
                        .clickable { onCancelar() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Cancelar", tint = Color(0xFFFF8A80), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancelar", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}