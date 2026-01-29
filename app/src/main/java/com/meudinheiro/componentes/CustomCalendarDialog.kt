package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val calendar = remember { Calendar.getInstance() }
    var displayedMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var displayedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val daysInMonth = remember(displayedMonth, displayedYear) {
        val cal = Calendar.getInstance()
        cal.set(displayedYear, displayedMonth, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(displayedMonth, displayedYear) {
        val cal = Calendar.getInstance()
        cal.set(displayedYear, displayedMonth, 1)
        cal.get(Calendar.DAY_OF_WEEK) // 1=Domingo, 7=Sábado
    }

    // Criação da lista de dias para o calendário
    val days = remember(daysInMonth, firstDayOfWeek) {
        mutableListOf<Int?>().apply {
            // Adiciona dias vazios antes do primeiro dia
            for (i in 1 until firstDayOfWeek) {
                add(null)
            }
            // Adiciona os dias do mês
            for (day in 1..daysInMonth) {
                add(day)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg, // Fundo escuro
        titleContentColor = TextWhite,
        textContentColor = TextWhite,
        confirmButton = {
            TextButton(
                onClick = {
                    selectedDay?.let { day ->
                        val selectedCalendar = Calendar.getInstance()
                        selectedCalendar.set(displayedYear, displayedMonth, day, 0, 0, 0)
                        selectedCalendar.set(Calendar.MILLISECOND, 0)

                        onDateSelected(
                            selectedCalendar.get(Calendar.YEAR),
                            selectedCalendar.get(Calendar.MONTH),
                            selectedCalendar.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = TextWhite)
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextWhite.copy(alpha = 0.7f))
            ) {
                Text("Cancelar")
            }
        },
        title = {
            Text(
                text = "Selecionar Data",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header com mês e ano
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (displayedMonth == 0) {
                            displayedMonth = 11
                            displayedYear -= 1
                        } else {
                            displayedMonth -= 1
                        }
                    }) {
                        Text("<", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "${calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())?.replaceFirstChar { it.uppercase() }} $displayedYear",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    IconButton(onClick = {
                        if (displayedMonth == 11) {
                            displayedMonth = 0
                            displayedYear += 1
                        } else {
                            displayedMonth += 1
                        }
                    }) {
                        Text(">", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Dias da semana
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEach {
                        Text(
                            text = it,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = TextWhite.copy(alpha = 0.6f) // Dias da semana mais apagados
                        )
                    }
                }

                // Dias do calendário
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    items(days.size) { index ->
                        val day = days[index]
                        val isSelected = (day == selectedDay)

                        // Cores do item
                        val bgColor = if (isSelected) Color.White else Color.Transparent
                        val textColor = if (isSelected) PremiumDarkBlue else TextWhite

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(CircleShape) // Forma circular fica mais moderna
                                .background(bgColor)
                                .clickable(enabled = day != null) {
                                    if (day != null) selectedDay = day
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day?.toString() ?: "",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    )
}