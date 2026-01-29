package com.meudinheiro.componentes

// ... imports padrão ...
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.util.copy
import com.meudinheiro.R
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.funcoes.formatarMoedaBR

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ... funções utilitárias (coerceInDp, findCenteredItemIndex) mantidas ...
private fun Dp.coerceInDp(min: Dp, max: Dp): Dp = if(this < min) min else if(this > max) max else this

// Cores
private val Gold = Color(0xFFFFD700)
//private val TextWhite = Color(0xFFE0E1DD)

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
    // ... Lógica de Scroll e SnapshotFlow mantida (copie do original) ...
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // ...

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        val cardWidth = (maxWidth * 0.85f).coerceInDp(280.dp, 400.dp)
        val cardHeight = 210.dp

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 24.dp), // Mais margem
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
                        onContaSelecionada(conta.conta)
                        onAtualizar(conta)
                        // ... logica de scroll ...
                    },
                    onLongClick = { /* logica excluir */ }
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
    val shape = RoundedCornerShape(24.dp)
    val borderCol by animateColorAsState(if (selected) Gold else Color.Transparent)
    val scale by animateFloatAsState(if (selected) 1.05f else 1f)

    Card(
        modifier = Modifier
            .size(width, height)
            .scale(scale)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        border = BorderStroke(2.dp, borderCol),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 12.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fundo Gradiente Tecnológico
            Image(
                painter = painterResource(id = R.drawable.card_tecno),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.8f // Um pouco mais escuro
            )
            // Overlay escuro
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            // Conteúdo
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Topo: Banco e Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = conta.banco,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Image(
                        painter = painterResource(id = R.drawable.sim_chip_2),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Centro: Numero Conta
                Text(
                    text = "Ag: ${conta.agencia}   CC: ${conta.conta}",
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 2.sp),
                    color = TextWhite.copy(alpha = 0.8f)
                )

                // Base: Saldo e Totais
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Saldo", fontSize = 12.sp, color = TextWhite.copy(0.6f))
                        Text(
                            text = formatarMoedaBR(conta.saldo),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gold
                        )
                    }

                    // Mini Resumo no Cartão
                    Column(horizontalAlignment = Alignment.End) {
                        Text("▲ ${formatarMoedaBR(receita)}", fontSize = 11.sp, color = Color(0xFF69F0AE))
                        Text("▼ ${formatarMoedaBR(despesa)}", fontSize = 11.sp, color = Color(0xFFFF8A80))
                    }
                }
            }
        }
    }
}