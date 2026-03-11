package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.R
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen

@Composable
fun TransferenciaDialog(
    contas: List<ContaSaldoDomain>,
    contaOrigemInicial: String,
    onDismiss: () -> Unit,
    onConfirmar: (origem: String, destino: String, valor: Double, dataAgendada: Long?) -> Unit
) {
    var contaOrigem by remember { mutableStateOf(contaOrigemInicial) }
    var contaDestino by remember { mutableStateOf(contas.firstOrNull { it.conta != contaOrigem }?.conta ?: "") }
    var valorStr by remember { mutableStateOf("") }

    // Controle do Agendamento
    var agendar by remember { mutableStateOf(false) } // Controla o Switch
    var dataSelecionada by remember { mutableStateOf(System.currentTimeMillis()) } // O valor da data
    var mostrarCalendario by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        PremiumDialogCard {
            Text(
                "Transferência entre Contas",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ÁREA DE SELEÇÃO (DE -> PARA)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(0.2f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectorContaMini(label = "SAI DE", contaId = contaOrigem, contas = contas) {
                    contaOrigem = it
                }

                // Ícone de seta com animação de pulso (opcional)
                val resId = R.drawable.transferencia

                Icon(

                    painter = painterResource(id = resId), // Ou use Icons.Default.ArrowForward
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(28.dp)
                )

                SelectorContaMini(label = "ENTRA EM", contaId = contaDestino, contas = contas) {
                    contaDestino = it
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CAMPO DE VALOR
            PremiumTextField(
                value = valorStr,
                onValueChange = { valorStr = it },
                label = "Valor a transferir",
                prefix = { Text("R$ ", color = NeonGreen) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onClick = {}
            )

            // BOTÕES DE AÇÃO
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(0.2f))
                ) {
                    Text("Cancelar", color = Color.White)
                }
                Button(
                    onClick = {
                        val v = valorStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (v > 0) {
                            // Agora o compilador reconhece 'agendar' e 'dataSelecionada'
                            onConfirmar(
                                contaOrigem,
                                contaDestino,
                                v,
                                if (agendar) dataSelecionada else null
                            )
                        }
                    },                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Confirmar", color = Color(0xFF1B263B), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorContaMini(
    label: String,
    contaId: String,
    contas: List<ContaSaldoDomain>,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    // Encontra a conta atual para pegar o ícone e o nome do banco
    val contaAtual = contas.find { it.conta == contaId }

    // Resolve o ícone da conta selecionada
    val resId = remember(contaAtual?.pic) {
        if (contaAtual != null) {
            val id = context.resources.getIdentifier(contaAtual.pic, "drawable", context.packageName)
            if (id != 0) id else com.meudinheiro.R.drawable.sim_chip_2
        } else com.meudinheiro.R.drawable.sim_chip_2
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(130.dp) // Largura fixa para manter o alinhamento
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(0.5f),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box {
            // Card do Seletor
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = contaAtual?.banco ?: "Selecionar",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Menu de Seleção (Dropdown)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF1B263B))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
            ) {
                contas.forEach { conta ->
                    val itemResId = context.resources.getIdentifier(conta.pic, "drawable", context.packageName)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(if (itemResId != 0) itemResId else com.meudinheiro.R.drawable.sim_chip_2),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(conta.banco, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("CC ${conta.conta}", color = Color.White.copy(0.5f), fontSize = 11.sp)
                                }
                            }
                        },
                        onClick = {
                            onSelect(conta.conta)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
