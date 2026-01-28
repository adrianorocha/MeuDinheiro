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
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory
import com.meudinheiro.viewModel.HomeViewModel
import com.meudinheiro.viewModel.HomeViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
    userPrefs: UserPreferences,
    onOpenAvisos: () -> Unit,
    onOpenPendencias: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf(-1) }
    fun onItemSelected(index: Int) { selectedIndex = index }

    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val onlyCredit by userPrefs.notifOnlyCreditFlow.collectAsState(initial = false)

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
    var notifCount by remember { mutableStateOf(0) }

    LaunchedEffect(daysAhead, onlyCredit) {
        notifCount = withContext(Dispatchers.IO) {
            repository.contarPendencias(daysAhead, onlyCredit = false)
        }
    }
    // Se a tela de avisos está aberta, mostra ela em tela cheia e não desenha mais nada da Home

    LaunchedEffect(contas, contaSelecionadaId) {
        if (contas.isEmpty()) return@LaunchedEffect

        val selected = contaSelecionadaId?.trim().orEmpty()
        val exists = selected.isNotBlank() && contas.any { it.conta == selected }

        if (!exists) {
            val first = contas.first().conta
            contaVM.selecionarConta(first)
        }
    }

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
                hasUnreadNotifications = (notifCount > 0),
                notificationCount = notifCount,
                onNotificationsClick = onOpenPendencias
            )

            if (contas.isNotEmpty()) {
                CardSection(
                    contas = contas,
                    contasSelecionadaId = contaSelecionadaId?.orEmpty(),
                    onExcluir = { conta -> contaVM.removerContaSaldo(conta.id) },
                    onContaSelecionada = { novaConta -> contaVM.selecionarConta(novaConta) },
                    onAtualizar = { conta -> contaVM.selecionarConta(conta.conta) }
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
                viewModel = contaVM,
                onConfigClick = onOpenAvisos
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
                        DespesasItem(
                            item = item,
                            onRemover = { id -> despVM.removerDespesaComRestituicao(id) },
                            onTogglePago = { id, pago -> despVM.marcarComoPaga(id, pago) }
                        )
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
