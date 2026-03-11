package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meudinheiro.R
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.SuccessAnimation
import com.meudinheiro.funcoes.compartilharComprovante
import com.meudinheiro.funcoes.gerarBitmapComprovante
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

enum class Frequencia {
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
    val currentContext = LocalContext.current
    val parentScope = rememberCoroutineScope()

    var exibirFormulario by remember { mutableStateOf(false) }
    var exibirDeposito by remember { mutableStateOf(false) }
    var exibirAssinaturas by remember { mutableStateOf(false) }

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
                    if (contaSelecionada.isBlank()) Toast.makeText(
                        currentContext,
                        "Selecione uma conta",
                        Toast.LENGTH_SHORT
                    ).show()
                    else exibirDeposito = true
                }
            )

            ActionButton(
                icon = R.drawable.add,
                text = "Nova Despesa",
                color = Color(0xFF2196F3),
                modifier = modifierItem,
                onClick = {
                    if (contaSelecionada.isBlank()) Toast.makeText(
                        currentContext,
                        "Selecione uma conta",
                        Toast.LENGTH_SHORT
                    ).show()
                    else exibirFormulario = true
                }
            )

            ActionButton(
                icon = R.drawable.assinaturas,
                text = "Assinaturas ",
                color = Color(0xFF2196F3),
                modifier = modifierItem,
                onClick = {
                    if (contaSelecionada.isBlank()) Toast.makeText(
                        currentContext,
                        "Selecione uma conta",
                        Toast.LENGTH_SHORT
                    ).show()
                    else exibirAssinaturas = true
                }
            )

            ActionButton(
                icon = R.drawable.sim_chip,
                text = "Configurações",
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
    if (exibirAssinaturas) {
        GerenciarRecorrenciaDialog(
            viewModel = viewModel,
            onDismiss = { exibirAssinaturas = false }
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
fun PremiumDialogCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
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
    visualTransformation: VisualTransformation = VisualTransformation.None, // O parâmetro que faltava!
    readOnly: Boolean = false,
    onClick: () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Dispara o onClick quando detecta o pressionamento
    LaunchedEffect(isPressed) {
        if (isPressed) onClick?.invoke()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF69F0AE),
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF69F0AE)
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onClick = { }
            )

            PremiumTextField(
                value = SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.getDefault()
                ).format(Date(dataMillis.value)),
                onValueChange = {},
                label = "Data",
                readOnly = true,
                onClick = { mostrarCalendario = true },
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
                                pago = true,
                                mes = Calendar.getInstance().get(Calendar.MONTH) + 1,
                                ano = Calendar.getInstance().get(Calendar.YEAR)
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    )
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
    val currentContext = LocalContext.current
    // --- ESTADOS ---
    val scrollState = rememberScrollState()
    val contaAtual by rememberUpdatedState(contaSelecionada.trim())
    var mostrarSucesso by remember { mutableStateOf(false) }

    var categoriaSelecionada by remember { mutableStateOf<String?>(null) }
    var frequencia by remember { mutableStateOf(Frequencia.UNICA) }
    var descricao by rememberSaveable { mutableStateOf("") }
    var valorTexto by rememberSaveable { mutableStateOf("") }
    var numeroParcelas by rememberSaveable { mutableStateOf("2") }
    var moedaSelecionada by remember { mutableStateOf("BRL") }
    var cotacaoTexto by remember { mutableStateOf("1.00") }

    val mostrarCalendario = remember { mutableStateOf(false) }
    val dataMillis = remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var expandidoCategoria by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }

    // --- LÓGICA DE VALIDAÇÃO ---
    fun validar(): Boolean {
        val novosErros = mutableMapOf<String, String>()
        if (categoriaSelecionada.isNullOrBlank()) novosErros["cat"] = "Selecione a categoria"
        if (descricao.isBlank()) novosErros["desc"] = "Descrição vazia"
        val v = valorTexto.replace(",", ".").toDoubleOrNull()
        if (v == null || v <= 0.0) novosErros["valor"] = "Valor inválido"
        if (frequencia == Frequencia.PARCELADA && (numeroParcelas.toIntOrNull() ?: 0) < 2) {
            novosErros["parc"] = "Mínimo 2x"
        }
        erros = novosErros
        return novosErros.isEmpty()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            PremiumDialogCard(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp)
                    .imePadding()
            ) {
                Crossfade(targetState = mostrarSucesso, animationSpec = tween(500)) { sucesso ->
                    if (sucesso) {
                        SuccessAnimation(onFinished = onDismiss)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(16.dp)
                        ) {
                            HeaderSection(contaAtual)
                            Spacer(Modifier.height(16.dp))

                            FrequenciaSelector(frequencia) { frequencia = it }
                            Spacer(Modifier.height(16.dp))

                            ValueSection(
                                moeda = moedaSelecionada,
                                valor = valorTexto,
                                cotacao = cotacaoTexto,
                                onMoedaChange = { moedaSelecionada = it },
                                onValorChange = { valorTexto = it },
                                onCotacaoChange = { cotacaoTexto = it },
                                erroValor = erros["valor"]
                            )

                            CategoryAndDescSection(
                                categorias = categorias,
                                expandido = expandidoCategoria,
                                setExpandido = { expandidoCategoria = it },
                                selecionada = categoriaSelecionada,
                                onSelect = { categoriaSelecionada = it },
                                descricao = descricao,
                                onDescChange = { descricao = it },
                                erroCat = erros["cat"],
                                erroDesc = erros["desc"]
                            )

                            DateAndInstallmentSection(
                                frequencia = frequencia,
                                dataMillis = dataMillis.value,
                                onOpenCalendar = { mostrarCalendario.value = true },
                                parcelas = numeroParcelas,
                                onParcelasChange = { numeroParcelas = it },
                                erroParc = erros["parc"]
                            )

                            Spacer(Modifier.height(24.dp))

                            ActionButtons(
                                onCancel = onDismiss,
                                onSave = {
                                    if (validar()) {
                                        val vCotacao =
                                            cotacaoTexto.replace(",", ".").toDoubleOrNull() ?: 1.0
                                        val vOriginal = (valorTexto.toDoubleOrNull() ?: 0.0) / 100.0
                                        val vFinalBRL = vOriginal * vCotacao

                                        val desp = Despesa(
                                            id = 0,
                                            descricao = descricao.trim(),
                                            valor = vFinalBRL,
                                            data = Date(dataMillis.value!!),
                                            categoria = categoriaSelecionada!!,
                                            pic = getPicCategoria(categoriaSelecionada!!),
                                            conta = contaAtual,
                                            tipo = TipoDespesa.DEBITO,
                                            pago = false,
                                            mes = Calendar.getInstance().get(Calendar.MONTH) + 1,
                                            ano = Calendar.getInstance().get(Calendar.YEAR)
                                        )

                                        parentScope.launch {
                                            when (frequencia) {
                                                Frequencia.UNICA -> viewModel.adicionarDespesa(desp)
                                                Frequencia.PARCELADA -> viewModel.adicionarDespesaParcelada(
                                                    desp,
                                                    numeroParcelas.toInt(),
                                                    dataMillis.value!!
                                                )

                                                Frequencia.FIXA -> viewModel.salvarDespesaRecorrente(
                                                    desp,
                                                    Calendar.getInstance()
                                                        .apply { timeInMillis = dataMillis.value!! }
                                                        .get(Calendar.DAY_OF_MONTH)
                                                )
                                            }
                                            mostrarSucesso = true

                                            delay(100)

                                            val bitmap = gerarBitmapComprovante(desp)
                                            compartilharComprovante(currentContext, bitmap)
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            // --- DIÁLOGO DE CALENDÁRIO ---
            if (mostrarCalendario.value) {
                CustomCalendarDialog(
                    onDismiss = { mostrarCalendario.value = false },
                    onDateSelected = { y, m, d ->
                        dataMillis.value =
                            Calendar.getInstance().apply { set(y, m, d) }.timeInMillis
                        mostrarCalendario.value = false
                    }
                )
            }
        }
    }
}

// --- SUB-COMPOSABLES SUPORTE ---

@Composable
fun HeaderSection(conta: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nova Despesa", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
        Text("Conta: $conta", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequenciaSelector(atual: Frequencia, onSelect: (Frequencia) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val opcoes = listOf(
            Frequencia.UNICA to "Única",
            Frequencia.PARCELADA to "Parcelada",
            Frequencia.FIXA to "Fixa"
        )
        opcoes.forEachIndexed { index, (freq, label) ->
            SegmentedButton(
                selected = atual == freq,
                onClick = { onSelect(freq) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = opcoes.size),
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun ValueSection(
    moeda: String, valor: String, cotacao: String,
    onMoedaChange: (String) -> Unit, onValorChange: (String) -> Unit,
    onCotacaoChange: (String) -> Unit, erroValor: String?
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // Um pequeno delay de 300ms garante que o diálogo já terminou de "subir"
        delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column {
        CurrencySelector(moeda, onMoedaChange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumTextField(
                value = valor,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) onValorChange(input)
                },
                label = "Valor ($moeda)", modifier = Modifier.weight(1f),
                visualTransformation = CurrencyVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                onClick = { }

            )
            if (moeda != "BRL") {
                PremiumTextField(
                    value = cotacao, onValueChange = onCotacaoChange,
                    label = "Cotação", modifier = Modifier.weight(0.7f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onClick = { }
                )
            }
        }
        erroValor?.let { Text(it, color = Color.Red, fontSize = 10.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryAndDescSection(
    categorias: List<String>, expandido: Boolean, setExpandido: (Boolean) -> Unit,
    selecionada: String?, onSelect: (String) -> Unit,
    descricao: String, onDescChange: (String) -> Unit,
    erroCat: String?, erroDesc: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = setExpandido) {
            PremiumTextField(
                value = selecionada ?: "",
                onValueChange = {},
                readOnly = true,
                label = "Categoria",
                onClick = { setExpandido(true) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expandido, onDismissRequest = { setExpandido(false) }) {
                categorias.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = { onSelect(it); setExpandido(false) })
                }
            }
        }
        erroCat?.let { Text(it, color = Color.Red, fontSize = 10.sp) }

        PremiumTextField(
            value = descricao,
            onValueChange = onDescChange,
            label = "Descrição",
            modifier = Modifier.fillMaxWidth(),
            onClick = { }
        )
        erroDesc?.let { Text(it, color = Color.Red, fontSize = 10.sp) }
    }
}

@Composable
fun DateAndInstallmentSection(
    frequencia: Frequencia, dataMillis: Long?, onOpenCalendar: () -> Unit,
    parcelas: String, onParcelasChange: (String) -> Unit, erroParc: String?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        PremiumTextField(
            value = dataMillis?.let {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(
                    Date(it)
                )
            } ?: "",
            onValueChange = {}, readOnly = true, label = "Data",
            modifier = Modifier
                .weight(1f),
            onClick = onOpenCalendar,
//                .clickable { onOpenCalendar() },
            trailingIcon = {
                IconButton(onClick = onOpenCalendar) {
                    Icon(Icons.Default.CalendarMonth, null, tint = Color.White)
                }
            })
        if (frequencia == Frequencia.PARCELADA) {
            Column(Modifier.weight(0.6f)) {
                PremiumTextField(
                    value = parcelas,
                    onValueChange = onParcelasChange,
                    label = "x Vezes",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onClick = { }
                )
                erroParc?.let { Text(it, color = Color.Red, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
fun ActionButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
        ) { Text("Salvar", color = Color(0xFF1E2B3E), fontWeight = FontWeight.Bold) }
    }
}