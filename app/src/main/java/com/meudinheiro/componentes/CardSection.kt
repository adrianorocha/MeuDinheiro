package com.meudinheiro.componentes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.funcoes.formatarMoedaBR
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

// Cores Premium
private val Gold = Color(0xFFFFD54F) // Dourado um pouco mais suave
private val NeonGreen = Color(0xFF69F0AE)
private val NeonCyan = Color(0xFF00E5FF)
private val CardBgDark = Color(0xFF0D1B2A)
private val CardBgLight = Color(0xFF1B263B)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardSection(
    contas: List<ContaSaldoDomain>,
    contasSelecionadaId: String?,
    isPrivate: Boolean = false,
    onExcluir: (ContaSaldoDomain) -> Unit,
    onContaSelecionada: (String) -> Unit,
    onAtualizar: (ContaSaldoDomain) -> Unit,
    getReceitaConta: (String) -> Double = { 0.0 },
    getDespesaConta: (String) -> Double = { 0.0 }
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 1. Scroll Inicial para a conta selecionada (apenas uma vez)
    LaunchedEffect(Unit) {
        if (contas.isNotEmpty() && !contasSelecionadaId.isNullOrBlank()) {
            val index = contas.indexOfFirst { it.conta == contasSelecionadaId }
            if (index >= 0) {
                lazyListState.scrollToItem(index)
            }
        }
    }

    // 2. Lógica de "Auto-Select" ao parar o Scroll
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo }
            .mapNotNull { layoutInfo ->
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index
            }
            .distinctUntilChanged()
            .collect { centeredIndex ->
                if (!lazyListState.isScrollInProgress) {
                    val contaCentral = contas.getOrNull(centeredIndex)
                    if (contaCentral != null && contaCentral.conta != contasSelecionadaId) {
                        onContaSelecionada(contaCentral.conta)
                        onAtualizar(contaCentral)
                    }
                }
            }
    }

    // 3. Garantia extra para o final do Fling (Snap)
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val layoutInfo = lazyListState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centeredItem = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }

                if (centeredItem != null) {
                    val conta = contas.getOrNull(centeredItem.index)
                    if (conta != null && conta.conta != contasSelecionadaId) {
                        onContaSelecionada(conta.conta)
                        onAtualizar(conta)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val cardWidth = maxWidth - 32.dp
        val cardHeight = 185.dp // Um pouquinho mais alto para os grafismos respirarem

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState)
        ) {
            itemsIndexed(contas) { index, conta ->
                val selected = conta.conta == contasSelecionadaId
                val receitaValor = getReceitaConta(conta.conta)
                val despesaValor = getDespesaConta(conta.conta)

                ContaCard(
                    conta = conta,
                    width = cardWidth,
                    height = cardHeight,
                    selected = selected,
                    isPrivate = isPrivate,
                    receita = receitaValor,
                    despesa = despesaValor,
                    onClick = {
                        onContaSelecionada(conta.conta)
                        onAtualizar(conta)
                        scope.launch { lazyListState.animateScrollToItem(index) }
                    },
                    onLongClick = { onExcluir(conta) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContaCard(
    conta: ContaSaldoDomain,
    width: Dp,
    height: Dp,
    selected: Boolean,
    isPrivate: Boolean,
    receita: Double,
    despesa: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Animações de Seleção
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.90f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        label = "alpha"
    )

    // Animação Contínua do Feixe Holográfico
    val infiniteTransition = rememberInfiniteTransition(label = "HoloTransition")
    val holoProgress by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "holoProgress"
    )

    val context = LocalContext.current
    val resId = remember(conta.pic) {
        val id = context.resources.getIdentifier(conta.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.sim_chip_2
    }

    // Gradientes Base e Holográfico (Padrão Blu Macaw Infinite)
    val cardGradient = Brush.linearGradient(
        colors = listOf(CardBgDark, CardBgLight),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val holoGradient = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            NeonGreen.copy(alpha = 0.15f),
            NeonCyan.copy(alpha = 0.15f),
            Color.Transparent
        ),
        start = Offset(holoProgress, holoProgress),
        end = Offset(holoProgress + 400f, holoProgress + 400f)
    )

    Card(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = alphaAnim
                // Dá um leve tilt 3D para trás nos cartões que não estão selecionados
                rotationX = if (selected) 0f else 10f
                cameraDistance = 12f * density
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp),
        // A borda muda de transparente para o Dourado Neon quando selecionada
        border = BorderStroke(
            if (selected) 1.5.dp else 0.dp,
            if (selected) Gold.copy(alpha = 0.8f) else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 12.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardGradient)
        ) {
            // Camada 1: Círculos abstratos do Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.03f),
                    radius = 140f,
                    center = Offset(size.width + 20f, size.height - 20f)
                )
                drawCircle(
                    color = NeonGreen.copy(alpha = 0.05f),
                    radius = 100f,
                    center = Offset(-20f, size.height + 20f)
                )
            }

            // Camada 2: O Brilho Holográfico animado
            Box(modifier = Modifier
                .fillMaxSize()
                .background(holoGradient))

            // Camada 3: Conteúdo e Textos
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOPO: Banco e Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conta.banco.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // MEIO: Chip e Dados
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Chip desenhado com box (mais moderno que imagem)
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Gold.copy(alpha = 0.7f))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                Color.Black.copy(0.2f),
                                Offset(size.width * 0.3f, 0f),
                                Offset(size.width * 0.3f, size.height),
                                2f
                            )
                            drawLine(
                                Color.Black.copy(0.2f),
                                Offset(size.width * 0.7f, 0f),
                                Offset(size.width * 0.7f, size.height),
                                2f
                            )
                            drawLine(
                                Color.Black.copy(0.2f),
                                Offset(0f, size.height * 0.5f),
                                Offset(size.width, size.height * 0.5f),
                                2f
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "AG ${conta.agencia} • CC ${conta.conta}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextWhite.copy(alpha = 0.8f)
                    )
                }

                // RODAPÉ: Saldo e Entradas/Saídas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Saldo Atual",
                            color = TextWhite.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = formatarMoedaBR(conta.saldo, isPrivate),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatarMoedaBR(receita, isPrivate),
                                fontSize = 12.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatarMoedaBR(despesa, isPrivate),
                                fontSize = 12.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}