package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.R
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.viewModel.ContaSaldoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Cores locais
private val DialogBg = Color(0xFF1E2B3E)
private val TextColor = Color(0xFFE0E1DD)
private val CardBg = Color(0xFF1E2B3E)

@Composable
fun ActionButtonRow(
    categorias: List<String>,
    getPicCategoria: (String) -> String,
    contaSelecionada: String,
    viewModel: ContaSaldoViewModel,
    onConfigClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var exibirFormulario by remember { mutableStateOf(false) }
    var exibirDeposito by remember { mutableStateOf(false) }

    // Container que centraliza e limita a largura em telas muito grandes
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 500.dp) // Limita a largura máxima para não esticar demais
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), // Espaçamento levemente reduzido
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
            onDismiss = { exibirFormulario = false }
        )
    }

    if (exibirDeposito) {
        DepositDialog(
            contaSelecionada = contaSelecionada,
            viewModel = viewModel,
            onDismiss = { exibirDeposito = false }
        )
    }
}

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
        // Reduzi o tamanho do botão circular de 56dp para 48dp
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(16.dp)) // Corner radius ajustado
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp) // Ícone mantido em tamanho legível
            )
        }
        Spacer(Modifier.height(6.dp)) // Espaço reduzido
        Text(
            text = text,
            color = TextColor.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp // Fonte levemente menor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}@Composable
fun PremiumDialogCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DialogBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat
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

// ... (Lógica do DepositDialog e AddDespesaDialog mantida, apenas trocando os componentes visuais pelos Premium acima)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositDialog(
    contaSelecionada: String,
    viewModel: ContaSaldoViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
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
                                descricao = "Depósito em Conta",
                                categoria = "Depósito",
                                valor = v,
                                data = Date(dataMillis.value),
                                pic = "deposit", // Assegure-se que este drawable existe
                                conta = contaSelecionada,
                                tipo = TipoDespesa.CREDITO,
                                pago = true
                            )
                            viewModel.adicionarDespesa(dep)
                            scope.launch {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDespesaDialog(
    categorias: List<String>,
    contaSelecionada: String,
    getPicCategoria: (String) -> String,
    viewModel: ContaSaldoViewModel,
    onDismiss: () -> Unit
) {

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val contaFixada = remember { contaSelecionada.trim() }

    var categoriaSelecionada by remember { mutableStateOf<String?>(null) }
    var expandido by remember { mutableStateOf(false) }
    var tipo by remember { mutableStateOf(TipoDespesa.DEBITO) }

    var descricao by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    var numeroParcelas by rememberSaveable { mutableStateOf("1") }

    val mostrarCalendario = remember { mutableStateOf(false) }
    val dataMillis = remember { mutableStateOf<Long?>(null) }

    // Erros (UI)
    var erroConta by remember { mutableStateOf<String?>(null) }
    var erroCategoria by remember { mutableStateOf<String?>(null) }
    var erroDescricao by remember { mutableStateOf<String?>(null) }
    var erroValor by remember { mutableStateOf<String?>(null) }
    var erroParcelas by remember { mutableStateOf<String?>(null) }
    var erroData by remember { mutableStateOf<String?>(null) }

    // --- LÓGICA DE VALIDAÇÃO E FORMATAÇÃO (MANTIDA) ---
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

    fun parseParcelas(text: String): Int? {
        val t = text.trim()
        if (t.isBlank()) return null
        return t.toIntOrNull()
    }

    fun validarTudo(): Boolean {
        val desc = descricao.trim()
        val cat = categoriaSelecionada?.trim().orEmpty()
        val valorDouble = parseMoneyToDouble(valor)
        val parcelasInt = parseParcelas(numeroParcelas)
        val data = dataMillis.value

        erroConta = null; erroCategoria = null; erroDescricao = null
        erroValor = null; erroParcelas = null; erroData = null

        var ok = true

        if (contaFixada.isBlank()) { erroConta = "Conta inválida."; ok = false }
        if (cat.isBlank()) { erroCategoria = "Selecione uma categoria."; ok = false }
        else if (categorias.isNotEmpty() && !categorias.contains(cat)) { erroCategoria = "Categoria inválida."; ok = false }

        if (desc.isBlank()) { erroDescricao = "Descrição obrigatória."; ok = false }
        else if (desc.length < 3) { erroDescricao = "Descrição muito curta."; ok = false }
        else if (desc.length > 60) { erroDescricao = "Máximo de 60 caracteres."; ok = false }

        if (valorDouble == null || valorDouble <= 0.0) { erroValor = "Valor inválido."; ok = false }

        if (parcelasInt == null || parcelasInt < 1 || parcelasInt > 360) { erroParcelas = "Parcelas inválidas (1-360)."; ok = false }

        if (data == null) { erroData = "Selecione uma data."; ok = false }

        return ok
    }

    val podeAdicionar by remember(contaFixada, categoriaSelecionada, descricao, valor, numeroParcelas, dataMillis.value) {
        mutableStateOf(
            contaFixada.isNotBlank() && !categoriaSelecionada.isNullOrBlank() && descricao.trim().length >= 3
                    && (parseMoneyToDouble(valor) ?: 0.0) > 0.0 && (parseParcelas(numeroParcelas) ?: 0) >= 1 && dataMillis.value != null
        )
    }

    if (mostrarCalendario.value) {
        CustomCalendarDialog(
            onDismiss = { mostrarCalendario.value = false },
            onDateSelected = { ano, mes, dia ->
                val cal = Calendar.getInstance()
                cal.set(ano, mes, dia, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                dataMillis.value = cal.timeInMillis
                mostrarCalendario.value = false
                erroData = null
            }
        )
    }

    // --- ESTILOS PREMIUM ---
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.White.copy(alpha = 0.8f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedTextColor = TextWhite,
        unfocusedTextColor = TextWhite,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
        cursorColor = Color.White,
        errorCursorColor = MaterialTheme.colorScheme.error,
        errorLabelColor = MaterialTheme.colorScheme.error,
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.7f)
    )

    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = TextWhite,
        activeContentColor = PremiumDarkBlue,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = TextWhite,
        activeBorderColor = TextWhite,
        inactiveBorderColor = TextWhite.copy(alpha = 0.3f)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DialogBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nova Movimentação",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextWhite
                )

                Text(
                    text = "Conta: $contaFixada",
                    color = TextWhite.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                if (erroConta != null) Text(text = erroConta!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

                // Segmented Button Estilizado
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tipo == TipoDespesa.DEBITO,
                        onClick = { tipo = TipoDespesa.DEBITO },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = segmentColors,
                        border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                    ) { Text("Débito") }

                    SegmentedButton(
                        selected = tipo == TipoDespesa.CREDITO,
                        onClick = { tipo = TipoDespesa.CREDITO },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = segmentColors,
                        border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                    ) { Text("Crédito") }
                }

                // Categoria Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandido,
                    onExpandedChange = { expandido = !expandido },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = categoriaSelecionada ?: "",
                        onValueChange = {},
                        readOnly = true,
                        isError = erroCategoria != null,
                        label = { Text("Categoria") },
                        placeholder = { Text("Selecione") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                        supportingText = { if (erroCategoria != null) Text(erroCategoria!!, color = MaterialTheme.colorScheme.error) },
                        colors = inputColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expandido,
                        onDismissRequest = { expandido = false },
                        modifier = Modifier.background(DialogBg)// Menu escuro
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

                // Descrição
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it; if (erroDescricao != null) erroDescricao = null },
                    isError = erroDescricao != null,
                    label = { Text("Descrição") },
                    singleLine = true,
                    supportingText = {
                        if (erroDescricao != null) Text(erroDescricao!!, color = MaterialTheme.colorScheme.error)
                        else Text("${descricao.trim().length}/60", fontSize = 12.sp, color = TextWhite.copy(alpha = 0.5f))
                    },
                    colors = inputColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Valor e Parcelas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { valor = normalizeMoneyInput(it); if (erroValor != null) erroValor = null },
                        isError = erroValor != null,
                        label = { Text("Valor", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { if (erroValor != null) Text(erroValor!!, color = MaterialTheme.colorScheme.error) },
                        colors = inputColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = numeroParcelas,
                        onValueChange = { novo ->
                            val digitsOnly = novo.filter { it.isDigit() }
                            numeroParcelas = if (digitsOnly.isBlank()) "" else digitsOnly.take(3)
                            if (erroParcelas != null) erroParcelas = null
                        },
                        isError = erroParcelas != null,
                        label = { Text("Parcelas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { if (erroParcelas != null) Text(erroParcelas!!, color = MaterialTheme.colorScheme.error) },
                        colors = inputColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).widthIn(min = 100.dp)
                    )
                }

                // Data
                OutlinedTextField(
                    value = dataMillis.value?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    isError = erroData != null,
                    label = { Text("Data") },
                    placeholder = { Text("Selecionar data") },
                    trailingIcon = {
                        IconButton(onClick = { mostrarCalendario.value = true }) {
                            Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = "Data", tint = TextWhite)
                        }
                    },
                    supportingText = { if (erroData != null) Text(erroData!!, color = MaterialTheme.colorScheme.error) },
                    colors = inputColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Botões
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                    ) { Text("Cancelar") }

                    Button(
                        enabled = podeAdicionar,
                        onClick = {
                            if (!validarTudo()) {
                                Toast.makeText(context, "Revise os campos.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // ... (Lógica de inserção mantida) ...
                            val valorDouble = parseMoneyToDouble(valor) ?: return@Button
                            val parcelasInt = parseParcelas(numeroParcelas) ?: 1
                            val dataFinal = dataMillis.value ?: System.currentTimeMillis()
                            val categoriaFinal = categoriaSelecionada!!.trim()

                            val novaDespesa = Despesa(
                                descricao = descricao.trim(),
                                valor = valorDouble,
                                data = Date(dataFinal),
                                categoria = categoriaFinal,
                                pic = getPicCategoria(categoriaFinal),
                                conta = contaFixada,
                                tipo = tipo
                            )

                            if (parcelasInt > 1) {
                                viewModel.adicionarDespesaParcelada(novaDespesa, parcelasInt, dataFinal)
                            } else {
                                viewModel.adicionarDespesa(novaDespesa)
                            }

                            scope.launch {
                                delay(300)
                                viewModel.carregarResumoFinanceiro()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextWhite, // Botão Sólido Branco
                            contentColor = PremiumDarkBlue, // Texto Escuro
                            disabledContainerColor = TextWhite.copy(alpha = 0.3f),
                            disabledContentColor = PremiumDarkBlue.copy(alpha = 0.5f)
                        )
                    ) { Text("Adicionar") }
                }
            }
        }
    }
}
@Composable
fun RowScope.ActionButton(
    icon: Int,
    text: String,
    accent: Color,
    perItemWidth: Dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(targetValue = if (pressed) 0.98f else 1f, label = "actionScale")

    val bg by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        label = "actionBg"
    )

    val border by animateColorAsState(
        targetValue = if (pressed) accent.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        label = "actionBorder"
    )

    val compactText = perItemWidth < 120.dp
    val textStyle = if (compactText) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    Surface(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 76.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = if (pressed) 6.dp else 0.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(colors = listOf(accent.copy(alpha = if (pressed) 0.30f else 0.22f), accent.copy(alpha = 0.10f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                Icon(painter = painterResource(icon), contentDescription = text, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(text = text, style = textStyle, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Clip, modifier = Modifier.fillMaxWidth())
        }
        if (pressed) Box(modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = 0.07f)))
    }
}
