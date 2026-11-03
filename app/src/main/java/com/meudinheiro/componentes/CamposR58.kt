package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonRed

/** Campos e utilidades compartilhados pelas telas de Dívidas (R58) e Pessoas/Rateios (R59). */

internal val CardFundoR58 = Color(0xFF1B263B)
internal val TextoClaroR58 = Color(0xFFE0E1DD)

internal val coresCampoR58
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
        unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan, focusedLabelColor = NeonCyan,
        unfocusedLabelColor = Color.White.copy(0.5f)
    )

/** "1.234,56", "1234,56", "1234.56" -> 1234.56; vazio/inválido -> `null`. Mesma regra do portal (`parseValorBR`). */
internal fun parseValorBR(texto: String): Double? {
    var t = texto.trim().replace(Regex("[R$\\s]"), "")
    if (t.isEmpty()) return null
    t = if (t.contains(',')) t.replace(".", "").replace(',', '.')
    else if (t.count { it == '.' } > 1) t.replace(".", "")
    else t
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** 1234.5 -> "1234,5" (campo de texto). */
internal fun valorParaCampo(valor: Double): String =
    java.math.BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString().replace('.', ',')

@Composable
internal fun CampoR58(
    valor: String, aoMudar: (String) -> Unit, rotulo: String, modifier: Modifier = Modifier.fillMaxWidth(),
    numerico: Boolean = false, apoio: String? = null, maxChars: Int = 80
) {
    OutlinedTextField(
        value = valor, onValueChange = { aoMudar(it.take(maxChars)) }, singleLine = true, label = { Text(rotulo, fontSize = 12.sp) },
        modifier = modifier, colors = coresCampoR58,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        supportingText = apoio?.let { { Text(it, color = Color.White.copy(0.5f), fontSize = 11.sp) } }
    )
}

/** Escolha da conta (uma só) por chips. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SeletorContaR58(contas: List<ContaSaldo>, selecionada: String?, aoEscolher: (String) -> Unit) {
    if (contas.isEmpty()) {
        Text("Cadastre uma conta primeiro.", color = NeonRed, fontSize = 12.sp)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        contas.forEach { c ->
            FilterChip(
                selected = c.conta == selecionada, onClick = { aoEscolher(c.conta) },
                label = { Text("${c.banco} · ${c.conta}", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f)
                )
            )
        }
    }
}

private val MESES = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

/** (ano, mês 1-12) -> "Março de 2026". */
internal fun mesAnoExtenso(ano: Int, mes: Int): String = "${MESES[mes - 1]} de $ano"
