package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.GrupoIcones
import com.meudinheiro.domain.IconesCategoria
import java.text.Normalizer

private val NeonCyanSel = Color(0xFF00E5FF)
private val TextoClaro = Color.White

private fun semAcento(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Filtra grupos do catálogo pelo texto (rótulo ou chave, sem acento); vazio devolve tudo. Pura, testável. */
fun filtrarGruposIcones(grupos: List<GrupoIcones>, busca: String): List<GrupoIcones> {
    val q = semAcento(busca.trim())
    if (q.isEmpty()) return grupos
    return grupos.mapNotNull { g ->
        val itens = g.itens.filter { semAcento(it.rotulo).contains(q) || it.chave.contains(q) }
        if (itens.isEmpty()) null else g.copy(itens = itens)
    }
}

/** Seletor de ícone de categoria em grade, agrupado e com busca por nome (estilo neon). */
@Composable
fun SeletorIconeCategoria(
    selecionado: String,
    onSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var busca by remember { mutableStateOf("") }
    val grupos = remember(busca) { filtrarGruposIcones(IconesCategoria.GRUPOS, busca) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            label = { Text("Buscar ícone", color = TextoClaro.copy(0.5f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyanSel,
                unfocusedBorderColor = TextoClaro.copy(0.2f),
                focusedTextColor = TextoClaro,
                unfocusedTextColor = TextoClaro,
                cursorColor = NeonCyanSel
            )
        )
        Spacer(Modifier.height(8.dp))
        if (grupos.isEmpty()) {
            Text("Nenhum ícone encontrado.", color = TextoClaro.copy(0.5f), fontSize = 12.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grupos.forEach { grupo ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_${grupo.rotulo}") {
                    Text(
                        grupo.rotulo.uppercase(),
                        color = TextoClaro.copy(0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(grupo.itens, key = { it.chave }) { icone ->
                    val ativo = icone.chave == selecionado
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (ativo) NeonCyanSel.copy(0.15f) else TextoClaro.copy(0.05f))
                            .border(
                                BorderStroke(if (ativo) 2.dp else 1.dp, if (ativo) NeonCyanSel else Color.Transparent),
                                CircleShape
                            )
                            .semantics { contentDescription = icone.rotulo }
                            .clickable { onSelecionar(icone.chave) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icone.vetor,
                            contentDescription = null,
                            tint = if (ativo) NeonCyanSel else TextoClaro.copy(0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        val rotuloSel = IconesCategoria.porChave(selecionado)?.rotulo
        if (rotuloSel != null) {
            Spacer(Modifier.height(6.dp))
            Text("Selecionado: $rotuloSel", color = NeonCyanSel, fontSize = 12.sp)
        }
    }
}
