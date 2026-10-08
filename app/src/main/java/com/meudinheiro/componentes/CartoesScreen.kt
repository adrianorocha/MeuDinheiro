package com.meudinheiro.componentes

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCardOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.Despesa
import com.meudinheiro.domain.CartoesUi
import com.meudinheiro.domain.Financas
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.funcoes.Haptics // Nosso motor de vibração
import com.meudinheiro.viewModel.CartoesViewModel
import com.meudinheiro.viewModel.CartoesViewModelFactory
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.absoluteValue

// Cores Blu Macaw
private val DeepSpaceBlue = Color(0xFF131E29)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFF7000FF)
private val CardGlass = Color(0xFF1B263B).copy(alpha = 0.8f)

data class EstadoFatura(
    val lista: List<Despesa>,
    val total: Double,
    val totalPendente: Double,
    val jaPaga: Boolean,
    val mesNome: String,
    val diaFechamento: Int,
    val dataReferencia: Date,
    /** Compras do grupo ainda não chegaram (troca de cartão em andamento): não mostrar "vazio" nem dados do anterior. */
    val carregando: Boolean = false,
    /** R42: compras no débito (saem direto da conta) do mês da fatura; fora de total/em aberto/limite/pagamento. */
    val debitos: List<Despesa> = emptyList(),
    val outras: Financas.OutrasFaturas = Financas.OutrasFaturas(0.0, null)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartoesScreen(
    viewModel: CartoesViewModel = viewModel(factory = CartoesViewModelFactory(LocalContext.current)),
    /** Para o botão "Nova compra" (diálogo de despesa). Sem [contaViewModel] o botão não aparece. */
    contaViewModel: ContaSaldoViewModel? = null,
    categorias: List<String> = emptyList(),
    getPicCategoria: (String) -> String = { "" },
    historico: List<Despesa> = emptyList()
) {
    val context = LocalContext.current
    val todasDespesas by viewModel.todasDespesas.collectAsState()
    var showNovaCompra by remember { mutableStateOf(false) }
    var avisoExclusao by remember { mutableStateOf<String?>(null) }
    var virtualParaExcluir by remember { mutableStateOf<CartaoComConta?>(null) }
    // Depois de salvar: (id editado | null, ids que já existiam) -> foca no cartão salvo/criado.
    var focoAposSalvar by remember { mutableStateOf<Pair<Int?, List<CartaoComConta>>?>(null) }
    val listaCartoes by viewModel.cartoes.collectAsState()
    val listaContas by viewModel.contasDisponiveis.collectAsState()
    val comprasEmitidas by viewModel.comprasDoCartao.collectAsState()
    var showResumoFatura by remember { mutableStateOf(false) }

    var processandoPagamento by remember { mutableStateOf(false) }
    var exibirConfirmacao by remember { mutableStateOf(false) }
    val exigirBioPagamento by remember { com.meudinheiro.funcoes.UserPreferences(context).biometriaLancarFlow }.collectAsState(initial = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    var cartaoEmEdicao by remember { mutableStateOf<CartaoComConta?>(null) }

    val pagerState = rememberPagerState(pageCount = { listaCartoes.size })
    val paginaAtual by remember { derivedStateOf { pagerState.currentPage } }
    var mesFaturaOffset by remember(paginaAtual) { mutableIntStateOf(0) }
    val escopo = rememberCoroutineScope()

    // Só usa a lista emitida se for do grupo do cartão focado: ao deslizar, a emissão do cartão anterior
    // (ou vazia) nunca é exibida como se fosse a do novo.
    val comprasDoFoco = comprasEmitidas.takeIf { it.principalId != null && it.principalId == listaCartoes.getOrNull(paginaAtual)?.idDoGrupo }
    val despesasDoCartaoAtual = comprasDoFoco?.credito ?: emptyList()
    val debitosDoGrupo = comprasDoFoco?.debito ?: emptyList()

    fun solicitarExclusao(c: CartaoComConta) {
        if (c.ehVirtual) {
            val msg = CartoesUi.bloqueioExclusaoVirtual(c.id, c.nomeCartao, todasDespesas)
            if (msg != null) avisoExclusao = msg else virtualParaExcluir = c
        } else {
            val ids = listaCartoes.filter { it.idDoGrupo == c.id }.map { it.id }.toSet()
            val abertas = todasDespesas.count { it.cartaoId in ids && !it.pago }
            if (abertas > 0) {
                avisoExclusao = "O cartão \"${c.nomeCartao}\" (ou um virtual dele) tem $abertas compra(s) em aberto. Pague as faturas antes de excluí-lo."
            } else viewModel.removerCartao(c)
        }
    }

    // Filtro por cartão do grupo: virtual focado => já filtrado nele; físico => "Todos".
    val cartaoFocadoAgora = listaCartoes.getOrNull(paginaAtual)
    val grupoFocado = remember(listaCartoes, cartaoFocadoAgora?.idDoGrupo) {
        listaCartoes.filter { it.idDoGrupo == cartaoFocadoAgora?.idDoGrupo }.sortedBy { it.ehVirtual }
    }
    var filtroBruto by remember(cartaoFocadoAgora?.id) { mutableStateOf(CartoesUi.filtroInicial(cartaoFocadoAgora)) }
    val filtro = CartoesUi.filtroValido(filtroBruto, grupoFocado)

    // Após salvar, leva o carrossel ao cartão salvo (novo: o id que não existia antes).
    LaunchedEffect(listaCartoes, focoAposSalvar) {
        val pendente = focoAposSalvar ?: return@LaunchedEffect
        if (listaCartoes == pendente.second) return@LaunchedEffect // o salvamento ainda não chegou à lista
        val idsAntes = pendente.second.map { it.id }.toSet()
        val alvo = pendente.first ?: listaCartoes.firstOrNull { it.id !in idsAntes }?.id
        val idx = listaCartoes.indexOfFirst { it.id == alvo }
        focoAposSalvar = null
        if (idx >= 0) pagerState.animateScrollToPage(idx)
    }
    // Se o salvamento falhar (a lista nunca muda), não deixa o foco pendente para uma alteração futura.
    LaunchedEffect(focoAposSalvar) {
        if (focoAposSalvar != null) {
            kotlinx.coroutines.delay(5000)
            focoAposSalvar = null
        }
    }

    var visivel by remember { mutableStateOf(false) }

    val mesesNomes = remember {
        arrayOf(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
        )
    }

    LaunchedEffect(paginaAtual, listaCartoes) {
        if (listaCartoes.isNotEmpty() && paginaAtual < listaCartoes.size) {
            val cartaoFocado = listaCartoes[paginaAtual]
            viewModel.buscarDespesasPorCartao(cartaoFocado.id)
            processandoPagamento = false
            Haptics.vibrar(context, "movimento") // Feedback ao trocar de cartão
        }
    }

    LaunchedEffect(Unit) { visivel = true }

    val faturaInfo by remember(comprasDoFoco, mesFaturaOffset, paginaAtual, listaCartoes) {
        derivedStateOf {
            val cartao = listaCartoes.getOrNull(paginaAtual)
            if (cartao == null) null else {
                // R6: usa o fechamento REAL do cartão (antes era "vencimento − 7", ignorando o cadastro).
                val aberta = Financas.faturaDaCompra(System.currentTimeMillis(), cartao.diaFechamento)
                val ref = aberta.deslocar(mesFaturaOffset)
                // R18: fatura única do grupo (físico + virtuais).
                val idsGrupo = listaCartoes.filter { it.idDoGrupo == cartao.idDoGrupo }.map { it.id }.toSet()
                val resumo = Financas.resumoFatura(
                    idsGrupo, cartao.diaFechamento, cartao.diaVencimento, despesasDoCartaoAtual, ref
                )
                EstadoFatura(
                    lista = resumo.itens,
                    total = resumo.total,
                    totalPendente = resumo.pendente,
                    jaPaga = resumo.paga,
                    mesNome = mesesNomes[ref.mes - 1],
                    diaFechamento = Financas.diaDeFechamento(cartao.diaFechamento, ref),
                    dataReferencia = Date(Financas.inicioDoMes(ref.mes, ref.ano)),
                    carregando = comprasDoFoco == null,
                    debitos = Financas.debitosDaFatura(debitosDoGrupo, ref),
                    outras = Financas.outrasFaturasEmAberto(idsGrupo, cartao.diaFechamento, despesasDoCartaoAtual, ref)
                )
            }
        }
    }

    AnimatedVisibility(
        visible = visivel,
        enter = fadeIn(animationSpec = tween(600)) +
                slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ),
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        Haptics.vibrar(context, "clique")
                        showBottomSheet = true
                    },
                    containerColor = NeonCyan,
                    shape = CircleShape
                ) { Icon(Icons.Default.Add, contentDescription = null, tint = DeepSpaceBlue) }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
                    ) {
                        Text(
                            "MEUS CARTÕES //",
                            color = Color.White.copy(0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${listaCartoes.size} cartões ativos",
                            color = NeonCyan.copy(0.7f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (listaCartoes.isEmpty()) {
                    item { EstadoVazioCartoes() }
                } else {
                    item {
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            modifier = Modifier.fillMaxWidth().height(200.dp) // Mais alto para o efeito 3D respirar
                        ) { page ->
                            val cartao = listaCartoes[page]
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

                            Box(modifier = Modifier.clickable {
                                // Tocar num cartão vizinho o traz para o foco (a lista é recalculada para ele).
                                if (page != pagerState.currentPage) {
                                    escopo.launch { pagerState.animateScrollToPage(page) }
                                }
                            }.graphicsLayer {
                                val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 1f)
                                scaleX = scale; scaleY = scale
                                alpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 1f)
                                // O Pager já dá uma leve rotação de carrossel
                                rotationY = pageOffset * 25f
                            }) {
                                // 🚀 O NOVO CARTÃO PARALLAX SENSORIZADO
                                CartaoFisicoHolografico(
                                    cartao,
                                    nomePrincipal = cartao.cartaoPrincipalId?.let { pid ->
                                        listaCartoes.firstOrNull { it.id == pid }?.nomeCartao ?: "(cartão físico não encontrado)"
                                    },
                                    nVirtuais = listaCartoes.count { it.cartaoPrincipalId == cartao.id }
                                )
                            }
                        }
                    }

                    faturaInfo?.let { fatura ->
                        val cartaoFocado = listaCartoes[paginaAtual]

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            AcoesCartao(
                                cartao = cartaoFocado,
                                onDelete = {
                                    Haptics.vibrar(context, "alerta")
                                    solicitarExclusao(cartaoFocado)
                                },
                                onEditar = {
                                    Haptics.vibrar(context, "clique")
                                    cartaoEmEdicao = cartaoFocado
                                },
                                onPagar = {
                                    if (!fatura.jaPaga && !fatura.carregando) {
                                        Haptics.vibrar(context, "clique")
                                        exibirConfirmacao = true
                                    }
                                },
                                onFatura = {
                                    Haptics.vibrar(context, "clique")
                                    showResumoFatura = true
                                },
                                faturaPaga = fatura.jaPaga || processandoPagamento
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (!fatura.carregando) PainelLimiteCompartilhado(
                                cartaoFocado, listaCartoes, despesasDoCartaoAtual,
                                onSelecionar = { alvo ->
                                    val idx = listaCartoes.indexOfFirst { it.id == alvo.id }
                                    if (idx >= 0 && idx != pagerState.currentPage) {
                                        Haptics.vibrar(context, "clique")
                                        escopo.launch { pagerState.animateScrollToPage(idx) }
                                    }
                                },
                                onEditar = { alvo -> Haptics.vibrar(context, "clique"); cartaoEmEdicao = alvo },
                                onExcluir = { alvo -> Haptics.vibrar(context, "alerta"); solicitarExclusao(alvo) }
                            )
                        }

                        if (contaViewModel != null && categorias.isNotEmpty()) {
                            item {
                                val alvoCompra = grupoFocado.firstOrNull { it.id == filtro } ?: cartaoFocado
                                Button(
                                    onClick = { Haptics.vibrar(context, "clique"); showNovaCompra = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue),
                                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Nova compra no ${alvoCompra.nomeCartao}", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            SelectorDeMes(
                                mesNome = fatura.mesNome,
                                total = fatura.total,
                                diaFechamento = fatura.diaFechamento,
                                mesOffset = mesFaturaOffset,
                                onAnterior = {
                                    Haptics.vibrar(context, "movimento")
                                    mesFaturaOffset--
                                },
                                onProximo = {
                                    Haptics.vibrar(context, "movimento")
                                    mesFaturaOffset++
                                }
                            )
                        }

                        val creditoVisivel = CartoesUi.filtrarCredito(fatura.lista, filtro)
                        val debitosVisiveis = CartoesUi.filtrarDebitos(fatura.debitos, filtro)

                        if (!fatura.carregando && grupoFocado.size > 1) {
                            item {
                                FiltroCartoesChips(
                                    grupo = grupoFocado, filtro = filtro,
                                    onFiltro = { Haptics.vibrar(context, "clique"); filtroBruto = it }
                                )
                                if (filtro != null && fatura.lista.isNotEmpty()) {
                                    Text(
                                        "Mostrando ${creditoVisivel.size} de ${fatura.lista.size} · subtotal ${formatarMoedaBR(CartoesUi.subtotal(creditoVisivel), false)} (a fatura continua única: ${formatarMoedaBR(fatura.total, false)})",
                                        color = Color.White.copy(0.5f), fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                                    )
                                }
                            }
                        }

                        if (fatura.carregando) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        } else if (fatura.lista.isNotEmpty() && creditoVisivel.isEmpty()) {
                            item {
                                Text(
                                    "Nenhuma compra deste cartão nesta fatura", color = Color.White.copy(0.3f),
                                    textAlign = TextAlign.Center, fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 24.dp, end = 24.dp)
                                )
                            }
                        } else if (fatura.lista.isEmpty()) {
                            item {
                                EstadoFaturaVazia(
                                    outras = fatura.outras,
                                    onIr = {
                                        fatura.outras.proxima?.let { alvo ->
                                            val aberta = Financas.faturaDaCompra(System.currentTimeMillis(), cartaoFocado.diaFechamento)
                                            mesFaturaOffset = (alvo.ano * 12 + alvo.mes) - (aberta.ano * 12 + aberta.mes)
                                        }
                                    }
                                )
                            }
                        } else {
                            items(creditoVisivel, key = { it.id }) { despesa ->
                                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                                    ItemExtratoNeon(
                                        despesa = despesa,
                                        cartao = CartoesUi.cartaoDaCompra(despesa, listaCartoes) ?: cartaoFocado,
                                        mostrarOrigem = grupoFocado.size > 1
                                    )
                                }
                            }
                        }

                        if (!fatura.carregando && debitosVisiveis.isNotEmpty()) {
                            item {
                                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.AccountBalance, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Compras no débito (saem direto da conta)", color = Color.White.copy(0.7f),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        "Não entram no total da fatura, no limite nem no pagamento.",
                                        color = Color.White.copy(0.4f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            items(debitosVisiveis, key = { "debito-${it.id}" }) { despesa ->
                                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                                    ItemExtratoNeon(
                                        despesa = despesa,
                                        cartao = CartoesUi.cartaoDaCompra(despesa, listaCartoes) ?: cartaoFocado,
                                        mostrarOrigem = grupoFocado.size > 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // DIALOG E BOTTOM SHEETS (Mantidos intactos)
            if (exibirConfirmacao && faturaInfo != null) {
                val fatura = faturaInfo!!
                val focado = listaCartoes.getOrNull(paginaAtual)
                val principalSheet = focado?.let { f -> listaCartoes.firstOrNull { it.id == f.idDoGrupo } }
                if (focado != null && principalSheet != null) {
                    val idsGrupoSheet = listaCartoes.filter { it.idDoGrupo == focado.idDoGrupo }.map { it.id }.toSet()
                    PagarFaturaSheet(
                        principal = principalSheet,
                        idsDoGrupo = idsGrupoSheet,
                        despesasDoGrupo = despesasDoCartaoAtual,
                        saldoConta = listaContas.firstOrNull { it.id == principalSheet.contaId }?.saldo,
                        faturaInicial = Financas.FaturaRef(
                            Financas.mesDe(fatura.dataReferencia.time), Financas.anoDe(fatura.dataReferencia.time)
                        ),
                        onDismiss = { exibirConfirmacao = false },
                        onPagar = { ids, valor ->
                            autenticarParaLancar(
                                context, exigirBioPagamento, "Confirmar pagamento",
                                "Autentique para pagar ${formatarMoedaBR(valor, false)} da fatura"
                            ) {
                                exibirConfirmacao = false
                                processandoPagamento = true
                                viewModel.pagarItens(focado, ids)
                                Haptics.vibrar(context, "sucesso")
                            }
                        }
                    )
                }
            }

            if (showBottomSheet) {
                FormularioCartaoBottomSheet(
                    contasDisponiveis = listaContas,
                    todosCartoes = listaCartoes,
                    onDismiss = { showBottomSheet = false }
                ) { novo ->
                    focoAposSalvar = null to listaCartoes
                    viewModel.salvarCartao(novo)
                }
            }
            if (showNovaCompra && contaViewModel != null) {
                val focoCompra = listaCartoes.getOrNull(paginaAtual)
                val alvoCompra = grupoFocado.firstOrNull { it.id == filtro } ?: focoCompra
                if (alvoCompra != null) {
                    AddDespesaDialog(
                        categorias = categorias,
                        contaSelecionada = alvoCompra.numeroConta,
                        cartoesDisponiveis = listaCartoes,
                        getPicCategoria = getPicCategoria,
                        viewModel = contaViewModel,
                        cartoesViewModel = viewModel,
                        parentScope = escopo,
                        onDismiss = { showNovaCompra = false },
                        historico = historico,
                        formaPagamentoInicial = "CARTAO",
                        cartaoInicialId = alvoCompra.id
                    )
                }
            }
            avisoExclusao?.let { msg ->
                AlertDialog(
                    onDismissRequest = { avisoExclusao = null },
                    confirmButton = { TextButton(onClick = { avisoExclusao = null }) { Text("Entendi", color = NeonCyan) } },
                    title = { Text("Não é possível excluir", color = Color.White) },
                    text = { Text(msg, color = Color.White.copy(0.8f)) },
                    containerColor = DeepSpaceBlue
                )
            }
            virtualParaExcluir?.let { v ->
                AlertDialog(
                    onDismissRequest = { virtualParaExcluir = null },
                    confirmButton = {
                        TextButton(onClick = { viewModel.removerCartao(v); virtualParaExcluir = null }) {
                            Text("Excluir", color = Color(0xFFFF5252))
                        }
                    },
                    dismissButton = { TextButton(onClick = { virtualParaExcluir = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } },
                    title = { Text("Excluir cartão virtual?", color = Color.White) },
                    text = {
                        Text(
                            "\"${v.nomeCartao}\" •••• ${v.finalCartao} será removido. O histórico de compras passa para o cartão físico.",
                            color = Color.White.copy(0.8f)
                        )
                    },
                    containerColor = DeepSpaceBlue
                )
            }
            cartaoEmEdicao?.let { editando ->
                FormularioCartaoBottomSheet(
                    contasDisponiveis = listaContas,
                    todosCartoes = listaCartoes,
                    cartaoEdicao = editando,
                    onDismiss = { cartaoEmEdicao = null }
                ) { alterado ->
                    focoAposSalvar = editando.id to listaCartoes
                    viewModel.salvarCartao(alterado)
                }
            }
            if (showResumoFatura && faturaInfo != null) {
                val cartaoFocado = listaCartoes.getOrNull(paginaAtual)
                if (cartaoFocado != null) {
                    ResumoFaturaBottomSheet(faturaInfo!!, cartaoFocado) { showResumoFatura = false }
                }
            }
        }
    }
}

// ============================================================================
// 🚀 O CARTÃO HOLOGRÁFICO (SENSORIZADO)
// ============================================================================
@Composable
fun CartaoFisicoHolografico(cartao: CartaoComConta, nomePrincipal: String? = null, nVirtuais: Int = 0) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    // Estados do Acelerômetro
    var roll by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }

    // Suavização da leitura do sensor para o cartão não "tremer"
    val animatedRoll by animateFloatAsState(targetValue = roll, animationSpec = tween(300, easing = LinearOutSlowInEasing), label = "roll")
    val animatedPitch by animateFloatAsState(targetValue = pitch, animationSpec = tween(300, easing = LinearOutSlowInEasing), label = "pitch")

    // Hook no Acelerômetro do S25
    DisposableEffect(Unit) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // X (event.values[0]) é a inclinação lateral
                // Y (event.values[1]) é a inclinação vertical
                val x = event.values[0]
                val y = event.values[1]

                // Multiplicador exagerado para gerar o efeito visual com pouco movimento
                roll = (x * 4f).coerceIn(-25f, 25f)
                // Subtrai ~5f porque o celular geralmente é segurado levemente inclinado para cima
                pitch = ((y - 5f) * 4f).coerceIn(-25f, 25f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val corBorda = if (cartao.tipo == "CRÉDITO") NeonCyan else NeonPurple

    // 🌟 O Degradê Holográfico que se move com o sensor
    val holographicBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            corBorda.copy(alpha = 0.1f),
            Color(0xFFFFD700).copy(alpha = 0.15f), // Dourado do chip
            Color.White.copy(alpha = 0.4f), // Feixe de luz forte
            NeonPurple.copy(alpha = 0.1f),
            Color.Transparent
        ),
        start = Offset(0f + (animatedRoll * 30f), 0f + (animatedPitch * 30f)),
        end = Offset(800f + (animatedRoll * 30f), 800f + (animatedPitch * 30f))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .graphicsLayer {
                // 🚀 A MÁGICA 3D ACONTECE AQUI!
                rotationX = animatedPitch  // Inclinação cima/baixo
                rotationY = animatedRoll   // Inclinação esquerda/direita
                cameraDistance = 16f * density // Dá a profundidade 3D
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, corBorda.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CardGlass),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // O fundo de vidro
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1B2A).copy(alpha = 0.6f)))

                // O brilho holográfico reativo passando por cima
                Box(modifier = Modifier.fillMaxSize().background(holographicBrush))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // TOPO
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            Text(
                                cartao.nomeCartao.uppercase(),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(if (cartao.ehVirtual) "VIRTUAL · ${cartao.tipo}" else cartao.tipo, color = corBorda, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            val comLimite = Financas.tipoDeCartao(cartao.tipo) != "DEBITO" && cartao.limiteTotal > 0.0
                            val dispGrupo = if (comLimite) " · disponível do grupo ${formatarMoedaBR(cartao.limiteDisponivel, false)}" else ""
                            if (cartao.ehVirtual && nomePrincipal != null) {
                                Text("Compartilha o saldo de $nomePrincipal$dispGrupo", color = Color.White.copy(0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            } else if (!cartao.ehVirtual && nVirtuais > 0) {
                                Text(
                                    (if (nVirtuais == 1) "1 virtual compartilha este saldo" else "$nVirtuais virtuais compartilham este saldo") + dispGrupo,
                                    color = Color.White.copy(0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text("🏦 ${cartao.nomeConta}", color = Color.White.copy(0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    // CHIP E LIMITE
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Memory, null, tint = Color(0xFFFFD700).copy(alpha = 0.8f), modifier = Modifier.size(32.dp))

                        if (Financas.tipoDeCartao(cartao.tipo) != "DEBITO" && cartao.limiteTotal > 0.0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text("LIMITE", color = Color.White.copy(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(formatarMoedaBR(cartao.limiteDisponivel, false), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }

                            val progressoLimite = (cartao.limiteDisponivel.toDouble() / cartao.limiteTotal.toDouble()).toFloat().coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progressoLimite },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(CircleShape),
                                color = corBorda,
                                trackColor = Color.White.copy(0.1f)
                            )
                        }
                    }

                    // RODAPÉ
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "•••• ${cartao.finalCartao}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text("VENCTO", color = Color.White.copy(0.4f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${cartao.diaVencimento}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstadoFaturaVazia(outras: Financas.OutrasFaturas, onIr: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (outras.emAberto > 0.0 && outras.proxima != null) {
            Text(
                "Nenhuma compra nesta fatura. Há ${formatarMoedaBR(outras.emAberto, false)} em aberto em outras faturas",
                color = Color.White.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp
            )
            TextButton(onClick = onIr) {
                Text("Ir para a fatura ${"%02d/%d".format(outras.proxima.mes, outras.proxima.ano)}", color = NeonCyan)
            }
        } else {
            Text(
                "Nenhuma despesa nesta fatura", color = Color.White.copy(0.3f),
                textAlign = TextAlign.Center, fontSize = 14.sp
            )
        }
    }
}

// ============================================================================
// COMPONENTES AUXILIARES (Mantidos)
// ============================================================================
@Composable
fun SelectorDeMes(mesNome: String, total: Double, diaFechamento: Int, mesOffset: Int, onAnterior: () -> Unit, onProximo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAnterior) { Icon(Icons.Rounded.ChevronLeft, "Anterior", tint = NeonCyan) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FATURA DE ${mesNome.uppercase()}", color = Color.White.copy(0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(formatarMoedaBR(total, false), color = if (mesOffset == 0) NeonCyan else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("Fecha dia $diaFechamento", color = Color.White.copy(0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        IconButton(onClick = onProximo) { Icon(Icons.Rounded.ChevronRight, "Próxima", tint = NeonCyan) }
    }
}

@Composable
fun AcoesCartao(cartao: CartaoComConta, onDelete: () -> Unit, onEditar: () -> Unit = {}, onPagar: () -> Unit, onFatura: () -> Unit, faturaPaga: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        BotaoAcaoRapida(Icons.Rounded.Receipt, "FATURA", NeonCyan, onClick = onFatura)
        BotaoAcaoRapida(
            if (faturaPaga) Icons.Rounded.CheckCircle else Icons.Rounded.Payments,
            if (faturaPaga) "PAGA" else "PAGAR",
            if (faturaPaga) Color.Gray else Color.White,
            { if (!faturaPaga) onPagar() })
        BotaoAcaoRapida(Icons.Rounded.Edit, "EDITAR", NeonPurple, onClick = onEditar)
        BotaoAcaoRapida(Icons.Rounded.DeleteSweep, "EXCLUIR", Color(0xFFFF5252), onClick = onDelete)
    }
}

@Composable
fun BotaoAcaoRapida(icone: ImageVector, texto: String, cor: Color, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(CardGlass).border(1.dp, cor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(texto, color = Color.White.copy(0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EstadoVazioCartoes() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.CreditCardOff, null, tint = Color.White.copy(0.1f), modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(16.dp))
        Text("SISTEMA OFFLINE", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("Adicione seus cartões para habilitar o monitoramento holográfico de faturas.", color = Color.White.copy(0.5f), fontSize = 12.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumoFaturaBottomSheet(fatura: EstadoFatura, cartao: CartaoComConta, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepSpaceBlue,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val statusText = if (fatura.jaPaga) "PAGA" else if (fatura.total == 0.0) "ZERADA" else "ABERTA"
            val statusColor = if (fatura.jaPaga) Color(0xFF69F0AE) else NeonCyan

            Box(modifier = Modifier.background(statusColor.copy(0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("FATURA // ${fatura.mesNome.uppercase()}", color = Color.White.copy(0.5f), fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            Text(formatarMoedaBR(fatura.total, false), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)

            Spacer(modifier = Modifier.height(32.dp))

            Card(colors = CardDefaults.cardColors(containerColor = CardGlass), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vencimento", color = Color.White.copy(0.6f), fontSize = 14.sp)
                        Text("${cartao.diaVencimento} de ${fatura.mesNome}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fechamento", color = Color.White.copy(0.6f), fontSize = 14.sp)
                        Text("${fatura.diaFechamento} de ${fatura.mesNome}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Pendente", color = Color.White.copy(0.6f), fontSize = 14.sp)
                        Text(formatarMoedaBR(fatura.totalPendente, false), color = if (fatura.totalPendente > 0) Color(0xFFFF5252) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// R41 — limite compartilhado do grupo + saldo PRÓPRIO de cada cartão (físico e virtuais)
// ============================================================================
@Composable
fun PainelLimiteCompartilhado(
    focado: CartaoComConta, todos: List<CartaoComConta>, despesasDoGrupo: List<Despesa>,
    /** Tocar na linha leva o carrossel até o cartão. */
    onSelecionar: (CartaoComConta) -> Unit = {},
    /** Editar/excluir por linha de cartão VIRTUAL (o físico usa os botões do cartão focado). */
    onEditar: ((CartaoComConta) -> Unit)? = null,
    onExcluir: ((CartaoComConta) -> Unit)? = null
) {
    val grupo = todos.filter { it.idDoGrupo == focado.idDoGrupo }.sortedBy { it.ehVirtual }
    val principal = grupo.firstOrNull { !it.ehVirtual } ?: return
    if (Financas.tipoDeCartao(principal.tipo) == "DEBITO" || principal.limiteTotal <= 0.0) return
    val cartoes = grupo.map { it.paraCartao() }
    val saldos = cartoes.map { it to Financas.saldoDoCartao(it, cartoes, despesasDoGrupo) }
    val disponivelGrupo = saldos.first().second.disponivelGrupo
    val usadoGrupo = Financas.limiteDisponivel(principal.paraCartao(), emptyList(), emptySet()) - disponivelGrupo

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = CardGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Limite compartilhado", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text(formatarMoedaBR(principal.limiteTotal, false), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Usado (grupo)", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text(formatarMoedaBR(usadoGrupo, false), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Disponível (grupo)", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text(formatarMoedaBR(disponivelGrupo, false), color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            ComprometimentoLimite(principal, grupo, despesasDoGrupo, disponivelGrupo)
            saldos.forEach { (c, s) ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(0.1f))
                val ehFocado = c.id == focado.id
                val real = grupo.first { it.id == c.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onSelecionar(real) }
                ) {
                    Text("${c.nome} •••• ${c.finalCartao}", color = if (ehFocado) NeonCyan else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (c.ehVirtual) {
                        Text(
                            "VIRTUAL", color = DeepSpaceBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(NeonPurple.copy(alpha = 0.9f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (c.ehVirtual) {
                        if (onEditar != null) IconButton(onClick = { onEditar(real) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Edit, "Editar ${c.nome}", tint = NeonPurple, modifier = Modifier.size(18.dp))
                        }
                        if (onExcluir != null) IconButton(onClick = { onExcluir(real) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, "Excluir ${c.nome}", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Usado: ${formatarMoedaBR(s.usado, false)} (${(s.razao * 100).toInt()}%)", color = Color.White.copy(0.8f), fontSize = 12.sp)
                    Text("Disponível: ${formatarMoedaBR(s.disponivel, false)}", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { s.razao.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(CircleShape),
                    color = if (s.razao >= 0.9) Color(0xFFFF5252) else if (c.ehVirtual) NeonPurple else NeonCyan,
                    trackColor = Color.White.copy(0.1f)
                )
                if (s.limiteProprio != null) {
                    Text("Limite próprio: ${formatarMoedaBR(s.limiteProprio, false)}", color = Color.White.copy(0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// ============================================================================
// Visibilidade do limite: fatura atual × parcelas futuras × disponível + o que volta ao pagar
// ============================================================================
private val CorParcelasFuturas = Color(0xFFFFB74D)
private val CorFaturaAtual = Color(0xFFFF5252)

@Composable
private fun ComprometimentoLimite(
    principal: CartaoComConta, grupo: List<CartaoComConta>, despesas: List<Despesa>, disponivelGrupo: Double
) {
    val ids = grupo.map { it.id }.toSet()
    val comp = remember(principal, despesas, grupo) {
        Financas.comprometimentoCartao(principal.paraCartao(), despesas, System.currentTimeMillis(), ids)
    }
    val total = principal.limiteTotal
    if (total <= 0.0 || comp.emAbertoTotal <= 0.0) return
    val pAtual = (comp.faturaAtual / total).toFloat().coerceIn(0f, 1f)
    val pFut = (comp.parcelasFuturas / total).toFloat().coerceIn(0f, 1f - pAtual)
    val pDisp = (1f - pAtual - pFut).coerceAtLeast(0f)
    val fmt = remember { java.text.SimpleDateFormat("dd/MM", java.util.Locale("pt", "BR")) }

    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(0.08f))) {
        if (pAtual > 0f) Box(Modifier.weight(pAtual).fillMaxSize().background(CorFaturaAtual))
        if (pFut > 0f) Box(Modifier.weight(pFut).fillMaxSize().background(CorParcelasFuturas))
        if (pDisp > 0f) Box(Modifier.weight(pDisp).fillMaxSize().background(NeonCyan.copy(alpha = 0.6f)))
    }
    Spacer(Modifier.height(6.dp))
    LegendaLimite(CorFaturaAtual, "Fatura atual", comp.faturaAtual)
    LegendaLimite(CorParcelasFuturas, "Parcelas futuras", comp.parcelasFuturas)
    LegendaLimite(NeonCyan, "Disponível", disponivelGrupo)

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Payments, null, tint = NeonCyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Limite que volta ao pagar", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    comp.liberacaoPorFatura.take(6).forEach { l ->
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "%02d/%d • vence %s".format(l.ref.mes, l.ref.ano, fmt.format(Date(l.vencimento))),
                color = Color.White.copy(0.6f), fontSize = 12.sp
            )
            Text("+ " + formatarMoedaBR(l.valor, false), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LegendaLimite(cor: Color, rotulo: String, valor: Double) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(6.dp))
        Text(rotulo, color = Color.White.copy(0.6f), fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(formatarMoedaBR(valor, false), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Chips "Todos | <cada cartão do grupo>" que filtram os lançamentos da fatura (a fatura continua única, R18). */
@Composable
private fun FiltroCartoesChips(grupo: List<CartaoComConta>, filtro: Int?, onFiltro: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChipFiltroCartao("Todos os cartões", filtro == null) { onFiltro(null) }
        grupo.forEach { k ->
            ChipFiltroCartao(
                (if (k.ehVirtual) "${k.nomeCartao} · virtual" else k.nomeCartao) + " ••${k.finalCartao}",
                filtro == k.id
            ) { onFiltro(k.id) }
        }
    }
}

@Composable
private fun ChipFiltroCartao(texto: String, ativo: Boolean, onClick: () -> Unit) {
    Text(
        texto,
        color = if (ativo) NeonCyan else Color.White.copy(0.7f),
        fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (ativo) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (ativo) NeonCyan else Color.White.copy(0.2f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
