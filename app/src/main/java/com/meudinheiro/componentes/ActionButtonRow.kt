package com.meudinheiro.componentes

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.LightGray
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.R
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionButtonRow(
    categorias: List<String>,
    onAddDespesa: (Despesa) -> Unit,
    getPicCategoria: (String) -> String,
    contaSelecionada: String,
    viewModelFactory: ContaSaldoViewModelFactory
) {
    var exibirFormulario by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionButton(R.drawable.deposit, "Depositar", onClick = { })
        ActionButton(R.drawable.add, "Adicionar", onClick = { exibirFormulario = true })
        ActionButton(R.drawable.sim_chip, "Config.", onClick = { })
    }

    if (exibirFormulario) {
        AddDespesaDialog(
            categorias = categorias,
            contaSelecionada = contaSelecionada,
            getPicCategoria = getPicCategoria,
            viewModelFactory = viewModelFactory,
            onAddDespesa = onAddDespesa,
            onDismiss = { exibirFormulario = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDespesaDialog(
    categorias: List<String>,
    contaSelecionada: String,
    getPicCategoria: (String) -> String,
    viewModelFactory: ContaSaldoViewModelFactory,
    onAddDespesa: (Despesa) -> Unit,
    onDismiss: () -> Unit
) {
    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)

    var categoriaSelecionada by remember { mutableStateOf<String?>(null) }
    var expandido by remember { mutableStateOf(false) }
    var tipo by remember { mutableStateOf(TipoDespesa.DEBITO) }

    var descricao by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    var numeroParcelas by rememberSaveable { mutableStateOf("1") }

    val mostrarCalendario = remember { mutableStateOf(false) }
    val dataMillis = remember { mutableStateOf<Long?>(null) }

    if (mostrarCalendario.value) {
        CustomCalendarDialog(
            onDismiss = { mostrarCalendario.value = false },
            onDateSelected = { ano, mes, dia ->
                val cal = Calendar.getInstance()
                cal.set(ano, mes, dia, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                dataMillis.value = cal.timeInMillis
                mostrarCalendario.value = false
            }
        )
    }

    val dateText = remember(dataMillis.value) {
        dataMillis.value?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
        } ?: "Selecionar data"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Nova Despesa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                // Débito / Crédito mais organizado
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = tipo == TipoDespesa.DEBITO,
                        onClick = { tipo = TipoDespesa.DEBITO },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Débito") }

                    SegmentedButton(
                        selected = tipo == TipoDespesa.CREDITO,
                        onClick = { tipo = TipoDespesa.CREDITO },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Crédito") }
                }

                // Categoria
                ExposedDropdownMenuBox(
                    expanded = expandido,
                    onExpandedChange = { expandido = !expandido },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = categoriaSelecionada ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoria") },
                        placeholder = { Text("Selecione") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expandido,
                        onDismissRequest = { expandido = false }
                    ) {
                        categorias.forEach { categoria ->
                            DropdownMenuItem(
                                text = { Text(categoria) },
                                onClick = {
                                    categoriaSelecionada = categoria
                                    expandido = false
                                }
                            )
                        }
                    }
                }

                // Descrição
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Linha: Valor | Parcelas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { valor = it },
                        label = { Text("Valor", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = numeroParcelas,
                        onValueChange = { numeroParcelas = it },
                        label = { Text("Parcelas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 130.dp) // evita quebrar "Parcelas" em telas menores
                    )
                }

                // Data (linha separada, padrão moderno)
                OutlinedTextField(
                    value = dataMillis.value?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
                    } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Data") },
                    placeholder = { Text("Selecionar data", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        IconButton(onClick = { mostrarCalendario.value = true }) {
                            Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = "Selecionar data")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Botões
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            val valorDouble = valor.replace(",", ".").toDoubleOrNull()
                            val parcelasInt = numeroParcelas.toIntOrNull() ?: 1

                            when {
                                descricao.isBlank() -> return@Button
                                valorDouble == null || valorDouble <= 0.0 -> return@Button
                                categoriaSelecionada.isNullOrBlank() -> return@Button
                            }

                            val novaDespesa = Despesa(
                                descricao = descricao,
                                valor = valorDouble,
                                data = dataMillis.value?.let { Date(it) } ?: Date(),
                                categoria = categoriaSelecionada ?: "Sem Categoria",
                                pic = getPicCategoria(categoriaSelecionada ?: ""),
                                conta = contaSelecionada.trim(),
                                tipo = tipo
                            )

                            if (parcelasInt > 1) {
                                viewModel.adicionarDespesaParcelada(
                                    novaDespesa,
                                    parcelasInt,
                                    dataMillis.value ?: System.currentTimeMillis()
                                )
                            } else {
                                //onAddDespesa(novaDespesa)
                                viewModel.adicionarDespesa(novaDespesa)
                            }

                            // limpa e fecha
                            descricao = ""
                            valor = ""
                            numeroParcelas = "1"
                            categoriaSelecionada = null
                            dataMillis.value = null
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Adicionar")
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.ActionButton(icon: Int, text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color = LightGray)
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = text
        )
        Text(
            text = text,
            color = Color.Blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
}
