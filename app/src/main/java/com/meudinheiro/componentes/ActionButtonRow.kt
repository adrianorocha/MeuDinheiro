package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.room.util.copy
import com.meudinheiro.R
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.viewModel.ContaSaldoViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Cores locais
private val DialogBg = Color(0xFF1E2B3E)
private val TextColor = Color(0xFFE0E1DD)
private enum class Frequencia {
    UNICA,
    PARCELADA,
    FIXA // Recorrente Automática
}

@Composable
fun ActionButtonRow(
    categorias: List<String>,
    getPicCategoria: (String) -> String,
    contaSelecionada: String,
    viewModel: ContaSaldoViewModel,
    onConfigClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val parentScope = rememberCoroutineScope()

    var exibirFormulario by remember { mutableStateOf(false) }
    var exibirDeposito by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modifierItem = Modifier.weight(1f)

            ActionButton(
                icon = R.drawable.deposit,
                text = "Depositar",
                color = Color(0xFF4CAF50),
                modifier = modifierItem,
                onClick = {
                    if (contaSelecionada.isBlank()) Toast.makeText(context, "Selecione uma conta", Toast.LENGTH_SHORT).show()
                    else exibirDeposito = true
                }
            )

            ActionButton(
                icon = R.drawable.add,
                text = "Nova Despesa",
                color = Color(0xFF2196F3),
                modifier = modifierItem,
                onClick = {
                    if (contaSelecionada.isBlank()) Toast.makeText(context, "Selecione uma conta", Toast.LENGTH_SHORT).show()
                    else exibirFormulario = true
                }
            )

            ActionButton(
                icon = R.drawable.sim_chip,
                text = "Avisos",
                color = Color(0xFFFFC107),
                modifier = modifierItem,
                onClick = onConfigClick
            )
        }
    }

    if (exibirFormulario) {
        AddDespesaDialog(
            categorias = categorias,
            contaSelecionada = contaSelecionada,
            getPicCategoria = getPicCategoria,
            viewModel = viewModel,
            parentScope = parentScope,
            onDismiss = { exibirFormulario = false }
        )
    }

    if (exibirDeposito) {
        DepositDialog(
            contaSelecionada = contaSelecionada,
            viewModel = viewModel,
            parentScope = parentScope,
            onDismiss = { exibirDeposito = false }
        )
    }
}

// ... (ActionButton, PremiumDialogCard, PremiumTextField e DepositDialog mantidos iguais para economizar espaço) ...
// ... Copie eles do código anterior se necessário ...
@Composable
fun ActionButton(
    icon: Int,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f)

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            color = TextColor.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PremiumDialogCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DialogBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        modifier = modifier,
        keyboardOptions = keyboardOptions,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedTextColor = TextColor,
            unfocusedTextColor = TextColor,
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
            cursorColor = Color.White
        )
    )
}
// ... (DepositDialog Code) ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositDialog(
    contaSelecionada: String,
    viewModel: ContaSaldoViewModel,
    parentScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    var valor by rememberSaveable { mutableStateOf("") }
    val dataMillis = remember { mutableStateOf(System.currentTimeMillis()) }
    var mostrarCalendario by remember { mutableStateOf(false) }

    if (mostrarCalendario) {
        CustomCalendarDialog(
            onDismiss = { mostrarCalendario = false },
            onDateSelected = { y, m, d ->
                val c = Calendar.getInstance().apply { set(y, m, d) }
                dataMillis.value = c.timeInMillis
                mostrarCalendario = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        PremiumDialogCard {
            Text("Novo Depósito", style = MaterialTheme.typography.titleLarge, color = TextColor)
            Text("Para: $contaSelecionada", color = TextColor.copy(alpha = 0.6f))

            PremiumTextField(
                value = valor,
                onValueChange = { valor = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = "Valor (R$)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            PremiumTextField(
                value = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(dataMillis.value)),
                onValueChange = {},
                label = "Data",
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { mostrarCalendario = true }) {
                        Icon(Icons.Default.CalendarMonth, null, tint = TextColor)
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
                ) { Text("Cancelar") }

                Button(
                    onClick = {
                        val v = valor.replace(",", ".").toDoubleOrNull()
                        if (v != null && v > 0) {
                            val dep = Despesa(
                                descricao = "Depósito",
                                categoria = "Depósito",
                                valor = v,
                                data = Date(dataMillis.value),
                                pic = "deposit",
                                conta = contaSelecionada,
                                tipo = TipoDespesa.CREDITO,
                                pago = true
                            )
                            viewModel.adicionarDespesa(dep)
                            parentScope.launch {
                                delay(200)
                                viewModel.carregarResumoFinanceiro()
                            }
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                ) { Text("Confirmar") }
            }
        }
    }
}


// -----------------------------------------------------------
// ADICIONAR DESPESA - COM LÓGICA DE RECORRÊNCIA AUTOMÁTICA
// -----------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDespesaDialog(
    categorias: List<String>,
    contaSelecionada: String,
    getPicCategoria: (String) -> String,
    viewModel: ContaSaldoViewModel,
    parentScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val contaAtual by rememberUpdatedState(contaSelecionada.trim())

    var categoriaSelecionada by remember { mutableStateOf<String?>(null) }
    var expandido by remember { mutableStateOf(false) }

    // Estados
    var tipo by remember { mutableStateOf(TipoDespesa.DEBITO) }
    var frequencia by remember { mutableStateOf(Frequencia.UNICA) }

    var descricao by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    var numeroParcelas by rememberSaveable { mutableStateOf("1") } // Usado apenas se Frequencia.PARCELADA

    val mostrarCalendario = remember { mutableStateOf(false) }
    val dataMillis = remember { mutableStateOf<Long?>(System.currentTimeMillis()) }

    // Validações
    var erroCategoria by remember { mutableStateOf<String?>(null) }
    var erroDescricao by remember { mutableStateOf<String?>(null) }
    var erroValor by remember { mutableStateOf<String?>(null) }
    var erroParcelas by remember { mutableStateOf<String?>(null) }
    var erroData by remember { mutableStateOf<String?>(null) }

    fun normalizeMoneyInput(raw: String): String {
        val filtered = raw.filter { it.isDigit() || it == ',' || it == '.' }
        if (filtered.isBlank()) return ""
        var separatorCount = 0
        val normalized = buildString {
            filtered.forEach { ch ->
                if (ch == ',' || ch == '.') {
                    if (separatorCount == 0) { append(ch); separatorCount++ }
                } else { append(ch) }
            }
        }
        val first = normalized.firstOrNull()
        return if (first == ',' || first == '.') "0$normalized" else normalized
    }

    fun parseMoneyToDouble(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        return trimmed.replace(",", ".").toDoubleOrNull()
    }

    fun validarTudo(): Boolean {
        val desc = descricao.trim()
        val cat = categoriaSelecionada?.trim().orEmpty()
        val valorDouble = parseMoneyToDouble(valor)
        val data = dataMillis.value

        erroCategoria = null; erroDescricao = null; erroValor = null; erroParcelas = null; erroData = null
        var ok = true

        if (contaAtual.isBlank()) ok = false
        if (cat.isBlank()) { erroCategoria = "Obrigatório"; ok = false }
        if (desc.isBlank()) { erroDescricao = "Obrigatório"; ok = false }
        if (valorDouble == null || valorDouble <= 0.0) { erroValor = "Inválido"; ok = false }
        if (data == null) { erroData = "Obrigatório"; ok = false }

        // Valida parcelas apenas se for parcelado
        if (frequencia == Frequencia.PARCELADA) {
            val p = numeroParcelas.toIntOrNull()
            if (p == null || p < 2) { erroParcelas = "Mín 2"; ok = false }
        }

        return ok
    }

    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = TextWhite,
        activeContentColor = PremiumDarkBlue,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = TextWhite,
        activeBorderColor = TextWhite,
        inactiveBorderColor = TextWhite.copy(alpha = 0.3f)
    )

    if (mostrarCalendario.value) {
        CustomCalendarDialog(
            onDismiss = { mostrarCalendario.value = false },
            onDateSelected = { y, m, d ->
                val c = Calendar.getInstance().apply { set(y, m, d) }
                dataMillis.value = c.timeInMillis
                mostrarCalendario.value = false
                erroData = null
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        PremiumDialogCard {
            Text("Nova Movimentação", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextWhite)
            Text("Conta: $contaAtual", color = TextWhite.copy(alpha = 0.6f), fontSize = 13.sp)

            // 1. Frequência (Novo Layout Compacto)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = frequencia == Frequencia.UNICA,
                    onClick = { frequencia = Frequencia.UNICA },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    colors = segmentColors,
                    label = { Text("Única", fontSize = 11.sp) }
                )
                SegmentedButton(
                    selected = frequencia == Frequencia.PARCELADA,
                    onClick = { frequencia = Frequencia.PARCELADA },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    colors = segmentColors,
                    label = { Text("Parcelada", fontSize = 11.sp) }
                )
                SegmentedButton(
                    selected = frequencia == Frequencia.FIXA,
                    onClick = { frequencia = Frequencia.FIXA },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    colors = segmentColors,
                    label = { Text("Fixa", fontSize = 11.sp) },
                    icon = { Icon(Icons.Rounded.Repeat, null, Modifier.size(14.dp)) }
                )
            }

            // Explicação Dinâmica
            AnimatedVisibility(visible = frequencia == Frequencia.FIXA) {
                Box(Modifier.background(Color.White.copy(0.05f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text(
                        "Será lançada automaticamente todo mês no dia escolhido abaixo. Para parar, exclua a recorrência nas configurações.",
                        style = MaterialTheme.typography.bodySmall, color = TextWhite.copy(0.7f), fontSize = 11.sp
                    )
                }
            }

            // Dropdown de Categoria (Compactado)
            ExposedDropdownMenuBox(
                expanded = expandido,
                onExpandedChange = { expandido = !expandido },
                modifier = Modifier.fillMaxWidth()
            ) {
                PremiumTextField(
                    value = categoriaSelecionada ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = "Categoria",
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandido,
                    onDismissRequest = { expandido = false },
                    modifier = Modifier.background(DialogBg)
                ) {
                    categorias.forEach { categoria ->
                        DropdownMenuItem(
                            text = { Text(categoria, color = TextWhite) },
                            onClick = {
                                categoriaSelecionada = categoria
                                expandido = false
                                erroCategoria = null
                            }
                        )
                    }
                }
            }
            if (erroCategoria != null) Text(erroCategoria!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

            PremiumTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = "Descrição",
                modifier = Modifier.fillMaxWidth()
            )
            if (erroDescricao != null) Text(erroDescricao!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    PremiumTextField(
                        value = valor,
                        onValueChange = { valor = normalizeMoneyInput(it) },
                        label = "Valor",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (erroValor != null) Text(erroValor!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                // Parcelas (Só aparece se for parcelado)
                if (frequencia == Frequencia.PARCELADA) {
                    Column(Modifier.weight(1f)) {
                        PremiumTextField(
                            value = numeroParcelas,
                            onValueChange = { numeroParcelas = it.filter { c -> c.isDigit() } },
                            label = "Parcelas",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (erroParcelas != null) Text(erroParcelas!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            // Data (O Rótulo muda se for fixa)
            val labelData = if (frequencia == Frequencia.FIXA) "Dia do Vencimento" else "Data"
            PremiumTextField(
                value = dataMillis.value?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = labelData,
                trailingIcon = {
                    IconButton(onClick = { mostrarCalendario.value = true }) {
                        Icon(Icons.Default.CalendarMonth, null, tint = TextWhite)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (erroData != null) Text(erroData!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                ) { Text("Cancelar") }

                Button(
                    onClick = {
                        if (validarTudo()) {
                            val v = parseMoneyToDouble(valor)!!
                            val d = Date(dataMillis.value!!)

                            val desp = Despesa(
                                descricao = descricao.trim(),
                                valor = v,
                                data = d,
                                categoria = categoriaSelecionada!!,
                                pic = getPicCategoria(categoriaSelecionada!!),
                                conta = contaAtual,
                                tipo = tipo
                            )

                            parentScope.launch {
                                when (frequencia) {
                                    Frequencia.UNICA -> {
                                        viewModel.adicionarDespesa(desp)
                                    }
                                    Frequencia.PARCELADA -> {
                                        val p = numeroParcelas.toIntOrNull() ?: 1
                                        viewModel.adicionarDespesaParcelada(desp, p, dataMillis.value!!)
                                    }
                                    Frequencia.FIXA -> {
                                        // Extrai o dia do vencimento da data selecionada
                                        val cal = Calendar.getInstance()
                                        cal.time = d
                                        val diaVencimento = cal.get(Calendar.DAY_OF_MONTH)

                                        viewModel.salvarDespesaRecorrente(desp, diaVencimento)
                                    }
                                }

                                delay(500)
                                viewModel.carregarSaldosGlobais()
                            }
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PremiumDarkBlue)
                ) { Text("Salvar") }
            }
        }
    }
}