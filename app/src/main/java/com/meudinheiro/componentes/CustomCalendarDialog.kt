package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

// Cores Premium Locais
private val CardBg = Color(0xFF1E2B3E)

@Composable
fun CustomCalendarDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int, Int) -> Unit
) {
    // 1. Estados de navegação
    var displayedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var displayedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // 2. Cálculo dinâmico do nome do mês (CORREÇÃO AQUI)
    val monthName = remember(displayedMonth) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, displayedMonth)
        cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            ?.replaceFirstChar { it.uppercase() } ?: ""
    }

    val daysInMonth = remember(displayedMonth, displayedYear) {
        Calendar.getInstance().apply {
            set(displayedYear, displayedMonth, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(displayedMonth, displayedYear) {
        Calendar.getInstance().apply {
            set(displayedYear, displayedMonth, 1)
        }.get(Calendar.DAY_OF_WEEK)
    }

    // Grid de dias
    val days = remember(daysInMonth, firstDayOfWeek) {
        List(firstDayOfWeek - 1) { null } + (1..daysInMonth).toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        confirmButton = {
            TextButton(
                enabled = selectedDay != null,
                onClick = {
                    selectedDay?.let { day ->
                        onDateSelected(displayedYear, displayedMonth, day)
                        onDismiss()
                    }
                }
            ) {
                Text("OK", color = if (selectedDay != null) Color(0xFF69F0AE) else Color.Gray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextWhite.copy(alpha = 0.7f))
            }
        },
        title = { Text("Selecionar Data", color = TextWhite) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Header de Navegação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        selectedDay = null // Reseta seleção ao mudar mês
                        if (displayedMonth == 0) {
                            displayedMonth = 11
                            displayedYear -= 1
                        } else {
                            displayedMonth -= 1
                        }
                    }) {
                        Text("<", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }

                    // AQUI EXIBIMOS O NOME CORRETO
                    Text(
                        text = "$monthName $displayedYear",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    IconButton(onClick = {
                        selectedDay = null
                        if (displayedMonth == 11) {
                            displayedMonth = 0
                            displayedYear += 1
                        } else {
                            displayedMonth += 1
                        }
                    }) {
                        Text(">", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dias da Semana
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = TextWhite.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Grade de Dias
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(250.dp).padding(top = 8.dp)
                ) {
                    items(days.size) { index ->
                        val day = days[index]
                        val isSelected = day == selectedDay

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF69F0AE) else Color.Transparent)
                                .clickable(enabled = day != null) { selectedDay = day },
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                Text(
                                    text = day.toString(),
                                    color = if (isSelected) PremiumDarkBlue else TextWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}