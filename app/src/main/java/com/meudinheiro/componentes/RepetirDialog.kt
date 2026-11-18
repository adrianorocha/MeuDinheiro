package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meudinheiro.domain.Analises.UnidadeRepeticao
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan

/** R23 — "Repetir": cria N ocorrências futuras a cada X dias, semanas ou meses (em aberto). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepetirDialog(descricao: String, onDismiss: () -> Unit, onConfirmar: (n: Int, intervalo: Int, unidade: UnidadeRepeticao) -> Unit) {
    var n by remember { mutableStateOf("3") }
    var intervalo by remember { mutableStateOf("1") }
    var unidade by remember { mutableStateOf(UnidadeRepeticao.MESES) }
    val nInt = n.toIntOrNull()
    val iInt = intervalo.toIntOrNull()
    val valido = nInt != null && nInt in 1..120 && iInt != null && iInt >= 1

    fun campo(valor: String, rotulo: String, onChange: (String) -> Unit, modifier: Modifier) = @Composable {
        OutlinedTextField(
            value = valor, onValueChange = { onChange(it.filter(Char::isDigit).take(3)) }, singleLine = true,
            label = { Text(rotulo) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
                unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f)
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Repetir lançamento", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("\"$descricao\" será criado novamente, em aberto, nas próximas datas.", color = Color.White.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    campo(n, "Repetições (1–120)", { n = it }, Modifier.weight(1f))()
                    campo(intervalo, "A cada", { intervalo = it }, Modifier.weight(1f))()
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnidadeRepeticao.entries.forEach { u ->
                        FilterChip(
                            selected = unidade == u, onClick = { unidade = u },
                            label = { Text(when (u) { UnidadeRepeticao.DIAS -> "dias"; UnidadeRepeticao.SEMANAS -> "semanas"; UnidadeRepeticao.MESES -> "meses" }) },
                            colors = FilterChipDefaults.filterChipColors(labelColor = Color.White.copy(0.8f), selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = NeonCyan)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmar(nInt!!, iInt!!, unidade) }, enabled = valido,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
            ) { Text("Repetir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.6f)) } }
    )
}
