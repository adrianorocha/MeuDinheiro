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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.room.util.copy
import com.meudinheiro.R
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.formatarMoedaBR
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
fun AddDespesaDialog(
    categorias: List<String>,
    contaSelecionada: String,
    getPicCategoria: (String) -> String,
    viewModel: ContaSaldoViewModel,
    parentScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    // --- ESTADOS DE DADOS ---
    val contaAtual by rememberUpdatedState(contaSelecionada.trim())
    var categoriaSelecionada by remember { mutableStateOf<String?>(null) }
    var tipo by remember { mutableStateOf(TipoDespesa.DEBITO) } // Poderia ter um Switch para Crédito
    var frequencia by remember { mutableStateOf(Frequencia.UNICA) }

    var descricao by rememberSaveable { mutableStateOf("") }
    var valorTexto by rememberSaveable { mutableStateOf("") }
    var numeroParcelas by rememberSaveable { mutableStateOf("2") }

    // --- ESTADOS DE DATA ---
    val mostrarCalendario = remember { mutableStateOf(false) }
    val dataMillis = remember { mutableStateOf<Long?>(System.currentTimeMillis()) }

    // --- ESTADOS DE MOEDA (NOVO!) ---
    var moedaSelecionada by remember { mutableStateOf("BRL") }
    var cotacaoTexto by remember { mutableStateOf("1.00") } // Padrão 1.0 para BRL

    // --- ESTADOS DE UI ---
    var expandidoCategoria by remember { mutableStateOf(false) }

    // --- VALIDAÇÃO (Mensagens de erro) ---
    var erroCategoria by remember { mutableStateOf<String?>(null) }
    var erroDescricao by remember { mutableStateOf<String?>(null) }
    var erroValor by remember { mutableStateOf<String?>(null) }
    var erroParcelas by remember { mutableStateOf<String?>(null) }
    var erroData by remember { mutableStateOf<String?>(null) }

    // --- FUNÇÕES AUXILIARES ---
    fun normalizeMoneyInput(raw: String): String {
        return raw.filter { it.isDigit() || it == ',' || it == '.' }.replace(',', '.')
    }

    fun validarSalvar(): Boolean {
        // Limpa erros anteriores
        erroCategoria = null; erroDescricao = null; erroValor = null; erroParcelas = null; erroData = null
        var isValid = true

        if (categoriaSelecionada.isNullOrBlank()) { erroCategoria = "Selecione uma categoria"; isValid = false }
        if (descricao.isBlank()) { erroDescricao = "Digite uma descrição"; isValid = false }

        val v = valorTexto.toDoubleOrNull()
        if (v == null || v <= 0.0) { erroValor = "Valor inválido"; isValid = false }

        if (dataMillis.value == null) { erroData = "Data obrigatória"; isValid = false }

        if (frequencia == Frequencia.PARCELADA) {
            val p = numeroParcelas.toIntOrNull()
            if (p == null || p < 2) { erroParcelas = "Mínimo 2x"; isValid = false }
        }

        return isValid
    }

    // --- COMPONENTES VISUAIS ---
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
            // CABEÇALHO
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nova Despesa", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextWhite)
                Text(
                    "Conta: $contaAtual",
                    color = TextWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 1. SELETOR DE FREQUÊNCIA
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val opcoes = listOf(
                    Frequencia.UNICA to "Única",
                    Frequencia.PARCELADA to "Parcelada",
                    Frequencia.FIXA to "Fixa"
                )

                opcoes.forEachIndexed { index, (freq, label) ->
                    SegmentedButton(
                        selected = frequencia == freq,
                        onClick = { frequencia = freq },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = opcoes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Color(0xFF69F0AE),
                            activeContentColor = PremiumDarkBlue,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = TextWhite
                        ),
                        label = { Text(label, fontSize = 11.sp, fontWeight = if(frequencia == freq) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Explicação Recorrência
            AnimatedVisibility(visible = frequencia == Frequencia.FIXA) {
                Text(
                    "Será lançada todo mês no dia selecionado. Gerencie em Ajustes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF69F0AE),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 2. SELETOR DE MOEDA (Feature Nova)
            Text("Moeda da Transação", fontSize = 12.sp, color = TextWhite.copy(0.7f))
            CurrencySelector(
                moedaAtual = moedaSelecionada,
                onMoedaSelecionada = {
                    moedaSelecionada = it
                    if (it == "BRL") cotacaoTexto = "1.00" // Reset se voltar pra Real
                }
            )

            // 3. VALORES E COTAÇÃO
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Campo Valor
                Column(Modifier.weight(1f)) {
                    PremiumTextField(
                        value = valorTexto,
                        onValueChange = { valorTexto = normalizeMoneyInput(it) },
                        label = "Valor (${moedaSelecionada})",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (erroValor != null) Text(erroValor!!, color = Color(0xFFEF5350), fontSize = 10.sp)
                }

                // Campo Cotação (Só aparece se não for BRL)
                if (moedaSelecionada != "BRL") {
                    Column(Modifier.weight(0.7f)) {
                        PremiumTextField(
                            value = cotacaoTexto,
                            onValueChange = { cotacaoTexto = normalizeMoneyInput(it) },
                            label = "Cotação",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Preview da Conversão
            if (moedaSelecionada != "BRL" && valorTexto.isNotEmpty()) {
                val vOrig = valorTexto.toDoubleOrNull() ?: 0.0
                val cot = cotacaoTexto.toDoubleOrNull() ?: 1.0
                val final = vOrig * cot
                Text(
                    text = "Valor final: ${formatarMoedaBR(final, false)}", // Assumindo isPrivate false no dialog
                    color = Color(0xFF69F0AE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 4. DESCRIÇÃO E CATEGORIA
            ExposedDropdownMenuBox(
                expanded = expandidoCategoria,
                onExpandedChange = { expandidoCategoria = !expandidoCategoria },
                modifier = Modifier.fillMaxWidth()
            ) {
                PremiumTextField(
                    value = categoriaSelecionada ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = "Categoria",
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoCategoria) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandidoCategoria,
                    onDismissRequest = { expandidoCategoria = false },
                    modifier = Modifier.background(Color(0xFF1E2B3E))
                ) {
                    categorias.forEach { categoria ->
                        DropdownMenuItem(
                            text = { Text(categoria, color = TextWhite) },
                            onClick = {
                                categoriaSelecionada = categoria
                                expandidoCategoria = false
                                erroCategoria = null
                            }
                        )
                    }
                }
            }
            if (erroCategoria != null) Text(erroCategoria!!, color = Color(0xFFEF5350), fontSize = 10.sp)

            Spacer(Modifier.height(8.dp))

            PremiumTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = "Descrição",
                modifier = Modifier.fillMaxWidth()
            )
            if (erroDescricao != null) Text(erroDescricao!!, color = Color(0xFFEF5350), fontSize = 10.sp)

            Spacer(Modifier.height(8.dp))

            // 5. DATA E PARCELAS
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Campo Data
                Column(Modifier.weight(1f)) {
                    val labelData = if (frequencia == Frequencia.FIXA) "Dia Vencimento" else "Data"
                    PremiumTextField(
                        value = dataMillis.value?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = labelData,
                        trailingIcon = {
                            Icon(Icons.Default.CalendarMonth, null, tint = TextWhite.copy(0.7f), modifier = Modifier.clickable { mostrarCalendario.value = true })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (erroData != null) Text(erroData!!, color = Color(0xFFEF5350), fontSize = 10.sp)
                }

                // Campo Parcelas (Condicional)
                if (frequencia == Frequencia.PARCELADA) {
                    Column(Modifier.weight(0.6f)) {
                        PremiumTextField(
                            value = numeroParcelas,
                            onValueChange = { if(it.length <= 3) numeroParcelas = it.filter { c -> c.isDigit() } },
                            label = "x Vezes",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (erroParcelas != null) Text(erroParcelas!!, color = Color(0xFFEF5350), fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 6. BOTÕES DE AÇÃO
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.2f))
                ) { Text("Cancelar") }

                Button(
                    onClick = {
                        if (validarSalvar()) {
                            val vOriginal = valorTexto.toDouble()
                            val cot = cotacaoTexto.toDoubleOrNull() ?: 1.0
                            val vFinalBRL = vOriginal * cot
                            val dataFinal = Date(dataMillis.value!!)

                            val desp = Despesa(
                                id = 0,
                                descricao = descricao.trim(),
                                valor = vFinalBRL, // Salva sempre em REAIS para somar certo
                                valorOriginal = vOriginal, // Guarda o original para histórico
                                moedaOriginal = moedaSelecionada,
                                cotacaoNaData = cot,
                                data = dataFinal,
                                categoria = categoriaSelecionada!!,
                                pic = getPicCategoria(categoriaSelecionada!!),
                                conta = contaAtual,
                                tipo = tipo,
                                pago = true // Assume pago ao criar (ou adicionar checkbox depois)
                            )

                            parentScope.launch {
                                when (frequencia) {
                                    Frequencia.UNICA -> viewModel.adicionarDespesa(desp)
                                    Frequencia.PARCELADA -> {
                                        val p = numeroParcelas.toIntOrNull() ?: 1
                                        viewModel.adicionarDespesaParcelada(desp, p, dataMillis.value!!)
                                    }
                                    Frequencia.FIXA -> {
                                        val cal = Calendar.getInstance().apply { time = dataFinal }
                                        viewModel.salvarDespesaRecorrente(desp, cal.get(Calendar.DAY_OF_MONTH))
                                    }
                                }
                                // Pequeno delay para animação de fechar não engasgar
                                delay(100)
                            }
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE), contentColor = PremiumDarkBlue)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}