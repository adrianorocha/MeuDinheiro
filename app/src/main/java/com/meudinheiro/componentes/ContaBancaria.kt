package com.meudinheiro.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory

@Composable
@Preview(showBackground = true)
fun ContaBancaria(
    viewModelFactory: ContaSaldoViewModelFactory
){
    var conta: ContaSaldoDomain
    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)
    val exibirFormulario = remember { mutableStateOf(false) }

    if(exibirFormulario.value) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .height(230.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .clickable{}
        ) {
            var banco by remember { mutableStateOf("") }
            var agencia by remember { mutableStateOf("") }
            var contaCorrente by remember { mutableStateOf("") }
            var saldo by remember { mutableStateOf(0.00) }

            //Descrição
            TextField(
                value = banco,
                onValueChange = { banco = it },
                label = { Text("Banco") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ){
                Button(
                    onClick = { exibirFormulario.value = false },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Text( text = "Cancelar")
                }

            }
        }
    }
}