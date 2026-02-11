package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory
import com.meudinheiro.viewModel.HomeViewModel
import com.meudinheiro.viewModel.HomeViewModelFactory
import com.meudinheiro.viewModel.OrcamentoViewModel
import com.meudinheiro.viewModel.OrcamentoViewModelFactory
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
    val orcamentoVM: OrcamentoViewModel = viewModel(factory = OrcamentoViewModelFactory(repository))
    var orcamentoSelecionado by remember { mutableStateOf<OrcamentoProgresso?>(null) }

    // --- Estados (Preferences & User) ---
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val isPrivate by userPrefs.privateModeFlow.collectAsState(initial = false)

    val nomeState by homeVM.userName.collectAsState(initial = null)
    val fotoSalva by homeVM.userPhoto.collectAsState(initial = "")
    var emCadastro by remember { mutableStateOf(false) }
    var showAddOrcamentoDialog by remember { mutableStateOf(false) }

    // --- Estados de Dados ---
    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null)
    val dashboardState by contaVM.dashboardState.collectAsState()

    val mesAtual by despVM.mesSelecionado.collectAsState()
    val anoAtual by despVM.anoSelecionado.collectAsState()

    val despesasFiltradas by despVM.despesasFiltradas.collectAsState()
    val orcamentosComProgresso by orcamentoVM.orcamentosComProgresso.collectAsState()

    var selectedIndex by remember { mutableStateOf(-1) }
    fun onItemSelected(index: Int) {
        selectedIndex = index
    }

    // --- Verificações de Inicialização ---
    if (nomeState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PremiumDarkBlue)
        }
        return
    }

    val nome = nomeState
    if (nome!!.isBlank() || emCadastro) {
        CadastroUsuarioScreen(
            userPrefs = userPrefs,
            onBack = { emCadastro = false },
            onFinished = { emCadastro = false })
        return
    }

    // --- Lógica de Notificações ---
    var notifCount by remember { mutableStateOf(0) }
    LaunchedEffect(daysAhead) {
        notifCount = withContext(Dispatchers.IO) {
            repository.contarPendencias(daysAhead, onlyCredit = false)
        }
    }

    // --- LÓGICA DE SELEÇÃO DE CONTA ---
    LaunchedEffect(contas) {
        if (contas.isNotEmpty()) {
            val current = contaSelecionadaId
            if (current.isNullOrBlank() || contas.none { it.conta == current }) {
                contaVM.selecionarConta(contas.first().conta)
            }
        }
    }

    LaunchedEffect(contaSelecionadaId) {
        val idParaFiltro = contaSelecionadaId?.trim().orEmpty()
        despVM.setContaSelecionada(idParaFiltro)
        contaVM.carregarSaldosGlobais()
    }

    // --- UI Principal ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(PremiumDarkBlue, PremiumLightBlue)))
    ) {

        // O SCAFFOLD GARANTE QUE A LISTA NÃO FIQUE EMBAIXO DO MENU
        Scaffold(
            containerColor = Color.Transparent, // Mantém o fundo do Box visível
            bottomBar = {
                NavigationSection(
                    selectedIndex = selectedIndex,
                    onItemSelected = ::onItemSelected
                )
            }
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding) // <-- O segredo do scroll responsivo está aqui
            ) {

                // 1. HEADER (Fixo no topo)
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
                    despesaTotal = dashboardState.despesaGlobal,
                    isPrivateMode = isPrivate,
                    onTogglePrivate = { scope.launch { userPrefs.togglePrivateMode() } }
                )
                // Item: Resumo Geral
                ResumoGeralCard(
                    receitaTotal = dashboardState.receitaGlobal,
                    despesaTotal = dashboardState.despesaGlobal,
                    isPrivate = isPrivate
                )

                // 2. TUDO O QUE ROLA FICA NA LAZYCOLUMN
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp) // Reduzi o bottom já que o Scaffold resolve o menu
                ) {


                    // Item: Carrossel de Contas
                    item {
                        if (contas.isNotEmpty()) {
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
                                        contaVM.selecionarConta(novaContaId)
                                    },
                                    onAtualizar = {},
                                    getReceitaConta = { id -> contaVM.obterReceitaPorConta(id) },
                                    getDespesaConta = { id -> contaVM.obterDespesaPorConta(id) }
                                )
                            }
                        } else {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                text = "Nenhuma Conta Cadastrada"
                            )
                        }
                    }

                    // Item: Ações Rápidas
                    item {
                        Box(modifier = Modifier.padding(top = 8.dp)) { // Mais perto do carrossel
                            ActionButtonRow(
                                categorias = repository.categorias.map { it.title },
                                getPicCategoria = { nomeCategoria ->
                                    repository.getPicCategoria(
                                        nomeCategoria
                                    )
                                },
                                contaSelecionada = contaSelecionadaId.orEmpty(),
                                viewModel = contaVM,
                                onConfigClick = onOpenAvisos
                            )
                        }
                    }

                    // --- SESSÃO: ORÇAMENTOS ---
                    item {
                        Text(
                            text = "Orçamentos do Mês",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    item {
                        // Quebra a lista em grupos de até 4 orçamentos
                        val gruposDeQuatro = orcamentosComProgresso.chunked(4)

                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            gruposDeQuatro.forEach { linhaDeOrcamentos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Espaço entre os cards
                                ) {
                                    // Desenha os orçamentos daquela linha
                                    linhaDeOrcamentos.forEach { orcamento ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            OrcamentoCard(
                                                item = orcamento,
                                                onClick = { orcamentoSelecionado = orcamento })
                                        }
                                    }
                                    // Se a linha tiver menos de 4 itens, cria "espaços fantasmas" para alinhar
                                    repeat(4 - linhaDeOrcamentos.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp)) // Espaço para a linha de baixo
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showAddOrcamentoDialog = true },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Configurar Teto de Gasto")
                        }
                    }

                    // --- SESSÃO: SELETOR DE MÊS ---
                    item {
                        val meses = remember {
                            listOf(
                                "Janeiro",
                                "Fevereiro",
                                "Março",
                                "Abril",
                                "Maio",
                                "Junho",
                                "Julho",
                                "Agosto",
                                "Setembro",
                                "Outubro",
                                "Novembro",
                                "Dezembro"
                            )
                        }
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
                    }

                    // --- SESSÃO: LISTA DE DESPESAS ---
                    if (despesasFiltradas.isEmpty()) {
                        item {
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
                        }
                    } else {
                        items(despesasFiltradas, key = { it.id }) { item ->
                            DespesasItem(
                                item = item,
                                isPrivate = isPrivate,
                                onRemover = { despesa ->
                                    contaVM.removerDespesa(despesa)
                                },
                                onTogglePago = { itemClicado ->
                                    contaVM.alternarStatusDespesa(itemClicado)
                                }
                            )
                        }
                    }
                } // Fim da LazyColumn
            }
        } // Fim do Scaffold

        // --- CAMADAS SOBREPOSTAS (Dialogs) ---
        // Fora do Scaffold para preencher a tela inteira quando abertos
        orcamentoSelecionado?.let { orcamento ->
            DetalheOrcamentoBottomSheet(
                item = orcamento,
                onDismiss = { orcamentoSelecionado = null },
                onExcluir = {
                    orcamentoVM.excluirOrcamento(orcamento.categoria)
                    orcamentoSelecionado = null
                },
                onEditar = { novoValor ->
                    // AGORA CHAMA A FUNÇÃO DE ATUALIZAR, NÃO A DE SALVAR!
                    orcamentoVM.atualizarOrcamento(orcamento.categoria, novoValor)
                }
            )
        }

        if (showAddOrcamentoDialog) {
            AddOrcamentoDialog(
                categoriasDisponiveis = repository.categorias.map { it.title },
                onSalvar = { categoria, valor ->
                    orcamentoVM.salvarOrcamento(categoria, valor)
                },
                onDismiss = { showAddOrcamentoDialog = false }
            )
        }

        if (selectedIndex == 0) {
            ContaBancaria(
                viewModelFactory = ContaSaldoViewModelFactory(repository),
                onClose = { selectedIndex = -1 }
            )
        }
    }
}