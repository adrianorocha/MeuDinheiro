package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.viewModel.CartoesViewModel
import com.meudinheiro.viewModel.CartoesViewModelFactory
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
    // 1. COLETA DE ESTADOS DO VIEWMODEL
    val listaCartoes by viewModel.cartoes.collectAsState()
    val listaContas by viewModel.contasDisponiveis.collectAsState()

    // 2. ESTADOS DE INTERFACE
    var showBottomSheet by remember { mutableStateOf(false) }

    // O PagerState agora observa o tamanho real da lista que vem do banco
    val pagerState = rememberPagerState(pageCount = { listaCartoes.size })

    Scaffold(
        containerColor = DeepSpaceBlue,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = NeonCyan,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Novo", tint = DeepSpaceBlue)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // CABEÇALHO COM TÍTULO PREMIUM
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Minha Carteira",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${listaCartoes.size} cartões ativos",
                    color = NeonCyan.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }

            if (listaCartoes.isEmpty()) {
                EstadoVazioCartoes()
            } else {
                // CARROSSEL 3D
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 45.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) { page ->
                    val cartao = listaCartoes[page]

                    // Cálculo do efeito de profundidade
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val scale = 1f - (pageOffset.absoluteValue * 0.2f).coerceIn(0f, 1f)
                    val alpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                rotationY = pageOffset * 15f // 💡 Leve rotação 3D ao deslizar
                            }
                    ) {
                        CartaoFisicoHolografico(cartao)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // AÇÕES DINÂMICAS (Surgem suavemente conforme o foco)
                AnimatedVisibility(
                    visible = listaCartoes.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val cartaoFocado = listaCartoes.getOrNull(pagerState.currentPage)
                    if (cartaoFocado != null) {
                        AcoesCartao(
                            cartao = cartaoFocado,
                            onDelete = { viewModel.removerCartao(cartaoFocado) }
                        )
                    }
                }
            }
        }

        // 3. BOTTOM SHEET DE CADASTRO
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
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, corBorda.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CardGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(shimmerBrush))

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(cartao.nomeCartao, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(cartao.tipo, color = corBorda, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("🏦 ${cartao.nomeConta}", color = Color.White.copy(0.6f), fontSize = 13.sp)
                }

                Icon(Icons.Rounded.Memory, null, tint = Color(0xFFFFD700), modifier = Modifier.size(38.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "•••• ${cartao.finalCartao}",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("VENCTO", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${cartao.diaVencimento}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AcoesCartao(cartao: CartaoComConta, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BotaoAcaoRapida(Icons.Rounded.Receipt, "Fatura", NeonCyan)
        BotaoAcaoRapida(Icons.Rounded.Payments, "Pagar", Color.White)
        BotaoAcaoRapida(Icons.Rounded.DeleteSweep, "Excluir", Color(0xFFFF5252), onClick = onDelete)
    }
}

@Composable
fun BotaoAcaoRapida(icone: androidx.compose.ui.graphics.vector.ImageVector, texto: String, cor: Color, onClick: () -> Unit = {}) {
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
        modifier = Modifier.fillMaxSize().padding(32.dp),
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