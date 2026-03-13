package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.ui.theme.NeonCyan
import androidx.compose.foundation.lazy.items

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
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp) // Limite de altura elegante
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // CABEÇALHO DA TELA FLUTUANTE
                Text(
                    text = "Transferências Agendadas",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // LISTA DE AGENDAMENTOS (Com rolagem nativa)
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false), // Permite rolar se tiver muitos, mas encolhe se tiver poucos
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(agendamentos, key = { it.id }) { agendamento ->
                        ItemResumoAgendamento(
                            agendamento = agendamento,
                            isPrivate = isPrivate,
                            onCancelar = onCancelar
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // BOTÃO FECHAR
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}