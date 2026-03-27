package com.meudinheiro.componentes

import android.util.Log
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.R
import com.meudinheiro.funcoes.Haptics
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaBancariaDialog(
    bancos: List<String>,
    onAdicionar: (banco: String, agencia: String, contaCorrente: String, saldoInicial: Double) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    var agencia by rememberSaveable { mutableStateOf("") }
    var contaCorrente by rememberSaveable { mutableStateOf("") }
    var bancoSelecionado by rememberSaveable { mutableStateOf("") }
    var saldoInicialStr by rememberSaveable { mutableStateOf("") }
    var expandido by remember { mutableStateOf(false) }

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
                    text = "NOVA CONTA //",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // --- PREVIEW DO CARTÃO (HOLOGRÁFICO LIGHT) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(animatedCardColor, animatedCardColor.copy(alpha = 0.6f))
                            )
                        )
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    val textColor = if (colorBank == Color(0xFFF7F700)) Color.Black else Color.White

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bancoSelecionado.ifEmpty { "SELECIONE O BANCO" }.uppercase(),
                                color = textColor,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.sim_chip_2),
                                contentDescription = null,
                                tint = if (colorBank == Color(0xFFF7F700)) Color.Black.copy(0.5f) else Color.White.copy(
                                    0.5f
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "AGÊNCIA / CONTA",
                                    color = textColor.copy(0.5f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (agencia.isEmpty() && contaCorrente.isEmpty()) "0000 / 000000-0" else "$agencia / $contaCorrente",
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                            // SALDO AO VIVO
                            Text(
                                text = "${saldoInicialStr.ifEmpty { "0,00" }}",
                                color = textColor,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- FORMULÁRIO ---
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
                        modifier = Modifier
                            .background(Color(0xFF1B263B))
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                    ) {
                        bancos.forEach { nome ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        nome,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                onClick = {
                                    Haptics.vibrar(context, "clique")
                                    bancoSelecionado = nome
                                    expandido = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PremiumTextField(
                        value = agencia,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } && it.length <= 4) agencia = it
                        },
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
                        onClick = {}
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PremiumTextField(
                    value = saldoInicialStr,
                    onValueChange = { novoValor ->
                        // 1. 🧹 LIMPEZA: Remove tudo que não for número (tira R$, pontos e vírgulas)
                        val apenasNumeros = novoValor.replace(Regex("[^\\d]"), "")

                        // 2. 🛡️ SEGURANÇA: Se estiver vazio, define como zero para não dar crash
                        if (apenasNumeros.isEmpty()) {
                            saldoInicialStr = "0,00"
                            return@PremiumTextField
                        }

                        try {
                            // 3. 💸 CÁLCULO: Transforma "125" em 1.25 (Double)
                            val valorDouble = apenasNumeros.toDouble() / 100.0

                            // 4. ✨ FORMATAÇÃO: Usa a sua função que já funciona bem
                            // Se a sua formatarMoedaBR já coloca o "R$", remova o prefix abaixo.
                            saldoInicialStr = formatarMoedaBR(valorDouble, false)

                        } catch (e: Exception) {
                            // Se algo der muito errado, o app não fecha, ele apenas ignora
                            Log.e("MOEDA_ERROR", "Erro ao formatar: ${e.message}")
                        }
                    },
                    label = "Saldo Inicial",
                    //prefix = { Text("R$ ", color = NeonGreen, fontWeight = FontWeight.Bold) },
                    // Use NumberPassword para garantir que só apareça o teclado numérico sem ponto/vírgula
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    onClick = {}
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- BOTÕES ---
// --- BOTÕES (Ajustado para o seu BotaGlassmorphic) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    val podeSalvar = bancoSelecionado.isNotEmpty() && agencia.isNotEmpty()

                    BotaGlassmorphic(
                        texto = "CANCELAR",
                        corAcento = Color.White.copy(0.6f),
                        modifier = Modifier.weight(1f),
                        onClick = onCancelar
                    )

                    BotaGlassmorphic(
                        texto = "SALVAR",
                        // Se não puder salvar, a cor fica cinza e apagada
                        corAcento = if (podeSalvar) NeonCyan else Color.Gray.copy(0.3f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (podeSalvar) {
                                // 💡 TRATAMENTO SEGURO DO DOUBLE
                                val saldoLimpo = saldoInicialStr.replace(",", ".")
                                val saldo = saldoLimpo.toDoubleOrNull() ?: 0.0

                                Haptics.vibrar(context, "sucesso")
                                onAdicionar(bancoSelecionado, agencia, contaCorrente, saldo)
                            } else {
                                // Feedback de erro opcional se clicar sem preencher
                                Haptics.vibrar(context, "erro")
                            }
                        }
                    )
                }
            }
        }
    }
}