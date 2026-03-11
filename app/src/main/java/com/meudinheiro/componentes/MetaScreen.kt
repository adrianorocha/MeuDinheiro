package com.meudinheiro.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.ConfeteState
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Meta
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.funcoes.lembrarEstadoPerformance
import com.meudinheiro.viewModel.MetaViewModel

// --- CORES GLOBAIS (Caso não estejam importadas) ---
private val NeonGreen = Color(0xFF69F0AE)
private val RedAlert = Color(0xFFEF5350)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetasScreen(
    viewModel: MetaViewModel,
    isPrivate: Boolean,
    onBack: () -> Unit
) {
    val metas by viewModel.metas.collectAsState()
    val contasBancarias by viewModel.contas.observeAsState(emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var metaParaAportar by remember { mutableStateOf<Meta?>(null) }
    var metaParaExcluir by remember { mutableStateOf<Meta?>(null) }
    var metaParaEditar by remember { mutableStateOf<Meta?>(null) }

    // Sistema de Confetes
    val confettiState = remember { ConfeteState() }
    var screenWidth by remember { mutableStateOf(0f) }

    val modoEconomia = lembrarEstadoPerformance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenWidth = it.size.width.toFloat() }
    ) {
        Scaffold(
            containerColor = PremiumDarkBlue,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Minhas Metas",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Voltar", tint = TextWhite)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = NeonGreen,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, "Nova Meta", tint = PremiumDarkBlue)
                }
            }
        ) { padding ->
            if (metas.isEmpty()) {
                Box(Modifier
                    .fillMaxSize()
                    .padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Você ainda não tem metas.\nClique no + para começar!",
                        color = TextWhite.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(metas, key = { it.id }) { meta ->
                        MetaCard(
                            meta = meta,
                            isPrivate = isPrivate,
                            onAporteClick = { metaParaAportar = meta },
                            onExcluirClick = { metaParaExcluir = meta },
                            onEditClick = { metaParaEditar = meta }
                        )
                    }
                }
            }
        }

        // Camada de Confetes
        ConfettiOverlay(state = confettiState)
    }

    // --- DIÁLOGOS ---
    if (showAddDialog) {
        AddMetaDialog(
            onSalvar = { nome, objetivo -> viewModel.salvarMeta(nome, objetivo) },
            onDismiss = { showAddDialog = false }
        )
    }

    metaParaAportar?.let { meta ->
        AporteMetaDialog(
            contasDisponiveis = contasBancarias,
            onDismiss = { metaParaAportar = null },
            onConfirmar = { contaId, valor ->
                if (meta.valorGuardado + valor >= meta.valorObjetivo && meta.valorGuardado < meta.valorObjetivo) {
                    val posX = screenWidth / 2
                    val posY = 1200f
                    if (!modoEconomia) {
                        confettiState.disparar(posX, posY)
                    }
                }
                viewModel.realizarAporteReal(meta, contaId, valor)
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

@Composable
fun MetaCard(
    meta: Meta,
    isPrivate: Boolean,
    onAporteClick: () -> Unit,
    onExcluirClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val progresso = (meta.valorGuardado / meta.valorObjetivo).toFloat().coerceIn(0f, 1f)
    val isConcluida = progresso >= 1f

    val animProgresso by animateFloatAsState(
        targetValue = progresso,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "progressoMeta"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConcluida) NeonGreen.copy(0.08f) else PremiumLightBlue
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (isConcluida) NeonGreen.copy(0.4f) else Color.White.copy(0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        meta.nome,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (isConcluida) {
                        Text(
                            "Objetivo Alcançado 🎉",
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isConcluida) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = NeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            "${(progresso * 100).toInt()}%",
                            color = NeonGreen,
                            fontWeight = FontWeight.Black
                        )
                    }
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, null, tint = TextWhite.copy(0.5f))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onExcluirClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.DeleteOutline, null, tint = TextWhite.copy(0.3f))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Barra de Progresso Animada e Protegida com Clip
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.1f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animProgresso)
                        .height(10.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF00C853), NeonGreen)))
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Guardado", fontSize = 10.sp, color = TextWhite.copy(0.5f))
                    Text(
                        formatarMoedaBR(meta.valorGuardado, isPrivate),
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Objetivo", fontSize = 10.sp, color = TextWhite.copy(0.5f))
                    Text(
                        formatarMoedaBR(meta.valorObjetivo, isPrivate),
                        color = TextWhite.copy(0.7f)
                    )
                }
            }

            if (!isConcluida) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAporteClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, null, Modifier.size(16.dp), tint = NeonGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Realizar Aporte", color = NeonGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// COMPONENTES REUTILIZÁVEIS E DIÁLOGOS
// ============================================================================

@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isDecimal: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (isDecimal) {
                if (input.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(input)
            } else {
                onValueChange(input)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            focusedLabelColor = NeonGreen,
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = Color.White.copy(0.3f),
            cursorColor = NeonGreen
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}


@Composable
fun SeletorContaButton(
    contas: List<ContaSaldo>,
    selecionadaId: String?,
    onSelecionar: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PremiumLightBlue),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(0.2f)),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = contas.find { it.conta == selecionadaId }
                    ?.let { "${it.conta} (R$ ${it.saldo})" } ?: "Selecionar Banco",
                color = TextWhite,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Icon(Icons.Default.ArrowDropDown, null, tint = TextWhite)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(PremiumLightBlue)
                .fillMaxWidth(0.8f)
        ) {
            contas.forEach { conta ->
                DropdownMenuItem(
                    text = { Text("${conta.conta} (R$ ${conta.saldo})", color = TextWhite) },
                    onClick = {
                        onSelecionar(conta.conta)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AddMetaDialog(onSalvar: (String, Double) -> Unit, onDismiss: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var objetivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PremiumDarkBlue,
        title = { Text("Nova Meta Financeira", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = "Nome (Ex: Viagem)"
                )
                DialogTextField(
                    value = objetivo,
                    onValueChange = { objetivo = it },
                    label = "Valor do Objetivo (R$)",
                    isDecimal = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val valor = objetivo.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (nome.isNotBlank() && valor > 0) {
                        onSalvar(nome, valor)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) { Text("Criar Meta", color = PremiumDarkBlue, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextWhite.copy(0.6f)) }
        }
    )
}

@Composable
fun AporteMetaDialog(
    contasDisponiveis: List<ContaSaldo>,
    onConfirmar: (String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var valorTexto by remember { mutableStateOf("") }
    var contaSelecionadaId by remember {
        mutableStateOf(
            contasDisponiveis.firstOrNull()?.conta ?: ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PremiumDarkBlue,
        title = { Text("Realizar Aporte", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogTextField(
                    value = valorTexto,
                    onValueChange = { valorTexto = it },
                    label = "Valor do Aporte (R$)",
                    isDecimal = true
                )
                Text(
                    "De qual conta o dinheiro vai sair?",
                    color = TextWhite.copy(0.7f),
                    fontSize = 12.sp
                )
                SeletorContaButton(
                    contas = contasDisponiveis,
                    selecionadaId = contaSelecionadaId,
                    onSelecionar = { contaSelecionadaId = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = valorTexto.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v > 0 && contaSelecionadaId.isNotBlank()) {
                        onConfirmar(contaSelecionadaId, v)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) { Text("Confirmar", color = PremiumDarkBlue, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextWhite.copy(0.6f)) }
        }
    )
}

@Composable
fun DeleteMetaDialog(
    meta: Meta,
    contas: List<ContaSaldo>,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var contaSelecionadaId by remember { mutableStateOf(contas.firstOrNull()?.conta) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PremiumDarkBlue,
        title = { Text("Excluir Meta?", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Deseja excluir '${meta.nome}'? Esta ação é irreversível.",
                    color = TextWhite.copy(0.8f),
                    fontSize = 14.sp
                )

                if (meta.valorGuardado > 0) {
                    Divider(color = Color.White.copy(0.1f))
                    Text(
                        "Você tem ${
                            formatarMoedaBR(
                                meta.valorGuardado,
                                false
                            )
                        } guardados aqui.\nPara qual conta devemos devolver esse dinheiro?",
                        color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    SeletorContaButton(
                        contas = contas,
                        selecionadaId = contaSelecionadaId,
                        onSelecionar = { contaSelecionadaId = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(contaSelecionadaId) },
                colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
            ) { Text("Sim, Excluir", color = TextWhite, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextWhite.copy(0.6f)) }
        }
    )
}

@Composable
fun EditMetaDialog(meta: Meta, onConfirmar: (String, Double) -> Unit, onDismiss: () -> Unit) {
    var nome by remember { mutableStateOf(meta.nome) }
    var objetivo by remember { mutableStateOf(meta.valorObjetivo.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PremiumDarkBlue,
        title = { Text("Editar Meta", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogTextField(value = nome, onValueChange = { nome = it }, label = "Nome da Meta")
                DialogTextField(
                    value = objetivo,
                    onValueChange = { objetivo = it },
                    label = "Novo Valor (R$)",
                    isDecimal = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val valor = objetivo.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (nome.isNotBlank() && valor > 0) {
                        onConfirmar(nome, valor)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) { Text("Salvar", color = PremiumDarkBlue, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextWhite.copy(0.6f)) }
        }
    )
}