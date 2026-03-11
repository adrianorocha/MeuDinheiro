package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AddInvestimentoDialog(
    onDismiss: () -> Unit,
    onGuardar: (nome: String, tipo: String, valorInvestido: Double, valorAtual: Double) -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var tipoSelecionado by remember { mutableStateOf("Renda Fixa") }
    var valorInvestidoStr by remember { mutableStateOf("") }
    var valorAtualStr by remember { mutableStateOf("") }

    val tipos = listOf("Renda Fixa", "Ações", "FIIs", "Cripto")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E)), // Azul profundo Blu Macaw
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Novo Investimento",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Campo Nome do Ativo
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome (ex: ITUB4, Bitcoin)", color = Color.White.copy(0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF), // Ciano Neon
                        unfocusedBorderColor = Color.White.copy(0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Seleção de Tipo (Chips Modernos)
                Text(
                    text = "Tipo de Ativo",
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tipos) { tipo ->
                        val isSelected = tipo == tipoSelecionado
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0xFF00E5FF).copy(0.2f) else Color.White.copy(0.05f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { tipoSelecionado = tipo }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tipo,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo Valor Investido (O Custo)
                OutlinedTextField(
                    value = valorInvestidoStr,
                    onValueChange = { valorInvestidoStr = it },
                    label = { Text("Valor Investido (Custo)", color = Color.White.copy(0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Campo Valor Atual (Opcional na criação)
                OutlinedTextField(
                    value = valorAtualStr,
                    onValueChange = { valorAtualStr = it },
                    label = { Text("Valor Atual (Opcional)", color = Color.White.copy(0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF69F0AE), // Verde Neon para diferenciar
                        unfocusedBorderColor = Color.White.copy(0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.White.copy(0.5f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Converte a string garantindo que aceita vírgulas ou pontos
                            val vInvestido = valorInvestidoStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val vAtual = valorAtualStr.replace(",", ".").toDoubleOrNull() ?: vInvestido // Se não preencher, assume o investido

                            if (nome.isNotBlank() && vInvestido > 0) {
                                onGuardar(nome, tipoSelecionado, vInvestido, vAtual)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("Guardar", color = Color(0xFF1B263B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}