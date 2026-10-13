package com.meudinheiro.componentes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.Tags
import com.meudinheiro.ui.theme.NeonCyan

/**
 * R56 — campo de tags: chips com remoção, "Adicionar"/Enter (ou vírgula) inclui, sugestões das tags já usadas.
 * Normalização igual à do portal ([Tags.normalizar]: sem `#`, até 5 tags de 30 caracteres, sem repetir a chave).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CampoTags(
    tags: List<String>,
    onChange: (List<String>) -> Unit,
    sugestoes: List<Tags.Usada>,
    modifier: Modifier = Modifier
) {
    var texto by remember { mutableStateOf("") }
    val cheio = tags.size >= Tags.MAX_TAGS

    fun adicionar(bruto: String) {
        val limpo = Tags.rotulo(bruto)
        if (limpo.isNotEmpty()) onChange(Tags.normalizar(tags + limpo))
        texto = ""
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tags (opcional, até ${Tags.MAX_TAGS})", color = Color.White.copy(0.6f), fontSize = 11.sp)
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t ->
                    InputChip(
                        selected = true,
                        onClick = { onChange(tags.filterNot { Tags.chave(it) == Tags.chave(t) }) },
                        label = { Text("#$t", fontSize = 12.sp) },
                        trailingIcon = { Text("×", color = NeonCyan, fontSize = 14.sp) }
                    )
                }
            }
        }
        if (!cheio) {
            OutlinedTextField(
                value = texto,
                onValueChange = { novo ->
                    if (novo.endsWith(",") || novo.endsWith(";")) adicionar(novo.dropLast(1)) else texto = novo.take(Tags.MAX_CHARS + 1)
                },
                singleLine = true,
                placeholder = { Text("ex.: viagem, reforma", fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { adicionar(texto) }),
                trailingIcon = { TextButton(onClick = { adicionar(texto) }, enabled = texto.isNotBlank()) { Text("Adicionar", color = NeonCyan, fontSize = 12.sp) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan,
                    focusedPlaceholderColor = Color.White.copy(0.4f), unfocusedPlaceholderColor = Color.White.copy(0.4f)
                )
            )
            val chavesSel = tags.map(Tags::chave).toSet()
            val termo = Tags.chave(texto)
            val lista = sugestoes.filter { it.chave !in chavesSel && (termo.isEmpty() || it.chave.contains(termo)) }.take(8)
            if (lista.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    lista.forEach { s -> AssistChip(onClick = { adicionar(s.rotulo) }, label = { Text("#${s.rotulo}", fontSize = 12.sp) }) }
                }
            }
        } else {
            Text("Máximo de ${Tags.MAX_TAGS} tags atingido.", color = Color.White.copy(0.5f), fontSize = 11.sp)
        }
    }
}
