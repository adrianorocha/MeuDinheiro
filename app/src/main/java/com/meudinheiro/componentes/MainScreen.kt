package com.meudinheiro.componentes

import android.R.attr.text
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.meudinheiro.funcoes.PremiumSnackbar
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.gerarCorParaCategoria
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.viewModel.CartoesViewModel
import com.meudinheiro.viewModel.CartoesViewModelFactory
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory
import com.meudinheiro.viewModel.HomeViewModel
import com.meudinheiro.viewModel.HomeViewModelFactory
import com.meudinheiro.viewModel.InvestimentoViewModel
import com.meudinheiro.viewModel.InvestimentoViewModelFactory
import com.meudinheiro.viewModel.MetaViewModel
import com.meudinheiro.viewModel.MetaViewModelFactory
import com.meudinheiro.viewModel.OrcamentoViewModel
import com.meudinheiro.viewModel.OrcamentoViewModelFactory
import com.meudinheiro.viewModel.TransacaoViewModel
import com.meudinheiro.viewModel.TransacaoViewModelFactory
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
    val contaVM: ContaSaldoViewModel =
        viewModel(factory = ContaSaldoViewModelFactory(application, repository))
    val cartaoVM: CartoesViewModel =
        viewModel(factory = CartoesViewModelFactory(application))

    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val orcamentoVM: OrcamentoViewModel = viewModel(factory = OrcamentoViewModelFactory(repository))
    val metaVM: MetaViewModel = viewModel(factory = MetaViewModelFactory(repository))
    val investimentoVM: InvestimentoViewModel =
        viewModel(factory = InvestimentoViewModelFactory(db.investimentoDao()))
    val transacaoVM: TransacaoViewModel =
        viewModel(factory = TransacaoViewModelFactory(db.transacaoDao()))
    val cartoesViewModel: CartoesViewModel = viewModel(factory = CartoesViewModelFactory(LocalContext.current))
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
    val mainTabs = remember { listOf("Saldo", "Contas", "Cartões","Cofrinhos", "Investimentos", "Resumos") }
    var selectedIndex by remember { mutableIntStateOf(-1) } // Controle das telas cheias sobrepostas

    // Controles de Dialogs
    var showAddDespesaDialog by remember { mutableStateOf(false) }
    var showAddOrcamentoDialog by remember { mutableStateOf(false) }
    var showRecorrenciaDialog by remember { mutableStateOf(false) }
    var showInvestDialog by remember { mutableStateOf(false) }
    var showTransferenciaDialog by remember { mutableStateOf(false) }
    var orcamentoSelecionado by remember { mutableStateOf<OrcamentoProgresso?>(null) }
    var showAgendamentosDialog by remember { mutableStateOf(false) }
    var showRelatorioDialog by remember { mutableStateOf(false) }
    var showPatrimonioDialog by remember { mutableStateOf(false) }
    var showPrevisaoDialog by remember { mutableStateOf(false) }

    val historicoPatrimonio by contaVM.historicoPatrimonial.collectAsState(initial = emptyList())
    var showScanner by remember { mutableStateOf(false) }
    var valorEscaneado by remember { mutableStateOf<Double?>(null) }
    var codigoEscaneado by remember { mutableStateOf("") }
    // ==========================================
    // 4. ESTADOS DE DADOS (FLUXOS DO BANCO)
    // ==========================================
    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null)
    val listaCartoes by cartoesViewModel.cartoes.collectAsState()
    val dashboardState by contaVM.dashboardState.collectAsState()
    val resumo by contaVM.resumoFinanceiro.collectAsState()
    val totalMetas by contaVM.totalPoupado.collectAsState()
    val agendados by contaVM.agendamentosFiltrados.collectAsState()

    val mesAtual by despVM.mesSelecionado.collectAsState()
    val anoAtual by despVM.anoSelecionado.collectAsState()
    val despesasFiltradas by despVM.despesasFiltradas.collectAsState()
    val saidasMesAnterior by despVM.getDespesaMesAnterior(mesAtual, anoAtual)
        .collectAsState(initial = 0.0)

    val orcamentosComProgresso by orcamentoVM.orcamentosComProgresso.collectAsState()
    val listaTransacoes by transacaoVM.ultimasTransacoes.collectAsState()
    val rendimentoTotal by investimentoVM.rendimentoTotal.collectAsState()

    val contasAVencer by contaVM.contasAVencer.collectAsState()
    //val saldoTotal by contaVM.saldoPatrimonial.collectAsState(initial = 0.0)


    val filtroAtivo = contaVM.filtroAtual

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        } else {
            Toast.makeText(context, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
        }
    }
    // ==========================================
    // 5. VERIFICAÇÕES DE INICIALIZAÇÃO E SPLASH
    // ==========================================
    if (nomeState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
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
        notifCount = withContext(Dispatchers.IO) {
            repository.contarPendencias(
                daysAhead,
                onlyCredit = false
            )
        }
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

                NeonBottomNavigation(
                    abaSelecionada = selectedIndex,
                    onTabSelected = { selectedIndex = it }
                )
/*
                NavigationSection(
                    selectedIndex = selectedIndex,
                    onItemSelected = { selectedIndex = it })
*/
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    PremiumSnackbar(data)
                    /*
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
                    */
                }
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showPatrimonioDialog = true
                        },
                        containerColor = Color(0xFF1B263B),
                        contentColor = NeonCyan,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, "Evolução", modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showRelatorioDialog = true
                        },
                        containerColor = Color(0xFF1B263B),
                        contentColor = NeonCyan,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.AutoGraph, "Análise", modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (agendados.isNotEmpty() && mainTabSelecionada in listOf(0, 1)) {
                        FloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showAgendamentosDialog = true
                            },
                            containerColor = PremiumLightBlue,
                            contentColor = NeonCyan,
                            modifier = Modifier.size(48.dp) // Botão secundário um pouco menor
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = "Agendamentos",
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(22.dp)
                                )
                                // Bolinha Neon indicando a quantidade
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${agendados.size}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
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
                            },
                            onScanBoleto = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        )
                    }
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

                /*
                                // --- CARDS DE RESUMO FIXOS ---
                                ResumoAgendamentosCard(
                                    agendamentos = agendados,
                                    onCancelar = { id -> contaVM.cancelarAgendamento(id, context) },
                                    isPrivate = isPrivate
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                */

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
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 24.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    )
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
                        despesasFiltradas.groupBy { it.categoria }
                            .map { (categoria, despesasDaCategoria) ->
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
                                /*
                                                                item {
                                                                    RelatorioSaudeFinanceiraCard(
                                                                        receitaAtual = resumo.entradas,
                                                                        despesaAtual = resumo.saidas,
                                                                        despesaAnterior = saidasMesAnterior,
                                                                        isPrivate = isPrivate
                                                                    )
                                                                }
                                */
                                item {
                                    if (dadosGrafico.isNotEmpty()) {
                                        CompactCategoryGrid(
                                            dados = dadosGrafico,
                                            isPrivate = isPrivate
                                        )
                                    } else {
                                        Text(
                                            text = "Nenhum gasto neste período",
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .fillMaxWidth(),
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
                                                onContaSelecionada = { novaContaId ->
                                                    contaVM.selecionarConta(
                                                        novaContaId
                                                    )
                                                },
                                                onAtualizar = {},
                                                getReceitaConta = { id ->
                                                    contaVM.obterReceitaPorConta(
                                                        id
                                                    )
                                                },
                                                getDespesaConta = { id ->
                                                    contaVM.obterDespesaPorConta(
                                                        id
                                                    )
                                                }
                                            )
                                            TransacoesRecentesSection(
                                                transacoes = listaTransacoes,
                                                isPrivate = isPrivate
                                            )
                                        }
                                    } else {
                                        Text(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
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
                                            contentPadding = PaddingValues(
                                                horizontal = 12.dp,
                                                vertical = 4.dp
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                null,
                                                modifier = Modifier.size(18.dp),
                                                tint = Color(0xFF69F0AE)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "Configurar",
                                                color = Color(0xFF69F0AE),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
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
                                                        OrcamentoCard(
                                                            item = orcamento,
                                                            onClick = {
                                                                orcamentoSelecionado = orcamento
                                                            })
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
                                            Icon(
                                                Icons.Rounded.ChevronLeft,
                                                "Anterior",
                                                tint = TextWhite
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = meses.getOrElse(mesAtual) { "" },
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = TextWhite
                                            )
                                            Text(
                                                text = "$anoAtual",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextWhite.copy(alpha = 0.6f)
                                            )
                                        }
                                        IconButton(onClick = { despVM.proximoMes() }) {
                                            Icon(
                                                Icons.Rounded.ChevronRight,
                                                "Próximo",
                                                tint = TextWhite
                                            )
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
                                            onTogglePago = { itemClicado ->
                                                contaVM.alternarStatusDespesa(
                                                    itemClicado
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {

                            // --- ABA 2: Cartões ---
                            CartoesScreen(
                                viewModel = cartaoVM
                            )
                        }


                        3 -> {
                            // --- ABA 3: COFRINHOS ---
                            CofrinhosTab(
                                viewModel = metaVM,
                                isPrivate = isPrivate,
                                snackbarHostState = snackbarHostState,
                                userName = nome ?: "Viajante"
                            )
                        }

                        4 -> {
                            // --- ABA 4: INVESTIMENTOS ---
                            InvestimentosTab(viewModel = investimentoVM, isPrivate = isPrivate)
                        }

                        5 -> {
                            // --- ABA 5: RESUMOS
                            SecaoAgendamentosAtivos(viewModel = contaVM)
                        }
                    }
                }
            }
            SmallFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showPrevisaoDialog = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd) // Prende no canto superior direito
                    // ⚠️ O PULO DO GATO: Use o offset para mover pixel por pixel até o seu círculo vermelho
                    .offset(x = (-16).dp, y = 255.dp),
                containerColor = Color(0xFF1B263B),
                contentColor = Color(0xFF00E5FF), // NeonCyan
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.AreaChart,
                    contentDescription = "Previsão",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ==========================================
        // 8. RENDERIZAÇÃO DE DIALOGS
        // ==========================================
        if (showPrevisaoDialog) {
            PrevisaoFechamentoDialog(
                saldoAtual = 0.0, // As variáveis do seu ViewModel
                contasAVencer = contasAVencer, // Que configuramos antes
                isPrivate = isPrivate,
                onDismiss = { showPrevisaoDialog = false }
            )
        }

        if (showScanner) {
            ScannerBoletoScreen(
                onResult = { codigo, valor ->
                    codigoEscaneado = codigo
                    valorEscaneado = valor
                    showScanner = false
                    showAddDespesaDialog = true // Abre a tela de gasto logo em seguida!
                },
                onClose = { showScanner = false }
            )
        }

        if (showPatrimonioDialog) {
            PatrimonioHistoricoDialog(
                historico = historicoPatrimonio,
                isPrivate = isPrivate,
                onDismiss = { showPatrimonioDialog = false }
            )
        }

        if (showRelatorioDialog) {
            RelatorioSaudeDialog(
                receitaAtual = resumo.entradas,
                despesaAtual = resumo.saidas,
                despesaAnterior = saidasMesAnterior,
                isPrivate = isPrivate,
                onDismiss = { showRelatorioDialog = false }
            )
        }

        if (showAgendamentosDialog) {
            AgendamentosDialog(
                agendamentos = agendados,
                isPrivate = isPrivate,
                onDismiss = { showAgendamentosDialog = false },
                onCancelar = { id ->
                    contaVM.cancelarAgendamento(id, context)
                    // Se excluir o último, fecha o dialog automaticamente
                    if (agendados.size <= 1) showAgendamentosDialog = false
                }
            )
        }

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
                valorInicial = valorEscaneado ?: 0.0, // Passa o valor do scanner
                codigoBarras = if (codigoEscaneado.isNotEmpty()) "Boleto: $codigoEscaneado" else "",
                categorias = repository.categorias.map { it.title },
                cartoesDisponiveis = listaCartoes,
                contaSelecionada = contaSelecionadaId.orEmpty(),
                getPicCategoria = { nomeCat -> repository.getPicCategoria(nomeCat) },
                viewModel = contaVM,
                cartoesViewModel = cartaoVM,
                parentScope = parentScope,
                onDismiss = {
                    valorEscaneado = null // Limpa para não lixar o próximo
                    codigoEscaneado = ""
                    showAddDespesaDialog = false
                }
            )
        }

        if (showRecorrenciaDialog) {
            GerenciarRecorrenciaDialog(
                viewModel = contaVM,
                onDismiss = { showRecorrenciaDialog = false })
        }

        orcamentoSelecionado?.let { orcamento ->
            DetalheOrcamentoBottomSheet(
                item = orcamento,
                onDismiss = { orcamentoSelecionado = null },
                onExcluir = {
                    orcamentoVM.excluirOrcamento(orcamento.categoria)
                    orcamentoSelecionado = null
                },
                onEditar = { novoValor ->
                    orcamentoVM.atualizarOrcamento(
                        orcamento.categoria,
                        novoValor
                    )
                }
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