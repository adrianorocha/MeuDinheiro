package com.meudinheiro.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.funcoes.formatarMoedaBR

@Composable
fun OrcamentoCard(
    item: OrcamentoProgresso,
    onClick: () -> Unit
) {
    val barColor = when {
        item.porcentagem < 0.7f -> Color(0xFF66BB6A)
        item.porcentagem < 0.9f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }

    // --- MÁGICA DA ANIMAÇÃO AQUI ---
    var animationPlayed by remember { mutableStateOf(false) }

    val currentPercentage by animateFloatAsState(
        targetValue = if (animationPlayed) item.porcentagem.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(
            durationMillis = 1200, // Duração de 1.2 segundos
            delayMillis = 100,     // Pequeno atraso para a tela carregar primeiro
            easing = FastOutSlowInEasing // Começa rápido, termina suave
        ),
        label = "ProgressoCard"
    )

    // Dispara a animação assim que o card aparece na tela
    LaunchedEffect(key1 = true) {
        animationPlayed = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Fundo do anel
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = barColor.copy(alpha = 0.2f),
                    strokeWidth = 4.dp
                )
                // Anel Animado
                CircularProgressIndicator(
                    progress = { currentPercentage }, // Usa o valor animado
                    modifier = Modifier.fillMaxSize(),
                    color = barColor,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
                // Número Animado (vai subindo 0%, 15%, 40%...)
                Text(
                    text = "${(currentPercentage * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = item.categoria,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AddOrcamentoDialog(
    categoriasDisponiveis: List<String>,
    onSalvar: (String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var categoriaSelecionada by remember {
        mutableStateOf(
            categoriasDisponiveis.firstOrNull() ?: ""
        )
    }
    var valorLimite by remember { mutableStateOf("") }
    var expandido by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2B3E),
        title = { Text("Definir Teto de Gasto", color = Color.White) },
        text = {
            Column {
                Box {
                    OutlinedButton(
                        onClick = { expandido = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            categoriaSelecionada.ifBlank { "Selecionar Categoria" },
                            color = Color.White
                        )
                    }
                    DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                        categoriasDisponiveis.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = { categoriaSelecionada = cat; expandido = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = valorLimite,
                    onValueChange = { valorLimite = it },
                    label = { Text("Valor Limite (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(0.7f),
                        unfocusedLabelColor = Color.White.copy(0.7f)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val valor = valorLimite.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (valor > 0 && categoriaSelecionada.isNotBlank()) {
                        onSalvar(categoriaSelecionada, valor)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(0.7f))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalheOrcamentoBottomSheet(
    item: OrcamentoProgresso,
    onDismiss: () -> Unit,
    onExcluir: () -> Unit,
    onEditar: (Double) -> Unit // <-- NOVO: Parâmetro para salvar a edição
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Estados para o modo de edição
    var isEditing by remember { mutableStateOf(false) }
    var novoLimiteTexto by remember { mutableStateOf(item.limite.toString()) }

    val barColor = when {
        item.porcentagem < 0.7f -> Color(0xFF66BB6A)
        item.porcentagem < 0.9f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }

    // Animação do gráfico
    var animationPlayed by remember { mutableStateOf(false) }
    val currentPercentage by animateFloatAsState(
        targetValue = if (animationPlayed) item.porcentagem.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(1000, 150, FastOutSlowInEasing),
        label = "ProgressoSheet"
    )

    LaunchedEffect(key1 = true) { animationPlayed = true }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E2B3E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.categoria,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Gráfico Circular Grande
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = barColor.copy(alpha = 0.2f),
                    strokeWidth = 12.dp
                )
                CircularProgressIndicator(
                    progress = { currentPercentage },
                    modifier = Modifier.fillMaxSize(),
                    color = barColor,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(currentPercentage * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                    Text(
                        text = "Utilizado",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Se NÃO estiver editando, mostra os textos normais
            if (!isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Gasto Atual",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Text(
                            formatarMoedaBR(item.gastoAtual, false),
                            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Teto Definido",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Text(
                            formatarMoedaBR(item.limite, false),
                            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Se ESTIVER editando, mostra o campo de texto
                OutlinedTextField(
                    value = novoLimiteTexto,
                    onValueChange = { novoLimiteTexto = it },
                    label = { Text("Novo Valor Limite (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(0.7f),
                        unfocusedLabelColor = Color.White.copy(0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- BOTÕES DE AÇÃO DINÂMICOS ---
            if (isEditing) {
                // Modo Edição: Botões Salvar e Cancelar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { isEditing = false }, // Cancela a edição
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            val valor = novoLimiteTexto.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (valor > 0) {
                                onEditar(valor) // Salva o novo valor
                                isEditing = false // Sai do modo de edição
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // Verde
                    ) {
                        Text("Salvar Limite")
                    }
                }
            } else {
                // Modo Normal: Botões Editar e Remover
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { isEditing = true }, // Entra no modo de edição
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar Limite",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Editar")
                    }

                    OutlinedButton(
                        onClick = onExcluir,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir Orçamento",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Remover")
                    }
                }
            }
        }
    }
}