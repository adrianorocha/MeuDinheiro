package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Pessoa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Rateios
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed

/**
 * R59 — "Dividir esta despesa": escolhe pessoas e cotas (igual, por valor ou por %). O lançamento continua com o valor TOTAL (nada
 * muda no saldo); só nascem acertos A_RECEBER. A sobra de centavos fica com você. Se já foi dividida, mostra a divisão e permite desfazer.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DividirDespesaDialog(
    despesa: Despesa,
    pessoas: List<Pessoa>,
    acertos: List<Acerto>,
    onDismiss: () -> Unit,
    onDividir: (modo: Rateios.Modo, participantes: List<Rateios.Participante>, vocePercentual: Long?) -> Unit,
    onDesfazer: () -> Unit,
    onNovaPessoa: (nome: String, aoCriar: (Int) -> Unit) -> Unit
) {
    val total = Dinheiro.centavos(despesa.valor)
    val existentes = acertos.filter { it.grupoRateio == Rateios.grupoRateio(despesa.id) }

    if (existentes.isNotEmpty()) {
        val aReceber = existentes.sumOf { Dinheiro.centavos(it.valor) }
        AlertDialog(
            onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
            title = { Text("Despesa dividida", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("O lançamento continua com o valor total (${FormatoCentavos.brl(total)}).", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    existentes.forEach { a ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pessoas.firstOrNull { it.id == a.pessoaId }?.nome ?: "Pessoa removida", color = Color.White, fontSize = 13.sp)
                            Text(FormatoCentavos.brl(Dinheiro.centavos(a.valor)) + if (a.quitado) " · recebido" else " · a receber", color = Color.White.copy(0.8f), fontSize = 13.sp)
                        }
                    }
                    Text("Você realmente gastou ${FormatoCentavos.brl(total - aReceber)}.", color = NeonCyan, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { TextButton(onClick = onDesfazer) { Text("Desfazer divisão", color = NeonRed) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White.copy(0.7f)) } }
        )
        return
    }

    var modo by remember { mutableStateOf(Rateios.Modo.IGUAL) }
    val marcadas = remember { mutableStateMapOf<Int, Boolean>() }
    val valores = remember { mutableStateMapOf<Int, String>() }
    val pcts = remember { mutableStateMapOf<Int, String>() }
    var novoNome by remember { mutableStateOf("") }
    val escolhidas = pessoas.filter { marcadas[it.id] == true }
    val participantes = escolhidas.map { p ->
        when (modo) {
            Rateios.Modo.VALOR -> Rateios.Participante(p.id, valor = parseValorBR(valores[p.id].orEmpty())?.takeIf { it > 0 }?.let { Dinheiro.centavos(it) } ?: 0L)
            Rateios.Modo.PERCENTUAL -> Rateios.Participante(p.id, percentual = parseValorBR(pcts[p.id].orEmpty())?.takeIf { it > 0 }?.let { Math.round(it * 10) } ?: 0L)
            Rateios.Modo.IGUAL -> Rateios.Participante(p.id)
        }
    }
    val vocePct = 1000L - participantes.sumOf { it.percentual ?: 0L }
    val previa = if (escolhidas.isEmpty()) null else Rateios.ratear(total, modo, participantes, if (modo == Rateios.Modo.PERCENTUAL) vocePct else null)

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Dividir: ${despesa.descricao}", color = Color.White, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Total ${FormatoCentavos.brl(total)}. O lançamento NÃO muda e o saldo continua o mesmo: só são criados valores a receber das pessoas escolhidas.",
                    color = Color.White.copy(0.65f), fontSize = 12.sp
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Rateios.Modo.IGUAL to "Igual", Rateios.Modo.VALOR to "Por valor", Rateios.Modo.PERCENTUAL to "Por %").forEach { (m, r) ->
                        FilterChip(
                            selected = modo == m, onClick = { modo = m }, label = { Text(r, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                        )
                    }
                }
                if (pessoas.isEmpty()) Text("Nenhuma pessoa cadastrada. Adicione abaixo.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                pessoas.forEach { p ->
                    val on = marcadas[p.id] == true
                    val cota = (previa as? Rateios.Resultado.Ok)?.cotas?.firstOrNull { it.pessoaId == p.id }?.centavos
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = { marcadas[p.id] = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                        Text(p.nome, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        if (on && modo == Rateios.Modo.VALOR) CampoR58(valores[p.id].orEmpty(), { valores[p.id] = it }, "Valor", Modifier.width(110.dp), numerico = true, maxChars = 12)
                        if (on && modo == Rateios.Modo.PERCENTUAL) CampoR58(pcts[p.id].orEmpty(), { pcts[p.id] = it }, "%", Modifier.width(80.dp), numerico = true, maxChars = 6)
                        if (on && cota != null) Text(FormatoCentavos.brl(cota), color = Color.White.copy(0.8f), fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CampoR58(novoNome, { novoNome = it }, "Nova pessoa", Modifier.weight(1f), maxChars = 60)
                    TextButton(
                        onClick = { onNovaPessoa(novoNome) { id -> marcadas[id] = true }; novoNome = "" },
                        enabled = novoNome.isNotBlank()
                    ) { Text("Adicionar", color = NeonCyan) }
                }
                when (previa) {
                    is Rateios.Resultado.Ok -> {
                        Text(
                            "Fica com você: ${FormatoCentavos.brl(previa.voce)}" + if (modo == Rateios.Modo.PERCENTUAL) " (${FormatoCentavos.decimos(vocePct)}%)" else "",
                            color = NeonCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                        Text("Quanto você realmente gasta com esta despesa: ${FormatoCentavos.brl(previa.voce)} de ${FormatoCentavos.brl(total)}.", color = Color.White.copy(0.6f), fontSize = 11.sp)
                    }
                    is Rateios.Resultado.Erro -> Text(previa.mensagem, color = NeonOrange, fontSize = 12.sp)
                    null -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDividir(modo, participantes, if (modo == Rateios.Modo.PERCENTUAL) vocePct else null) }, enabled = previa is Rateios.Resultado.Ok) {
                Text("Dividir", color = NeonCyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}
