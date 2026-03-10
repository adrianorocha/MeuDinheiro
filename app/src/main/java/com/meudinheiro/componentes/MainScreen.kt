package com.meudinheiro.componentes

import android.app.Application
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.data.PieChartData
import com.meudinheiro.funcoes.CompactCategoryGrid
import com.meudinheiro.funcoes.EmptyStateSection
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.gerarCorParaCategoria
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory
import com.meudinheiro.viewModel.HomeViewModel
import com.meudinheiro.viewModel.HomeViewModelFactory
import com.meudinheiro.viewModel.MetaViewModel
import com.meudinheiro.viewModel.MetaViewModelFactory
import com.meudinheiro.viewModel.OrcamentoViewModel
import com.meudinheiro.viewModel.OrcamentoViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.emptyList

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
    val haptic = LocalHapticFeedback.current

    // --- ViewModels ---
    val application = context.applicationContext as Application
    val repository = remember { MainRepository(context) }
    val despVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(repository))
    val contaVM: ContaSaldoViewModel = viewModel(factory = ContaSaldoViewModelFactory(application, repository))
    val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val orcamentoVM: OrcamentoViewModel = viewModel(factory = OrcamentoViewModelFactory(repository))
    val metaVM: MetaViewModel = viewModel(factory = MetaViewModelFactory(repository))

    var orcamentoSelecionado by remember { mutableStateOf<OrcamentoProgresso?>(null) }
    var showAddDespesaDialog by remember { mutableStateOf(false) }

    // CORREÇÃO 1: Usando 'by' para extrair o valor real do MutableState
    val filtroAtivo = contaVM.filtroAtual

    // --- Controle das Novas Abas ---
    var mainTabSelecionada by remember { mutableIntStateOf(0) }
    val mainTabs = listOf("Saldo", "Contas", "Cofrinhos", "Investimentos")

    // --- Estados (Preferences & User) ---
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val isPrivate by userPrefs.privateModeFlow.collectAsState(initial = false)

    val nomeState by homeVM.userName.collectAsState(initial = null)
    val fotoSalva by homeVM.userPhoto.collectAsState(initial = "")
    var emCadastro by remember { mutableStateOf(false) }
    var showAddOrcamentoDialog by remember { mutableStateOf(false) }
    var showRecorrenciaDialog by remember { mutableStateOf(false) }

    // --- Estados de Dados Globais ---
    val contas by contaVM.contaSaldo.observeAsState(emptyList())
    val contaSelecionadaId by contaVM.contaSelecionadaId.observeAsState(null)
    val dashboardState by contaVM.dashboardState.collectAsState()

    val mesAtual by despVM.mesSelecionado.collectAsState()
    val anoAtual by despVM.anoSelecionado.collectAsState()

    // CORREÇÃO 2: Descomentado e renomeado para bater exatamente com a UI lá embaixo
    // Forçamos o tipo <List<Despesa>> para evitar o erro de inferência "T"
    val despesasFiltradas by despVM.despesasFiltradas.collectAsState()
    val orcamentosComProgresso by orcamentoVM.orcamentosComProgresso.collectAsState()

    val totalMetas by contaVM.totalPoupado.collectAsState()
    val resumo by contaVM.resumoFinanceiro.collectAsState()

    val saidasMesAnterior by despVM.getDespesaMesAnterior(mesAtual, anoAtual)
        .collectAsState(initial = 0.0)

    val parentScope = rememberCoroutineScope()

    var selectedIndex by remember { mutableStateOf(-1) }
    fun onItemSelected(index: Int) {
        selectedIndex = index
    }

    // --- Verificações de Inicialização ---
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
            userPrefs = userPrefs, onBack = { emCadastro = false }, onFinished = { emCadastro = false }
        )
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

        // O '.ordinal' transforma ESTE_MES em 0, MES_PASSADO em 1 e TOTAL em 2
        despVM.setFiltro(filtroAtivo.ordinal)

        despVM.setContaSelecionada(idParaFiltro)
        contaVM.carregarSaldosGlobais()
    }

    // --- UI Principal ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(PremiumDarkBlue, PremiumLightBlue)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationSection(selectedIndex = selectedIndex, onItemSelected = ::onItemSelected)
            },
            floatingActionButton = {
                if (mainTabSelecionada == 0 || mainTabSelecionada == 1) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddDespesaDialog = true
                        },
                        containerColor = Color(0xFF69F0AE),
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nova Despesa", tint = PremiumDarkBlue)
                    }
                }
            }
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                // 1. HEADER (Fixo no topo da tela)
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

                // 2. FILTRO GLOBAL (Fixo fora das abas)
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    // Cuidado aqui: contaVM.filtroAtual é o State completo.
                    // Se o SeletorPeriodo esperar o valor numérico/enum, passe 'filtroAtivo'
                    // Mas se ele esperar o State em si, mantenha como está.
                    SeletorPeriodo(
                        filtroSelecionado = contaVM.filtroAtual,
                        onFiltroSelected = { contaVM.alterarFiltro(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. SELETOR DE ABAS (Estilo Folder)
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

                // 4. O CORPO DA PASTA
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
                        despesasFiltradas.groupBy { it.categoria }
                            .map { (categoria, despesasDaCategoria) ->
                                PieChartData(
                                    categoria = categoria,
                                    valor = despesasDaCategoria.sumOf { it.valor },
                                    cor = gerarCorParaCategoria(categoria)
                                )
                            }
                    }

                    // --- CONTEÚDO DINÂMICO DAS ABAS ---
                    when (mainTabSelecionada) {
                        0 -> {
                            // --- ABA 0: SALDO (Apenas Gráfico e Resumo) ---
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
                                            "Nenhum gasto neste período",
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            color = Color.White.copy(0.3f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            // --- ABA 1: CONTAS (Operacional) ---
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
                                        }
                                    } else {
                                        Text(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                            color = TextWhite.copy(alpha = 0.5f), textAlign = TextAlign.Center,
                                            text = "Você ainda não possui contas cadastradas."
                                        )
                                    }
                                }

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

/*                                item {
                                    ActionButtonRow(
                                        categorias = repository.categorias.map { it.title },
                                        getPicCategoria = { repository.getPicCategoria(it) },
                                        contaSelecionada = contaSelecionadaId.orEmpty(),
                                        viewModel = contaVM,
                                        onConfigClick = { showRecorrenciaDialog = true } // Abre o Dialog de Recorrências
                                    )
                                }*/

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
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
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF69F0AE))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Configurar", color = Color(0xFF69F0AE), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                item {
                                    val gruposDeQuatro = orcamentosComProgresso.chunked(4)
                                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        gruposDeQuatro.forEach { linhaDeOrcamentos ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    val meses = remember {
                                        listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Suas Metas e Cofrinhos aparecerão aqui.", color = TextWhite.copy(alpha = 0.5f))
                            }
                        }

                        3 -> {
                            // --- ABA 3: INVESTIMENTOS ---
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Acompanhe seus Investimentos em breve.", color = TextWhite.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
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
            GerenciarRecorrenciaDialog(
                viewModel = contaVM,
                onDismiss = { showRecorrenciaDialog = false }
            )
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
                categoriasDisponiveis = repository.categorias.map { it.title },
                onSalvar = { categoria, valor ->
                    orcamentoVM.salvarOrcamento(categoria, valor)
                },
                onDismiss = { showAddOrcamentoDialog = false }
            )
        }

        if (selectedIndex == 0) {
            ContaBancaria(
                viewModelFactory = ContaSaldoViewModelFactory(application, repository),
                onClose = { selectedIndex = -1 }
            )
        }
        if (selectedIndex == 1) {
            ExtratoScreen(
                despesasVM = despVM,
                categorias = repository.categorias.map { it.title },
                isPrivate = isPrivate,
                onBack = { selectedIndex = -1 }
            )
        }
        if (selectedIndex == 2) {
            MetasScreen(
                viewModel = metaVM,
                isPrivate = isPrivate,
                onBack = { selectedIndex = -1 }
            )
        }
    }
}