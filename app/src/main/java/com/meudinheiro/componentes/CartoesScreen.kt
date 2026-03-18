package com.meudinheiro.componentes

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.CartoesViewModel
import com.meudinheiro.viewModel.CartoesViewModelFactory
import java.util.Calendar
import java.util.Date
import kotlin.math.absoluteValue

// Cores Blu Macaw
private val DeepSpaceBlue = Color(0xFF131E29)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFF7000FF)
private val CardGlass = Color(0xFF1B263B).copy(alpha = 0.8f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartoesScreen(
    viewModel: CartoesViewModel = viewModel(factory = CartoesViewModelFactory(LocalContext.current))
) {
    val listaCartoes by viewModel.cartoes.collectAsState()
    val listaContas by viewModel.contasDisponiveis.collectAsState()
    val despesasDoCartaoAtual by viewModel.despesasDoCartao.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { listaCartoes.size })
    val paginaAtual by remember { derivedStateOf { pagerState.currentPage } }
    var mesFaturaOffset by remember(paginaAtual) { mutableIntStateOf(0) }

    LaunchedEffect(paginaAtual, listaCartoes) {
        if (listaCartoes.isNotEmpty() && paginaAtual < listaCartoes.size) {
            val cartaoFocado = listaCartoes[paginaAtual]
            viewModel.buscarDespesasPorCartao(cartaoFocado.id)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
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
            // 1. Cabeçalho
            item {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Minha Carteira", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${listaCartoes.size} cartões ativos", color = NeonCyan.copy(0.7f), fontSize = 14.sp)
                }
            }

            if (listaCartoes.isEmpty()) {
                item { EstadoVazioCartoes() }
            } else {
                // 2. Carrossel de Cartões (👇 ALTURA REDUZIDA AQUI)
                item {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 40.dp), // Reduzi o padding horizontal
                        pageSpacing = 16.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp) // 👇 Diminuído de 260.dp para 210.dp (Cartão mais elegante)
                    ) { page ->
                        val cartao = listaCartoes[page]
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

                        Box(modifier = Modifier.graphicsLayer {
                            val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 1f)
                            scaleX = scale; scaleY = scale
                            alpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 1f)
                            rotationY = pageOffset * 15f
                        }) {
                            CartaoFisicoHolografico(cartao)
                        }
                    }
                }

                // 3. Botões de Ação
                val cartaoFocado = listaCartoes.getOrNull(paginaAtual)

// 4. LÓGICA DE FATURAS (Navegação e Fechamento Automático)
                    val diaFechamento = cartaoFocado?.diaVencimento?.let { if (it > 7) cartaoFocado.diaVencimento - 7 else 25 }

                    // Calcula qual mês estamos visualizando na tela com base nas setas
                    val calVisao = Calendar.getInstance()
                    calVisao.add(Calendar.MONTH, mesFaturaOffset)
                    val mesAlvo = calVisao.get(Calendar.MONTH)
                    val anoAlvo = calVisao.get(Calendar.YEAR)

                    // Filtra as despesas jogando as que passaram do fechamento para o mês seguinte
                    val despesasFaturaAtual = despesasDoCartaoAtual.filter { despesa ->
                        val (mesDaFatura, anoDaFatura) = calcularFaturaDaDespesa(despesa.data, diaFechamento)
                        mesDaFatura == mesAlvo && anoDaFatura == anoAlvo
                    }.sortedByDescending { it.data }

                    // Soma o valor total da fatura
                    val totalFatura = despesasFaturaAtual.sumOf { it.valor }

                if (cartaoFocado != null) {
                    val despesasFaturaAtual = despesasDoCartaoAtual.filter { despesa ->
                        val (mesDaFatura, anoDaFatura) = calcularFaturaDaDespesa(despesa.data, diaFechamento)
                        mesDaFatura == mesAlvo && anoDaFatura == anoAlvo
                    }.sortedByDescending { it.data }
                    val despesasPendentes = despesasFaturaAtual.filter { !it.Pago }
                    val totalPendente = despesasPendentes.sumOf { it.valor }
                    val faturaJaPaga = despesasFaturaAtual.isNotEmpty() && despesasPendentes.isEmpty()
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        AcoesCartao(cartao = cartaoFocado, onDelete = { viewModel.removerCartao(cartaoFocado) },
                            onPagar = {
                                if (totalFatura > 0.0) {
                                    viewModel.pagarFatura(cartaoFocado, totalFatura)
                                }
                            },
                            faturaPaga = faturaJaPaga)
                    }

                    val mesesNomes = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

                    item {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 👇 CONTROLADOR DE NAVEGAÇÃO DE FATURAS < MÊS >
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { mesFaturaOffset-- }) {
                                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Anterior", tint = NeonCyan)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Fatura de ${mesesNomes[mesAlvo]}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatarMoedaBR(totalFatura, true), // Usando a sua função de formatar moeda!
                                    color = if (mesFaturaOffset == 0) NeonCyan else Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Fecha dia $diaFechamento",
                                    color = Color.White.copy(0.5f),
                                    fontSize = 11.sp
                                )
                            }

                            IconButton(onClick = { mesFaturaOffset++ }) {
                                Icon(Icons.Rounded.ChevronRight, contentDescription = "Próxima", tint = NeonCyan)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 5. Lista de Extrato Visível
                    if (despesasFaturaAtual.isEmpty()) {
                        item {
                            Text(
                                "Nenhuma despesa nesta fatura",
                                color = Color.White.copy(0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        items(despesasFaturaAtual, key = { it.id }) { despesa ->
                            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                                ItemExtratoNeon(despesa = despesa) // (Ou despesa.paraCompra())
                            }
                        }
                    }                }
            }
        }

        if (showBottomSheet) {
            FormularioCartaoBottomSheet(
                contasDisponiveis = listaContas,
                onDismiss = { showBottomSheet = false },
                onSalvar = { novo -> viewModel.salvarCartao(novo) }
            )
        }
    }
}

@Composable
fun CartaoFisicoHolografico(cartao: CartaoComConta) {
    val transition = rememberInfiniteTransition(label = "shine")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_anim"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
        start = Offset(translateAnim, translateAnim),
        end = Offset(translateAnim + 300f, translateAnim + 300f)
    )

    val corBorda = if (cartao.tipo == "CRÉDITO") NeonCyan else NeonPurple

    Card(
        modifier = Modifier
            .fillMaxSize() // 👇 Agora ele preenche o espaço reduzido definido no Pager
            .clip(RoundedCornerShape(20.dp)) // Borda um pouco mais suave
            .border(1.dp, corBorda.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CardGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp), // 👇 Reduzi o padding interno para acomodar no cartão menor
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOPO
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(cartao.nomeCartao, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) // Fonte menor
                        Text(cartao.tipo, color = corBorda, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("🏦 ${cartao.nomeConta}", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }

                // MEIO: CHIP E LIMITE
                Column(modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Memory, null, tint = Color(0xFFFFD700), modifier = Modifier.size(32.dp)) // Chip menor

                    if (cartao.tipo == "CRÉDITO" && cartao.limiteTotal > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Limite Disponível", color = Color.White.copy(0.6f), fontSize = 10.sp)
                            Text(formatarMoedaBR(cartao.limiteDisponivel,false), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        val progressoLimite = (cartao.limiteDisponivel.toDouble() / cartao.limiteTotal.toDouble()).toFloat().coerceIn(0f, 1f)

                        LinearProgressIndicator(
                            progress = { progressoLimite },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
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
                        fontSize = 18.sp, // Fonte menor
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("VENCTO", color = Color.White.copy(0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("${cartao.diaVencimento}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
@Composable
fun AcoesCartao(cartao: CartaoComConta, onDelete: () -> Unit, onPagar: () -> Unit, ,
                faturaPaga: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BotaoAcaoRapida(Icons.Rounded.Receipt, "Fatura", NeonCyan, onClick = {})
        BotaoAcaoRapida(
            if (faturaPaga) Icons.Rounded.CheckCircle else Icons.Rounded.Payments,
            if (faturaPaga) "Paga" else "Pagar",
            if (faturaPaga) Color.Gray else Color.White,
            { if (!faturaPaga) onPagar() })
        BotaoAcaoRapida(Icons.Rounded.DeleteSweep, "Excluir", Color(0xFFFF5252), onClick = onDelete)
    }
}

@Composable
fun BotaoAcaoRapida(icone: ImageVector, texto: String, cor: Color, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CardGlass)
                .border(1.dp, cor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(texto, color = Color.White.copy(0.6f), fontSize = 12.sp)
    }
}

@Composable
fun EstadoVazioCartoes() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.CreditCardOff, null, tint = Color.White.copy(0.1f), modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(16.dp))
        Text("Nenhum cartão por aqui", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Adicione seus cartões de crédito ou débito para organizar seus limites e faturas.",
            color = Color.White.copy(0.5f), fontSize = 14.sp, textAlign = TextAlign.Center
        )
    }
}

// Função que joga a despesa para a próxima fatura se passar da data de fechamento
fun calcularFaturaDaDespesa(dataCompra: Date, diaFechamento: Int?): Pair<Int, Int> {
    val cal = Calendar.getInstance().apply { time = dataCompra }
    val dia = cal.get(Calendar.DAY_OF_MONTH)
    var mes = cal.get(Calendar.MONTH)
    var ano = cal.get(Calendar.YEAR)

    // Se o dia da compra for DEPOIS do dia do fechamento, vai para a próxima fatura!
    if (dia > diaFechamento!!) {
        mes += 1
        // Se passar de Dezembro (11), vai para Janeiro (0) do ano seguinte
        if (mes > 11) {
            mes = 0
            ano += 1
        }
    }
    return Pair(mes, ano)
}