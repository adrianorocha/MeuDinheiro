package com.meudinheiro.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.R
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import kotlinx.coroutines.launch
import java.util.Locale


/*@OptIn(ExperimentalMaterial3Api::class)
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
                    viewModel.atualizarSaldo(conta.banco, novoSaldo)
                }
            )
        }
    }
}*/
/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaSaldoCard(
    conta: ContaSaldoDomain,
    onSalvar: (Double) -> Unit
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
                            val novo = textoSaldo.toDoubleOrNull() ?: Double
                            onSalvar(novo as Double)
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
}*/

@Composable
//@Preview(showBackground = true)
fun CardSection(
    contas: List<ContaSaldoDomain>,
    contasSelecionadaId: String?,
    viewModelFactory: ContaSaldoViewModelFactory,
    onExcluir: (ContaSaldoDomain) -> Unit,
    onContaSelecionada: (String) -> Unit,
    onAtualizar: (ContaSaldoDomain) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val showDialog = remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)

    LazyRow(
        state = lazyListState,
        modifier = Modifier
            .padding(16.dp)
            .height(210.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
                flingBehavior = rememberSnapFlingBehavior(lazyListState)
    ) {
        items(contas, key = {it.conta}) { conta ->
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)//Usa 80% da Largura da Tela
                    .clickable {
                        onContaSelecionada(conta.conta)
                        onAtualizar(conta)
                    }
                    .height(210.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    if (conta.conta == contasSelecionadaId) {
                        Color.Green // A conta selecionada pode ter uma cor diferente
                    } else {
                        onContaSelecionada(conta.conta)
                        contasSelecionadaId.takeIf { it == conta.conta }?.let {
                            onAtualizar(conta)
                        }
                        Color.White
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(230.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = {
                                onAtualizar(conta)
                            },
                            onLongClick = {
                                coroutineScope.launch {
                                    onExcluir(conta)
                                    showDialog.value = true
                                }
                            }
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.card_tecno),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = conta.banco,
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
                            .padding(top = 25.dp, start = 315.dp)
                    )
                    Text(
                        text = "Agência :  ${conta.agencia} - C/C : ${conta.conta}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(start = 12.dp, bottom = 18.dp, end = 8.dp)
                    )

                    Text(
                        text = "R$ ${String.format(Locale("pt", "BR"), "%.2f", conta.saldo)}",
                        color = Color.Yellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 18.dp, end = 8.dp)
                    )
                    if (showDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showDialog.value = false },
                            title = { Text(text = "Excluir Conta") },
                            text = { Text(text = "Deseja excluir esta conta?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        onExcluir(conta)
                                        showDialog.value = false
                                    }
                                ) {
                                    Text(text = "Sim")
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = { showDialog.value = false }
                                ) {
                                    Text(text = "Não")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}