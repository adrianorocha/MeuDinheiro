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
import androidx.compose.material3.OutlinedButton
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

    Box(modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { screenWidth = it.size.width.toFloat() }
    ) {
        Scaffold(
            containerColor = PremiumDarkBlue,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Minhas Metas", color = TextWhite, fontWeight = FontWeight.Bold) },
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
                    containerColor = Color(0xFF69F0AE),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, "Nova Meta", tint = PremiumDarkBlue)
                }
            }
        ) { padding ->
            if (metas.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Você ainda não tem metas.\nClique no + para começar!",
                        color = TextWhite.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
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

    // --- Diálogos ---
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
                // Lógica de Confete: se o novo valor atingir o objetivo
                if (meta.valorGuardado + valor >= meta.valorObjetivo && meta.valorGuardado < meta.valorObjetivo) {
                    val posX = screenWidth / 2
                    val posY = 1200f // Altura aproximada de onde o diálogo aparece
                    if (!modoEconomia){
                        confettiState.disparar(posX, posY)
                    } else if (modoEconomia)
                        println("Meta atingida! (Confetes desativados por economia)")
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
            containerColor = if (isConcluida) Color(0xFF69F0AE).copy(0.08f) else Color.White.copy(0.05f)
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (isConcluida) Color(0xFF69F0AE).copy(0.4f) else Color.White.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(meta.nome, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isConcluida) {
                        Text("Objetivo Alcançado 🎉", color = Color(0xFF69F0AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isConcluida) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF69F0AE), modifier = Modifier.size(24.dp))
                    } else {
                        Text("${(progresso * 100).toInt()}%", color = Color(0xFF69F0AE), fontWeight = FontWeight.Black)
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

            // Barra de Progresso
            Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(0.1f), CircleShape)) {
                Box(
                    Modifier.fillMaxWidth(animProgresso).height(10.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF00C853), Color(0xFF69F0AE))),
                            CircleShape
                        )
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Guardado", fontSize = 10.sp, color = TextWhite.copy(0.5f))
                    Text(formatarMoedaBR(meta.valorGuardado, isPrivate), color = TextWhite, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Objetivo", fontSize = 10.sp, color = TextWhite.copy(0.5f))
                    Text(formatarMoedaBR(meta.valorObjetivo, isPrivate), color = TextWhite.copy(0.7f))
                }
            }

            if (!isConcluida) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAporteClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE).copy(0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, null, Modifier.size(16.dp), tint = Color(0xFF69F0AE))
                    Spacer(Modifier.width(8.dp))
                    Text("Realizar Aporte", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}@Composable
fun AddMetaDialog(
    onSalvar: (nome: String, objetivo: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var objetivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2B3E), // Seu CardBg
        title = {
            Text("Nova Meta Financeira", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome (Ex: Viagem, Reserva)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF69F0AE)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = objetivo,
                    onValueChange = { objetivo = it },
                    label = { Text("Valor do Objetivo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF69F0AE)
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
            ) {
                Text("Criar Meta", color = Color(0xFF0D1B2A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(0.6f))
            }
        }
    )
}

@Composable
fun AporteMetaDialog(
    contasDisponiveis: List<ContaSaldo>,
    onConfirmar: (contaId: String, valor: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var valorTexto by remember { mutableStateOf("") }
    var contaSelecionadaId by remember {
        mutableStateOf(
            contasDisponiveis.firstOrNull()?.conta ?: ""
        )
    }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2B3E),
        title = { Text("Quanto deseja guardar?", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Campo de Valor
                OutlinedTextField(
                    value = valorTexto,
                    onValueChange = { valorTexto = it },
                    label = { Text("Valor do Aporte (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Seletor de Conta
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color.White.copy(0.3f))
                    ) {
                        Text(
                            text = "Origem: ${contasDisponiveis.find { it.conta == contaSelecionadaId }?.conta ?: "Selecionar"}",
                            color = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1E2B3E))
                    ) {
                        contasDisponiveis.forEach { conta ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${conta.conta} (R$ ${conta.saldo})",
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    contaSelecionadaId = conta.conta
                                    expanded = false
                                }
                            )
                        }
                    }
                }
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
            ) {
                Text("Confirmar Aporte", color = PremiumDarkBlue)
            }
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
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2B3E),
        title = { Text("Excluir Meta?", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Deseja excluir '${meta.nome}'? Esta ação é irreversível.",
                    color = Color.White.copy(0.7f), fontSize = 14.sp
                )

                if (meta.valorGuardado > 0) {
                    Divider(color = Color.White.copy(0.1f))
                    Text(
                        "Para onde devemos devolver os ${formatarMoedaBR(meta.valorGuardado, false)} guardados?",
                        color = Color(0xFF69F0AE), fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color.White.copy(0.2f))
                        ) {
                            Text(contaSelecionadaId ?: "Selecionar Conta", color = Color.White)
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color(0xFF1E2B3E))
                        ) {
                            contas.forEach { conta ->
                                DropdownMenuItem(
                                    text = { Text(conta.conta, color = Color.White) },
                                    onClick = {
                                        contaSelecionadaId = conta.conta
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(contaSelecionadaId) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
            ) { Text("Excluir", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.6f)) }
        }
    )
}

@Composable
fun EditMetaDialog(
    meta: Meta,
    onConfirmar: (String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var nome by remember { mutableStateOf(meta.nome) }
    var objetivo by remember { mutableStateOf(meta.valorObjetivo.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2B3E),
        title = { Text("Editar Meta", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome da Meta") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = objetivo,
                    onValueChange = { objetivo = it },
                    label = { Text("Novo Valor do Objetivo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
            ) {
                Text("Salvar Alterações", color = PremiumDarkBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.6f)) }
        }
    )
}