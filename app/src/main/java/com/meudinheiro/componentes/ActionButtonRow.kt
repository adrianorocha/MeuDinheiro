package com.meudinheiro.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.LightGray
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun ActionButtonRow(
    categorias :List<String>,
    onAddDespesa: (Despesa) -> Unit,
    getPicCategoria: (String) -> String,
    bancoSelecionado: String // novo parâmetro
) {
    val exibirFormulario = remember { mutableStateOf(false) }
    var categoriaSelecionada by remember { mutableStateOf<String?>(null) }
    var expandido by remember { mutableStateOf(false) }
    var tipo by remember { mutableStateOf(TipoDespesa.DEBITO) }
    val openDatePicker = remember { mutableStateOf(false) }

    var data = rememberDatePickerState(initialSelectedDateMillis = null)
    if (openDatePicker.value) {
        DatePickerDialog(
            onDismissRequest = {
                openDatePicker.value = false
            },
            confirmButton = {
                data.selectedDateMillis?.let { selectedDateMillis ->
                    data = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
                    openDatePicker.value = false
                }
            },
            dismissButton = {
                openDatePicker.value = false
            },
        ){
            DatePicker(
                state = data,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ActionButton(
            R.drawable.deposit,
            "Depositar",
            onClick = {}
        )
        ActionButton(
            R.drawable.add,
            "Adicionar",
            onClick = { exibirFormulario.value = true}
        )
        ActionButton(
            R.drawable.sim_chip,
            "Configurações",
            onClick = {}
        )
    }
    if(exibirFormulario.value) {
        /* Adicionar Despesas */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ){
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(400.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Novas Despesas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    var descricao by remember { mutableStateOf("") }
                    var valor by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ){
                        RadioButton(
                            selected = tipo == TipoDespesa.DEBITO,
                            onClick = { tipo = TipoDespesa.DEBITO },
                        )
                        Text( modifier = Modifier.padding(start = 3.dp,top = 18.dp),
                            text = "Débito")
                        RadioButton(
                            selected = tipo == TipoDespesa.CREDITO,
                            onClick = { tipo = TipoDespesa.CREDITO }
                        )
                        Text( modifier = Modifier.padding(start = 3.dp,top = 18.dp),
                            text = "Crédito")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandido,
                            onExpandedChange = { expandido = !expandido }
                        ) {
                            TextField(
                                value = categoriaSelecionada ?: "Categoria",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Categoria") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
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
                    }
                    //Descrição
                    TextField(
                        value = descricao,
                        onValueChange = { descricao = it },
                        label = { Text("Descrição") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        //Valor
                        TextField(
                            value = valor,
                            onValueChange = { valor = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Valor") },
                            modifier = Modifier
                                .width(200.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        TextField(
                            value = data.selectedDateMillis?.let { Date(it).toString() } ?: "",
                            onValueChange = {},
                            label = { Text("Data") },
                            modifier = Modifier
                                .width(200.dp)
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { openDatePicker.value = true }
                        )

                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val valorDouble = valor.toDoubleOrNull() ?: 0.0
                                val novaDespesa = Despesa(
                                    descricao = descricao,
                                    valor = valorDouble,
                                    data = data.selectedDateMillis?.let { Date(it) } ?: Date(),
                                    categoria = categoriaSelecionada ?: "Sem Categoria",
                                    pic = getPicCategoria(categoriaSelecionada ?:""),
                                    conta = bancoSelecionado,
                                    tipo = tipo
                                )

                                onAddDespesa(novaDespesa)

                                // Limpa os campos
                                descricao = ""
                                valor = ""
                                openDatePicker.value = false
                                categoriaSelecionada = null
                                exibirFormulario.value = false
                            },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            Text("Adicionar")
                        }
                        Button(
                            onClick = { exibirFormulario.value = false },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            Text("Cancelar")
                        }
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
            .clip(RoundedCornerShape(10.dp))
            .background(color = LightGray)
            .clickable { onClick() }
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,

        ) {
        Image(painter = painterResource(id = icon),
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
                .padding(top = 4.dp)
        )
    }
}