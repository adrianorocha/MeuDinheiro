package com.meudinheiro.componentes

import android.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.ui.theme.NeonCyan

@Composable
fun AgendamentosDialog(
    agendamentos: List<TransferenciaAgendada>,
    isPrivate: Boolean,
    onDismiss: () -> Unit,
    onCancelar: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B)),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp) // Respiro lateral para o Dialog
                .heightIn(max = 550.dp, min = 200.dp) // Altura mínima para nunca ficar "achatado"
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // CABEÇALHO COM ÍCONE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday, // Ou Icons.Default.CalendarToday
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Agendamentos",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 🚀 LÓGICA DO "QUADRO VAZIO"
                if (agendamentos.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f) // Ocupa o espaço central
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Um ícone grande e apagadinho de fundo
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.EventNote, // Ou um drawable seu
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tudo limpo por aqui!",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Nenhuma transferência pendente.",
                            color = Color.White.copy(alpha = 0.2f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // LISTA DE AGENDAMENTOS (Só aparece se houver dados)
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(agendamentos, key = { it.id }) { agendamento ->
                            ItemResumoAgendamento(
                                agendamento = agendamento,
                                isPrivate = isPrivate,
                                onCancelar = onCancelar
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // BOTÃO FECHAR (Estilo Neon Glass)
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(0.05f)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(0.1f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Fechar",
                        color = NeonCyan,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}