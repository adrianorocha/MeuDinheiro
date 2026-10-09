package com.meudinheiro.componentes

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.compose.ui.graphics.Brush
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
import com.meudinheiro.domain.CartoesUi
import com.meudinheiro.domain.Financas
import com.meudinheiro.funcoes.SuccessAnimation
import com.meudinheiro.funcoes.formatarMoedaBR
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
    var exibirAjuste by remember { mutableStateOf(false) }

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
                text = "Assinaturas",
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

    // R42: ajuste de saldo para igualar ao banco.
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.TextButton(onClick = {
            if (contaSelecionada.isBlank()) Toast.makeText(currentContext, "Selecione uma conta", Toast.LENGTH_SHORT).show()
            else exibirAjuste = true
        }) { Text("Ajustar saldo da conta", color = NeonCyan.copy(alpha = 0.8f), fontSize = 12.sp) }
    }

    if (exibirAjuste) {
        AjusteSaldoDialog(contaSelecionada = contaSelecionada, viewModel = viewModel, onDismiss = { exibirAjuste = false })
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
            valorInicial = valorEscaneado ?: 0.0,
            codigoBarras = codigoEscaneado,
            onDismiss = {
                exibirFormulario = false
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
                // 🚀 Adicionado Glassmorphism aos botões do topo também!
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.03f),
                        0.5f to Color.White.copy(alpha = 0.08f),
                        1.0f to Color.White.copy(alpha = 0.03f)
                    ),
                    RoundedCornerShape(18.dp)
                )
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Unspecified,
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

/** R42 — confere o saldo do sistema com o do banco e lança um AJUSTE (não conta como receita/despesa). */
@Composable
fun AjusteSaldoDialog(contaSelecionada: String, viewModel: ContaSaldoViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val contas by viewModel.contaSaldo.observeAsState(emptyList())
    val conta = contas.firstOrNull { it.conta.trim().equals(contaSelecionada.trim(), ignoreCase = true) }
    val exigirBio by remember { com.meudinheiro.funcoes.UserPreferences(ctx).biometriaLancarFlow }.collectAsState(initial = true)
    var saldoRealTxt by rememberSaveable { mutableStateOf("") }
    var observacao by rememberSaveable { mutableStateOf("") }

    val saldoSistema = conta?.saldo ?: 0.0
    val saldoReal = com.meudinheiro.domain.Financas.parseSaldoInformado(saldoRealTxt)
    val ajuste = saldoReal?.let { com.meudinheiro.domain.Financas.calcularAjusteSaldo(saldoSistema, it) }

    Dialog(onDismissRequest = onDismiss) {
        PremiumDialogCard {
            Text("Ajustar saldo", style = MaterialTheme.typography.titleLarge, color = TextColor, fontWeight = FontWeight.Bold)
            Text(conta?.let { "${it.banco} · ${it.conta}" } ?: contaSelecionada, color = NeonCyan)
            Text("Saldo no sistema: " + formatarMoedaBR(saldoSistema, false), color = TextColor)

            PremiumTextField(
                value = saldoRealTxt,
                onValueChange = { saldoRealTxt = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' } },
                label = "Saldo real no banco",
                prefix = { Text("R$ ", color = NeonCyan) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                onClick = { }
            )
            PremiumTextField(value = observacao, onValueChange = { observacao = it.take(60) }, label = "Observação (opcional)", onClick = { })

            val previa = when {
                saldoRealTxt.isBlank() -> "Informe o saldo que aparece no extrato do banco."
                saldoReal == null -> "Valor inválido."
                ajuste == null -> "O saldo já confere com o banco. Nada a ajustar."
                else -> (if (ajuste.tipo == TipoDespesa.CREDITO) "Diferença: +" else "Diferença: -") +
                    formatarMoedaBR(ajuste.valor, false) +
                    (if (ajuste.tipo == TipoDespesa.CREDITO) " (entra na conta)" else " (sai da conta)")
            }
            Text(
                previa,
                color = if (ajuste == null) Color.White.copy(0.6f) else if (ajuste.tipo == TipoDespesa.CREDITO) NeonGreen else Color(0xFFFF8A80),
                fontSize = 13.sp
            )
            Text(
                "O ajuste não conta como receita nem despesa; só acerta o saldo. Para desfazer, exclua o lançamento.",
                color = Color.White.copy(0.45f), fontSize = 11.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                BotaGlassmorphic(
                    texto = "Cancelar", corAcento = Color.White.copy(alpha = 0.6f), hapticType = "impacto",
                    animateIdleJump = false, modifier = Modifier.weight(1f), onClick = onDismiss
                )
                BotaGlassmorphic(
                    texto = "Ajustar", corAcento = NeonCyan, hapticType = "sucesso",
                    animateIdleJump = false, modifier = Modifier.weight(1f),
                    onClick = {
                        if (conta == null || saldoReal == null) {
                            Toast.makeText(ctx, "Informe o saldo real no banco.", Toast.LENGTH_SHORT).show()
                        } else if (ajuste == null) {
                            Toast.makeText(ctx, "O saldo já confere com o banco.", Toast.LENGTH_SHORT).show()
                        } else {
                            autenticarParaLancar(ctx, exigirBio, "Confirmar ajuste de saldo", "Autentique para ajustar o saldo de ${conta.banco}") {
                                viewModel.ajustarSaldoConta(conta.conta, saldoReal, observacao)
                                Toast.makeText(ctx, "Saldo ajustado", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    }
                )
            }
        }
    }
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
    val depositoContext = LocalContext.current
    val exigirBioDeposito by remember { com.meudinheiro.funcoes.UserPreferences(depositoContext).biometriaLancarFlow }.collectAsState(initial = true)
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

            // 🚀 BotaGlassmorphic IMPLEMENTADOS AQUI!
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 16.dp) // Um respiro extra
            ) {
                BotaGlassmorphic(
                    texto = "Cancelar",
                    corAcento = Color.White.copy(alpha = 0.6f),
                    hapticType = "impacto",
                    animateIdleJump = false, // 🚀 Sem pulo no formulário
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )

                BotaGlassmorphic(
                    texto = "Confirmar",
                    corAcento = NeonGreen,
                    hapticType = "sucesso",
                    animateIdleJump = false, // 🚀 Sem pulo no formulário
                    modifier = Modifier.weight(1f),
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
                                pago = dataMillis.value <= System.currentTimeMillis(), // depósito futuro só entra no saldo na data
                                mes = Calendar.getInstance().get(Calendar.MONTH) + 1,
                                ano = Calendar.getInstance().get(Calendar.YEAR)
                            )
                            autenticarParaLancar(depositoContext, exigirBioDeposito, "Confirmar depósito", "Autentique para lançar o depósito") {
                                viewModel.adicionarDespesa(dep)
                                parentScope.launch {
                                    delay(200)
                                    viewModel.carregarResumoFinanceiro()
                                }
                                onDismiss()
                            }
                        }
                    }
                )
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
    cartoesDisponiveis: List<CartaoComConta> = emptyList(),
    getPicCategoria: (String) -> String,
    viewModel: ContaSaldoViewModel,
    cartoesViewModel: CartoesViewModel,
    parentScope: CoroutineScope,
    onDismiss: () -> Unit,
    historico: List<Despesa> = emptyList(),
    /** "CONTA" (padrão) ou "CARTAO": a tela de cartões abre já no cartão. */
    formaPagamentoInicial: String = "CONTA",
    /** Cartão pré-selecionado (ex.: o filtrado/focado na tela de cartões). */
    cartaoInicialId: Int? = null
) {
    val currentContext = LocalContext.current
    val scrollState = rememberScrollState()
    val contaAtual by rememberUpdatedState(contaSelecionada.trim())
    var mostrarSucesso by remember { mutableStateOf(false) }

    var formaPagamento by remember { mutableStateOf(formaPagamentoInicial) } // "CONTA" ou "CARTAO"
    var cartaoSelecionadoId by remember {
        mutableStateOf<Int?>(
            cartaoInicialId?.takeIf { id -> cartoesDisponiveis.any { it.id == id } } ?: cartoesDisponiveis.firstOrNull()?.id
        )
    }
    // Compras de todos os cartões: saldo/disponível do GRUPO por opção do seletor e aviso de limite.
    val comprasDosCartoes by cartoesViewModel.todasDespesas.collectAsState()
    // R42: só cartão MÚLTIPLO deixa escolher; CRÉDITO/DÉBITO são fixos pelo tipo do cartão.
    var modalidadePedida by remember { mutableStateOf(Financas.Modalidade.CREDITO) }

    var categoriaSelecionada by remember { mutableStateOf(categorias.firstOrNull()) }
    var frequencia by remember { mutableStateOf(Frequencia.UNICA) }
    var descricao by rememberSaveable { mutableStateOf("") }
    var valorTexto by rememberSaveable { mutableStateOf(if (valorInicial > 0) Math.round(valorInicial * 100).toString() else "") }
    var numeroParcelas by rememberSaveable { mutableStateOf("2") }
    var parcelaAtualTexto by rememberSaveable { mutableStateOf("1") }
    var moedaSelecionada by remember { mutableStateOf("BRL") }
    var cotacaoTexto by remember { mutableStateOf("1.00") }

    val mostrarCalendario = remember { mutableStateOf(false) }
    val dataMillis = remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    val exigirBio by remember { com.meudinheiro.funcoes.UserPreferences(currentContext).biometriaLancarFlow }.collectAsState(initial = true)
    var erros by remember { mutableStateOf(mapOf<String, String>()) }

    // R21: lançamentos recentes (1 toque para repetir) e categoria sugerida pelo histórico.
    val recentes = remember(historico) {
        historico.filter { it.natureza == com.meudinheiro.domain.Natureza.NORMAL && it.tipo == TipoDespesa.DEBITO }
            .sortedByDescending { it.dataMs }
            .distinctBy { com.meudinheiro.domain.Texto.normalizar(it.descricao.replace(Regex("\\(\\d+/\\d+\\)"), "")) }
            .take(6)
    }
    val categoriaSugerida = remember(descricao, historico, categorias) {
        if (descricao.trim().length < 3) null
        else com.meudinheiro.domain.Analises.sugerirCategoria(descricao, historico)
            ?.let { s -> categorias.firstOrNull { it.equals(s, ignoreCase = true) } }
    }

    var observacao by remember { mutableStateOf(if (codigoBarras.isNotEmpty()) "Boleto: $codigoBarras" else "") }

    // R49: escanear cupom fiscal / boleto / PIX preenche valor (e descrição/data) para o usuário revisar.
    var mostrarScannerCupom by remember { mutableStateOf(false) }
    val permissaoCameraCupom = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) mostrarScannerCupom = true
        else Toast.makeText(currentContext, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
    }

    val cartoesFiltrados = remember(contaAtual, cartoesDisponiveis) {
        cartoesDisponiveis.filter { cartao ->
            cartao.numeroConta.trim().equals(contaAtual, ignoreCase = true)
        }
    }

    val cartaoEscolhido = cartoesFiltrados.find { it.id == cartaoSelecionadoId }
    val tipoCartaoEscolhido = cartaoEscolhido?.let { Financas.tipoDeCartao(it.tipo) }
    val cartaoMultiplo = formaPagamento == "CARTAO" && tipoCartaoEscolhido == "MULTIPLO"
    val compraNoDebito = formaPagamento == "CARTAO" && cartaoEscolhido != null &&
        Financas.modalidadeDaCompra(cartaoEscolhido.paraCartao(), modalidadePedida) == Financas.Modalidade.DEBITO

    // Aviso (não bloqueante): compra de crédito acima do limite próprio / disponível do grupo.
    val avisoLimite = if (formaPagamento == "CARTAO" && cartaoEscolhido != null) {
        val valorCompra = ((valorTexto.toDoubleOrNull() ?: 0.0) / 100.0) * (cotacaoTexto.replace(",", ".").toDoubleOrNull() ?: 1.0)
        val dominio = cartoesDisponiveis.map { it.paraCartao() }
        CartoesUi.avisoDeLimite(
            Financas.saldoDoCartao(cartaoEscolhido.paraCartao(), dominio, comprasDosCartoes), valorCompra, compraNoDebito
        )
    } else null

    // Débito sai direto da conta: não parcela.
    LaunchedEffect(compraNoDebito) {
        if (compraNoDebito && frequencia == Frequencia.PARCELADA) frequencia = Frequencia.UNICA
    }

    fun validar(): Boolean {
        val novosErros = mutableMapOf<String, String>()
        if (categoriaSelecionada.isNullOrBlank()) novosErros["cat"] = "Selecione a categoria"
        if (descricao.isBlank()) novosErros["desc"] = "Descrição vazia"
        val v = valorTexto.replace(",", ".").toDoubleOrNull()
        if (v == null || v <= 0.0) novosErros["valor"] = "Valor inválido"
        if (frequencia == Frequencia.PARCELADA && (numeroParcelas.toIntOrNull() ?: 0) < 2) {
            novosErros["parc"] = "Mínimo 2x"
        } else if (frequencia == Frequencia.PARCELADA && formaPagamento == "CARTAO") {
            val n = numeroParcelas.toIntOrNull() ?: 0
            val k = parcelaAtualTexto.toIntOrNull() ?: 0
            if (k !in 1..n) novosErros["parcAtual"] = "Entre 1 e $n"
        }
        if (formaPagamento == "CARTAO" && cartaoSelecionadoId == null) {
            novosErros["cartao"] = "Selecione um cartão"
        }
        erros = novosErros
        return novosErros.isEmpty()
    }

    LaunchedEffect(cartoesFiltrados) {
        if (cartaoSelecionadoId != null && cartoesFiltrados.none { it.id == cartaoSelecionadoId }) {
            cartaoSelecionadoId = cartoesFiltrados.firstOrNull()?.id
        } else if (cartaoSelecionadoId == null) {
            cartaoSelecionadoId = cartoesFiltrados.firstOrNull()?.id
        }
    }
    if (mostrarScannerCupom) {
        Dialog(
            onDismissRequest = { mostrarScannerCupom = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ScannerCupomScreen(
                    onResult = { c ->
                        valorTexto = c.valorCentavos.toString()
                        moedaSelecionada = "BRL"
                        cotacaoTexto = "1.00"
                        if (descricao.isBlank() && !c.descricao.isNullOrBlank()) descricao = c.descricao
                        c.dataMs?.let { dataMillis.value = it }
                        erros = erros - "valor" - "desc"
                        mostrarScannerCupom = false
                    },
                    onClose = { mostrarScannerCupom = false }
                )
            }
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

                            FormaPagamentoSelector(
                                atual = formaPagamento,
                                onSelect = { formaPagamento = it }
                            )

                            if (formaPagamento == "CARTAO") {
                                CartaoDropdownSection(
                                    cartoes = cartoesFiltrados,
                                    selecionadoId = cartaoSelecionadoId,
                                    onSelect = { cartaoSelecionadoId = it },
                                    erro = erros["cartao"],
                                    todosCartoes = cartoesDisponiveis,
                                    despesas = comprasDosCartoes
                                )
                                avisoLimite?.let {
                                    Text(it, color = Color(0xFFFFB74D), fontSize = 12.sp)
                                }
                                if (cartaoMultiplo) {
                                    ModalidadeSelector(modalidadePedida) { modalidadePedida = it }
                                }
                                if (compraNoDebito) {
                                    Text(
                                        "Compra no débito: sai direto da conta",
                                        color = NeonCyan.copy(alpha = 0.9f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            FrequenciaSelector(frequencia, permitirParcelada = !compraNoDebito) { frequencia = it }

                            if (recentes.isNotEmpty() && descricao.isBlank() && valorTexto.isBlank()) {
                                Text("Recentes", color = Color.White.copy(0.5f), fontSize = 11.sp)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    recentes.forEach { r ->
                                        androidx.compose.material3.AssistChip(
                                            onClick = {
                                                descricao = r.descricao.replace(Regex("\\s*\\(\\d+/\\d+\\)"), "").trim()
                                                valorTexto = Math.round(r.valor * 100).toString()
                                                categorias.firstOrNull { it.equals(r.categoria, ignoreCase = true) }?.let { categoriaSelecionada = it }
                                            },
                                            label = { Text(r.descricao.take(16), fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }

                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                                        currentContext, android.Manifest.permission.CAMERA
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (ok) mostrarScannerCupom = true
                                    else permissaoCameraCupom.launch(android.Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp)
                                )
                                Text("  Escanear cupom", color = NeonCyan, fontSize = 13.sp)
                            }

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

                            if (categoriaSugerida != null && !categoriaSugerida.equals(categoriaSelecionada, ignoreCase = true)) {
                                androidx.compose.material3.AssistChip(
                                    onClick = { categoriaSelecionada = categoriaSugerida },
                                    label = { Text("Sugestão: $categoriaSugerida — usar", fontSize = 12.sp) }
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
                                erroParc = erros["parc"],
                                mostrarParcelaAtual = formaPagamento == "CARTAO",
                                parcelaAtual = parcelaAtualTexto,
                                onParcelaAtualChange = { parcelaAtualTexto = it.filter(Char::isDigit).take(3) },
                                erroParcelaAtual = erros["parcAtual"]
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

                                        val idDoCartaoParaSalvar: Int? =
                                            if (formaPagamento == "CARTAO") {
                                                cartaoSelecionadoId
                                                    ?: cartoesFiltrados.firstOrNull()?.id
                                            } else {
                                                null
                                            }

                                        android.util.Log.d(
                                            "CADASTRO_DESPESA",
                                            "Forma Pgto: $formaPagamento | Cartão ID capturado: $idDoCartaoParaSalvar"
                                        )

                                        val modalidadeEnviada = if (formaPagamento == "CARTAO") modalidadePedida else null

                                        val nomeCartaoParaRecibo =
                                            cartoesFiltrados.find { it.id == idDoCartaoParaSalvar }?.nomeCartao

                                        val desp = Despesa(
                                            descricao = descricao.trim(),
                                            valor = vFinalBRL,
                                            data = Date(dataMillis.value!!),
                                            categoria = categoriaSelecionada!!,
                                            pic = getPicCategoria(categoriaSelecionada!!),
                                            conta = contaAtual,
                                            tipo = TipoDespesa.DEBITO,
                                            pago = (formaPagamento == "CONTA" && dataMillis.value!! <= System.currentTimeMillis()),
                                            valorOriginal = vOriginal,
                                            moedaOriginal = moedaSelecionada,
                                            cotacaoNaData = vCotacao,
                                            mes = Calendar.getInstance().get(Calendar.MONTH) + 1,
                                            ano = Calendar.getInstance().get(Calendar.YEAR),
                                            cartaoId = idDoCartaoParaSalvar
                                        )

                                        autenticarParaLancar(currentContext, exigirBio, "Confirmar lançamento", "Autentique para lançar esta despesa") {
                                        parentScope.launch {
                                            when (frequencia) {
                                                Frequencia.UNICA -> viewModel.adicionarDespesa(desp, modalidadeEnviada)
                                                Frequencia.PARCELADA -> viewModel.adicionarDespesaParcelada(
                                                    desp,
                                                    numeroParcelas.toInt(),
                                                    dataMillis.value!!,
                                                    modalidadeEnviada,
                                                    if (formaPagamento == "CARTAO") parcelaAtualTexto.toIntOrNull() ?: 1 else 1
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
                                            compartilharComprovante(
                                                currentContext,
                                                desp,
                                                nomeCartaoParaRecibo,
                                                contaAtual
                                            )
                                            onDismiss()
                                        }
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
fun FrequenciaSelector(atual: Frequencia, permitirParcelada: Boolean = true, onSelect: (Frequencia) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val opcoes = listOf(
            Frequencia.UNICA to "Única",
            Frequencia.PARCELADA to "Parcelada",
            Frequencia.FIXA to "Fixa"
        ).filter { permitirParcelada || it.first != Frequencia.PARCELADA }
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
            modifier = Modifier.height(170.dp),
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
                        .background(if (isSelected) corCat.copy(0.12f) else Color.Transparent)
                        .clickable { onSelect(cat) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = cat,
                        fontSize = 11.sp,
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
    parcelas: String, onParcelasChange: (String) -> Unit, erroParc: String?,
    mostrarParcelaAtual: Boolean = false, parcelaAtual: String = "1",
    onParcelaAtualChange: (String) -> Unit = {}, erroParcelaAtual: String? = null
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
    if (frequencia == Frequencia.PARCELADA && mostrarParcelaAtual) {
        Column {
            PremiumTextField(
                value = parcelaAtual,
                onValueChange = onParcelaAtualChange,
                label = "Parcela atual (1 = compra nova)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                onClick = { }
            )
            erroParcelaAtual?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp) }
            val k = parcelaAtual.toIntOrNull() ?: 1
            if (k > 1) {
                Text(
                    "Valor = total da compra; só as parcelas $k a ${parcelas.ifBlank { "N" }} serão lançadas, a primeira na data informada.",
                    color = Color.White.copy(0.5f), fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ActionButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    // 🚀 BotaGlassmorphic IMPLEMENTADOS AQUI TAMBÉM!
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        BotaGlassmorphic(
            texto = "Cancelar",
            corAcento = Color.White.copy(alpha = 0.6f),
            hapticType = "impacto",
            animateIdleJump = false, // 🚀 Sem pulo no formulário
            modifier = Modifier.weight(1f),
            onClick = onCancel
        )

        BotaGlassmorphic(
            texto = "Salvar",
            corAcento = NeonCyan,
            hapticType = "sucesso",
            animateIdleJump = false, // 🚀 Sem pulo no formulário
            modifier = Modifier.weight(1f),
            onClick = onSave
        )
    }
}

/** R42: escolha Crédito/Débito, exibida só para cartão MÚLTIPLO. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalidadeSelector(atual: Financas.Modalidade, onSelect: (Financas.Modalidade) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val opcoes = listOf(Financas.Modalidade.CREDITO to "Crédito", Financas.Modalidade.DEBITO to "Débito")
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
                Text(label, fontSize = 12.sp, fontWeight = if (atual == valor) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormaPagamentoSelector(atual: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val opcoes = listOf("CONTA" to "Conta", "CARTAO" to "Cartão")
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
    erro: String?,
    /** Todos os cartões (para achar o físico dos virtuais e o grupo no cálculo do disponível). */
    todosCartoes: List<CartaoComConta> = cartoes,
    /** Compras dos cartões, para o "Disponível" por opção (R41). Vazio = não mostra valores. */
    despesas: List<Despesa> = emptyList()
) {
    var expandido by remember { mutableStateOf(false) }
    val cartaoAtual = cartoes.find { it.id == selecionadoId }
    val dominio = remember(todosCartoes) { todosCartoes.map { it.paraCartao() } }

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
                label = "Cartão",
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
                                Column {
                                    Text("${cartao.nomeCartao} •••• ${cartao.finalCartao}", color = Color.White)
                                    cartao.cartaoPrincipalId?.let { pid ->
                                        val fisico = todosCartoes.firstOrNull { it.id == pid }?.nomeCartao ?: "cartão físico"
                                        Text("Virtual de $fisico", color = Color(0xFFB388FF), fontSize = 11.sp)
                                    }
                                    if (Financas.tipoDeCartao(cartao.tipo) == "DEBITO") {
                                        Text("Débito: sai da conta", color = Color.White.copy(0.6f), fontSize = 11.sp)
                                    } else {
                                        val s = remember(cartao, dominio, despesas) {
                                            Financas.saldoDoCartao(cartao.paraCartao(), dominio, despesas)
                                        }
                                        Text(
                                            "Disponível " + formatarMoedaBR(s.disponivelGrupo, false) +
                                                (s.limiteProprio?.let { " · limite próprio " + formatarMoedaBR(it, false) } ?: ""),
                                            color = NeonCyan.copy(0.8f), fontSize = 11.sp
                                        )
                                    }
                                }
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