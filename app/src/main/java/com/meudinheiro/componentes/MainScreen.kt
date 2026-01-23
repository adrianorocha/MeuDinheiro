package com.meudinheiro.componentes

import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory
import com.meudinheiro.viewModel.HomeViewModel
import com.meudinheiro.viewModel.HomeViewModelFactory

@Composable
fun MainScreen(userPrefs: UserPreferences) {
    var selectedIndex by remember { mutableStateOf(-1) }
    var contaSelecionada by remember { mutableStateOf("") }

    fun onItemSelected(index: Int) {
        selectedIndex = index
    }

    val context = LocalContext.current
    val repository = remember { MainRepository(context) } //Carrega as Informações do Repository
    val despVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(repository))
    val contaVM: ContaSaldoViewModel = viewModel(
        factory = ContaSaldoViewModelFactory(
            repository
        )
    )
    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val nome by homeVM.userName.collectAsState(initial = "")
    val fotoUri  by homeVM.userPhoto.collectAsState()

    var emCadastro by remember { mutableStateOf(false) }

    var despesasCarregadas by remember { mutableStateOf(false) }

    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    var contaPrincipal = contas.firstOrNull()

    if (nome.isBlank() || emCadastro) {
        CadastroUsuarioScreen(
            userPrefs   = userPrefs,
            onFinished  = { emCadastro = false } // volta para a home
        )
        return
    }
    fun onContaSelecionada(novaConta: String) {
        contaSelecionada = novaConta
        despVM.carregarDespesasPorConta(novaConta)
        Log.d("MainScreen", "Conta selecionada: $contaSelecionada")
    }
    fun atualizarDespesas(conta: ContaSaldoDomain) {
        contaSelecionada = contas.first().conta
        if (contaSelecionada.isNotEmpty() && !despesasCarregadas) {
            despVM.carregarDespesasPorConta(contaSelecionada)
            despesasCarregadas = true
        }
    }

    LaunchedEffect(contas) {
        if (contas.isNotEmpty()) {
            if (contaSelecionada.isEmpty()) {  // Somente se não estiver selecionada
                contaSelecionada = contas.first().conta
                despVM.carregarDespesasPorConta(contaSelecionada)
                Log.d("MainScreen", "Conta selecionada na inicialização: $contaSelecionada")
            }
        }
    }
    LaunchedEffect(contaSelecionada) {
        contaSelecionada?.let { id ->
            despVM.carregarDespesasPorConta(id)
        }
    }

    val despesas by despVM.despesasLiveData.observeAsState(emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            HeaderSection(
                nome = nome,
                fotoUri = fotoUri,
                onProfileClick = { emCadastro = true }
            )

            if (contas.isNotEmpty()) {
                CardSection(
                    contas = contas,
                    viewModelFactory = ContaSaldoViewModelFactory(repository),
                    onExcluir = { conta ->
                        contaVM.removerContaSaldo(conta.id)
                    },
                    contasSelecionadaId = contaSelecionada,
                    onAtualizar = { conta ->
                        atualizarDespesas(conta)
                        despVM.carregarDespesasPorConta(conta.conta)
                    },
                    onContaSelecionada = { novaConta ->
                        onContaSelecionada(novaConta)
                        despVM.carregarDespesasPorConta(novaConta)
                        contaVM.selecionarConta(novaConta)
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
                    contaSelecionada.takeIf { it.isNotEmpty() }?.let {conta ->
                    despVM.adicionarDespesa( nova.copy(conta = conta))
                }},
                getPicCategoria = { nome ->
                    repository.getPicCategoria(nome)
                },
                contaSelecionada = contaSelecionada.orEmpty(),
                viewModelFactory = ContaSaldoViewModelFactory(repository)
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
            if (despesas.isEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    text = "Nenhuma despesa encontrada para esta conta."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                ) {
                    items(despesas) { item ->
                        DespesasItem(item = item,
                            onRemover = { id ->
                                despVM.removerDespesaComRestituicao(id)
                            }
                        )
                    }
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
