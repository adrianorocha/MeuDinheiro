package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- ViewModels ---
    val repository = remember { MainRepository(context) }
    val despVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(repository))
    val contaVM: ContaSaldoViewModel = viewModel(factory = ContaSaldoViewModelFactory(repository))
    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))

    // --- Estados (Preferences & User) ---
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val isPrivate by userPrefs.privateModeFlow.collectAsState(initial = false)

    val nomeState by homeVM.userName.collectAsState(initial = null)
    val fotoSalva by homeVM.userPhoto.collectAsState(initial = "")
    var emCadastro by remember { mutableStateOf(false) }

    // --- Estados de Dados (Conta, Despesa, Dashboard) ---
    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null) // ID da conta atual
    val dashboardState by contaVM.dashboardState.collectAsState()

    val mesAtual by despVM.mesSelecionado.collectAsState()
    val anoAtual by despVM.anoSelecionado.collectAsState()

    var selectedIndex by remember { mutableStateOf(-1) }
    fun onItemSelected(index: Int) { selectedIndex = index }

    // --- Verificações de Inicialização ---
    if (nomeState == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PremiumDarkBlue)
        }
        return
    }

    val nome = nomeState
    if (nome!!.isBlank() || emCadastro) {
        CadastroUsuarioScreen(userPrefs = userPrefs, onFinished = { emCadastro = false })
        return
    }

    // --- Lógica de Notificações ---
    var notifCount by remember { mutableStateOf(0) }
    LaunchedEffect(daysAhead) {
        notifCount = withContext(Dispatchers.IO) {
            repository.contarPendencias(daysAhead, onlyCredit = false)
        }
    }

    // --- LÓGICA CRÍTICA DE SELEÇÃO DE CONTA ---
    // 1. Se carregar contas e nenhuma estiver selecionada, seleciona a primeira.
    LaunchedEffect(contas) {
        if (contas.isNotEmpty()) {
            val current = contaSelecionadaId
            // Se nulo ou se a conta selecionada não existe mais na lista
            if (current.isNullOrBlank() || contas.none { it.conta == current }) {
                contaVM.selecionarConta(contas.first().conta)
            }
        }
    }

    // 2. A PONTE: Sempre que o ID da conta mudar (no ContaVM), avisa o DespesasVM para filtrar
    LaunchedEffect(contaSelecionadaId) {
        val idParaFiltro = contaSelecionadaId?.trim().orEmpty()
        despVM.setContaSelecionada(idParaFiltro)

        // Opcional: Recarrega o resumo se necessário, embora o ContaVM já deva fazer isso nas ações
        contaVM.carregarSaldosGlobais()
    }

    // --- UI Principal ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(PremiumDarkBlue, PremiumLightBlue)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. HEADER
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
                // Passamos totais globais
                receitaTotal = dashboardState.receitaGlobal,
                despesaTotal = dashboardState.despesaGlobal,
                // Modo Privado
                isPrivateMode = isPrivate,
                onTogglePrivate = { scope.launch { userPrefs.togglePrivateMode() } }
            )

            // 2. RESUMO GERAL (Patrimônio)
            ResumoGeralCard(
                receitaTotal = dashboardState.receitaGlobal,
                despesaTotal = dashboardState.despesaGlobal,
                isPrivate = isPrivate // Conectado ao Modo Privado
            )

            // 3. CARDS DE CONTAS (Carrossel)
            if (contas.isNotEmpty()) {
                // Key força recomposição se o dashboard mudar
                key(dashboardState, contaSelecionadaId) {
                    CardSection(
                        contas = contas,
                        contasSelecionadaId = contaSelecionadaId,
                        isPrivate = isPrivate,
                        onExcluir = { conta ->
                            contaVM.removerContaSaldo(conta.id)
                            contaVM.carregarSaldosGlobais()
                        },
                        onContaSelecionada = { novaContaId ->
                            // Atualiza no VM de Conta -> Dispara o LaunchedEffect acima -> Atualiza Filtro
                            contaVM.selecionarConta(novaContaId)
                        },
                        onAtualizar = {
                            // Apenas para garantir UI update
                        },
                        getReceitaConta = { id -> contaVM.obterReceitaPorConta(id) },
                        getDespesaConta = { id -> contaVM.obterDespesaPorConta(id) }
                    )
                }
            } else {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    text = "Nenhuma Conta Cadastrada"
                )
            }

            // 4. AÇÕES RÁPIDAS
            ActionButtonRow(
                categorias = repository.categorias.map { it.title },
                getPicCategoria = { nomeCategoria -> repository.getPicCategoria(nomeCategoria) },
                contaSelecionada = contaSelecionadaId.orEmpty(),
                viewModel = contaVM,
                onConfigClick = onOpenAvisos
            )

            // Janela de Cadastro de Conta (se selecionado no menu inferior)
            if (selectedIndex == 0) {
                ContaBancaria(
                    viewModelFactory = ContaSaldoViewModelFactory(repository),
                    onClose = { selectedIndex = -1 }
                )
            }

            // 5. SELETOR DE MÊS
            val meses = remember { listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro") }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { despVM.mesAnterior() }) {
                    Icon(Icons.Rounded.ChevronLeft, "Anterior", tint = TextWhite)
                }
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
                IconButton(onClick = { despVM.proximoMes() }) {
                    Icon(Icons.Rounded.ChevronRight, "Próximo", tint = TextWhite)
                }
            }

            // 6. LISTA DE DESPESAS (FILTRADA)
            val despesasFiltradas by despVM.despesasFiltradas.collectAsState()

            if (despesasFiltradas.isEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextWhite.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    text = "Nenhuma movimentação neste mês."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(despesasFiltradas, key = { it.id }) { item ->
                        DespesasItem(
                            item = item,
                            isPrivate = isPrivate, // Conectado ao Modo Privado
                            onRemover = { despesa ->
                                contaVM.removerDespesa(despesa)
                                // Pequeno delay ou refresh pode ser necessário dependendo da velocidade do banco
                            },
                            onTogglePago = { itemClicado ->
                                contaVM.alternarStatusDespesa(itemClicado)
                            }
                        )
                    }
                }
            }
        }

        // MENU INFERIOR
        NavigationSection(
            selectedIndex = selectedIndex,
            onItemSelected = ::onItemSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}