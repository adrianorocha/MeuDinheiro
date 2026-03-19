package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.R
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaBancariaDialog(
    bancos: List<String>,
    onAdicionar: (banco: String, agencia: String, contaCorrente: String, saldoInicial: Double) -> Unit,
    onCancelar: () -> Unit
) {
    var agencia by rememberSaveable { mutableStateOf("") }
    var contaCorrente by rememberSaveable { mutableStateOf("") }
    var bancoSelecionado by rememberSaveable { mutableStateOf("") }
    var saldoInicialStr by rememberSaveable { mutableStateOf("") } // Novo Estado
    var expandido by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Identificação visual do banco
    val colorBank = remember(bancoSelecionado) {
        when {
            bancoSelecionado.contains("Nubank", true) -> Color(0xFF8A05BE)
            bancoSelecionado.contains("Inter", true) -> Color(0xFFFF7A00)
            bancoSelecionado.contains("Itaú", true) -> Color(0xFFEC7000)
            bancoSelecionado.contains("Santander", true) -> Color(0xFFCC0000)
            bancoSelecionado.contains("Brasil", true) -> Color(0xFFF7F700)
            bancoSelecionado.contains("Caixa", true) -> Color(0xFF005CA9)
            else -> Color(0xFF37474F)
        }
    }

    val animatedCardColor by animateColorAsState(targetValue = colorBank, label = "color")

    Dialog(onDismissRequest = onCancelar) {
        PremiumDialogCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nova Conta",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // --- PREVIEW DO CARTÃO COM SALDO ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(animatedCardColor, animatedCardColor.copy(alpha = 0.5f))))
                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    val textColor = if (colorBank == Color(0xFFF7F700)) Color.Black else Color.White

                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = bancoSelecionado.ifEmpty { "Selecione um Banco" },
                                color = textColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            // Tenta carregar o ícone pelo nome, se não achar, usa o chip
                            val iconId = context.resources.getIdentifier(
                                bancoSelecionado.lowercase().replace(" ", "_"), "drawable", context.packageName
                            ).let { if (it != 0) it else R.drawable.sim_chip_2 }

                            Image(
                                painter = painterResource(iconId),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if(agencia.isEmpty() && contaCorrente.isEmpty()) "**** ****" else "Ag: $agencia / Cc: $contaCorrente",
                                color = textColor.copy(0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Saldo ao vivo no cartão
                            Text(
                                text = "R$ ${saldoInicialStr.ifEmpty { "0,00" }}",
                                color = textColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- SELETOR DE BANCO ---
                Box(modifier = Modifier.fillMaxWidth()) {
                    PremiumTextField(
                        value = bancoSelecionado,
                        onValueChange = {},
                        label = "Instituição Financeira",
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = NeonCyan) },
                        modifier = Modifier.clickable { expandido = true },
                        onClick = { expandido = true }
                    )
                    DropdownMenu(
                        expanded = expandido,
                        onDismissRequest = { expandido = false },
                        modifier = Modifier.background(Color(0xFF1B263B)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                    ) {
                        bancos.forEach { nome ->
                            DropdownMenuItem(
                                text = { Text(nome, color = Color.White) },
                                onClick = { bancoSelecionado = nome; expandido = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- AGÊNCIA E CONTA ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PremiumTextField(
                        value = agencia,
                        onValueChange = { if (it.length <= 4) agencia = it },
                        label = "Agência",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onClick = {}
                    )
                    PremiumTextField(
                        value = contaCorrente,
                        onValueChange = { if (it.length <= 12) contaCorrente = it },
                        label = "Conta",
                        modifier = Modifier.weight(1.2f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onClick = {}
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- SALDO INICIAL ---
                PremiumTextField(
                    value = saldoInicialStr,
                    onValueChange = { saldoInicialStr = it },
                    label = "Saldo Inicial",
                    prefix = { Text("R$ ", color = NeonGreen, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onClick = {}
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- BOTÕES ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = onCancelar,
                        modifier = Modifier.weight(1f).height(50.dp),
                        border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Voltar", color = Color.White)
                    }
                    Button(
                        onClick = {
                            val saldo = saldoInicialStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (bancoSelecionado.isNotBlank() && agencia.isNotBlank()) {
                                onAdicionar(bancoSelecionado, agencia, contaCorrente, saldo)
                            }
                        },
                        enabled = bancoSelecionado.isNotEmpty() && agencia.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, disabledContainerColor = Color.Gray.copy(0.3f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Salvar", color = Color(0xFF1B263B), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}