package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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

// Cores
private val Gold = Color(0xFFFFD700)
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
                // Calcula o centro da viewport
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                // Encontra o item mais próximo do centro
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index
            }
            .distinctUntilChanged() // Só reage se o índice central mudar
            .collect { centeredIndex ->
                // Verifica se o scroll parou (isScrollInProgress == false)
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
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
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
        // Largura dinâmica (igual ao ResumoGeral)
        val cardWidth = maxWidth - 32.dp
        val cardHeight = 175.dp

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState)
        ) {
            itemsIndexed(contas) { index, conta ->
                val selected = conta.conta == contasSelecionadaId

                // Captura valores
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
                        scope.launch {
                            lazyListState.animateScrollToItem(index)
                        }
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
    val shape = RoundedCornerShape(22.dp)
    val borderCol by animateColorAsState(
        targetValue = if (selected) Gold else Color.Transparent,
        label = "border"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.95f,
        label = "scale"
    )

    val context = LocalContext.current
    val resId = remember(conta.pic) {
        val id = context.resources.getIdentifier(conta.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.sim_chip_2
    }

    Card(
        modifier = Modifier
            .width(width)
            .height(height)
            .scale(scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        border = BorderStroke(2.dp, borderCol),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.card_tecno),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.5f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2B3E).copy(alpha = 0.85f))
            )

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOPO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conta.banco,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        fontSize = 18.sp
                    )
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // MEIO
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.sim_chip),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).padding(end = 8.dp),
                        alpha = 0.8f
                    )
                    Text(
                        text = "Ag ${conta.agencia}   CC ${conta.conta}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextWhite.copy(alpha = 0.9f)
                    )
                }

                // RODAPÉ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Saldo Atual",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextWhite.copy(alpha = 0.6f)
                        )
                        // AQUI ESTÁ O OLHINHO FUNCIONANDO
                        Text(
                            text = formatarMoedaBR(conta.saldo, isPrivate),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Gold,
                            fontSize = 24.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("▲", fontSize = 10.sp, color = Color(0xFF69F0AE))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatarMoedaBR(receita, isPrivate),
                                fontSize = 12.sp,
                                color = TextWhite.copy(0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("▼", fontSize = 10.sp, color = Color(0xFFFF8A80))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatarMoedaBR(despesa, isPrivate),
                                fontSize = 12.sp,
                                color = TextWhite.copy(0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}