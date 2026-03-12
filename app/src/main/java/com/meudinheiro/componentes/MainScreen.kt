package com.meudinheiro.componentes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.data.PieChartData
import com.meudinheiro.funcoes.CompactCategoryGrid
import com.meudinheiro.funcoes.EmptyStateSection
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.gerarCorParaCategoria
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// --- Cores Globais Premium ---
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
    val parentScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ==========================================
    // 1. INJEÇÃO DE DEPENDÊNCIAS E VIEWMODELS
    // ==========================================
    val application = context.applicationContext as Application
    val repository = remember {
        val db = AppDatabase.getDatabase(context)
        MainRepository(context, db.contaSaldoDao())
    }
    val db = AppDatabase.getDatabase(context)

    val despVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(repository))
    val contaVM: ContaSaldoViewModel = viewModel(factory = ContaSaldoViewModelFactory(application, repository))
    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val orcamentoVM: OrcamentoViewModel = viewModel(factory = OrcamentoViewModelFactory(repository))
    val metaVM: MetaViewModel = viewModel(factory = MetaViewModelFactory(repository))
    val investimentoVM: InvestimentoViewModel = viewModel(factory = InvestimentoViewModelFactory(db.investimentoDao()))
    val transacaoVM: TransacaoViewModel = viewModel(factory = TransacaoViewModelFactory(db.transacaoDao()))

    // ==========================================
    // 2. ESTADOS DE PREFERÊNCIAS E USUÁRIO
    // ==========================================
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val isPrivate by userPrefs.privateModeFlow.collectAsState(initial = false)
    val nomeState by homeVM.userName.collectAsState(initial = null)
    val fotoSalva by homeVM.userPhoto.collectAsState(initial = "")
    var emCadastro by remember { mutableStateOf(false) }

    // ==========================================
    // 3. ESTADOS DE CONTROLE DE TELA (UI)
    // ==========================================
    var mainTabSelecionada by remember { mutableIntStateOf(0) }
    val mainTabs = remember { listOf("Saldo", "Contas", "Cofrinhos", "Investimentos") }
    var selectedIndex by remember { mutableIntStateOf(-1) } // Controle das telas cheias sobrepostas

    // Controles de Dialogs
    var showAddDespesaDialog by remember { mutableStateOf(false) }
    var showAddOrcamentoDialog by remember { mutableStateOf(false) }
    var showRecorrenciaDialog by remember { mutableStateOf(false) }
    var showInvestDialog by remember { mutableStateOf(false) }
    var showTransferenciaDialog by remember { mutableStateOf(false) }
    var orcamentoSelecionado by remember { mutableStateOf<OrcamentoProgresso?>(null) }

    // ==========================================
    // 4. ESTADOS DE DADOS (FLUXOS DO BANCO)
    // ==========================================
    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null)
    val dashboardState by contaVM.dashboardState.collectAsState()
    val resumo by contaVM.resumoFinanceiro.collectAsState()
    val totalMetas by contaVM.totalPoupado.collectAsState()
    val agendados by contaVM.agendamentosFiltrados.collectAsState()

    val mesAtual by despVM.mesSelecionado.collectAsState()
    val anoAtual by despVM.anoSelecionado.collectAsState()
    val despesasFiltradas by despVM.despesasFiltradas.collectAsState()
    val saidasMesAnterior by despVM.getDespesaMesAnterior(mesAtual, anoAtual).collectAsState(initial = 0.0)

    val orcamentosComProgresso by orcamentoVM.orcamentosComProgresso.collectAsState()
    val listaTransacoes by transacaoVM.ultimasTransacoes.collectAsState()
    val rendimentoTotal by investimentoVM.rendimentoTotal.collectAsState()

    val filtroAtivo = contaVM.filtroAtual

    // ==========================================
    // 5. VERIFICAÇÕES DE INICIALIZAÇÃO E SPLASH
    // ==========================================
    if (nomeState == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = PremiumDarkBlue) }
        return
    }

    val nome = nomeState
    if (nome!!.isBlank() || emCadastro) {
        CadastroUsuarioScreen(
            userPrefs = userPrefs,
            onBack = { emCadastro = false },
            onFinished = { emCadastro = false }
        )
        return
    }

    // ==========================================
    // 6. EFEITOS COLATERAIS (LAUNCHED EFFECTS)
    // ==========================================
    var notifCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(daysAhead) {
        notifCount = withContext(Dispatchers.IO) { repository.contarPendencias(daysAhead, onlyCredit = false) }
    }

    LaunchedEffect(contas, filtroAtivo) {
        if (contas.isNotEmpty()) {
            val current = contaSelecionadaId
            if (current.isNullOrBlank() || contas.none { it.conta == current }) {
                contaVM.selecionarConta(contas.first().conta)
            }
        }
    }

    LaunchedEffect(contaSelecionadaId, filtroAtivo) {
        val idParaFiltro = contaSelecionadaId?.trim().orEmpty()

        despVM.setFiltro(filtroAtivo.ordinal)
        despVM.setContaSelecionada(idParaFiltro)

        if (filtroAtivo == FiltroPeriodo.ESTE_MES) {
            val hoje = Calendar.getInstance()
            despVM.setDataAtual(hoje.get(Calendar.MONTH), hoje.get(Calendar.YEAR))
        }
        contaVM.carregarSaldosGlobais()
    }

    // ==========================================
    // 7. CONSTRUÇÃO DA INTERFACE PRINCIPAL
    // ==========================================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(PremiumDarkBlue, PremiumLightBlue)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationSection(selectedIndex = selectedIndex, onItemSelected = { selectedIndex = it })
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        modifier = Modifier
                            .padding(12.dp)
                            .border(1.dp, Color(0xFF69F0AE).copy(0.3f), RoundedCornerShape(16.dp)),
                        containerColor = Color(0xFF1E2B3E),
                        contentColor = Color.White,
                        actionContentColor = Color(0xFF69F0AE),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(data.visuals.message)
                    }
                }
            },
            floatingActionButton = {
                if (mainTabSelecionada in listOf(0, 1, 3)) {
                    SpeedDialFAB(
                        onNovoGasto = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddDespesaDialog = true
                        },
                        onNovoInvestimento = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showInvestDialog = true
                        },
                        onTransferencia = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showTransferenciaDialog = true
                        }
                    )
                }
            }
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // --- HEADER ---
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

                Spacer(modifier = Modifier.height(8.dp))

                // --- CARDS DE RESUMO FIXOS ---
                ResumoAgendamentosCard(
                    agendamentos = agendados,
                    onCancelar = { id -> contaVM.cancelarAgendamento(id, context) },
                    isPrivate = isPrivate
                )

                Spacer(modifier = Modifier.height(8.dp))

                NotificacaoRendimentoCard(
                    rendimentoNoMes = rendimentoTotal,
                    isPrivate = isPrivate
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- FILTRO GLOBAL ---
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    SeletorPeriodo(
                        filtroSelecionado = contaVM.filtroAtual,
                        onFiltroSelected = { contaVM.alterarFiltro(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- TABS (ABAS) ---
                ScrollableTabRow(
                    selectedTabIndex = mainTabSelecionada,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF69F0AE),
                    edgePadding = 16.dp,
                    indicator = { },
                    divider = { }
                ) {
                    mainTabs.forEachIndexed { index, title ->
                        val selecionado = mainTabSelecionada == index
                        Tab(
                            selected = selecionado,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                mainTabSelecionada = index
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(if (selecionado) PremiumLightBlue else Color.Transparent),
                            text = {
                                Text(
                                    text = title,
                                    color = if (selecionado) TextWhite else TextWhite.copy(alpha = 0.5f),
                                    fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }

                // --- CORPO DA ABA SELECIONADA ---
                val folderShape = if (mainTabSelecionada == 0) {
                    RoundedCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                } else {
                    RoundedCornerShape(24.dp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(folderShape)
                        .background(PremiumLightBlue)
                ) {
                    val dadosGrafico = remember(despesasFiltradas) {
                        despesasFiltradas.groupBy { it.categoria }.map { (categoria, despesasDaCategoria) ->
                            PieChartData(
                                categoria = categoria,
                                valor = despesasDaCategoria.sumOf { it.valor },
                                cor = gerarCorParaCategoria(categoria)
                            )
                        }
                    }

                    when (mainTabSelecionada) {
                        0 -> {
                            // --- ABA 0: SALDO ---
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                            ) {
                                item {
                                    ResumoGeralCard(
                                        receitaTotal = resumo.entradas,
                                        despesaTotal = resumo.saidas,
                                        despesaMesAnterior = saidasMesAnterior,
                                        metasTotal = totalMetas,
                                        isPrivate = isPrivate,
                                        dadosGrafico = dadosGrafico
                                    )
                                }
                                item {
                                    if (dadosGrafico.isNotEmpty()) {
                                        CompactCategoryGrid(dados = dadosGrafico, isPrivate = isPrivate)
                                    } else {
                                        Text(
                                            text = "Nenhum gasto neste período",
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            color = Color.White.copy(0.3f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            // --- ABA 1: CONTAS E DESPESAS ---
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                            ) {
                                // 1. Carrossel de Contas
                                item {
                                    if (contas.isNotEmpty()) {
                                        key(contaSelecionadaId, filtroAtivo) {
                                            CardSection(
                                                contas = contas,
                                                contasSelecionadaId = contaSelecionadaId,
                                                isPrivate = isPrivate,
                                                onExcluir = { conta ->
                                                    contaVM.removerContaSaldo(conta.id)
                                                    contaVM.carregarSaldosGlobais()
                                                },
                                                onContaSelecionada = { novaContaId -> contaVM.selecionarConta(novaContaId) },
                                                onAtualizar = {},
                                                getReceitaConta = { id -> contaVM.obterReceitaPorConta(id) },
                                                getDespesaConta = { id -> contaVM.obterDespesaPorConta(id) }
                                            )
                                            TransacoesRecentesSection(transacoes = listaTransacoes, isPrivate = isPrivate)
                                        }
                                    } else {
                                        Text(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite.copy(alpha = 0.5f),
                                            textAlign = TextAlign.Center,
                                            text = "Você ainda não possui contas cadastradas."
                                        )
                                    }
                                }

                                // 2. Botões de Ação Rápida
                                item {
                                    Box(modifier = Modifier.padding(top = 8.dp)) {
                                        ActionButtonRow(
                                            categorias = repository.categorias.map { it.title },
                                            getPicCategoria = { nomeCategoria -> repository.getPicCategoria(nomeCategoria) },
                                            contaSelecionada = contaSelecionadaId.orEmpty(),
                                            viewModel = contaVM,
                                            onConfigClick = onOpenAvisos
                                        )
                                    }
                                }

                                // 3. Cabeçalho Orçamentos
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Orçamentos",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TextButton(
                                            onClick = { showAddOrcamentoDialog = true },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = Color(0xFF69F0AE))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Configurar", color = Color(0xFF69F0AE), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // 4. Grid de Orçamentos
                                item {
                                    val gruposDeQuatro = orcamentosComProgresso.chunked(4)
                                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        gruposDeQuatro.forEach { linhaDeOrcamentos ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                linhaDeOrcamentos.forEach { orcamento ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        OrcamentoCard(item = orcamento, onClick = { orcamentoSelecionado = orcamento })
                                                    }
                                                }
                                                repeat(4 - linhaDeOrcamentos.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }

                                // 5. Seletor de Meses
                                item {
                                    val meses = remember {
                                        listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
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
                                            Text(text = "$anoAtual", style = MaterialTheme.typography.labelSmall, color = TextWhite.copy(alpha = 0.6f))
                                        }
                                        IconButton(onClick = { despVM.proximoMes() }) {
                                            Icon(Icons.Rounded.ChevronRight, "Próximo", tint = TextWhite)
                                        }
                                    }
                                }

                                // 6. Lista de Despesas
                                if (despesasFiltradas.isEmpty()) {
                                    item {
                                        EmptyStateSection(
                                            onAddClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showAddDespesaDialog = true
                                            }
                                        )
                                    }
                                } else {
                                    items(despesasFiltradas, key = { it.id }) { item ->
                                        DespesasItem(
                                            item = item,
                                            isPrivate = isPrivate,
                                            onRemover = { despesa -> contaVM.removerDespesa(despesa) },
                                            onTogglePago = { itemClicado -> contaVM.alternarStatusDespesa(itemClicado) }
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            // --- ABA 2: COFRINHOS ---
                            CofrinhosTab(
                                viewModel = metaVM,
                                isPrivate = isPrivate,
                                snackbarHostState = snackbarHostState,
                                userName = nome ?: "Viajante"
                            )
                        }

                        3 -> {
                            // --- ABA 3: INVESTIMENTOS ---
                            InvestimentosTab(viewModel = investimentoVM, isPrivate = isPrivate)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 8. RENDERIZAÇÃO DE DIALOGS
        // ==========================================
        if (showTransferenciaDialog) {
            TransferenciaDialog(
                contas = contas,
                contaOrigemInicial = contaSelecionadaId.orEmpty(),
                onDismiss = { showTransferenciaDialog = false },
                onConfirmar = { origem, destino, valor, dataAgendada ->
                    if (dataAgendada == null) {
                        contaVM.transferirValor(origem, destino, valor, context)
                    } else {
                        contaVM.agendarTransferencia(origem, destino, valor, dataAgendada, context)
                    }
                    showTransferenciaDialog = false
                }
            )
        }

        if (showInvestDialog) {
            AddInvestimentoDialog(
                onDismiss = { showInvestDialog = false },
                onGuardar = { n, t, vi, va ->
                    investimentoVM.salvarInvestimento(n, t, vi, va)
                    showInvestDialog = false
                }
            )
        }

        if (showAddDespesaDialog) {
            AddDespesaDialog(
                categorias = repository.categorias.map { it.title },
                contaSelecionada = contaSelecionadaId.orEmpty(),
                getPicCategoria = { nomeCat -> repository.getPicCategoria(nomeCat) },
                viewModel = contaVM,
                parentScope = parentScope,
                onDismiss = { showAddDespesaDialog = false }
            )
        }

        if (showRecorrenciaDialog) {
            GerenciarRecorrenciaDialog(viewModel = contaVM, onDismiss = { showRecorrenciaDialog = false })
        }

        orcamentoSelecionado?.let { orcamento ->
            DetalheOrcamentoBottomSheet(
                item = orcamento,
                onDismiss = { orcamentoSelecionado = null },
                onExcluir = {
                    orcamentoVM.excluirOrcamento(orcamento.categoria)
                    orcamentoSelecionado = null
                },
                onEditar = { novoValor -> orcamentoVM.atualizarOrcamento(orcamento.categoria, novoValor) }
            )
        }

        if (showAddOrcamentoDialog) {
            AddOrcamentoDialog(
                categoriasDisponiveis = repository.categorias.map { it.title },
                onSalvar = { categoria, valor -> orcamentoVM.salvarOrcamento(categoria, valor) },
                onDismiss = { showAddOrcamentoDialog = false }
            )
        }

        // ==========================================
        // 9. NAVEGAÇÃO DE TELAS SOBREPOSTAS (Z-Index)
        // ==========================================
        when (selectedIndex) {
            0 -> ContaBancaria(
                viewModelFactory = ContaSaldoViewModelFactory(application, repository),
                onClose = { selectedIndex = -1 }
            )
            1 -> ExtratoScreen(
                despesasVM = despVM,
                categorias = repository.categorias.map { it.title },
                isPrivate = isPrivate,
                onBack = { selectedIndex = -1 }
            )
            2 -> MetasScreen(
                viewModel = metaVM,
                isPrivate = isPrivate,
                onBack = { selectedIndex = -1 }
            )
        }
    }
}