package com.meudinheiro.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Despesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.LancamentoAcoes
import com.meudinheiro.domain.LancamentoAcoes.DescontoModo
import com.meudinheiro.domain.LancamentoAcoes.ModoAntecipacao
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * R40 — "Antecipar pagamento": quita parcelas em aberto (com desconto opcional) ou adianta só um valor.
 * [candidatos] = lançamentos antecipáveis do mesmo grupo/conta, em ordem de vencimento.
 */
@Composable
fun AntecipacaoDialog(
    base: Despesa,
    candidatos: List<Despesa>,
    saldoConta: Double?,
    onDismiss: () -> Unit,
    onConfirmar: (ids: List<Long>, valorPago: Double, desconto: Double, data: Long) -> Unit
) {
    var sel by remember { mutableStateOf(setOf(base.id)) }
    var modo by remember { mutableStateOf(ModoAntecipacao.QUITAR) }
    var descModo by remember { mutableStateOf(DescontoModo.COBRADO) }
    var descTxt by remember { mutableStateOf("") }
    var parcialTxt by remember { mutableStateOf("") }
    var ultimasPrimeiro by remember { mutableStateOf(false) }
    var dataMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val escolhidos = candidatos.filter { it.id in sel }
    val devido = Dinheiro.reais(escolhidos.sumOf { Dinheiro.centavos(it.valor) })
    val calculo = LancamentoAcoes.calcularAntecipacao(modo, descModo, devido, parcialTxt, descTxt)
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    fun confirmar() {
        if (escolhidos.isEmpty()) { erro = "Selecione ao menos uma parcela."; return }
        if (calculo == null) { erro = "Informe um valor válido."; return }
        val ordenados = if (modo == ModoAntecipacao.PARCIAL && ultimasPrimeiro) escolhidos.sortedByDescending { it.dataMs } else escolhidos.sortedBy { it.dataMs }
        try {
            Financas.antecipar(ordenados, calculo.pago, calculo.desconto, dataMillis) // valida com as mesmas regras do repositório
        } catch (e: IllegalArgumentException) {
            erro = e.message; return
        }
        onConfirmar(ordenados.map { it.id }, calculo.pago, calculo.desconto, dataMillis)
    }

    @Composable
    fun chip(selecionado: Boolean, texto: String, onClick: () -> Unit) = FilterChip(
        selected = selecionado, onClick = onClick, label = { Text(texto, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = Color.White.copy(0.8f), selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = NeonCyan
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepSpaceBlue,
        title = { Text("Antecipar: ${base.descricao}", color = Color.White, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chip(modo == ModoAntecipacao.QUITAR, "Quitar parcelas") { modo = ModoAntecipacao.QUITAR; erro = null }
                    chip(modo == ModoAntecipacao.PARCIAL, "Adiantar um valor") { modo = ModoAntecipacao.PARCIAL; erro = null }
                }

                Text(if (candidatos.size > 1) "Parcelas em aberto" else "Lançamento", color = Color.White.copy(0.7f), fontSize = 12.sp)
                Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                    candidatos.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { sel = if (d.id in sel) sel - d.id else sel + d.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = d.id in sel, onCheckedChange = { sel = if (it) sel + d.id else sel - d.id },
                                colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(d.descricao, color = Color.White, fontSize = 13.sp, maxLines = 1)
                                Text("vence ${fmt.format(d.data)}", color = Color.White.copy(0.5f), fontSize = 11.sp)
                            }
                            Text(formatarMoedaBR(d.valor, false), color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                if (candidatos.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { sel = candidatos.map { it.id }.toSet() }) { Text("Todas", color = NeonCyan, fontSize = 12.sp) }
                    TextButton(onClick = { sel = candidatos.takeLast(2).map { it.id }.toSet() }) { Text("Últimas 2", color = NeonCyan, fontSize = 12.sp) }
                }
                Text("Em aberto nas selecionadas: ${formatarMoedaBR(devido, false)}", color = Color.White.copy(0.7f), fontSize = 12.sp)

                if (modo == ModoAntecipacao.QUITAR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chip(descModo == DescontoModo.COBRADO, "Valor cobrado") { descModo = DescontoModo.COBRADO; descTxt = "" }
                        chip(descModo == DescontoModo.VALOR, "Desconto R$") { descModo = DescontoModo.VALOR; descTxt = "" }
                        chip(descModo == DescontoModo.PERCENTUAL, "Desconto %") { descModo = DescontoModo.PERCENTUAL; descTxt = "" }
                    }
                    PremiumTextField(
                        value = descTxt, onValueChange = { descTxt = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; erro = null },
                        label = when (descModo) {
                            DescontoModo.COBRADO -> "Quanto vão cobrar (vazio = ${formatarMoedaBR(devido, false)})"
                            DescontoModo.VALOR -> "Desconto em R$"
                            DescontoModo.PERCENTUAL -> "Desconto em %"
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), onClick = { }
                    )
                } else {
                    PremiumTextField(
                        value = parcialTxt, onValueChange = { parcialTxt = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; erro = null },
                        label = "Valor a adiantar (R$)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), onClick = { }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chip(!ultimasPrimeiro, "Das próximas") { ultimasPrimeiro = false }
                        chip(ultimasPrimeiro, "Das últimas") { ultimasPrimeiro = true }
                    }
                }

                PremiumTextField(
                    value = fmt.format(Date(dataMillis)), onValueChange = {}, readOnly = true,
                    label = "Data do pagamento", onClick = { mostrarCalendario = true }
                )

                if (calculo != null && devido > 0) {
                    val pct = calculo.desconto / devido * 100
                    Text(
                        "Sai da conta: ${formatarMoedaBR(calculo.pago, false)}" +
                            (if (calculo.desconto > 0) " · desconto ${formatarMoedaBR(calculo.desconto, false)} (${"%.1f".format(Locale("pt", "BR"), pct)}%)" else ""),
                        color = NeonCyan, fontSize = 13.sp
                    )
                    if (saldoConta != null && calculo.pago > saldoConta) {
                        Text("Atenção: o saldo da conta (${formatarMoedaBR(saldoConta, false)}) não cobre esse pagamento.", color = Color(0xFFFFB74D), fontSize = 12.sp)
                    }
                }
                erro?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = ::confirmar,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
            ) { Text("Antecipar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.6f)) } }
    )

    if (mostrarCalendario) {
        CustomCalendarDialog(
            onDismiss = { mostrarCalendario = false },
            onDateSelected = { y, m, d ->
                dataMillis = Calendar.getInstance().apply { set(y, m, d) }.timeInMillis
                mostrarCalendario = false
            }
        )
    }
}
