package com.meudinheiro.componentes

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val perItem = maxWidth / 3

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        icon = R.drawable.deposit,
                        text = "Depositar",
                        accent = MaterialTheme.colorScheme.tertiary,
                        perItemWidth = perItem,
                        onClick = { }
                    )

                    ActionButton(
                        icon = R.drawable.add,
                        text = "Adicionar",
                        accent = MaterialTheme.colorScheme.primary,
                        perItemWidth = perItem,
                        onClick = {
                            val conta = contaSelecionada.trim()
                            if (conta.isBlank()) {
                                Toast.makeText(context, "Selecione uma conta antes de adicionar.", Toast.LENGTH_SHORT).show()
                            } else {
                                exibirFormulario = true
                            }
                        }
                    )

                    ActionButton(
                        icon = R.drawable.sim_chip,
                        text = "Config.",
                        accent = MaterialTheme.colorScheme.secondary,
                        perItemWidth = perItem,
                        onClick = onConfigClick
                    )
                }
            }
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

    // Congela a conta selecionada no momento em que o Dialog abriu
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

    fun normalizeMoneyInput(raw: String): String {
        // Mantém apenas dígitos e separadores . ,
        val filtered = raw.filter { it.isDigit() || it == ',' || it == '.' }
        if (filtered.isBlank()) return ""

        // Permite só um separador decimal
        var separatorCount = 0
        val normalized = buildString {
            filtered.forEach { ch ->
                if (ch == ',' || ch == '.') {
                    if (separatorCount == 0) {
                        append(ch)
                        separatorCount++
                    }
                } else {
                    append(ch)
                }
            }
        }

        // Evita começar com separador (ex: ",50" -> "0,50")
        val first = normalized.firstOrNull()
        return if (first == ',' || first == '.') "0$normalized" else normalized
    }

    fun parseMoneyToDouble(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.replace(",", ".")
        return normalized.toDoubleOrNull()
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

        erroConta = null
        erroCategoria = null
        erroDescricao = null
        erroValor = null
        erroParcelas = null
        erroData = null

        var ok = true

        if (contaFixada.isBlank()) {
            erroConta = "Conta inválida. Selecione uma conta novamente."
            ok = false
        }

        if (cat.isBlank()) {
            erroCategoria = "Selecione uma categoria."
            ok = false
        } else if (categorias.isNotEmpty() && !categorias.contains(cat)) {
            // Evita categoria “fantasma” caso lista venha diferente
            erroCategoria = "Categoria inválida."
            ok = false
        }

        if (desc.isBlank()) {
            erroDescricao = "Descrição obrigatória."
            ok = false
        } else if (desc.length < 3) {
            erroDescricao = "Descrição muito curta."
            ok = false
        } else if (desc.length > 60) {
            erroDescricao = "Máximo de 60 caracteres."
            ok = false
        }

        if (valorDouble == null) {
            erroValor = "Informe um valor válido."
            ok = false
        } else if (valorDouble <= 0.0) {
            erroValor = "O valor deve ser maior que 0."
            ok = false
        } else if (valorDouble.isInfinite() || valorDouble.isNaN()) {
            erroValor = "Valor inválido."
            ok = false
        }

        if (parcelasInt == null) {
            erroParcelas = "Informe as parcelas."
            ok = false
        } else if (parcelasInt < 1) {
            erroParcelas = "Mínimo: 1 parcela."
            ok = false
        } else if (parcelasInt > 360) {
            erroParcelas = "Máximo: 360 parcelas."
            ok = false
        }

        if (data == null) {
            erroData = "Selecione uma data."
            ok = false
        }

        return ok
    }

    // Revalida de forma leve quando o usuário mexe nos campos (evita “travar” UX)
    val podeAdicionar by remember(
        contaFixada,
        categoriaSelecionada,
        descricao,
        valor,
        numeroParcelas,
        dataMillis.value
    ) {
        mutableStateOf(
            contaFixada.isNotBlank()
                    && !categoriaSelecionada.isNullOrBlank()
                    && descricao.trim().length >= 3
                    && (parseMoneyToDouble(valor) ?: 0.0) > 0.0
                    && (parseParcelas(numeroParcelas) ?: 0) >= 1
                    && dataMillis.value != null
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
                // limpa erro ao selecionar
                erroData = null
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Nova Despesa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Text(
                    text = "Conta: $contaFixada",
                    color = Color.DarkGray,
                    fontSize = 13.sp
                )
                if (erroConta != null) {
                    Text(text = erroConta!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tipo == TipoDespesa.DEBITO,
                        onClick = { tipo = TipoDespesa.DEBITO },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Débito") }

                    SegmentedButton(
                        selected = tipo == TipoDespesa.CREDITO,
                        onClick = { tipo = TipoDespesa.CREDITO },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Crédito") }
                }

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
                        supportingText = {
                            if (erroCategoria != null) {
                                Text(erroCategoria!!, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expandido,
                        onDismissRequest = { expandido = false }
                    ) {
                        categorias.forEach { categoria ->
                            DropdownMenuItem(
                                text = { Text(categoria) },
                                onClick = {
                                    categoriaSelecionada = categoria
                                    expandido = false
                                    erroCategoria = null
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = descricao,
                    onValueChange = {
                        descricao = it
                        if (erroDescricao != null) erroDescricao = null
                    },
                    isError = erroDescricao != null,
                    label = { Text("Descrição") },
                    singleLine = true,
                    supportingText = {
                        if (erroDescricao != null) {
                            Text(erroDescricao!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("${descricao.trim().length}/60", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = valor,
                        onValueChange = {
                            valor = normalizeMoneyInput(it)
                            if (erroValor != null) erroValor = null
                        },
                        isError = erroValor != null,
                        label = { Text("Valor", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            if (erroValor != null) Text(erroValor!!, color = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = numeroParcelas,
                        onValueChange = { novo ->
                            // só dígitos, evita caracteres que quebram toInt
                            val digitsOnly = novo.filter { it.isDigit() }
                            numeroParcelas = if (digitsOnly.isBlank()) "" else digitsOnly.take(3)
                            if (erroParcelas != null) erroParcelas = null
                        },
                        isError = erroParcelas != null,
                        label = { Text("Parcelas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            if (erroParcelas != null) Text(erroParcelas!!, color = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 130.dp)
                    )
                }

                OutlinedTextField(
                    value = dataMillis.value?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
                    } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    isError = erroData != null,
                    label = { Text("Data") },
                    placeholder = { Text("Selecionar data", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        IconButton(onClick = { mostrarCalendario.value = true }) {
                            Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = "Selecionar data")
                        }
                    },
                    supportingText = {
                        if (erroData != null) Text(erroData!!, color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancelar") }

                    Button(
                        enabled = podeAdicionar,
                        onClick = {
                            if (!validarTudo()) {
                                Toast.makeText(context, "Revise os campos destacados.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

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

                            Log.d(
                                "AddDespesaDialog",
                                "Inserindo despesa conta=$contaFixada valor=$valorDouble tipo=$tipo parcelas=$parcelasInt"
                            )

                            if (parcelasInt > 1) {
                                viewModel.adicionarDespesaParcelada(novaDespesa, parcelasInt, dataFinal)
                            } else {
                                viewModel.adicionarDespesa(novaDespesa)
                            }

                            // limpa e fecha
                            descricao = ""
                            valor = ""
                            numeroParcelas = "1"
                            categoriaSelecionada = null
                            dataMillis.value = null
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
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

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        label = "actionScale"
    )

    val bg by animateColorAsState(
        targetValue = if (pressed)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        label = "actionBg"
    )

    val border by animateColorAsState(
        targetValue = if (pressed)
            accent.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        label = "actionBorder"
    )

    // Em telas bem estreitas, reduz um pouco a fonte automaticamente
    val compactText = perItemWidth < 120.dp
    val textStyle = if (compactText) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    Surface(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 76.dp) // sem maxHeight, para respeitar fonte maior (acessibilidade)
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
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
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (pressed) 0.30f else 0.22f),
                                accent.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = text,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.size(8.dp))

            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (pressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.07f))
            )
        }
    }
}
