package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiversificacaoCard(distribuicao: List<Pair<String, Double>>) {
    // Mapa de cores neon para cada tipo
    val cores = mapOf(
        "Renda Fixa" to Color(0xFF00E5FF), // Ciano
        "Ações" to Color(0xFF69F0AE),      // Verde
        "FIIs" to Color(0xFFFFD54F),       // Amarelo
        "Cripto" to Color(0xFFE040FB)      // Roxo/Rosa
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF263248)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Distribuição da Carteira",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // BARRA SEGMENTADA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.05f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    distribuicao.forEach { (tipo, percentual) ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((percentual.toFloat() / 100f))
                                .background(cores[tipo] ?: Color.Gray)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LEGENDA EM GRID
            // Usamos FlowRow ou um simples Row/Column para as legendas
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                distribuicao.chunked(2).forEach { par ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        par.forEach { (tipo, percentual) ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(cores[tipo] ?: Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$tipo: ${"%.1f".format(percentual)}%",
                                    color = Color.White.copy(0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}