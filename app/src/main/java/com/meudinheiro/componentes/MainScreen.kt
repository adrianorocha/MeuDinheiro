package com.meudinheiro.componentes

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.MetaPremium
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.data.PieChartData
import com.meudinheiro.funcoes.CompactCategoryGrid
import com.meudinheiro.funcoes.PremiumSnackbar
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.gerarCorParaCategoria
import com.meudinheiro.repository.MainRepository
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
import com.meudinheiro.ui.theme.*
import com.meudinheiro.domain.Analises
import com.meudinheiro.domain.Financas
import kotlinx.coroutines.flow.first

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
    val cartaoVM: CartoesViewModel = viewModel(factory = CartoesViewModelFactory(application))

    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val orcamentoVM: OrcamentoViewModel = viewModel(factory = OrcamentoViewModelFactory(repository))
    val metaVM: MetaViewModel = viewModel(factory = MetaViewModelFactory(repository))
    val investimentoVM: InvestimentoViewModel = viewModel(factory = InvestimentoViewModelFactory(db.investimentoDao()))
    val transacaoVM: TransacaoViewModel = viewModel(factory = TransacaoViewModelFactory(db.despesaDao(), db.contaSaldoDao()))

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
    val mainTabs = remember { listOf("Saldo", "Contas", "Cartões", "Cofrinhos", "Investimentos", "Resumos") }

    // --- 📟 MOTOR DO DATA STREAM (VARREDURA) ---
    val scanProgress = remember { Animatable(0f) }
    LaunchedEffect(mainTabSelecionada) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Vibração Tática
        scanProgress.snapTo(0f)
        scanProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(450, easing = LinearOutSlowInEasing)
        )
    }

    LaunchedEffect(isPrivate) {
        if (isPrivate) {
            // Vibração curta de erro/interferência
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    // Controles de Dialogs e Menus
    var isMenuOpen by remember { mutableStateOf(false) }
    var showAddContaDialog by remember { mutableStateOf(false) }
    var showExtratoScreen by remember { mutableStateOf(false) }
    var alvoLancamento by remember { mutableStateOf<AlvoLancamento?>(null) }
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

    var showScanner by remember { mutableStateOf(false) }
    var showFerramentas by remember { mutableStateOf(false) }
    var showConferencia by remember { mutableStateOf(false) }

    // Atalho do launcher "Nova despesa": abre direto o formulário de lançamento.
    LaunchedEffect(com.meudinheiro.AtalhosApp.novaDespesaPedida) {
        if (com.meudinheiro.AtalhosApp.novaDespesaPedida) {
            showAddDespesaDialog = true
            com.meudinheiro.AtalhosApp.consumir()
        }
    }
    var valorEscaneado by remember { mutableStateOf<Double?>(null) }
    var codigoEscaneado by remember { mutableStateOf("") }

    var metaSelecionadaParaDeposito by remember { mutableStateOf<MetaPremium?>(null) }
    var mostrarCelebracao by remember { mutableStateOf(false) }
    var corCelebracao by remember { mutableStateOf(NeonCyan) }

    // ==========================================
    // 4. ESTADOS DE DADOS (FLUXOS DO BANCO)
    // ==========================================
    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null)
    val listaCartoes by cartaoVM.cartoes.collectAsState()
    val dashboardState by contaVM.dashboardState.collectAsState()
    val resumo by contaVM.resumoFinanceiro.collectAsState()
    val totalMetas by contaVM.totalPoupado.collectAsState()
    val agendados by contaVM.agendamentosFiltrados.collectAsState()
    val listaMetasReal by metaVM.metas.collectAsState(initial = emptyList())
    val historicoPatrimonio by contaVM.historicoPatrimonial.collectAsState(initial = emptyList())
    val previsao by contaVM.previsao.collectAsState()
    val todasDespesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())


    // Categorias padrão + as criadas pelo usuário (antes só as padrão apareciam nos formulários).
    val categoriasCustom by remember { repository.obterCategoriasCustom() }.collectAsState(initial = emptyList())
    val categoriasDisponiveis = remember(categoriasCustom) {
        (repository.categorias + categoriasCustom).map { it.title }.distinctBy { it.trim().lowercase() }.sorted()
    }

    // Chip do cabeçalho: onde os dados estão sendo gravados e o estado da sincronização.
    val storageManager = remember { (application as com.meudinheiro.MyApplication).storageManager }
    val modoArmazenamento by storageManager.modo.collectAsState(initial = com.meudinheiro.storage.StorageMode.LOCAL)
    val statusSync by storageManager.status.collectAsState()
    val chipArmazenamento = when {
        modoArmazenamento != com.meudinheiro.storage.StorageMode.FIREBASE -> "Salvo no celular" to HeaderChipStyle.NEUTRAL
        statusSync is com.meudinheiro.storage.SyncStatus.Sincronizado -> "Nuvem sincronizada" to HeaderChipStyle.SUCCESS
        statusSync is com.meudinheiro.storage.SyncStatus.Conectando -> "Sincronizando…" to HeaderChipStyle.PRIMARY
        statusSync is com.meudinheiro.storage.SyncStatus.AguardandoLogin -> "Nuvem: sem login" to HeaderChipStyle.NEUTRAL
        else -> "Nuvem: atenção" to HeaderChipStyle.NEUTRAL
    }

    val mesAtual by despVM.mesSelecionado.collectAsState()
    val anoAtual by despVM.anoSelecionado.collectAsState()
    val despesasFiltradas by despVM.despesasFiltradas.collectAsState()
    val saidasMesAnterior by despVM.getDespesaMesAnterior(mesAtual, anoAtual).collectAsState(initial = 0.0)

    val orcamentosComProgresso by orcamentoVM.orcamentosComProgresso.collectAsState()

    // R22 — alerta de orçamento (80% / 100%), uma única vez por categoria/mês/limiar.
    LaunchedEffect(orcamentosComProgresso) {
        if (orcamentosComProgresso.isEmpty()) return@LaunchedEffect
        val agora = System.currentTimeMillis()
        val mesChave = "%04d-%02d".format(Financas.anoDe(agora), Financas.mesDe(agora))
        val novos = Analises.alertasOrcamento(
            orcamentosComProgresso.map { it.categoria to it.porcentagem.toDouble() }, mesChave, userPrefs.alertasOrcamentoEnviados()
        )
        if (novos.isEmpty()) return@LaunchedEffect
        userPrefs.marcarAlertasOrcamento(novos.flatMap { Analises.chavesParaMarcar(it) }.toSet())
        val notificar = userPrefs.notifEnabledFlow.first()
        novos.forEach { a ->
            val msg = if (a.limiar == 100) "O orçamento de ${a.categoria} foi ultrapassado." else "Você já usou 80% do orçamento de ${a.categoria}."
            snackbarHostState.showSnackbar("Orçamento | $msg | Erro")
            if (notificar) {
                com.meudinheiro.notif.Notificacoes.mostrar(
                    context,
                    com.meudinheiro.notif.Aviso(
                        tipo = if (a.limiar == 100) com.meudinheiro.notif.TipoAviso.ORCAMENTO_ESTOURADO else com.meudinheiro.notif.TipoAviso.ORCAMENTO,
                        titulo = "Orçamento de ${a.categoria}", resumo = msg, id = a.chave.hashCode(),
                        textoPublico = "Aviso de orçamento"
                    )
                )
            }
        }
    }
    val listaTransacoes by transacaoVM.ultimasTransacoes.collectAsState()
    val rendimentoTotal by investimentoVM.rendimentoTotal.collectAsState()

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

    LaunchedEffect(Unit) {
        contaVM.uiEvent.collect { mensagem ->
            snackbarHostState.showSnackbar(mensagem)
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
            repository.contarPendencias(daysAhead, onlyCredit = false)
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
        transacaoVM.selecionarConta(idParaFiltro)

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
            floatingActionButton = {
                PowerCoreFab(
                    isMenuOpen = isMenuOpen,
                    onToggleMenu = { isMenuOpen = !isMenuOpen },
                    onOpcaoSelected = { opcao ->
                        isMenuOpen = false // Fecha o menu ao escolher
                        when(opcao) {
                            "minha conta" -> showAddContaDialog = true
                            "extrato" -> showExtratoScreen = true
                            "meta" -> mainTabSelecionada = 3
                            "transferencia" -> showTransferenciaDialog = true
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    PremiumSnackbar(data)
                }
            }
        ) { innerPadding ->

            // 🚀 BOX PRINCIPAL QUE ENCAPSULA O CONTEÚDO
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)) {

                Column(modifier = Modifier.fillMaxSize()) {
                    // --- HEADER ---
                    HeaderSection(
                        nome = nome,
                        fotoUri = fotoSalva.takeIf { it.isNotBlank() },
                        onProfileClick = { emCadastro = true },
                        chipText = chipArmazenamento.first,
                        chipStyle = chipArmazenamento.second,
                        showNotifications = true,
                        hasUnreadNotifications = (notifCount > 0),
                        notificationCount = notifCount,
                        onNotificationsClick = onOpenPendencias,
                        onToolsClick = { showFerramentas = true },
                        receitaTotal = dashboardState.receitaGlobal,
                        despesaTotal = dashboardState.despesaGlobal,
                        despesaPaga = dashboardState.despesaPagaGlobal,
                        despesaAPagar = dashboardState.despesaPendenteGlobal,
                        onConferirSaldos = { showConferencia = true },
                        isPrivateMode = isPrivate,
                        onTogglePrivate = { scope.launch { userPrefs.togglePrivateMode() } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NotificacaoRendimentoCard(
                        rendimentoNoMes = rendimentoTotal,
                        isPrivate = isPrivate
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- FILTRO GLOBAL ---
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        BarraFiltrosEAcoes(
                            filtroAtual = filtroAtivo,
                            onFiltroSelected = { contaVM.alterarFiltro(it) },
                            onEvolucaoPatrimonial = { showPatrimonioDialog = true },
                            onSaudeFinanceiro = { showRelatorioDialog = true },
                            onPreviaoMes = { showPrevisaoDialog = true },
                            onTransacoesAgendadas = { showAgendamentosDialog = true },
                            agendados = agendados
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

                    // --- CORPO DA ABA SELECIONADA COM DATA STREAM ---
                    val folderShape = if (mainTabSelecionada == 0) {
                        RoundedCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    } else {
                        RoundedCornerShape(24.dp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = mainTabSelecionada,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500), initialOffsetY = { 40 }))
                                    .togetherWith(fadeOut(animationSpec = tween(300)))
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(folderShape)
                                .background(PremiumLightBlue),
                            label = "TabTransition"
                        ) { targetTab ->
                            val dadosGrafico = remember(despesasFiltradas) {
                                despesasFiltradas.filter { it.natureza != com.meudinheiro.domain.Natureza.AJUSTE }.groupBy { it.categoria }.map { (categoria, despesasDaCategoria) ->
                                    PieChartData(
                                        categoria = categoria,
                                        valor = despesasDaCategoria.sumOf { it.valor },
                                        cor = gerarCorParaCategoria(categoria)
                                    )
                                }
                            }

                            when (targetTab) {
                                0 -> {
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
                                                    modifier = Modifier
                                                        .padding(16.dp)
                                                        .fillMaxWidth(),
                                                    color = Color.White.copy(0.3f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        item {
                                            val metasMapeadas = listaMetasReal.map { meta ->
                                                MetaPremium(
                                                    id = meta.id.toString(),
                                                    nome = meta.nome,
                                                    valorAlvo = meta.valorObjetivo,
                                                    valorPoupado = meta.valorGuardado,
                                                    iconePic = meta.icone,
                                                    corDestaque = gerarCorParaCategoria(meta.nome)
                                                )
                                            }

                                            SecaoCofresMetas(
                                                metas = metasMapeadas,
                                                isPrivate = isPrivate,
                                                userName = nome ?: "Viajante",
                                                onMetaLongClick = { meta: MetaPremium ->
                                                    metaSelecionadaParaDeposito = meta
                                                }
                                            )
                                        }
                                    }
                                }

                                1 -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                                    ) {
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
                                                    TransacoesRecentesSection(
                                                        transacoes = listaTransacoes, isPrivate = isPrivate,
                                                        onAcoes = { id -> alvoLancamento = AlvoLancamento(id.toLong()) }
                                                    )
                                                }
                                            }
                                        }

                                        item {
                                            ActionButtonRow(
                                                categorias = categoriasDisponiveis,
                                                getPicCategoria = { repository.getPicCategoria(it) },
                                                contaSelecionada = contaSelecionadaId.orEmpty(),
                                                viewModel = contaVM,
                                                onConfigClick = onOpenAvisos
                                            )
                                        }

                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Orçamentos", color = Color.White, fontWeight = FontWeight.Bold)
                                                TextButton(onClick = { showAddOrcamentoDialog = true }) {
                                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = Color(0xFF69F0AE))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Configurar", color = Color(0xFF69F0AE), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

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
                                                        repeat(4 - linhaDeOrcamentos.size) { Spacer(modifier = Modifier.weight(1f)) }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                            }
                                        }

                                        item {
                                            val meses = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
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
                                                    Text(text = meses.getOrElse(mesAtual) { "" }, fontWeight = FontWeight.Bold, color = TextWhite)
                                                    Text(text = "$anoAtual", fontSize = 11.sp, color = TextWhite.copy(alpha = 0.6f))
                                                }
                                                IconButton(onClick = { despVM.proximoMes() }) {
                                                    Icon(Icons.Rounded.ChevronRight, "Próximo", tint = TextWhite)
                                                }
                                            }
                                        }

                                        items(despesasFiltradas, key = { it.id }) { item ->
                                            DespesasItem(
                                                item = item,
                                                isPrivate = isPrivate,
                                                onRemover = { despesa -> contaVM.removerDespesa(despesa) },
                                                onTogglePago = { itemClicado -> contaVM.alternarStatusDespesa(itemClicado) },
                                                onDuplicar = { contaVM.duplicarDespesa(it) },
                                                onRepetir = { item, n, intervalo, unidade -> contaVM.repetirDespesa(item, n, intervalo, unidade) },
                                                onAcoes = { alvoLancamento = AlvoLancamento(it.id.toLong()) },
                                                onPedirExclusao = { alvoLancamento = AlvoLancamento(it.id.toLong(), excluirDireto = true) }
                                            )
                                        }
                                    }
                                }

                                2 -> CartoesScreen(
                                    viewModel = cartaoVM,
                                    contaViewModel = contaVM,
                                    categorias = categoriasDisponiveis,
                                    getPicCategoria = { nomeCat -> repository.getPicCategoria(nomeCat) },
                                    historico = todasDespesas
                                )
                                3 -> CofrinhosTab(viewModel = metaVM, isPrivate = isPrivate, snackbarHostState = snackbarHostState, userName = nome ?: "Viajante")
                                4 -> InvestimentosTab(viewModel = investimentoVM, isPrivate = isPrivate)
                                5 -> SecaoAgendamentosAtivos(viewModel = contaVM)
                            }
                        }

                        // 📟 LINHA LASER DE VARREDURA (Por cima do conteúdo da aba)
                        if (scanProgress.value < 1f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp) // Largura da "nuvem" de luz
                                    .graphicsLayer {
                                        translationY = scanProgress.value * size.height
                                        alpha = (1f - scanProgress.value) * 0.8f // Fade out
                                    }
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                NeonCyan.copy(alpha = 0.1f),
                                                NeonCyan, // Núcleo do laser
                                                NeonCyan.copy(alpha = 0.1f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                // 🚀 O ESCUDO ESCURO DO MENU ORBITAL FICA AQUI
                AnimatedVisibility(
                    visible = isMenuOpen,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.matchParentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isMenuOpen = false }
                    )
                }
            }
        }

        // ==========================================
        // 8. RENDERIZAÇÃO DE DIALOGS E TELAS SOBREPOSTAS
        // ==========================================
        if (showPrevisaoDialog) {
            PrevisaoFechamentoDialog(
                previsao = previsao,
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
                    showAddDespesaDialog = true
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
                        contaVM.transferirValor(origem, destino, valor)
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
                valorInicial = valorEscaneado ?: 0.0,
                codigoBarras = if (codigoEscaneado.isNotEmpty()) "Boleto: $codigoEscaneado" else "",
                categorias = categoriasDisponiveis,
                cartoesDisponiveis = listaCartoes,
                contaSelecionada = contaSelecionadaId.orEmpty(),
                getPicCategoria = { nomeCat -> repository.getPicCategoria(nomeCat) },
                viewModel = contaVM,
                historico = todasDespesas,
                cartoesViewModel = cartaoVM,
                parentScope = parentScope,
                onDismiss = {
                    valorEscaneado = null
                    codigoEscaneado = ""
                    showAddDespesaDialog = false
                }
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
                onEditar = { novoValor ->
                    orcamentoVM.atualizarOrcamento(orcamento.categoria, novoValor)
                }
            )
        }

        if (showAddOrcamentoDialog) {
            AddOrcamentoDialog(
                categoriasDisponiveis = categoriasDisponiveis,
                onSalvar = { categoria, valor -> orcamentoVM.salvarOrcamento(categoria, valor) },
                onDismiss = { showAddOrcamentoDialog = false }
            )
        }

        metaSelecionadaParaDeposito?.let { meta ->
            DepositoRapidoDialog(
                meta = meta,
                onConfirmar = { valor ->
                    metaVM.depositarNaMeta(meta.id.toLong(), contaSelecionadaId.orEmpty(), valor)
                    corCelebracao = meta.corDestaque
                    mostrarCelebracao = true
                    metaSelecionadaParaDeposito = null
                },
                onDismiss = { metaSelecionadaParaDeposito = null }
            )
        }

        // 🚀 O Z-INDEX MANUAL FICA AQUI (Por cima de tudo do Scaffold)
        if(showAddContaDialog){
            ContaBancaria(
                viewModelFactory = ContaSaldoViewModelFactory(application, repository),
                onClose = { showAddContaDialog = false }
            )
        }

        if (showConferencia) {
            ConferenciaSaldosScreen(repository = repository, isPrivate = isPrivate, onBack = { showConferencia = false })
        }

        if (showFerramentas) {
            FerramentasScreen(
                repository = repository,
                userPrefs = userPrefs,
                isPrivate = isPrivate,
                onBack = { showFerramentas = false }
            )
        }

        if (showExtratoScreen) {
            ExtratoScreen(
                despesasVM = despVM,
                categorias = categoriasDisponiveis,
                isPrivate = isPrivate,
                onBack = { showExtratoScreen = false },
                contaViewModel = contaVM,
                getPicCategoria = { repository.getPicCategoria(it) }
            )
        }

        // Menu de ações dos lançamentos (listas de conta, últimas movimentações): pago, antecipar, duplicar, repetir, editar, excluir.
        LancamentoAcoesHost(
            alvo = alvoLancamento,
            onFechar = { alvoLancamento = null },
            viewModel = contaVM,
            categorias = categoriasDisponiveis,
            getPicCategoria = { repository.getPicCategoria(it) }
        )

        // A Explosão de Partículas Neon fica por cima de absolutamente tudo
        if (mostrarCelebracao) {
            NeonConfettiEffect(corDestaque = corCelebracao) {
                mostrarCelebracao = false
            }
        }
    }
}