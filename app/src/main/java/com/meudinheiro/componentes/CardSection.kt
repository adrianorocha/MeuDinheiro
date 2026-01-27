package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.funcoes.formatarMoedaBR
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private fun Dp.coerceInDp(min: Dp, max: Dp): Dp {
    return when {
        this < min -> min
        this > max -> max
        else -> this
    }
}

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

@Composable
fun CardSection(
    contas: List<ContaSaldoDomain>,
    contasSelecionadaId: String?,
    onExcluir: (ContaSaldoDomain) -> Unit,
    onContaSelecionada: (String) -> Unit,
    onAtualizar: (ContaSaldoDomain) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val dialogContaId = remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Sempre enxergar os valores mais atuais dentro das coroutines (sem reiniciar efeito)
    val contasAtual by rememberUpdatedState(contas)
    val selecionadaAtual by rememberUpdatedState(contasSelecionadaId)
    val onContaSelecionadaAtual by rememberUpdatedState(onContaSelecionada)
    val onAtualizarAtual by rememberUpdatedState(onAtualizar)

    // 1) Se a seleção ficou inválida (ex.: conta removida), seleciona a primeira disponível
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

    // 2) Auto-seleção ao TERMINAR o scroll (sem reiniciar ao atualizar "contas" por saldo)
    LaunchedEffect(lazyListState) {
        var wasScrolling = false

        snapshotFlow { lazyListState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress ->
                if (inProgress) {
                    wasScrolling = true
                    return@collect
                }

                // só reage no "fim" de um scroll real
                if (!wasScrolling) return@collect
                wasScrolling = false

                val centeredIndex = lazyListState.findCenteredItemIndex() ?: return@collect
                val contaCentered = contasAtual.getOrNull(centeredIndex) ?: return@collect

                val targetId = contaCentered.conta
                if (targetId.isBlank()) return@collect

                // só troca se mudou
                if (selecionadaAtual != targetId) {
                    onContaSelecionadaAtual(targetId)
                }

                // garante que suas despesas/saldo da conta central sejam atualizados
                onAtualizarAtual(contaCentered)
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        val cardWidth = (maxWidth * 0.84f).coerceInDp(min = 280.dp, max = 420.dp)
        val cardHeight = 200.dp

        if (contas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma conta cadastrada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@BoxWithConstraints
        }

        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState)
        ) {
            items(contas, key = { it.conta }) { conta ->
                val selected = conta.conta == contasSelecionadaId

                ContaCard(
                    conta = conta,
                    width = cardWidth,
                    height = cardHeight,
                    selected = selected,
                    onClick = {
                        // Clique: seleciona e centraliza para evitar divergência entre "clicado" e "central"
                        val idx = contas.indexOfFirst { it.conta == conta.conta }
                        if (idx >= 0) {
                            scope.launch { lazyListState.animateScrollToItem(idx) }
                        }
                        onContaSelecionada(conta.conta)
                        onAtualizar(conta)
                    },
                    onLongClick = { dialogContaId.value = conta.conta }
                )
            }
        }
    }

    val contaParaExcluir = contas.firstOrNull { it.conta == dialogContaId.value }
    if (contaParaExcluir != null) {
        AlertDialog(
            onDismissRequest = { dialogContaId.value = null },
            title = { Text(text = "Excluir Conta") },
            text = { Text(text = "Deseja excluir a conta ${contaParaExcluir.banco} (C/C: ${contaParaExcluir.conta})?") },
            confirmButton = {
                Button(
                    onClick = {
                        onExcluir(contaParaExcluir)
                        dialogContaId.value = null
                    }
                ) { Text("Sim") }
            },
            dismissButton = {
                Button(onClick = { dialogContaId.value = null }) { Text("Não") }
            }
        )
    }
}

@Composable
private fun ContaCard(
    conta: ContaSaldoDomain,
    width: Dp,
    height: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)

    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "borderColor"
    )

    val elevation by animateDpAsState(
        targetValue = if (selected) 10.dp else 4.dp,
        label = "elevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        label = "scale"
    )

    Card(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        border = BorderStroke(width = 2.dp, color = borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.card_tecno),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.95f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xAA000000),
                                Color(0x33000000),
                                Color(0xAA000000)
                            )
                        )
                    )
            )

            Image(
                painter = painterResource(id = R.drawable.sim_chip_2),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp)
                    .size(34.dp),
                contentScale = ContentScale.Fit
            )

            val titleStyle = MaterialTheme.typography.titleMedium.copy(
                color = Color(0xFFFFD54F)
            )

            val infoStyle = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White
            )

            val moneyStyle = MaterialTheme.typography.titleLarge.copy(
                color = Color(0xFFFFD54F),
                fontSize = if (width < 320.dp) 18.sp else 20.sp
            )

            Text(
                text = conta.banco,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 14.dp, end = 56.dp)
            )

            Text(
                text = "Agência: ${conta.agencia}  •  C/C: ${conta.conta}",
                style = infoStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp, end = 14.dp)
            )

            Text(
                text = formatarMoedaBR(conta.saldo),
                style = moneyStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 14.dp, end = 14.dp)
            )

            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x1400C853))
                )
            }

            // Se quiser realmente aplicar o scale, você pode colocar no modifier do Card com graphicsLayer
            // (mantive seu scale calculado para uso futuro)
        }
    }
}
