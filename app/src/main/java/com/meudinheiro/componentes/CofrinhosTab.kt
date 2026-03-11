package com.meudinheiro.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.ConfeteState
import com.meudinheiro.data.Meta
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.MetaViewModel
import kotlinx.coroutines.launch

@Composable
fun CofrinhosTab(
    viewModel: MetaViewModel,
    isPrivate: Boolean,
    snackbarHostState: SnackbarHostState,
    userName: String
) {
    // Escuta o banco de dados em tempo real
    val metas by viewModel.metas.collectAsState(initial = emptyList())
    val contasBancarias by viewModel.contas.observeAsState(emptyList())
    val scope = rememberCoroutineScope()

    // Sistema de Confetes
    val confettiState = remember { ConfeteState() }
    var screenWidth by remember { mutableStateOf(0f) }

    // Controles de Diálogos
    var showAddDialog by remember { mutableStateOf(false) }
    var metaParaAportar by remember { mutableStateOf<Meta?>(null) }
    var metaParaExcluir by remember { mutableStateOf<Meta?>(null) }
    var metaParaEditar by remember { mutableStateOf<Meta?>(null) }

    // 1. Usamos o Box para criar as camadas (layers)
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Pegamos a largura para o confete saber onde é o centro
            .onGloballyPositioned { screenWidth = it.size.width.toFloat() }
    ) {

        // CAMADA 0 (Fundo): A sua Grade de Metas
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. O Botão "Criar Meta"
            item {
                AddMetaDashedCard(onClick = { showAddDialog = true })
            }

            // 2. A Lista de Metas Reais
            items(metas, key = { it.id }) { meta ->
                CofrinhoGridCard(
                    meta = meta,
                    isPrivate = isPrivate,
                    onAporteClick = { metaParaAportar = meta },
                    onMenuClick = { action ->
                        when (action) {
                            "edit" -> metaParaEditar = meta
                            "delete" -> metaParaExcluir = meta
                        }
                    }
                )
            }
        }

        // CAMADA 1 (Topo): O componente de confetes
        ConfettiOverlay(state = confettiState)
    }

    // --- DIÁLOGOS ---

    if (showAddDialog) {
        AddMetaDialog(
            onSalvar = { nome, objetivo -> viewModel.salvarMeta(nome, objetivo) },
            onDismiss = { showAddDialog = false }
        )
    }

    // AQUI ESTÁ A LÓGICA DE APORTE CORRIGIDA (SEM DUPLICAÇÃO)
    metaParaAportar?.let { metal ->
        AporteMetaDialog(
            contasDisponiveis = contasBancarias,
            onDismiss = { metaParaAportar = null },
            onConfirmar = { contaId, valorAporte ->

                // 1. Cálculo preciso para evitar problemas com Double no Kotlin
                val totalAposAporte =
                    "%.2f".format(metal.valorGuardado + valorAporte).replace(",", ".").toDouble()
                val objetivo = "%.2f".format(metal.valorObjetivo).replace(",", ".").toDouble()
                val valorGuardadoAtual =
                    "%.2f".format(metal.valorGuardado).replace(",", ".").toDouble()

                println("BLU MACAW DEBUG -> Guardado: $valorGuardadoAtual | Aporte: $valorAporte | Total: $totalAposAporte | Objetivo: $objetivo")

                // 2. Condição de Vitória: Chegou no alvo e antes não tinha chegado
                if (totalAposAporte >= objetivo && valorGuardadoAtual < objetivo) {

                    // Disparo com coordenadas seguras
                    val spawnX = if (screenWidth > 0) screenWidth / 2f else 500f
                    val spawnY = 300f // Ajustado para não cair tão rápido

                    confettiState.disparar(spawnX, spawnY)

                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Parabéns, $userName! Meta '${metal.nome}' concluída! 🏆",
                            duration = SnackbarDuration.Short
                        )
                    }
                }

                // 3. Grava no banco e fecha o diálogo
                viewModel.realizarAporteReal(metal, contaId, valorAporte)
                metaParaAportar = null
            }
        )
    }

    metaParaExcluir?.let { meta ->
        DeleteMetaDialog(
            meta = meta,
            contas = contasBancarias,
            onDismiss = { metaParaExcluir = null },
            onConfirm = { contaId ->
                viewModel.excluirMeta(meta, contaId)
                metaParaExcluir = null
            }
        )
    }

    metaParaEditar?.let { meta ->
        EditMetaDialog(
            meta = meta,
            onDismiss = { metaParaEditar = null },
            onConfirmar = { novoNome, novoObjetivo ->
                viewModel.editarMeta(meta.copy(nome = novoNome, valorObjetivo = novoObjetivo))
                metaParaEditar = null
            }
        )
    }
}
// ============================================================================
// OS CARDS DA GRADE
// ============================================================================

@Composable
fun AddMetaDashedCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp) // Mesma altura para manter o alinhamento do grid
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF69F0AE).copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = Color(0xFF69F0AE).copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Nova Meta",
                color = Color(0xFF69F0AE).copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CofrinhoGridCard(
    meta: Meta,
    isPrivate: Boolean,
    onAporteClick: () -> Unit,
    onMenuClick: (String) -> Unit
) {
    val progressoReal = (meta.valorGuardado / meta.valorObjetivo).toFloat().coerceIn(0f, 1f)
    var animationPlayed by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) progressoReal else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "EnergyBar"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    // Paleta de Cores Neon Dinâmica
    val currentColor = when {
        animatedProgress >= 1f -> Color(0xFF69F0AE) // Verde
        animatedProgress >= 0.6f -> Color(0xFFFFD54F) // Amarelo
        else -> Color(0xFF00E5FF) // Ciano
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp), // Compacto, mas com presença
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // CABEÇALHO: Nome e Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meta.nome,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.White.copy(0.3f)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E2B3E))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar", color = Color.White) },
                            onClick = { showMenu = false; onMenuClick("edit") }
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir", color = Color(0xFFEF5350)) },
                            onClick = { showMenu = false; onMenuClick("delete") }
                        )
                    }
                }
            }

            // VALORES: Guardado vs Porcentagem
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (isPrivate) "R$ •••••" else formatarMoedaBR(
                            meta.valorGuardado,
                            false
                        ),
                        color = currentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "de ${formatarMoedaBR(meta.valorObjetivo, false)}",
                        color = Color.White.copy(0.4f),
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // BARRA DE PROGRESSO NEON (O PONTO ALTO)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.05f))
            ) {
                // Efeito de Brilho (Glow) atrás da barra
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(currentColor.copy(0.1f), currentColor.copy(0.4f))
                            )
                        )
                )

                // Barra de Energia Sólida com Gradiente
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(currentColor.copy(0.7f), currentColor)
                            )
                        )
                )
            }

            // Botão de Aporte Sutil
            if (animatedProgress < 1f) {
                Text(
                    text = "+ Realizar Aporte",
                    color = currentColor.copy(0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { onAporteClick() }
                )
            }
        }
    }
}