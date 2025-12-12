package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory

@Composable
fun ContaBancaria(
    viewModelFactory: ContaSaldoViewModelFactory,
    onClose: () -> Unit
) {
    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)
    val contasExistentes by viewModel.contaSaldo.observeAsState(emptyList())
    val context = LocalContext.current
    var exibirFormulario by remember { mutableStateOf(true) }

    val bancoList = viewModel.bancos.value
    val bancosNomes = bancoList.map { it.nome }

    if (exibirFormulario) {
        ContaBancariaForm(
            bancos = bancosNomes,
            onAdicionar = { banco,
                            agencia,
                            contaCorrente ->
                val exists = contasExistentes.any { domain ->
                    domain.banco.equals(banco.trim(), true) &&
                            domain.agencia.equals(agencia.trim(), true) &&
                            domain.conta.equals(contaCorrente.trim(), true)
                }
                if (exists) {
                    Toast.makeText(context, "Essa conta já foi cadastrada", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    val novaconta = ContaSaldo(
                        banco = banco,
                        agencia = agencia,
                        conta = contaCorrente,
                        id = 0,
                        saldo = 0.00,
                        titular = ""
                    )
                    viewModel.adicionarContaSaldo(novaconta)
                    exibirFormulario = false
                    onClose()
                }
            },
            onCancelar = {
                exibirFormulario = false
                onClose()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaBancariaForm(
    bancos: List<String>,
    onAdicionar: (
        banco: String,
        agencia: String,
        contaCorrente: String
    ) -> Unit,
    onCancelar: () -> Unit
) {
    var agencia by remember { mutableStateOf("") }
    var bancoSelecionado by remember { mutableStateOf("") }
    var contaCorrente by remember { mutableStateOf("") }
    var expandido by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            color = Color.LightGray,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(150.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {}
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    //Descrição
                    ExposedDropdownMenuBox(
                        expanded = expandido,
                        onExpandedChange = { expandido = !expandido }
                    ) {
                        TextField(
                            value = bancoSelecionado?: "Banco",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Banco") },
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
                            bancos.forEach { nomeBanco ->
                                DropdownMenuItem(
                                    text = { Text(nomeBanco) },
                                    onClick = {
                                        bancoSelecionado = nomeBanco
                                        expandido = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ){
                        //Agência
                        TextField(
                            value = agencia,
                            onValueChange = { agencia = it },
                            label = { Text("Agência") },
                            modifier = Modifier
                                .weight(1f)
                                .width(1.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                            )
                        //Conta Corrente
                        TextField(
                            value = contaCorrente,
                            onValueChange = { contaCorrente = it },
                            label = { Text("Conta") },
                            modifier = Modifier
                                .weight(1f)
                                .width(18.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Button(
                            onClick = {
                                onAdicionar(
                                    bancoSelecionado,
                                    agencia,
                                    contaCorrente
                                )
                                bancoSelecionado = ""
                                agencia = ""
                                contaCorrente = ""
                            }
                        ) {
                            Text(text = "Adicionar")
                        }
                        Button(
                            onClick = onCancelar,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            Text(text = "Cancelar")
                        }
                    }
                }
            }
        }
    }
}