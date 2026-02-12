package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CurrencySelector(
    moedaAtual: String,
    onMoedaSelecionada: (String) -> Unit
) {
    val moedas = listOf("BRL" to "🇧🇷", "USD" to "🇺🇸", "EUR" to "🇪🇺")

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        moedas.forEach { (codigo, bandeira) ->
            FilterChip(
                selected = moedaAtual == codigo,
                onClick = { onMoedaSelecionada(codigo) },
                label = { Text("$bandeira $codigo") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF69F0AE),
                    selectedLabelColor = Color(0xFF0D1B2A)
                )
            )
        }
    }
}