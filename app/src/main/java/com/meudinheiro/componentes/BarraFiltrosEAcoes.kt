package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.ui.theme.NeonCyan

@Composable
fun BarraFiltrosEAcoes(
    filtroAtual: FiltroPeriodo,
    onFiltroSelected: (FiltroPeriodo) -> Unit,
    onEvolucaoPatrimonial: () -> Unit,
    onSaudeFinanceiro: () -> Unit,
    onPreviaoMes: () -> Unit,
    onTransacoesAgendadas: () -> Unit,
    agendados: List<TransferenciaAgendada> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()), // Permite deslizar os chips
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeletorPeriodo(
                filtroSelecionado = filtroAtual,
                onFiltroSelected = { onFiltroSelected(it) }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        // GRUPO DA DIREITA: Ações Consolidadas
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconeAcaoSuperior(
                icone = Icons.AutoMirrored.Filled.ShowChart,
                cor = NeonCyan,
                tooltip = "Evolução",
                onClick = onEvolucaoPatrimonial
            )

            IconeAcaoSuperior(
                icone = Icons.AutoMirrored.Filled.ReceiptLong,
                cor = Color(0xFF69F0AE),
                tooltip = "Saúde",
                onClick = onSaudeFinanceiro
            )

            IconeAcaoSuperior(
                icone = Icons.Default.AutoGraph,
                cor = Color(0xFFCE93D8),
                tooltip = "Insights",
                onClick = onPreviaoMes
            )

            IconeAcaoSuperior(
                icone = Icons.Default.CalendarToday,
                cor = NeonCyan,
                tooltip = "Agendados",
                onClick = onTransacoesAgendadas,
                agendados = agendados

            )
        }
    }
}

@Composable
fun IconeAcaoSuperior(
    agendados: List<TransferenciaAgendada> = emptyList(), // Mantive sua lista
    icone: ImageVector,
    cor: Color,
    tooltip: String,
    onClick: () -> Unit
) {
    // 1️⃣ BOX PAI INVISÍVEL: Não tem fundo nem clip. Só serve de âncora.
    Box(
        modifier = Modifier.wrapContentSize(),
        contentAlignment = Alignment.Center // Centraliza o botão dentro da área
    ) {

        // 2️⃣ O BOTÃO REAL (Escuro e clicável)
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Color(0xFF1B263B), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icone,
                contentDescription = tooltip,
                tint = cor,
                modifier = Modifier.size(20.dp)
            )
        }

        // 3️⃣ A BOLINHA DE NOTIFICAÇÃO (Por cima de tudo)
        if (agendados.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd) // Joga para o canto superior direito do Box Pai
                    .offset(
                        x = 4.dp,
                        y = (-4).dp
                    ) // Puxa ela um pouco para fora para o efeito "Premium"
                    .size(16.dp)
                    // 💡 Dica: background com CircleShape já faz o clip automático!
                    .background(Color.Red, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${agendados.size}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}