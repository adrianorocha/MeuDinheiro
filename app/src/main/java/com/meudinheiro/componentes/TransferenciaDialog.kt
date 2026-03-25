package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransferenciaDialog(
    contas: List<ContaSaldoDomain>,
    contaOrigemInicial: String,
    onDismiss: () -> Unit,
    onConfirmar: (origem: String, destino: String, valor: Double, dataAgendada: Long?) -> Unit
) {
    val context = LocalContext.current // Se estiver num Composable

    // ESTADOS
    var contaOrigem by remember { mutableStateOf(contaOrigemInicial) }
    var contaDestino by remember { mutableStateOf("") }
    var valorStr by remember { mutableStateOf("") }

    // Controle do Agendamento
    var agendar by remember { mutableStateOf(false) }
    var dataSelecionada by remember { mutableStateOf(System.currentTimeMillis()) }
    var mostrarCalendario by remember { mutableStateOf(false) }

    // O PULO DO GATO: Observa a lista de contas. Quando ela carregar, preenche os campos!
    LaunchedEffect(contas) {
        if (contas.isNotEmpty()) {
            // Se a origem estiver vazia, pega a primeira conta (ou a inicial se existir)
            if (contaOrigem.isEmpty() || contas.none { it.conta == contaOrigem }) {
                contaOrigem = contaOrigemInicial.ifEmpty { contas.first().conta }
            }
            // A destino pega a primeira que for diferente da origem
            if (contaDestino.isEmpty() || contaDestino == contaOrigem) {
                contaDestino = contas.firstOrNull { it.conta != contaOrigem }?.conta ?: contas.last().conta
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        PremiumDialogCard {
            if(contas.isEmpty()) {
                // --- ESTADO VAZIO: DESIGN PREMIUM ---
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.warning), // Use um ícone de alerta
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nenhuma conta encontrada",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Cadastre suas contas na aba principal antes de realizar transferências.",
                        color = Color.White.copy(0.6f),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Entendido", color = Color(0xFF1B263B), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "Transferência entre Contas",
                    style = MaterialTheme.typography.titleLarge, // Aumentado para dar mais destaque
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // ÁREA DE SELEÇÃO (DE -> PARA) Premium
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101828)) // Fundo bem escuro para contraste
                        .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectorContaMini(
                            label = "SAI DE",
                            contaId = contaOrigem,
                            contas = contas,
                            modifier = Modifier.weight(1f)
                        ) {
                            contaOrigem = it
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        SelectorContaMini(
                            label = "ENTRA EM",
                            contaId = contaDestino,
                            contas = contas,
                            modifier = Modifier.weight(1f)
                        ) {
                            contaDestino = it
                        }
                    }

                    // Ícone flutuante centralizado sobre a Row
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B263B))
                            .border(1.dp, NeonCyan.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.transferencia),
                            contentDescription = "Seta Transferência",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // CAMPO DE VALOR
                PremiumTextField(
                    value = valorStr,
                    onValueChange = { valorStr = it },
                    label = "Valor a transferir",
                    prefix = { Text("R$ ", color = NeonGreen, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onClick = {}
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- SEÇÃO DE AGENDAMENTO (Estilo Glassmorphism) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF101828).copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Agendar transferência?",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = agendar,
                            onCheckedChange = { agendar = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonCyan.copy(0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    AnimatedVisibility(visible = agendar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1B263B))
                                .clickable { mostrarCalendario = true }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val formatador =
                                SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("pt", "BR"))
                            Text(
                                text = formatador.format(Date(dataSelecionada)),
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // BOTÕES DE AÇÃO
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancelar", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val v = valorStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (v > 0 && contaOrigem.isNotEmpty() && contaDestino.isNotEmpty()) {
                                onConfirmar(
                                    contaOrigem.trim(),
                                    contaDestino.trim(),
                                    v,
                                    if (agendar) dataSelecionada else null
                                )

                                val inputData = androidx.work.workDataOf(
                                    "TIPO_WORK" to  if(agendar) "DIARIO" else "IMEDIATO",
                                    "VALOR" to v,
                                    "DESTINO" to contaDestino.trim()
                                )

                                val request = androidx.work.OneTimeWorkRequestBuilder<com.meudinheiro.worker.TransferenciaWorker>()
                                    .setInputData(inputData)
                                    .build()

                                androidx.work.WorkManager.getInstance(context).enqueue(request)
                                android.util.Log.d("MeuDinheiro_Teste", "Comando de disparo enviado!")
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (agendar) "Agendar" else "Transferir",
                            color = Color(0xFF1B263B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (mostrarCalendario) {
        CustomCalendarDialog(
            onDismiss = { mostrarCalendario = false },
            onDateSelected = { ano, mes, dia ->
                val cal = java.util.Calendar.getInstance().apply { set(ano, mes, dia) }
                dataSelecionada = cal.timeInMillis
                mostrarCalendario = false
            }
        )
    }
}

// --- SELETOR REFINADO ---
@Composable
fun SelectorContaMini(
    label: String,
    contaId: String,
    contas: List<ContaSaldoDomain>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val contaAtual = contas.find { it.conta == contaId }

    val resId = remember(contaAtual?.pic) {
        if (contaAtual != null) {
            val id = context.resources.getIdentifier(contaAtual.pic, "drawable", context.packageName)
            if (id != 0) id else R.drawable.sim_chip_2
        } else R.drawable.sim_chip_2
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(0.6f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp // Dá um ar mais sofisticado
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { if (contas.isNotEmpty()) expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B263B), // Cor sólida para contrastar com o fundo escuro
                border = BorderStroke(1.dp, Color.White.copy(0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp), // Ícone um pouco maior
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = contaAtual?.banco ?: "Selecionar", // Voltou para Selecionar!
                        color = if (contaAtual != null) Color.White else Color.White.copy(0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF101828))
                    .border(1.dp, NeonCyan.copy(0.2f), RoundedCornerShape(12.dp))
            ) {
                if (contas.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Nenhuma conta", color = Color.Gray) },
                        onClick = { expanded = false }
                    )
                } else {
                    contas.forEach { conta ->
                        val itemResId = context.resources.getIdentifier(conta.pic, "drawable", context.packageName)
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(if (itemResId != 0) itemResId else R.drawable.sim_chip_2),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(conta.banco, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("CC ${conta.conta}", color = Color.White.copy(0.5f), fontSize = 12.sp)
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
}