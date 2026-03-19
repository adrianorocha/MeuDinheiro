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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.R
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.SuccessAnimation
import com.meudinheiro.funcoes.compartilharComprovante
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.viewModel.CartoesViewModel
import com.meudinheiro.viewModel.CartoesViewModelFactory
import com.meudinheiro.viewModel.ContaSaldoViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Cores Premium Blu Macaw
private val DialogBg = Color(0xFF1B263B)
private val TextColor = Color(0xFFE0E1DD)
//private val NeonGreen = Color(0xFF69F0AE)
//private val NeonCyan = Color(0xFF00E5FF)

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
    cartoesViewModel: CartoesViewModel = viewModel(factory = CartoesViewModelFactory(LocalContext.current)),
    onConfigClick: () -> Unit
) {
    val currentContext = LocalContext.current
    val parentScope = rememberCoroutineScope()

    var exibirFormulario by remember { mutableStateOf(false) }
    var exibirDeposito by remember { mutableStateOf(false) }
    var exibirAssinaturas by remember { mutableStateOf(false) }

    var valorEscaneado by remember { mutableStateOf<Double?>(null) }
    var codigoEscaneado by remember { mutableStateOf("") }

    val cartoes by cartoesViewModel.cartoes.collectAsState()

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
                color = NeonGreen,
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
                color = NeonCyan,
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
                color = Color(0xFFE040FB), // Roxo
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
                text = "Configurar",
                color = Color(0xFFFFD54F), // Amarelo
                modifier = modifierItem,
                onClick = onConfigClick
            )
        }
    }

    if (exibirFormulario) {
        AddDespesaDialog(
            categorias = categorias,
            contaSelecionada = contaSelecionada,
            cartoesDisponiveis = cartoes,
            getPicCategoria = getPicCategoria,
            viewModel = viewModel,
            cartoesViewModel = cartoesViewModel,
            parentScope = parentScope,
            // --- NOVOS PARÂMETROS ---
            valorInicial = valorEscaneado ?: 0.0,
            codigoBarras = codigoEscaneado,
            // -------------------------
            onDismiss = {
                exibirFormulario = false
                // Limpa os dados do scanner após fechar para não lixar o próximo lançamento manual
                valorEscaneado = null
                codigoEscaneado = ""
            }
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
        GerenciarRecorrenciaDialog(viewModel = viewModel, onDismiss = { exibirAssinaturas = false })
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
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, label = "scale")

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Unspecified,
//                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            color = TextColor.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DialogBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) onClick?.invoke()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.5f)) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        prefix = prefix,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NeonCyan
        ),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        interactionSource = interactionSource
    )
}

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
            Text(
                "Novo Depósito",
                style = MaterialTheme.typography.titleLarge,
                color = TextColor,
                fontWeight = FontWeight.Bold
            )
            Text("Para: $contaSelecionada", color = NeonGreen)

            PremiumTextField(
                value = valor,
                onValueChange = { valor = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = "Valor",
                prefix = { Text("R$ ", color = NeonGreen) },
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor),
                    border = BorderStroke(1.dp, Color.White.copy(0.3f))
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
                        containerColor = NeonGreen,
                        contentColor = DialogBg
                    )
                ) { Text("Confirmar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDespesaDialog(
    valorInicial: Double = 0.0,
    codigoBarras: String = "",
    categorias: List<String>,
    contaSelecionada: String,
    // 👇 NOVO: Recebemos a lista de cartões para mostrar no Dropdown
    cartoesDisponiveis: List<CartaoComConta> = emptyList(),
    getPicCategoria: (String) -> String,
    viewModel: ContaSaldoViewModel,
    cartoesViewModel : CartoesViewModel,
    parentScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val currentContext = LocalContext.current
    val scrollState = rememberScrollState()
    val contaAtual by rememberUpdatedState(contaSelecionada.trim())
    var mostrarSucesso by remember { mutableStateOf(false) }

    // --- NOVOS ESTADOS PARA O CARTÃO ---
    var formaPagamento by remember { mutableStateOf("CONTA") } // "CONTA" ou "CARTAO"
    var cartaoSelecionadoId by remember { mutableStateOf<Int?>(cartoesDisponiveis.firstOrNull()?.id) }

    var categoriaSelecionada by remember { mutableStateOf(categorias.firstOrNull()) }
    var frequencia by remember { mutableStateOf(Frequencia.UNICA) }
    var descricao by rememberSaveable { mutableStateOf("") }
    var valorTexto by rememberSaveable { mutableStateOf(if (valorInicial > 0) valorInicial.toString() else "") }
    var numeroParcelas by rememberSaveable { mutableStateOf("2") }
    var moedaSelecionada by remember { mutableStateOf("BRL") }
    var cotacaoTexto by remember { mutableStateOf("1.00") }

    val mostrarCalendario = remember { mutableStateOf(false) }
    val dataMillis = remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }

    var observacao by remember { mutableStateOf(if (codigoBarras.isNotEmpty()) "Boleto: $codigoBarras" else "") }


    // 📍 Filtro Inteligente: Só mostra cartões vinculados à conta que está aberta no topo
    val cartoesFiltrados = remember(contaAtual, cartoesDisponiveis) {
        cartoesDisponiveis.filter { cartao ->
            cartao.numeroConta.trim().equals(contaAtual, ignoreCase = true)
        }
    }

    fun validar(): Boolean {
        val novosErros = mutableMapOf<String, String>()
        if (categoriaSelecionada.isNullOrBlank()) novosErros["cat"] = "Selecione a categoria"
        if (descricao.isBlank()) novosErros["desc"] = "Descrição vazia"
        val v = valorTexto.replace(",", ".").toDoubleOrNull()
        if (v == null || v <= 0.0) novosErros["valor"] = "Valor inválido"
        if (frequencia == Frequencia.PARCELADA && (numeroParcelas.toIntOrNull() ?: 0) < 2) {
            novosErros["parc"] = "Mínimo 2x"
        }
        if (formaPagamento == "CARTAO" && cartaoSelecionadoId == null) {
            novosErros["cartao"] = "Selecione um cartão"
        }
        erros = novosErros
        return novosErros.isEmpty()
    }

    // 📍 Efeito colateral: Se a lista filtrada mudar, atualiza o cartão selecionado
    LaunchedEffect(cartoesFiltrados) {
        if (cartaoSelecionadoId != null && cartoesFiltrados.none { it.id == cartaoSelecionadoId }) {
            cartaoSelecionadoId = cartoesFiltrados.firstOrNull()?.id
        } else if (cartaoSelecionadoId == null) {
            cartaoSelecionadoId = cartoesFiltrados.firstOrNull()?.id
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            PremiumDialogCard(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp)
                    .imePadding()
            ) {
                Crossfade(
                    targetState = mostrarSucesso,
                    animationSpec = tween(500),
                    label = "SuccessAnim"
                ) { sucesso ->
                    if (sucesso) {
                        SuccessAnimation(onFinished = onDismiss)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HeaderSection(contaAtual)

                            // 👇 NOVO: O SELETOR DE CONTA VS CARTÃO
                            FormaPagamentoSelector(
                                atual = formaPagamento,
                                onSelect = { formaPagamento = it }
                            )

                            // 👇 NOVO: SE ESCOLHER CARTÃO, MOSTRA O DROPDOWN
                            if (formaPagamento == "CARTAO") {
                                CartaoDropdownSection(
                                    cartoes = cartoesFiltrados, // 👈 Agora passamos apenas os vinculados
                                    selecionadoId = cartaoSelecionadoId,
                                    onSelect = { cartaoSelecionadoId = it },
                                    erro = erros["cartao"]
                                )
                            }

                            FrequenciaSelector(frequencia) { frequencia = it }

                            ValueSection(
                                moeda = moedaSelecionada,
                                valor = valorTexto,
                                cotacao = cotacaoTexto,
                                onMoedaChange = { moedaSelecionada = it },
                                onValorChange = { valorTexto = it },
                                onCotacaoChange = { cotacaoTexto = it },
                                erroValor = erros["valor"]
                            )

                            PremiumTextField(
                                value = descricao,
                                onValueChange = { descricao = it },
                                label = "Descrição",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { }
                            )
                            erros["desc"]?.let {
                                Text(
                                    it,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 10.sp
                                )
                            }

                            CategoryGridSection(
                                categorias = categorias,
                                getPicCategoria = getPicCategoria,
                                selecionada = categoriaSelecionada,
                                onSelect = { categoriaSelecionada = it },
                                erroCat = erros["cat"]
                            )

                            DateAndInstallmentSection(
                                frequencia = frequencia,
                                dataMillis = dataMillis.value,
                                onOpenCalendar = { mostrarCalendario.value = true },
                                parcelas = numeroParcelas,
                                onParcelasChange = { numeroParcelas = it },
                                erroParc = erros["parc"]
                            )

                            Spacer(Modifier.height(8.dp))

                            ActionButtons(
                                onCancel = onDismiss,
                                onSave = {
                                    if (validar()) {
                                        val vCotacao =
                                            cotacaoTexto.replace(",", ".").toDoubleOrNull() ?: 1.0
                                        val vOriginal = (valorTexto.toDoubleOrNull() ?: 0.0) / 100.0
                                        val vFinalBRL = vOriginal * vCotacao

                                        // 👇 NOVO: Passando o cartaoId se for crédito
                                        val idDoCartao =
                                            if (formaPagamento == "CARTAO") cartaoSelecionadoId else null

                                        val nomeCartaoParaRecibo = cartoesFiltrados.find { it.id == idDoCartao }?.nomeCartao

                                        val desp = Despesa(
                                            //id = 0,
                                            descricao = descricao.trim(),
                                            valor = vFinalBRL,
                                            data = Date(dataMillis.value!!),
                                            categoria = categoriaSelecionada!!,
                                            pic = getPicCategoria(categoriaSelecionada!!),
                                            conta = contaAtual,
                                            tipo = TipoDespesa.DEBITO,
                                            pago = (formaPagamento == "CONTA"), // Se for cartão, não está "pago" ainda, vai pra fatura!
                                            mes = Calendar.getInstance().get(Calendar.MONTH) + 1,
                                            ano = Calendar.getInstance().get(Calendar.YEAR),
                                            cartaoId = idDoCartao // Aqui a mágica acontece
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
                                            if (formaPagamento == "CARTAO" && idDoCartao != null) {
                                                cartoesViewModel.abaterLimite(idDoCartao, vFinalBRL)
                                            }
                                            mostrarSucesso = true
                                            delay(100)
                                            compartilharComprovante(currentContext,desp,
                                                nomeCartaoParaRecibo ,contaAtual)
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
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
// --- SUB-COMPOSABLES REFATORADOS ---

@Composable
fun HeaderSection(conta: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nova Despesa", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
        Text("Conta: $conta", color = NeonCyan.copy(alpha = 0.8f), fontSize = 13.sp)
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
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = NeonCyan.copy(alpha = 0.2f),
                    activeContentColor = NeonCyan,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Color.White.copy(0.6f)
                )
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (atual == freq) FontWeight.Bold else FontWeight.Normal
                )
            }
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
        delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column {
        CurrencySelector(moeda, onMoedaChange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumTextField(
                value = valor,
                onValueChange = { input -> if (input.all { it.isDigit() }) onValorChange(input) },
                label = "Valor ($moeda)",
                modifier = Modifier.weight(1f),
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
        erroValor?.let {
            Text(
                it,
                color = Color(0xFFFF8A80),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// -----------------------------------------------------------
// GRID DE CATEGORIAS - ATUALIZADO: ÍCONES LIVRES (SEM FUNDO)
// -----------------------------------------------------------
@Composable
fun CategoryGridSection(
    categorias: List<String>,
    getPicCategoria: (String) -> String,
    selecionada: String?,
    onSelect: (String) -> Unit,
    erroCat: String?
) {
    val context = LocalContext.current
    val paletaNeon = listOf(
        Color(0xFFFFD54F), Color(0xFF00E5FF), Color(0xFFE040FB),
        Color(0xFFEF5350), Color(0xFF69F0AE), Color(0xFF7986CB), Color(0xFFFF8A65)
    )

    Column {
        Text(
            "Categoria",
            color = Color.White.copy(0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(170.dp), // Aumentei ligeiramente a altura
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categorias) { cat ->
                val isSelected = cat == selecionada
                val corCat = paletaNeon[kotlin.math.abs(cat.hashCode()) % paletaNeon.size]

                val resId = remember(cat) {
                    val picName = getPicCategoria(cat)
                    val id =
                        context.resources.getIdentifier(picName, "drawable", context.packageName)
                    if (id != 0) id else R.drawable.sim_chip_2
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        // Mantivemos um fundo ultra subtil no item inteiro para feedback de clique
                        .background(if (isSelected) corCat.copy(0.12f) else Color.Transparent)
                        .clickable { onSelect(cat) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    // CONTEINER DO ÍCONE - REFATORADO
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(
                                1.5.dp,
                                if (isSelected) corCat else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(resId),
                            contentDescription = cat,
                            tint = Color.Unspecified,
//                            tint = if (isSelected) corCat else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = cat,
                        fontSize = 11.sp, // Aumentei ligeiramente a fonte
                        color = if (isSelected) Color.White else Color.White.copy(0.6f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        erroCat?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp) }
    }
}

@Composable
fun DateAndInstallmentSection(
    frequencia: Frequencia, dataMillis: Long?, onOpenCalendar: () -> Unit,
    parcelas: String, onParcelasChange: (String) -> Unit, erroParc: String?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PremiumTextField(
            value = dataMillis?.let {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(
                    Date(it)
                )
            } ?: "",
            onValueChange = {}, readOnly = true, label = "Data",
            modifier = Modifier.weight(1f),
            onClick = onOpenCalendar,
            trailingIcon = {
                IconButton(onClick = onOpenCalendar) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        null,
                        tint = Color.White.copy(0.6f)
                    )
                }
            }
        )
        if (frequencia == Frequencia.PARCELADA) {
            Column(Modifier.weight(0.6f)) {
                PremiumTextField(
                    value = parcelas,
                    onValueChange = onParcelasChange,
                    label = "Parcelas",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onClick = { }
                )
                erroParc?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp) }
            }
        }
    }
}

@Composable
fun ActionButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, Color.White.copy(0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) { Text("Cancelar") }

        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
        ) { Text("Salvar", color = DialogBg, fontWeight = FontWeight.Black) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormaPagamentoSelector(atual: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val opcoes = listOf("CONTA" to "Débito (Conta)", "CARTAO" to "Crédito (Cartão)")
        opcoes.forEachIndexed { index, (valor, label) ->
            SegmentedButton(
                selected = atual == valor,
                onClick = { onSelect(valor) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = opcoes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = NeonCyan.copy(alpha = 0.2f),
                    activeContentColor = NeonCyan,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Color.White.copy(0.6f)
                )
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (atual == valor) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartaoDropdownSection(
    cartoes: List<CartaoComConta>,
    selecionadoId: Int?,
    onSelect: (Int) -> Unit,
    erro: String?
) {
    var expandido by remember { mutableStateOf(false) }
    val cartaoAtual = cartoes.find { it.id == selecionadoId }

    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expandido,
            onExpandedChange = { expandido = it }
        ) {
            PremiumTextField(
                value = cartaoAtual?.let { "${it.nomeCartao} (Final ${it.finalCartao})" }
                    ?: "Selecione um cartão...",
                onValueChange = {},
                readOnly = true,
                label = "Cartão de Crédito",
                modifier = Modifier.menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                onClick = { expandido = true }
            )

            ExposedDropdownMenu(
                expanded = expandido,
                onDismissRequest = { expandido = false },
                modifier = Modifier.background(DialogBg)
            ) {
                if (cartoes.isEmpty()) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Nenhum cartão cadastrado", color = Color.White.copy(0.5f)) },
                        onClick = { expandido = false }
                    )
                } else {
                    cartoes.forEach { cartao ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    "${cartao.nomeCartao} - Final ${cartao.finalCartao}",
                                    color = Color.White
                                )
                            },
                            onClick = {
                                onSelect(cartao.id)
                                expandido = false
                            }
                        )
                    }
                }
            }
        }
        erro?.let {
            Text(
                it,
                color = Color(0xFFFF8A80),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}