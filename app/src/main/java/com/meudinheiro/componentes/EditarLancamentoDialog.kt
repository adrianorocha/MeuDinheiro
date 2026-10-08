package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import com.meudinheiro.ui.theme.NeonCyan
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val EditBg = Color(0xFF1B263B)

/**
 * Edição completa de um lançamento (conta ou cartão; vale para uma parcela). Mesmas regras do repositório
 * (R11, R42): valor > 0, descrição, categoria, conta/cartão existentes; MÚLTIPLO escolhe crédito/débito ao trocar o cartão.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarLancamentoDialog(
    despesa: Despesa,
    contas: List<ContaSaldo>,
    cartoes: List<Cartao>,
    categorias: List<String>,
    getPicCategoria: (String) -> String,
    onDismiss: () -> Unit,
    onSalvar: (editada: Despesa, modalidade: Financas.Modalidade?) -> Unit
) {
    val listaCategorias = remember(categorias, despesa.categoria) {
        if (categorias.any { it.equals(despesa.categoria, ignoreCase = true) }) categorias else listOf(despesa.categoria) + categorias
    }
    var descricao by remember { mutableStateOf(despesa.descricao) }
    var valorTexto by remember { mutableStateOf(Dinheiro.centavos(despesa.valor).toString()) }
    var tipo by remember { mutableStateOf(despesa.tipo) }
    var dataMillis by remember { mutableStateOf(despesa.data.time) }
    var categoria by remember {
        mutableStateOf<String?>(listaCategorias.firstOrNull { it.equals(despesa.categoria, ignoreCase = true) } ?: despesa.categoria)
    }
    val cartaoOriginal = despesa.cartaoId?.takeIf { it != 0 }
    var origem by remember { mutableStateOf(if (cartaoOriginal != null) "CARTAO" else "CONTA") }
    var contaNumero by remember { mutableStateOf(despesa.conta) }
    var cartaoId by remember { mutableStateOf(cartaoOriginal ?: cartoes.firstOrNull()?.id) }
    var modalidade by remember { mutableStateOf(Financas.Modalidade.CREDITO) }
    var pago by remember { mutableStateOf(despesa.pago) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }

    val cartaoEscolhido = cartoes.firstOrNull { it.id == cartaoId }
    val noCartao = origem == "CARTAO" && cartaoEscolhido != null
    val trocouCartao = noCartao && cartaoId != cartaoOriginal
    val multiplo = trocouCartao && Financas.tipoDeCartao(cartaoEscolhido!!.tipo) == "MULTIPLO"
    val vaiParaDebito = trocouCartao && Financas.modalidadeDaCompra(cartaoEscolhido!!, modalidade) == Financas.Modalidade.DEBITO
    val estrangeira = despesa.moedaOriginal != "BRL" && despesa.moedaOriginal.isNotBlank()

    fun validar(): Boolean {
        val e = mutableMapOf<String, String>()
        if (descricao.isBlank()) e["desc"] = "Descrição vazia"
        if ((valorTexto.toLongOrNull() ?: 0L) <= 0L) e["valor"] = "Valor inválido"
        if (categoria.isNullOrBlank()) e["cat"] = "Selecione a categoria"
        if (origem == "CARTAO" && cartaoEscolhido == null) e["origem"] = "Selecione um cartão"
        if (origem == "CONTA" && contas.none { it.conta == contaNumero }) e["origem"] = "Selecione uma conta"
        erros = e
        return e.isEmpty()
    }

    fun salvar() {
        if (!validar()) return
        val valorNovo = Dinheiro.reais(valorTexto.toLong())
        val mudouValor = valorNovo != Dinheiro.arredondar(despesa.valor)
        val cat = categoria!!
        val editada = despesa.copy(
            descricao = descricao.trim(),
            valor = valorNovo,
            data = Date(dataMillis),
            mes = Financas.mesDe(dataMillis),
            ano = Financas.anoDe(dataMillis),
            categoria = cat,
            pic = if (cat.equals(despesa.categoria, ignoreCase = true)) despesa.pic else getPicCategoria(cat).ifBlank { despesa.pic },
            tipo = tipo,
            conta = if (noCartao) despesa.conta else contaNumero,
            cartaoId = if (noCartao) cartaoId else null,
            pago = if (noCartao) (if (trocouCartao) false else despesa.pago) else pago,
            valorOriginal = if (mudouValor) valorNovo else despesa.valorOriginal,
            moedaOriginal = if (mudouValor) "BRL" else despesa.moedaOriginal,
            cotacaoNaData = if (mudouValor) 1.0 else despesa.cotacaoNaData
        )
        onSalvar(editada, if (multiplo) modalidade else null)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            PremiumDialogCard(
                modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight().padding(vertical = 16.dp).imePadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Editar lançamento", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                        if (Financas.cartaoDeDebito(despesa) == null && com.meudinheiro.domain.LancamentoAcoes.ehGrupoParcelas(despesa.natureza, despesa.grupoId)) {
                            Text("Altera só esta parcela.", color = NeonCyan.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }

                    SeletorSegmentado(
                        opcoes = listOf(TipoDespesa.DEBITO to "Despesa", TipoDespesa.CREDITO to "Receita"),
                        atual = tipo, onSelect = { tipo = it }
                    )

                    PremiumTextField(
                        value = valorTexto,
                        onValueChange = { input -> if (input.all { it.isDigit() } && input.length <= 12) valorTexto = input },
                        label = if (estrangeira) "Valor (R$) — original em ${despesa.moedaOriginal}" else "Valor (R$)",
                        visualTransformation = CurrencyVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onClick = { }
                    )
                    erros["valor"]?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp) }

                    PremiumTextField(value = descricao, onValueChange = { descricao = it }, label = "Descrição", onClick = { })
                    erros["desc"]?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp) }

                    if (cartoes.isNotEmpty()) {
                        SeletorSegmentado(
                            opcoes = listOf("CONTA" to "Conta", "CARTAO" to "Cartão"),
                            atual = origem, onSelect = { origem = it }
                        )
                    }
                    if (origem == "CARTAO") {
                        Dropdown(
                            label = "Cartão",
                            atual = cartaoEscolhido?.let { rotuloCartao(it, cartoes) } ?: "Selecione um cartão...",
                            opcoes = cartoes.map { rotuloCartao(it, cartoes) to { cartaoId = it.id } }
                        )
                        if (multiplo) SeletorSegmentado(
                            opcoes = listOf(Financas.Modalidade.CREDITO to "Crédito", Financas.Modalidade.DEBITO to "Débito"),
                            atual = modalidade, onSelect = { modalidade = it }
                        )
                        if (vaiParaDebito) Text("Compra no débito: sai direto da conta", color = NeonCyan.copy(alpha = 0.9f), fontSize = 12.sp)
                    } else {
                        Dropdown(
                            label = "Conta",
                            atual = contas.firstOrNull { it.conta == contaNumero }?.let { "${it.banco} · ${it.conta}" } ?: "Selecione uma conta...",
                            opcoes = contas.map { "${it.banco} · ${it.conta}" to { contaNumero = it.conta } }
                        )
                    }
                    erros["origem"]?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp) }

                    CategoryGridSection(
                        categorias = listaCategorias,
                        getPicCategoria = getPicCategoria,
                        selecionada = categoria,
                        onSelect = { categoria = it },
                        erroCat = erros["cat"]
                    )

                    PremiumTextField(
                        value = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(dataMillis)),
                        onValueChange = {}, readOnly = true, label = "Data",
                        onClick = { mostrarCalendario = true },
                        trailingIcon = {
                            IconButton(onClick = { mostrarCalendario = true }) {
                                Icon(Icons.Default.CalendarMonth, null, tint = Color.White.copy(0.6f))
                            }
                        }
                    )

                    if (origem == "CONTA") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(if (tipo == TipoDespesa.CREDITO) "Recebido" else "Pago", color = Color.White, modifier = Modifier.weight(1f))
                            Switch(
                                checked = pago, onCheckedChange = { pago = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan.copy(alpha = 0.5f), checkedThumbColor = NeonCyan)
                            )
                        }
                    } else {
                        Text("Compra de cartão é quitada pelo pagamento da fatura.", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    }

                    ActionButtons(onCancel = onDismiss, onSave = ::salvar)
                }
            }
            if (mostrarCalendario) {
                CustomCalendarDialog(
                    onDismiss = { mostrarCalendario = false },
                    onDateSelected = { y, m, d ->
                        // Mantém a hora original: só o dia muda.
                        dataMillis = Calendar.getInstance().apply { timeInMillis = dataMillis; set(y, m, d) }.timeInMillis
                        mostrarCalendario = false
                    }
                )
            }
        }
    }
}

private fun rotuloCartao(c: Cartao, todos: List<Cartao>): String {
    val base = "${c.nome} •••• ${c.finalCartao}"
    val fisico = c.cartaoPrincipalId?.let { pid -> todos.firstOrNull { it.id == pid }?.nome }
    return if (c.cartaoPrincipalId != null) "$base (virtual${fisico?.let { " de $it" } ?: ""})" else base
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SeletorSegmentado(opcoes: List<Pair<T, String>>, atual: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        opcoes.forEachIndexed { i, (valor, rotulo) ->
            SegmentedButton(
                selected = atual == valor,
                onClick = { onSelect(valor) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = opcoes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = NeonCyan.copy(alpha = 0.2f), activeContentColor = NeonCyan,
                    inactiveContainerColor = Color.Transparent, inactiveContentColor = Color.White.copy(0.6f)
                )
            ) { Text(rotulo, fontSize = 12.sp, fontWeight = if (atual == valor) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, atual: String, opcoes: List<Pair<String, () -> Unit>>) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { expandido = it }) {
        PremiumTextField(
            value = atual, onValueChange = {}, readOnly = true, label = label,
            modifier = Modifier.menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            onClick = { expandido = true }
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }, modifier = Modifier.background(EditBg)) {
            if (opcoes.isEmpty()) DropdownMenuItem(text = { Text("Nenhuma opção", color = Color.White.copy(0.5f)) }, onClick = { expandido = false })
            opcoes.forEach { (texto, acao) ->
                DropdownMenuItem(text = { Text(texto, color = Color.White) }, onClick = { acao(); expandido = false })
            }
        }
    }
}
