package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.launch

// --- Funções Utilitárias Necessárias ---
private fun Dp.coerceInDp(min: Dp, max: Dp): Dp = if(this < min) min else if(this > max) max else this

// Função para descobrir qual item está no meio da tela
private fun LazyListState.findCenteredItemIndex(): Int? {
    val layoutInfo = this.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportCenter = (viewportStart + viewportEnd) / 2

    var bestIndex: Int? = null
    var bestDistance = Int.MAX_VALUE

    for (item in visible) {
        val itemCenter = item.offset + (item.size / 2)
        val distance = kotlin.math.abs(itemCenter - viewportCenter)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = item.index
        }
    }
    return bestIndex
}

// Cores
private val Gold = Color(0xFFFFD700)

@Composable
fun CardSection(
    contas: List<ContaSaldoDomain>,
    contasSelecionadaId: String?,
    onExcluir: (ContaSaldoDomain) -> Unit,
    onContaSelecionada: (String) -> Unit,
    onAtualizar: (ContaSaldoDomain) -> Unit,
    getReceitaConta: (String) -> Double = { 0.0 },
    getDespesaConta: (String) -> Double = { 0.0 }
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val contasAtual by rememberUpdatedState(contas)
    val selecionadaAtual by rememberUpdatedState(contasSelecionadaId)
    val onContaSelecionadaAtual by rememberUpdatedState(onContaSelecionada)
    val onAtualizarAtual by rememberUpdatedState(onAtualizar)

    // 1. Se a lista mudar ou seleção sumir, volta para o primeiro
    LaunchedEffect(contas, contasSelecionadaId) {
        if (contas.isEmpty()) return@LaunchedEffect
        val exists = contasSelecionadaId != null && contas.any { it.conta == contasSelecionadaId }
        if (!exists) {
            val first = contas.first()
            onContaSelecionada(first.conta)
            onAtualizar(first)
            lazyListState.scrollToItem(0)
        }
    }

    // 2. Lógica de Auto-Seleção ao parar o Scroll
    LaunchedEffect(lazyListState) {
        var wasScrolling = false

        snapshotFlow { lazyListState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress ->
                if (inProgress) {
                    wasScrolling = true
                } else {
                    if (wasScrolling) {
                        wasScrolling = false
                        val centeredIndex = lazyListState.findCenteredItemIndex()
                        if (centeredIndex != null) {
                            val contaCentered = contasAtual.getOrNull(centeredIndex)
                            if (contaCentered != null) {
                                val targetId = contaCentered.conta
                                if (selecionadaAtual != targetId) {
                                    onContaSelecionadaAtual(targetId)
                                    onAtualizarAtual(contaCentered)
                                }
                            }
                        }
                    }
                }
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp) // Reduzi levemente o padding externo também
    ) {
        // AJUSTE DE TAMANHO: Reduzi a altura para 175.dp (era 210.dp)
        val cardWidth = (maxWidth * 0.85f).coerceInDp(280.dp, 400.dp)
        val cardHeight = 175.dp

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState)
        ) {
            items(contas, key = { it.conta }) { conta ->
                val selected = conta.conta == contasSelecionadaId
                ContaCard(
                    conta = conta,
                    width = cardWidth,
                    height = cardHeight,
                    selected = selected,
                    receita = getReceitaConta(conta.conta),
                    despesa = getDespesaConta(conta.conta),
                    onClick = {
                        val idx = contas.indexOfFirst { it.conta == conta.conta }
                        if (idx >= 0) {
                            scope.launch { lazyListState.animateScrollToItem(idx) }
                        }
                        onContaSelecionada(conta.conta)
                        onAtualizar(conta)
                    },
                    onLongClick = { onExcluir(conta) }
                )
            }
        }
    }
}

@Composable
private fun ContaCard(
    conta: ContaSaldoDomain,
    width: Dp,
    height: Dp,
    selected: Boolean,
    receita: Double,
    despesa: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp) // Levemente ajustado
    val borderCol by animateColorAsState(if (selected) Gold else Color.Transparent, label = "border")
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, label = "scale") // Zoom mais sutil

    val context = LocalContext.current
    val resId = remember(conta.pic) {
        val id = context.resources.getIdentifier(conta.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.sim_chip_2
    }

    Card(
        modifier = Modifier
            .size(width, height)
            .scale(scale)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        border = BorderStroke(2.dp, borderCol),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.card_tecno),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.8f
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            // AJUSTE: Reduzi o padding interno de 20.dp para 16.dp
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Topo: Banco e Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = conta.banco,
                        // AJUSTE: Fonte titleMedium para ocupar menos espaço
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        // AJUSTE: Chip levemente menor (32.dp)
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Centro: Numero Conta
                Text(
                    text = "Ag: ${conta.agencia}   CC: ${conta.conta}",
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 1.5.sp),
                    color = TextWhite.copy(alpha = 0.8f)
                )

                // Base: Saldo e Totais
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Saldo", fontSize = 11.sp, color = TextWhite.copy(0.6f))
                        Text(
                            text = formatarMoedaBR(conta.saldo),
                            // AJUSTE: Fonte 20.sp (era 22)
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gold
                        )
                    }

                    // Mini Resumo no Cartão
                    Column(horizontalAlignment = Alignment.End) {
                        // AJUSTE: Fontes 10.sp
                        Text("▲ ${formatarMoedaBR(receita)}", fontSize = 10.sp, color = Color(0xFF69F0AE))
                        Text("▼ ${formatarMoedaBR(despesa)}", fontSize = 10.sp, color = Color(0xFFFF8A80))
                    }
                }
            }
        }
    }
}