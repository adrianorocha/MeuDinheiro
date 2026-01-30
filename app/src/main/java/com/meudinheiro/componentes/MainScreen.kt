package com.meudinheiro.componentes

import android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.runtime.key
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight

// Cores Globais Premium
val PremiumDarkBlue = Color(0xFF0D1B2A)
val PremiumLightBlue = Color(0xFF1B263B)
val TextWhite = Color(0xFFE0E1DD)

@Composable
fun MainScreen(
    userPrefs: UserPreferences,
    onOpenAvisos: () -> Unit,
    onOpenPendencias: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf(-1) }
    fun onItemSelected(index: Int) {
        selectedIndex = index
    }

    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)

    val context = LocalContext.current
    val repository = remember { MainRepository(context) }
    val despVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(repository))
    val contaVM: ContaSaldoViewModel = viewModel(factory = ContaSaldoViewModelFactory(repository))
    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))

    val nomeState by homeVM.userName.collectAsState(initial = null)
    val fotoSalva by homeVM.userPhoto.collectAsState(initial = "")
    var emCadastro by remember { mutableStateOf(false) }

    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null)

    val dashboardState by contaVM.dashboardState.collectAsState()

    val nome = nomeState

    if (nome == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PremiumDarkBlue)
        }
        return // Interrompe a execução até carregar o nome
    }

    if (nome!!.isBlank() || emCadastro) {
        CadastroUsuarioScreen(
            userPrefs = userPrefs,
            onFinished = { emCadastro = false }
        )
        return
    }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    var notifCount by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) { contaVM.carregarResumoFinanceiro() }
    LaunchedEffect(Unit) { contaVM.carregarResumoFinanceiro() }
    LaunchedEffect(daysAhead, refreshTrigger) {
        notifCount = withContext(Dispatchers.IO) {
            repository.contarPendencias(daysAhead, onlyCredit = false)
        }
    }

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumDarkBlue, PremiumLightBlue)
                )
            )
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
                onNotificationsClick = onOpenPendencias,
                receitaTotal = dashboardState.receitaGlobal,
                despesaTotal = dashboardState.despesaGlobal
            )

            if (contas.isNotEmpty()) {
                key(dashboardState) {
                    CardSection(
                        contas = contas,
                        contasSelecionadaId = contaSelecionadaId?.orEmpty(),
                        onExcluir = { conta ->
                            contaVM.removerContaSaldo(conta.id)
                            contaVM.carregarResumoFinanceiro()
                        },
                        onContaSelecionada = { novaConta ->
                            contaVM.selecionarConta(novaConta)
                            contaVM.carregarResumoFinanceiro()
                        },
                        onAtualizar = { conta ->
                            contaVM.selecionarConta(conta.conta)
                            contaVM.carregarResumoFinanceiro()
                        },
                        getReceitaConta = { contaId -> contaVM.obterReceitaPorConta(contaId) },
                        getDespesaConta = { contaId -> contaVM.obterDespesaPorConta(contaId) }
                    )
                }
            } else {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite.copy(alpha = 0.5f),
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

/*            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )
            // --- SELETOR DE MÊS ---
 */
            val mesAtual by despVM.mesSelecionado.collectAsState()
            val anoAtual by despVM.anoSelecionado.collectAsState()

            // Array auxiliar para nome dos meses
            val meses = remember { listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro") }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp), // Padding ajustado
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Botão Voltar Mês
                IconButton(onClick = { despVM.mesAnterior() }) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft, // Use um icone de seta esquerda (ou Icons.Rounded.ChevronLeft)
                        contentDescription = "Anterior",
                        tint = TextWhite
                    )
                }

                // Texto Mês/Ano
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = meses.getOrElse(mesAtual) { "" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "$anoAtual",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextWhite.copy(alpha = 0.6f)
                    )
                }

                // Botão Avançar Mês
                IconButton(onClick = { despVM.proximoMes() }) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight, // Use um icone de seta direita (ou Icons.Rounded.ChevronRight)
                        contentDescription = "Próximo",
                        tint = TextWhite
                    )
                }
            }
            // ---------------------

            // OBSERVAÇÃO DA LISTA FILTRADA
            val despesasFiltradas by despVM.despesasFiltradas.collectAsState()

            if (despesasFiltradas.isEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextWhite.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    text = "Nenhuma movimentação neste mês."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
                ) {
                    items(despesasFiltradas, key = { it.id }) { item ->
                        DespesasItem(
                            item = item,
                            onRemover = { id ->
                                contaVM.removerDespesa(id)
                            },
                            onTogglePago = { itemClicado ->
                                contaVM.alternarStatusDespesa(itemClicado)
                            }
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