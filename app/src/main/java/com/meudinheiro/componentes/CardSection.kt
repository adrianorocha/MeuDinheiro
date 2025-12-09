package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSection(
    viewModelFactory: ContaSaldoViewModelFactory   // ou sua factory
) {
    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)
    val contas by viewModel.contaSaldo.observeAsState(emptyList())

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(contas, key = { it.id }) { conta ->
            ContaSaldoCard(
                conta = conta,
                onSalvar = { novoSaldo ->
                    viewModel.atualizarSaldo(conta, novoSaldo)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaSaldoCard(
    conta: ContaSaldoDomain,
    onSalvar: (BigDecimal) -> Unit
) {
    var textoSaldo by remember(conta) { mutableStateOf(conta.saldo.toString()) }
    var editando by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(120.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = conta.banco,
                style = MaterialTheme.typography.titleSmall
            )

            if (editando) {
                OutlinedTextField(
                    value = textoSaldo,
                    onValueChange = { textoSaldo = it },
                    label = { Text("Saldo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            editando = false
                            val novo = textoSaldo.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            onSalvar(novo)
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Salvar")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "R$ ${conta.saldo}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { editando = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                }
            }
        }
    }
}

/*@Composable
@Preview(showBackground = true)
fun CardSection(
    onClick: () -> Unit ={}
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .height(230.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable{onClick()}
    ) {
        Image(
            painter = painterResource(id = R.drawable.card_tecno),
            contentDescription = null, 
            modifier = Modifier
                .matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            text = "Nubank",
            color = Color.Yellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, bottom = 16.dp)
        )

        Image(
            painter = painterResource(id = R.drawable.sim_chip_2),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top=25.dp,start = 315.dp)
        )
        Text(
            text = "Agência : 00001 - C/C : 00000000000",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(start = 12.dp, bottom = 18.dp, end = 8.dp)
        )

        Text(
            text = "Saldo R$ 1.000,00",
            color = Color.Yellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 18.dp, end = 8.dp)
        )
    }
}*/