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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCardOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.funcoes.Haptics // Nosso motor de vibração
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

data class EstadoFatura(
    val lista: List<Despesa>,
    val total: Double,
    val totalPendente: Double,
    val jaPaga: Boolean,
    val mesNome: String,
    val diaFechamento: Int,
    val dataReferencia: Date
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartoesScreen(
    viewModel: CartoesViewModel = viewModel(factory = CartoesViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val listaCartoes by viewModel.cartoes.collectAsState()
    val listaContas by viewModel.contasDisponiveis.collectAsState()
    val despesasDoCartaoAtual by viewModel.despesasDoCartao.collectAsState()
    var showResumoFatura by remember { mutableStateOf(false) }

    var processandoPagamento by remember { mutableStateOf(false) }
    var exibirConfirmacao by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { listaCartoes.size })
    val paginaAtual by remember { derivedStateOf { pagerState.currentPage } }
    var mesFaturaOffset by remember(paginaAtual) { mutableIntStateOf(0) }

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

    val faturaInfo by remember(despesasDoCartaoAtual, mesFaturaOffset, paginaAtual, listaCartoes) {
        derivedStateOf {
            val cartao = listaCartoes.getOrNull(paginaAtual)
            if (cartao == null) null else {
                val diaF = if (cartao.diaVencimento > 7) cartao.diaVencimento - 7 else 25
                val cal = Calendar.getInstance().apply { add(Calendar.MONTH, mesFaturaOffset) }
                val mAlvo = cal.get(Calendar.MONTH)
                val aAlvo = cal.get(Calendar.YEAR)

                val filtradas = despesasDoCartaoAtual.filter { d ->
                    val (m, a) = calcularFaturaDaDespesa(d.data, diaF)
                    m == mAlvo && a == aAlvo
                }.sortedByDescending { it.data }

                val pendentes = filtradas.filter { !it.pago }

                EstadoFatura(
                    lista = filtradas,
                    total = filtradas.sumOf { it.valor },
                    totalPendente = pendentes.sumOf { it.valor },
                    jaPaga = filtradas.isNotEmpty() && pendentes.isEmpty(),
                    mesNome = mesesNomes[mAlvo],
                    diaFechamento = diaF,
                    dataReferencia = cal.time
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

                            Box(modifier = Modifier.graphicsLayer {
                                val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 1f)
                                scaleX = scale; scaleY = scale
                                alpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 1f)
                                // O Pager já dá uma leve rotação de carrossel
                                rotationY = pageOffset * 25f
                            }) {
                                // 🚀 O NOVO CARTÃO PARALLAX SENSORIZADO
                                CartaoFisicoHolografico(cartao)
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
                                    viewModel.removerCartao(cartaoFocado)
                                },
                                onPagar = {
                                    if (!fatura.jaPaga) {
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

                        if (fatura.lista.isEmpty()) {
                            item {
                                Text(
                                    "Nenhuma despesa nesta fatura", color = Color.White.copy(0.3f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                                    textAlign = TextAlign.Center, fontSize = 14.sp
                                )
                            }
                        } else {
                            items(fatura.lista, key = { it.id }) { despesa ->
                                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                                    ItemExtratoNeon(despesa = despesa, cartao = cartaoFocado!!)
                                }
                            }
                        }
                    }
                }
            }

            // DIALOG E BOTTOM SHEETS (Mantidos intactos)
            if (exibirConfirmacao && faturaInfo != null) {
                val fatura = faturaInfo!!
                AlertDialog(
                    onDismissRequest = { exibirConfirmacao = false },
                    containerColor = DeepSpaceBlue,
                    title = { Text("Confirmar Pagamento", color = Color.White) },
                    text = {
                        Text(
                            "Pagar fatura de ${fatura.mesNome} no valor de ${formatarMoedaBR(fatura.totalPendente, false)}?",
                            color = Color.White.copy(0.8f)
                        )
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            onClick = {
                                exibirConfirmacao = false
                                processandoPagamento = true
                                viewModel.pagarFatura(listaCartoes[paginaAtual], fatura.totalPendente, fatura.dataReferencia)
                                Haptics.vibrar(context, "sucesso")
                            }
                        ) { Text("Confirmar", color = DeepSpaceBlue, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { exibirConfirmacao = false }) { Text("Cancelar", color = Color.White.copy(0.6f)) }
                    }
                )
            }

            if (showBottomSheet) {
                FormularioCartaoBottomSheet(listaContas, { showBottomSheet = false }) { novo ->
                    viewModel.salvarCartao(novo)
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
fun CartaoFisicoHolografico(cartao: CartaoComConta) {
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
                            Text(cartao.tipo, color = corBorda, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Text("🏦 ${cartao.nomeConta}", color = Color.White.copy(0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    // CHIP E LIMITE
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Memory, null, tint = Color(0xFFFFD700).copy(alpha = 0.8f), modifier = Modifier.size(32.dp))

                        if (cartao.tipo == "CRÉDITO" && cartao.limiteTotal > 0.0) {
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
fun AcoesCartao(cartao: CartaoComConta, onDelete: () -> Unit, onPagar: () -> Unit, onFatura: () -> Unit, faturaPaga: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        BotaoAcaoRapida(Icons.Rounded.Receipt, "FATURA", NeonCyan, onClick = onFatura)
        BotaoAcaoRapida(
            if (faturaPaga) Icons.Rounded.CheckCircle else Icons.Rounded.Payments,
            if (faturaPaga) "PAGA" else "PAGAR",
            if (faturaPaga) Color.Gray else Color.White,
            { if (!faturaPaga) onPagar() })
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

fun calcularFaturaDaDespesa(dataCompra: Date, diaFechamento: Int?): Pair<Int, Int> {
    val cal = Calendar.getInstance().apply { time = dataCompra }
    val dia = cal.get(Calendar.DAY_OF_MONTH)
    var mes = cal.get(Calendar.MONTH)
    var ano = cal.get(Calendar.YEAR)

    if (dia > diaFechamento!!) {
        mes += 1
        if (mes > 11) {
            mes = 0
            ano += 1
        }
    }
    return Pair(mes, ano)
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