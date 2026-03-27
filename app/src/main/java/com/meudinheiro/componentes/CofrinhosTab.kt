package com.meudinheiro.componentes

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.ConfeteState
import com.meudinheiro.data.Meta
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.MetaViewModel
import kotlinx.coroutines.launch
import com.meudinheiro.funcoes.Haptics // Nosso motor de vibração

// Cores da Paleta VIP
private val BgDark = Color(0xFF0D1B2A)
private val BgLight = Color(0xFF1B263B)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonGreen = Color(0xFF69F0AE)
private val NeonYellow = Color(0xFFFFD54F)

@Composable
fun CofrinhosTab(
    viewModel: MetaViewModel,
    isPrivate: Boolean,
    snackbarHostState: SnackbarHostState,
    userName: String
) {
    val context = LocalContext.current
    val metas by viewModel.metas.collectAsState(initial = emptyList())
    val contasBancarias by viewModel.contas.observeAsState(emptyList())
    val scope = rememberCoroutineScope()

    val confettiState = remember { ConfeteState() }
    var screenWidth by remember { mutableStateOf(0f) }

    var showAddDialog by remember { mutableStateOf(false) }
    var metaParaAportar by remember { mutableStateOf<Meta?>(null) }
    var metaParaExcluir by remember { mutableStateOf<Meta?>(null) }
    var metaParaEditar by remember { mutableStateOf<Meta?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // O fundo já vem do MainScreen
            .onGloballyPositioned { screenWidth = it.size.width.toFloat() }
    ) {
        // --- 1. A GRADE DE TANQUES ---
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AddMetaDashedCard(onClick = { showAddDialog = true })
            }

            items(metas, key = { it.id }) { meta ->
                CofrinhoEnergyTankCard(
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

        // --- 2. O CONFETE OVERLAY ---
        ConfettiOverlay(state = confettiState)
    }

    // --- DIÁLOGOS ---
    if (showAddDialog) {
        AddMetaDialog(
            onSalvar = { nome, objetivo -> viewModel.salvarMeta(nome, objetivo) },
            onDismiss = { showAddDialog = false }
        )
    }

    metaParaAportar?.let { metal ->
        AporteMetaDialog(
            contasDisponiveis = contasBancarias,
            onDismiss = { metaParaAportar = null },
            onConfirmar = { contaId, valorAporte ->
                // 🚀 TRATAMENTO SEGURO DA REGRA DE NEGÓCIO E VITÓRIA
                val totalAposAporte = (metal.valorGuardado + valorAporte)
                val objetivo = metal.valorObjetivo

                if (totalAposAporte >= objetivo && metal.valorGuardado < objetivo) {
                    Haptics.vibrar(context, "sucesso")
                    val spawnX = if (screenWidth > 0) screenWidth / 2f else 500f
                    confettiState.disparar(spawnX, 300f)

                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "SISTEMA: Carga Concluída! Meta '${metal.nome}' atingida. ⚡",
                            duration = SnackbarDuration.Short
                        )
                    }
                } else {
                    Haptics.vibrar(context, "movimento") // Vibração leve para aporte normal
                }

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
// OS COMPONENTES GRID POWER
// ============================================================================

@Composable
fun AddMetaDashedCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Mais alto para parecer um "slot" vazio de tanque
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = BgLight.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, NeonCyan.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, null, tint = NeonCyan, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("NOVO TANQUE", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun CofrinhoEnergyTankCard(
    meta: Meta,
    isPrivate: Boolean,
    onAporteClick: () -> Unit,
    onMenuClick: (String) -> Unit
) {
    val progressoReal = (meta.valorGuardado / meta.valorObjetivo).toFloat().coerceIn(0f, 1f)
    var showMenu by remember { mutableStateOf(false) }

    // Animação elástica ao carregar
    val animatedProgress by animateFloatAsState(
        targetValue = progressoReal,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "EnergyTank"
    )

    // A cor muda de acordo com o nível da bateria
    val currentColor = when {
        animatedProgress >= 1f -> NeonGreen
        animatedProgress >= 0.5f -> NeonYellow
        else -> NeonCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Altura suficiente para o líquido subir
            .clickable { onAporteClick() }, // Clicar no card inteiro faz aporte
        colors = CardDefaults.cardColors(containerColor = BgLight),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 🚀 O LÍQUIDO DE ENERGIA (Fundo que sobe)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedProgress) // A altura é a % do progresso
                    .align(Alignment.BottomCenter) // Preenche de baixo para cima
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(currentColor.copy(0.4f), currentColor.copy(0.1f))
                        )
                    )
            ) {
                // Linha laser no topo do líquido
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(currentColor)
                )
            }

            // --- CONTEÚDO DO CARD (Por cima do líquido) ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header (Título + Menu)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = meta.nome.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(top = 4.dp)
                    )

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp).offset(x = 8.dp, y = (-8).dp)) {
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.White.copy(0.5f))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(BgDark)
                        ) {
                            DropdownMenuItem(text = { Text("Editar", color = Color.White) }, onClick = { showMenu = false; onMenuClick("edit") })
                            DropdownMenuItem(text = { Text("Excluir", color = RedColor) }, onClick = { showMenu = false; onMenuClick("delete") })
                        }
                    }
                }

                // Corpo Central (Porcentagem Gigante)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isCheio = animatedProgress >= 1f

                    if (isCheio) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(32.dp))
                    } else {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = currentColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        )
                    }
                }

                // Rodapé (Valores)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val guardadoStr = if (isPrivate) "****" else formatarMoedaBR(meta.valorGuardado, false)
                    val objetivoStr = if (isPrivate) "****" else formatarMoedaBR(meta.valorObjetivo, false)

                    Text(text = guardadoStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "ALVO: $objetivoStr", color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}