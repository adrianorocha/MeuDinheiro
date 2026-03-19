package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.data.ContaSaldoDomain

// Definição das Categorias com Cores e Nomes
data class CategoriaGasto(val nome: String, val cor: Color)

val categoriasPadrao = listOf(
    CategoriaGasto("Alimentação", Color(0xFFFFD54F)),
    CategoriaGasto("Transporte", Color(0xFF00E5FF)),
    CategoriaGasto("Lazer", Color(0xFFE040FB)),
    CategoriaGasto("Saúde", Color(0xFFEF5350)),
    CategoriaGasto("Casa", Color(0xFF69F0AE)),
    CategoriaGasto("Educação", Color(0xFF7986CB)),
    CategoriaGasto("Compras", Color(0xFFFF8A65)),
    CategoriaGasto("Outros", Color(0xFF90A4AE))
)

@Composable
fun AddGastoDialog(
    contas: List<ContaSaldoDomain>,
    onDismiss: () -> Unit,
    onSalvar: (String, Double, String, String, String) -> Unit
) {
    var descricao by remember { mutableStateOf("") }
    var valorStr by remember { mutableStateOf("") }
    var categoriaSelecionada by remember { mutableStateOf(categoriasPadrao[0]) }
    var bancoSelecionado by remember { mutableStateOf(contas.firstOrNull()?.banco ?: "Dinheiro") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B)),
            border = BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Novo Gasto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)

                Spacer(modifier = Modifier.height(16.dp))

                // CAMPO VALOR (O principal)
                OutlinedTextField(
                    value = valorStr,
                    onValueChange = { valorStr = it },
                    label = { Text("Quanto gastou?", color = Color.White.copy(0.5f)) },
                    prefix = { Text("R$ ", color = Color(0xFF69F0AE)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF69F0AE),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // CAMPO DESCRIÇÃO
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Onde foi? (ex: Mercado)", color = Color.White.copy(0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // SELEÇÃO DE CATEGORIA (Grid de Ícones)
                Text(
                    "Categoria",
                    color = Color.White.copy(0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoriasPadrao) { cat ->
                        val isSelected = cat == categoriaSelecionada
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) cat.cor.copy(0.2f) else Color.Transparent)
                                .clickable { categoriaSelecionada = cat }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) cat.cor else cat.cor.copy(0.1f))
                                    .border(1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                // Aqui o ícone é gerado dinamicamente no TransacaoModel,
                                // mas no diálogo mostramos apenas a cor/bola para ser rápido.
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if(isSelected) Color.White else cat.cor))
                            }
                            Text(
                                cat.nome,
                                fontSize = 10.sp,
                                color = if(isSelected) Color.White else Color.White.copy(0.5f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // BOTÕES
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.5f)) }
                    Button(
                        onClick = {
                            val valor = valorStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (valor > 0 && descricao.isNotBlank()) {
                                // Converte a cor para Hex para salvar no banco
                                val corHex = String.format("#%06X", (0xFFFFFF and categoriaSelecionada.cor.toArgb()))
                                onSalvar(descricao, valor, bancoSelecionado, categoriaSelecionada.nome, corHex)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
                    ) {
                        Text("Salvar Gasto", color = Color(0xFF1B263B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}