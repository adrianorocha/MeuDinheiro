package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

// Cores Premium (Locais para manter o arquivo independente)
private val CardBg = Color(0xFF1E2B3E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaBancariaDialog(
    bancos: List<String>,
    onAdicionar: (banco: String, agencia: String, contaCorrente: String) -> Unit,
    onCancelar: () -> Unit
) {
    var agencia by rememberSaveable { mutableStateOf("") }
    var contaCorrente by rememberSaveable { mutableStateOf("") }
    var bancoSelecionado by rememberSaveable { mutableStateOf("") }
    var expandido by remember { mutableStateOf(false) }

    // Estilo comum para os Inputs (Campos de Texto)
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.White.copy(alpha = 0.8f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedTextColor = TextWhite,
        unfocusedTextColor = TextWhite,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
        cursorColor = Color.White,
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.7f)
    )

    Dialog(onDismissRequest = onCancelar) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp), // Arredondamento maior
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.1f)
            ) // Borda sutil (Glass effect)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nova Conta Bancária",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWhite
                )

                ExposedDropdownMenuBox(
                    expanded = expandido,
                    onExpandedChange = { expandido = !expandido },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = bancoSelecionado,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Banco") },
                        placeholder = { Text("Selecione") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                        colors = inputColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expandido,
                        onDismissRequest = { expandido = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface) // Mantém menu legível
                    ) {
                        bancos.forEach { nomeBanco ->
                            DropdownMenuItem(
                                text = { Text(nomeBanco) },
                                onClick = {
                                    bancoSelecionado = nomeBanco
                                    expandido = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = agencia,
                        onValueChange = { agencia = it },
                        label = { Text("Agência") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = inputColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = contaCorrente,
                        onValueChange = { contaCorrente = it },
                        label = { Text("Conta") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = inputColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelar,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            val banco = bancoSelecionado.trim()
                            val ag = agencia.trim()
                            val cc = contaCorrente.trim()

                            // validação básica
                            if (banco.isBlank() || ag.isBlank() || cc.isBlank()) return@Button

                            onAdicionar(banco, ag, cc)

                            // limpa
                            bancoSelecionado = ""
                            agencia = ""
                            contaCorrente = ""
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextWhite, // Botão Branco para destaque
                            contentColor = PremiumDarkBlue // Texto Escuro
                        )
                    ) {
                        Text("Adicionar")
                    }
                }
            }
        }
    }
}