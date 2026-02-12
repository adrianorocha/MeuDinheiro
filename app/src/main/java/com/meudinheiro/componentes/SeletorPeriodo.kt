package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorPeriodo(
    filtroSelecionado: FiltroPeriodo,
    onFiltroSelected: (FiltroPeriodo) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FiltroPeriodo.entries.forEach { filtro ->
            val selecionado = filtro == filtroSelecionado
            FilterChip(
                selected = selecionado,
                onClick = { onFiltroSelected(filtro) },
                label = {
                    Text(
                        text = filtro.label,
                        fontSize = 12.sp,
                        fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal
                    )
                },
                enabled = true,
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = Color.White.copy(alpha = 0.6f),
                    selectedContainerColor = Color(0xFF69F0AE).copy(alpha = 0.15f),
                    selectedLabelColor = Color(0xFF69F0AE)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selecionado,
                    borderColor = Color.White.copy(alpha = 0.1f),
                    selectedBorderColor = Color(0xFF69F0AE),
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )
        }
    }
}