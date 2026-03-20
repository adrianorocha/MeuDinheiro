package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.MetaPremium
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.*

@Composable
fun SecaoCofresMetas(
    metas: List<MetaPremium>,
    isPrivate: Boolean,
    userName: String,
    onMetaLongClick: (MetaPremium) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Título da Seção (Personalizado e Tech)
        Text(
            text = "Galáxia de Metas de $userName",
            color = Color.White.copy(0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Linha Rolável de Orbes
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp) // Respiro lateral
        ) {
            items(metas, key = { it.id }) { meta ->
                // CARD INDIVIDUAL DO COFRE (Com Efeito Glassmorphism sutil)
                Card(
                    modifier = Modifier
                        .width(140.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardGlass.copy(alpha = 0.6f)), // Vidro sutil
                    border = BorderStroke(1.dp, Color.White.copy(0.03f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. O Orbe Holográfico (A estrela do show)
                        OrbeHolograficoMeta(
                            meta = meta,
                            isPrivate = isPrivate,
                            onLongClick = { onMetaLongClick(meta) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Detalhes da Meta
                        Text(
                            text = meta.nome,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (isPrivate) "****" else formatarMoedaBR(meta.valorPoupado, false),
                            color = if (meta.concluida) NeonCyan else Color.White.copy(0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "alvo: ${formatarMoedaBR(meta.valorAlvo, isPrivate)}",
                            color = Color.White.copy(0.4f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}