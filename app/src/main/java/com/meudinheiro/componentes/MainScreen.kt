package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory

@Composable
fun MainScreen(
    onCardClick: () -> Unit = {}
) {
    var selectedIndex by remember { mutableStateOf(-1) }
    var contaSelecionada by remember { mutableStateOf<ContaSaldoDomain?>(null) }


    fun onItemSelected(index: Int) {
        selectedIndex = index
    }

    val context = LocalContext.current
    val repository = remember { MainRepository(context) } //Carrega as Informações do Repository
    val viewModel: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(repository))
    val viewModelS: ContaSaldoViewModel = viewModel(
        factory = ContaSaldoViewModelFactory(
            repository
        )
    )

    fun atualizarDespesas(conta: ContaSaldoDomain) {
        contaSelecionada = conta
    }

    val despesas by viewModel.despesa.observeAsState(emptyList())
    val contas by viewModelS.contaSaldo.observeAsState(emptyList())

    var contaPrincipal = contas.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            HeaderSection()
            if (contaPrincipal != null && contaPrincipal.saldo <= 0.0) {
                CardSection(
                    contas = contas,
                    viewModelFactory = ContaSaldoViewModelFactory(repository),
                    onExcluir = { conta ->
                        viewModelS.removerContaSaldo(conta.id)
                    },
                    onAtualizar = { conta ->
                        atualizarDespesas(conta)
                    }
                )
            } else {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,

                    text = "Nenhuma Conta Cadastrada"
                )
            }
            ActionButtonRow(
                categorias = repository.categorias.map {
                    it.title
                },
                onAddDespesa = { nova ->
                    viewModel.adicionarDespesa(nova)
                },
                getPicCategoria = { nome ->
                    repository.getPicCategoria(nome)
                },
            )

            if (selectedIndex == 0) {
                ContaBancaria(
                    viewModelFactory = ContaSaldoViewModelFactory(repository),
                    onClose = { selectedIndex = -1 }
                )
            }
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Despesas Recentes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                items(despesas) { item ->
                    DespesasItem(item = item, onRemover = { id ->
                        viewModel.removerDespesa(id)
                    })
                }
            }
        }
        NavigationSection(
            selectedIndex = selectedIndex,
            onItemSelected = ::onItemSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
@Preview(showBackground = true)
fun MainScreenPreview() {
    MainScreen()
}