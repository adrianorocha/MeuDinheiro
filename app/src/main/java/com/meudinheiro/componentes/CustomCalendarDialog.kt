package com.meudinheiro.componentes

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@SuppressLint("RememberReturnType")
@Composable
fun CustomCalendarDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val displayedMonth = remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    val displayedYear = remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    val mostrarCalendario = remember { mutableStateOf(false) }

    // Cria o dialog uma única vez
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onDateSelected(year, month, dayOfMonth)
                mostrarCalendario.value = false
            },
            displayedYear.value,
            displayedMonth.value,
            1
        )
    }

    if (mostrarCalendario.value) {
        LaunchedEffect(Unit) {
            // Atualiza o calendário interno do dialog com os valores atuais
            datePickerDialog.updateDate(displayedYear.value, displayedMonth.value, 1)
            datePickerDialog.show()
        }
    }
}
