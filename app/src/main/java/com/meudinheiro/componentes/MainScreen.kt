package com.meudinheiro.componentes

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    fun onItemSelected(index: Int) { selectedIndex = index }

    val context = LocalContext.current
    val repository = remember { MainRepository(context) }

    val despVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(repository))
    val contaVM: ContaSaldoViewModel = viewModel(factory = ContaSaldoViewModelFactory(repository))
    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))

    val nome by homeVM.userName.collectAsState(initial = "")
    val fotoSalva by homeVM.userPhoto.collectAsState(initial = "")
    var emCadastro by remember { mutableStateOf(false) }

    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null)

    if (nome.isBlank() || emCadastro) {
        CadastroUsuarioScreen(
            userPrefs = userPrefs,
            onFinished = { emCadastro = false }
        )
        return
    }

    // Se não tem selecionada (ou ficou inválida), seleciona a primeira
    LaunchedEffect(contas, contaSelecionadaId) {
        if (contas.isEmpty()) return@LaunchedEffect

        val selected = contaSelecionadaId?.trim().orEmpty()
        val exists = selected.isNotBlank() && contas.any { it.conta == selected }

        if (!exists) {
            val first = contas.first().conta
            contaVM.selecionarConta(first)
        }
    }

    // Carrega despesas somente quando a conta selecionada mudar
    LaunchedEffect(contaSelecionadaId) {
        val id = contaSelecionadaId?.trim().orEmpty()
        if (id.isNotBlank()) {
            despVM.setContaSelecionada(contaSelecionadaId.orEmpty())
        }
    }

    val despesas by despVM.despesasLiveData.observeAsState(emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            HeaderSection(
                nome = nome,
                fotoUri = fotoSalva.takeIf { it.isNotBlank() },
                onProfileClick = { emCadastro = true },
                chipText = "Sincronizado",
                chipStyle = HeaderChipStyle.SUCCESS,
                showNotifications = true,
                hasUnreadNotifications = false,
                onNotificationsClick = {
                }
            )

            if (contas.isNotEmpty()) {
                CardSection(
                    contas = contas,
                    contasSelecionadaId = contaSelecionadaId?.orEmpty(),
                    onExcluir = { conta ->
                        contaVM.removerContaSaldo(conta.id)
                    },
                    onContaSelecionada = { novaConta ->
                        contaVM.selecionarConta(novaConta)
                    },
                    onAtualizar = { conta ->
                        // Quando o CardSection detecta o card central ao parar de rolar,
                        // ele deve apenas refletir isso na seleção.
                        contaVM.selecionarConta(conta.conta)
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
                categorias = repository.categorias.map { it.title },
                getPicCategoria = { nomeCategoria -> repository.getPicCategoria(nomeCategoria) },
                contaSelecionada = contaSelecionadaId.orEmpty(),
                viewModel = contaVM
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                    items(despesas, key = { it.id }) { item ->
                        DespesasItem(item = item, onRemover = { id -> despVM.removerDespesaComRestituicao(id) })
                    }
                }
            }
        }

        NavigationSection(
            selectedIndex = selectedIndex,
            onItemSelected = ::onItemSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
