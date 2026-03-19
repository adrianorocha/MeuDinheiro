package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.ui.theme.NeonCyan

@Composable
fun BarraAcoesRapidas(
    onNovoGasto: () -> Unit,
    onNovoInvestimento: () -> Unit,
    onTransferencia: () -> Unit,
    onScanBoleto: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Usamos um Card ou Surface para dar um "corpo" à barra
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent // Ou uma cor levemente mais clara que o fundo
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, // Distribui igual na largura
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { BotaoAcaoCompacto(Icons.Default.Payments, "Despesa", Color(0xFF69F0AE), onNovoGasto) }
            item { BotaoAcaoCompacto(Icons.Default.QrCodeScanner, "Escanear", NeonCyan, onScanBoleto) }
            item { BotaoAcaoCompacto(Icons.AutoMirrored.Filled.TrendingUp, "Investir", Color(0xFF12E7FF), onNovoInvestimento) }
            item { BotaoAcaoCompacto(Icons.AutoMirrored.Filled.CompareArrows, "Transferir", Color(0xFFEF7354), onTransferencia) }
        }
    }
}

@Composable
private fun BotaoAcaoCompacto(
    icone: ImageVector,
    texto: String,
    cor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp) // Reduzi de 56 para 48 para ser mais discreto
                .background(cor.copy(alpha = 0.15f), CircleShape) // Fundo suave com a cor do ícone
                .border(1.dp, cor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(texto, color = Color.White.copy(0.7f), fontSize = 10.sp)
    }
}