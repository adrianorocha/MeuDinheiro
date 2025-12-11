package com.meudinheiro.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory

@Composable
fun ContaBancaria(
    viewModelFactory: ContaSaldoViewModelFactory
){
    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)
    var exibirFormulario by remember { mutableStateOf(true) }

    if(exibirFormulario) {
        ContaBancariaForm(
            onAdicionar = {
                banco,
                agencia,
                contaCorrente ->
                val novaconta = ContaSaldo(
                    banco = banco,
                    agencia = agencia,
                    conta = contaCorrente,
                    id = 0,
                    saldo = 0.00,
                    titular = ""
                )
                viewModel.adicionarContaSaldo(novaconta)
            },
            onCancelar = { exibirFormulario = false }
        )
    }
}

@Composable
fun ContaBancariaForm(
    onAdicionar: (
            banco: String,
            agencia: String,
            contaCorrente: String
            ) -> Unit,
    onCancelar: () -> Unit
){
    var banco by remember { mutableStateOf("") }
    var agencia by remember { mutableStateOf("") }
    var contaCorrente by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ){
        Surface(
            color = Color.LightGray,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(230.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {}
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
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
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    //Agência
                    TextField(
                        value = agencia,
                        onValueChange = { agencia = it },
                        label = { Text("Agência") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    //Conta Corrente
                    TextField(
                        value = contaCorrente,
                        onValueChange = { contaCorrente = it },
                        label = { Text("Conta Corrente") },
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
                            onClick = {
                                onAdicionar(
                                    banco,
                                    agencia,
                                    contaCorrente
                                )
                                banco = ""
                                agencia = ""
                                contaCorrente=""
                            }
                        ) {
                            Text( text = "Adicionar")
                        }
                        Button(
                            onClick = onCancelar,
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
    }
}