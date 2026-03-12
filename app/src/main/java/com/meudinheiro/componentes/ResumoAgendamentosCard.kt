package com.meudinheiro.componentes

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResumoAgendamentosCard(
    agendamentos: List<TransferenciaAgendada>,
    isPrivate: Boolean,
    onCancelar: (Int) -> Unit
) {
    if (agendamentos.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B).copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, Color.White.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // CABEÇALHO FIXO DO CARD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Próximos Agendamentos",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NeonCyan.copy(0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${agendamentos.size}",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ÁREA ROLÁVEL (A Mágica acontece aqui!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp) // Limita o tamanho para não empurrar as abas para fora da tela
                    .verticalScroll(rememberScrollState()) // Faz a tela rolar apenas aqui dentro!
            ) {
                agendamentos.forEach { agendamento ->
                    ItemResumoAgendamento(
                        agendamento = agendamento,
                        isPrivate = isPrivate,
                        onCancelar = onCancelar
                    )
                }
            }
        }
    }
}
@Composable
fun ItemResumoAgendamento(
    agendamento: TransferenciaAgendada,
    isPrivate: Boolean, // <-- INCLUÍDO AQUI
    onCancelar: (Int) -> Unit
) {
    val formatador = SimpleDateFormat("dd/MM", Locale("pt", "BR"))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.03f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ícone Calendário
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${agendamento.contaOrigem} ➔ ${agendamento.contaDestino}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatador.format(Date(agendamento.dataAgendada)),
                color = Color.White.copy(0.5f),
                fontSize = 11.sp
            )
        }

        // VALOR PROTEGIDO PELO MODO PRIVACIDADE
        Text(
            text = formatarMoedaBR(agendamento.valor, isPrivate), // <-- AGORA RESPEITA O OLHINHO
            color = if (isPrivate) Color.Gray else NeonGreen, // Muda a cor se estiver censurado para ficar mais discreto
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        IconButton(onClick = { onCancelar(agendamento.id) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Excluir",
                tint = Color.White.copy(0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}