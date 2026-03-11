package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.formatarMoedaBR

@Composable
fun NotificacaoRendimentoCard(
    rendimentoNoMes: Double,
    isPrivate: Boolean
) {
    // Estado para controlar se o utilizador já fechou a notificação nesta sessão
    var isVisible by remember { mutableStateOf(true) }

    // Só aparece se houver lucro e se o utilizador não a tiver fechado
    AnimatedVisibility(
        visible = isVisible && rendimentoNoMes > 0,
        enter = expandVertically(animationSpec = tween(600)),
        exit = shrinkVertically(animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1B263B), // Fundo azul escuro da Blu Macaw
                            Color(0xFF0D1B2A)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF69F0AE).copy(alpha = 0.3f), // Borda Neon Verde subtil
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Ícone de Crescimento
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF69F0AE).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF69F0AE))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Textos da Notificação
                    Column {
                        Text(
                            text = "O seu dinheiro está a trabalhar!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isPrivate) "Rendimentos ocultos" else "Lucrou +${formatarMoedaBR(rendimentoNoMes, false)} com os seus ativos.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Botão de Fechar (X)
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar notificação",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isVisible = false } // Esconde o card com animação
                        .padding(4.dp)
                )
            }
        }
    }
}