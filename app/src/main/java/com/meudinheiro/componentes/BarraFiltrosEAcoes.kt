package com.meudinheiro.componentes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory

@Composable
fun BarraFiltrosEAcoes(
    filtroAtual: FiltroPeriodo, // 👈 Recebe o estado de fora
    onFiltroSelected: (FiltroPeriodo) -> Unit, // 👈 Callback para o filtro
    onEvolucaoPatrimonial: () -> Unit,
    onSaudeFinanceiro: () -> Unit,
    onPreviaoMes: () -> Unit,
    onTransacoesAgendadas: () -> Unit,
    modifier: Modifier = Modifier // 👈 Boa prática: permitir modificadores externos
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GRUPO DA ESQUERDA: Filtros (Chips)
        // Usamos um Row aqui para garantir que os chips fiquem alinhados
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeletorPeriodo(
                filtroSelecionado = filtroAtual,
                onFiltroSelected = {onFiltroSelected(it)}
            )
        }

        // GRUPO DA DIREITA: Ações Consolidadas
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconeAcaoSuperior(
                icone = Icons.AutoMirrored.Filled.TrendingUp,
                cor = NeonCyan,
                tooltip = "Evolução",
                onClick = onEvolucaoPatrimonial
            )

            IconeAcaoSuperior(
                icone = Icons.Default.ReceiptLong,
                cor = Color(0xFF69F0AE),
                tooltip = "Saúde",
                onClick = onSaudeFinanceiro
            )

            IconeAcaoSuperior(
                icone = Icons.Default.AutoAwesome,
                cor = Color(0xFFCE93D8),
                tooltip = "Insights",
                onClick = onPreviaoMes
            )

            IconeAcaoSuperior(
                icone = Icons.Default.BarChart,
                cor = NeonCyan,
                tooltip = "Agendados",
                onClick = onTransacoesAgendadas
            )
        }
    }
}

@Composable
fun IconeAcaoSuperior(
    icone: ImageVector,
    cor: Color,
    tooltip: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp) // Aumentei levemente para melhorar o touch target
            .background(Color(0xFF1B263B), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)) // Garante que o ripple não saia do shape
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
}