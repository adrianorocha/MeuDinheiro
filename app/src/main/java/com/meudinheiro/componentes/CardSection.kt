package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.meudinheiro.R
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardSection(
    contas: List<ContaSaldoDomain>,
    contasSelecionadaId: String?,
    viewModelFactory: ContaSaldoViewModelFactory, // mantive por compatibilidade, mas não uso aqui
    onExcluir: (ContaSaldoDomain) -> Unit,
    onContaSelecionada: (String) -> Unit,
    onAtualizar: (ContaSaldoDomain) -> Unit
) {
    val lazyListState = rememberLazyListState()
    var lastAutoSelected by remember { mutableStateOf<String?>(null) }
    var contaParaExcluir by remember { mutableStateOf<ContaSaldoDomain?>(null) }

    // Seleciona a primeira conta automaticamente (apenas uma vez), se não houver selecionada
    LaunchedEffect(contas.size, contasSelecionadaId) {
        if (contas.isNotEmpty() && (contasSelecionadaId.isNullOrBlank())) {
            val first = contas.first()
            onContaSelecionada(first.conta)
            onAtualizar(first)
        }
    }

    // Quando parar de rolar, pega o card mais central e seleciona automaticamente
    LaunchedEffect(contas) {
        snapshotFlow { lazyListState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { isScrolling -> !isScrolling } // só quando solta / termina o snap
            .map {
                val layoutInfo = lazyListState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return@map null

                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

                val centeredItem = visible.minByOrNull { item ->
                    val itemCenter = item.offset + (item.size / 2)
                    abs(itemCenter - viewportCenter)
                }

                centeredItem?.key as? String // como key = conta.conta, isso vira String
            }
            .distinctUntilChanged()
            .collect { centeredKey ->
                if (centeredKey.isNullOrBlank()) return@collect
                if (centeredKey == lastAutoSelected) return@collect

                val conta = contas.firstOrNull { it.conta == centeredKey } ?: return@collect
                lastAutoSelected = centeredKey
                onContaSelecionada(conta.conta)
                onAtualizar(conta) // aqui carrega despesas da conta centralizada
            }
    }
    if (contaParaExcluir != null) {
        AlertDialog(
            onDismissRequest = { contaParaExcluir = null },
            title = { Text("Excluir conta") },
            text = { Text("Deseja excluir esta conta?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onExcluir(contaParaExcluir!!)
                        contaParaExcluir = null
                    }
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { contaParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Responsivo:
        // - Em telas grandes, limita o card pra não ficar “gigante”
        // - Em telas pequenas, usa ~88% da largura
        val cardWidth = (maxWidth * 0.88f).coerceAtMost(380.dp)

        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState)
        ) {
            items(contas, key = { it.conta }) { conta ->
                val selected = conta.conta == contasSelecionadaId

                ContaCard(
                    conta = conta,
                    selected = selected,
                    width = cardWidth,
                    onClick = {
                        onContaSelecionada(conta.conta)
                        onAtualizar(conta)
                    },
                    onLongClick = { contaParaExcluir = conta }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContaCard(
    conta: ContaSaldoDomain,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)

    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (selected) 2.dp else 0.dp

    ElevatedCard(
        modifier = Modifier
            .width(width)
            .aspectRatio(1.85f) // ajusta a altura automaticamente (responsivo)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 6.dp else 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background
            Image(
                painter = painterResource(id = R.drawable.card_tecno),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Overlay para legibilidade (moderno)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // Borda de seleção (sem “pintar tudo”)
            if (borderWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(shape)
                        .background(Color.Transparent)
                )
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    shape = shape,
                    border = BorderStroke(borderWidth, borderColor)
                ) {}
            }

            // Chip no topo direito (sem padding fixo)
            Image(
                painter = painterResource(id = R.drawable.sim_chip_2),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(28.dp)
            )

            // Banco
            Text(
                text = conta.banco,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 12.dp, end = 52.dp)
            )

            // Agência e Conta (menor, topo)
            Text(
                text = "Agência: ${conta.agencia}  •  C/C: ${conta.conta}",
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 38.dp, end = 14.dp)
            )

            // Saldo (destaque)
            Text(
                text = formatarMoedaBR(conta.saldo),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 14.dp, end = 14.dp)
            )
        }
    }
}
