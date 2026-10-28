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
    onSalvar: (editada: Despesa, modalidade: Financas.Modalidade?, anexos: AnexosEdicao, desvincularDebito: Boolean) -> Unit,
    /** R56 — tags já usadas (sugestões do campo de tags). */
    tagsSugeridas: List<com.meudinheiro.domain.Tags.Usada> = emptyList(),
    /** R60 — reenviar à nuvem um anexo que ficou só no aparelho ("Tentar de novo"). */
    onReenviarAnexo: ((com.meudinheiro.data.Anexo) -> Unit)? = null
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
    // R78 — onde o lançamento está: conta, crédito de um cartão ou compra no débito de um cartão (vínculo `debito:<id>`).
    val cartaoOriginal = despesa.cartaoId?.takeIf { it != 0 }
    val debitoOriginal = if (cartaoOriginal == null) Financas.cartaoDeDebito(despesa) else null
    val localOriginal = if (cartaoOriginal != null) LOCAL_CREDITO else if (debitoOriginal != null) LOCAL_DEBITO else LOCAL_CONTA
    var local by remember { mutableStateOf(localOriginal) }
    var contaNumero by remember { mutableStateOf(despesa.conta) }
    var cartaoId by remember { mutableStateOf(cartaoOriginal ?: debitoOriginal) }
    var pago by remember { mutableStateOf(despesa.pago) }
    var tags by remember { mutableStateOf(despesa.tags) } // R56
    val anexosForm = remember { AnexosFormState() } // R60
    var mostrarCalendario by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }

    val idOriginalDoLocal = if (local == LOCAL_CREDITO) cartaoOriginal else if (local == LOCAL_DEBITO) debitoOriginal else null
    val cartoesDoLocal = remember(cartoes, local) { cartoes.filter { cartaoServeAoLocal(it, local) || it.id == idOriginalDoLocal } }
    val cartaoEscolhido = cartoesDoLocal.firstOrNull { it.id == cartaoId }
    val noCartao = local != LOCAL_CONTA
    val estrangeira = despesa.moedaOriginal != "BRL" && despesa.moedaOriginal.isNotBlank()

    fun validar(): Boolean {
        val e = mutableMapOf<String, String>()
        if (descricao.isBlank()) e["desc"] = "Descrição vazia"
        if ((valorTexto.toLongOrNull() ?: 0L) <= 0L) e["valor"] = "Valor inválido"
        if (categoria.isNullOrBlank()) e["cat"] = "Selecione a categoria"
        if (noCartao && cartaoEscolhido == null) e["origem"] = "Selecione um cartão"
        if (!noCartao && contas.none { it.conta == contaNumero }) e["origem"] = "Selecione uma conta"
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
            cartaoId = if (noCartao) cartaoEscolhido?.id else null,
            // Em cartão o repositório decide o `pago` (crédito: em aberto; débito: pela data) ao mudar de local.
            pago = if (noCartao) despesa.pago else pago,
            valorOriginal = if (mudouValor) valorNovo else despesa.valorOriginal,
            moedaOriginal = if (mudouValor) "BRL" else despesa.moedaOriginal,
            cotacaoNaData = if (mudouValor) 1.0 else despesa.cotacaoNaData,
            tags = com.meudinheiro.domain.Tags.normalizar(tags)
        )
        val modalidade = when (local) {
            LOCAL_CREDITO -> Financas.Modalidade.CREDITO
            LOCAL_DEBITO -> Financas.Modalidade.DEBITO
            else -> null
        }
        onSalvar(
            editada, modalidade, AnexosEdicao(anexosForm.novos.toList(), anexosForm.removidos.toSet()),
            local == LOCAL_CONTA && debitoOriginal != null // virou lançamento comum de conta: solta o vínculo de débito
        )
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
                            Text("Altera só esta parcela; mudar o local (conta/cartão) vale para todas as parcelas.", color = NeonCyan.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

                    // R78 — onde foi lançado: Conta | Cartão de crédito | Cartão de débito (múltiplo escolhe pelo local).
                    SeletorSegmentado(
                        opcoes = buildList {
                            add(LOCAL_CONTA to "Conta")
                            if (cartoes.isNotEmpty() || localOriginal == LOCAL_CREDITO) add(LOCAL_CREDITO to "Cartão crédito")
                            if (cartoes.isNotEmpty() || localOriginal == LOCAL_DEBITO) add(LOCAL_DEBITO to "Cartão débito")
                        },
                        atual = local,
                        onSelect = { novo ->
                            if (novo != local) {
                                if (novo == LOCAL_CONTA) pago = dataMillis <= System.currentTimeMillis() // veio de cartão: pago pela data
                                local = novo
                                val original = if (novo == LOCAL_CREDITO) cartaoOriginal else debitoOriginal
                                val aceitos = cartoes.filter { cartaoServeAoLocal(it, novo) || it.id == original }
                                if (novo != LOCAL_CONTA && aceitos.none { it.id == cartaoId }) cartaoId = aceitos.firstOrNull()?.id
                            }
                        }
                    )
                    if (noCartao) {
                        Dropdown(
                            label = if (local == LOCAL_DEBITO) "Cartão de débito" else "Cartão de crédito",
                            atual = cartaoEscolhido?.let { rotuloCartao(it, cartoes) } ?: "Selecione um cartão...",
                            opcoes = cartoesDoLocal.map { rotuloCartao(it, cartoes) to { cartaoId = it.id } }
                        )
                        if (local == LOCAL_DEBITO) Text(
                            "Compra no débito: sai direto da conta (não usa limite nem entra na fatura).",
                            color = NeonCyan.copy(alpha = 0.9f), fontSize = 12.sp
                        )
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

                    if (local == LOCAL_CONTA) {
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

                    CampoTags(tags = tags, onChange = { tags = it }, sugestoes = tagsSugeridas)

                    SecaoAnexos(existentes = despesa.anexos, estado = anexosForm, reenviar = onReenviarAnexo)

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

private const val LOCAL_CONTA = "CONTA"
private const val LOCAL_CREDITO = "CREDITO"
private const val LOCAL_DEBITO = "DEBITO"

/** Crédito: cartões de crédito/múltiplos; débito: cartões de débito/múltiplos (múltiplo escolhe a modalidade pelo local). */
private fun cartaoServeAoLocal(c: Cartao, local: String): Boolean {
    val t = Financas.tipoDeCartao(c.tipo)
    return when (local) {
        LOCAL_CREDITO -> t != "DEBITO"
        LOCAL_DEBITO -> t == "DEBITO" || t == "MULTIPLO"
        else -> false
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
