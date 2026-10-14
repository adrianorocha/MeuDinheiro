package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meudinheiro.data.Acerto
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Pessoa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Natureza
import com.meudinheiro.domain.Rateios
import com.meudinheiro.domain.Texto
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PAGINA = 50

private enum class PeriodoVinculo(val rotulo: String) { TUDO("Tudo"), MES("Este mês"), D30("Últimos 30 dias"), D90("Últimos 90 dias") }

/**
 * R68 — "Vincular lançamentos": liga lançamentos JÁ EXISTENTES a uma pessoa. Só cria acertos (controle de quanto a pessoa te
 * deve / você deve); não altera lançamentos, saldo, cartão nem relatórios. Mesmas regras e textos do portal (`VincularModal`);
 * a validação final é `Rateios.vincular` (transação única no repositório).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VincularLancamentosDialog(
    pessoas: List<Pessoa>, pessoaInicial: Int?, despesas: List<Despesa>, acertos: List<Acerto>, contas: List<ContaSaldo>,
    onDismiss: () -> Unit, onConfirmar: (pessoaId: Int, tipo: String, itens: List<Rateios.ItemVinculo>, total: Long, aoErro: (String?) -> Unit) -> Unit
) {
    var pessoa by remember { mutableStateOf(pessoaInicial ?: pessoas.firstOrNull()?.id) }
    var tipo by remember { mutableStateOf(Rateios.A_RECEBER) }
    var busca by remember { mutableStateOf("") }
    var periodo by remember { mutableStateOf(PeriodoVinculo.TUDO) }
    var conta by remember { mutableStateOf<String?>(null) }
    var semVinculo by remember { mutableStateOf(true) }
    var limite by remember { mutableStateOf(PAGINA) }
    var marcados by remember { mutableStateOf(setOf<Long>()) }
    var valores by remember { mutableStateOf(mapOf<Long, String>()) }
    var erro by remember { mutableStateOf<String?>(null) }

    val recebe = tipo == Rateios.A_RECEBER
    val nomePessoa = pessoas.firstOrNull { it.id == pessoa }?.nome ?: "a pessoa"
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    fun brl(c: Long) = FormatoCentavos.brl(c)

    val vinculado = remember(acertos, tipo) {
        acertos.filter { it.tipo == tipo && it.despesaId != null }.groupBy { it.despesaId!! }.mapValues { (_, l) -> l.sumOf { Dinheiro.centavos(it.valor) } }
    }
    val jaDestaPessoa = remember(acertos, pessoa, tipo) { acertos.filter { it.pessoaId == pessoa && it.tipo == tipo && it.despesaId != null }.map { it.despesaId!! }.toSet() }
    val temAcerto = remember(acertos) { acertos.mapNotNull { it.despesaId }.toSet() }

    val filtrados = remember(despesas, busca, periodo, conta, semVinculo, recebe, temAcerto) {
        val q = Texto.normalizar(busca.trim())
        val agora = Calendar.getInstance()
        val ini: Long? = when (periodo) {
            PeriodoVinculo.TUDO -> null
            PeriodoVinculo.MES -> Calendar.getInstance().apply { clear(); set(agora.get(Calendar.YEAR), agora.get(Calendar.MONTH), 1) }.timeInMillis
            PeriodoVinculo.D30 -> agora.timeInMillis - 30L * 86_400_000
            PeriodoVinculo.D90 -> agora.timeInMillis - 90L * 86_400_000
        }
        despesas.asSequence()
            .filter { it.natureza == Natureza.NORMAL && !(recebe && it.tipo == TipoDespesa.CREDITO) }
            .filter { q.isEmpty() || Texto.normalizar("${it.descricao} ${it.categoria}").contains(q) }
            .filter { ini == null || it.data.time >= ini }
            .filter { conta == null || it.conta == conta }
            .filter { !semVinculo || it.id !in temAcerto }
            .sortedWith(compareByDescending<Despesa> { it.data.time }.thenByDescending { it.id })
            .toList()
    }

    /** O que ainda pode ser vinculado (centavos): A_RECEBER respeita o teto; A_PAGAR vai até o valor do lançamento. */
    fun restante(d: Despesa): Long = Dinheiro.centavos(d.valor) - if (recebe) Rateios.comprometidoAReceber(acertos, d.id) else 0L
    fun campoPadrao(d: Despesa) = String.format(Locale("pt", "BR"), "%.2f", Dinheiro.reais(maxOf(restante(d), 0L)))
    fun centavosDoItem(d: Despesa): Long? {
        val t = valores[d.id]
        if (t == null || t.isBlank()) return maxOf(restante(d), 0L)
        return parseValorBR(t)?.takeIf { it.isFinite() }?.let { Dinheiro.centavos(it) }
    }
    val selecionados = despesas.filter { it.id in marcados }
    val total = selecionados.sumOf { centavosDoItem(it) ?: 0L }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(DeepSpaceBlue).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vincular lançamentos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White.copy(0.7f)) }
            }
            Text(
                "Isto não altera saldo nem os lançamentos — só cria o controle de quanto a pessoa te deve/você deve.",
                color = NeonOrange, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.06f), RoundedCornerShape(8.dp)).padding(10.dp)
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text("Pessoa", color = Color.White.copy(0.6f), fontSize = 11.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pessoas.forEach { p ->
                                FilterChip(
                                    selected = pessoa == p.id, onClick = { pessoa = p.id; erro = null }, label = { Text(p.nome, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Rateios.A_RECEBER to "Ela me deve", Rateios.A_PAGAR to "Eu devo a ela").forEach { (t, rotulo) ->
                                FilterChip(
                                    selected = tipo == t, onClick = { tipo = t; marcados = emptySet(); valores = emptyMap(); erro = null },
                                    label = { Text(rotulo, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                                )
                            }
                        }
                        CampoR58(busca, { busca = it; limite = PAGINA }, "Buscar (descrição ou categoria)", maxChars = 60)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PeriodoVinculo.values().forEach { p ->
                                FilterChip(
                                    selected = periodo == p, onClick = { periodo = p; limite = PAGINA }, label = { Text(p.rotulo, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                                )
                            }
                        }
                        if (contas.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = conta == null, onClick = { conta = null; limite = PAGINA }, label = { Text("Todas as contas", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                                )
                                contas.forEach { c ->
                                    FilterChip(
                                        selected = conta == c.conta, onClick = { conta = c.conta; limite = PAGINA }, label = { Text("${c.banco} · ${c.conta}", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = semVinculo, onCheckedChange = { semVinculo = it; limite = PAGINA }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                            Text("Apenas lançamentos ainda sem vínculo", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            "${filtrados.size} lançamento(s)" + if (filtrados.size > limite) " · mostrando $limite" else "",
                            color = Color.White.copy(0.5f), fontSize = 11.sp
                        )
                    }
                }
                items(filtrados.take(limite), key = { it.id }) { d ->
                    val marcado = d.id in marcados
                    val dela = d.id in jaDestaPessoa
                    val sem = restante(d) <= 0
                    Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = marcado, enabled = !dela && !sem, colors = CheckboxDefaults.colors(checkedColor = NeonCyan),
                                onCheckedChange = { on -> marcados = if (on) marcados + d.id else marcados - d.id; erro = null }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(d.descricao, color = Color.White, fontSize = 13.sp, maxLines = 1)
                                Text(
                                    "${fmt.format(d.data)} · ${d.categoria} · ${if (d.tipo == TipoDespesa.CREDITO) "receita" else "despesa"} ${brl(Dinheiro.centavos(d.valor))}",
                                    color = Color.White.copy(0.55f), fontSize = 11.sp
                                )
                                Text("já vinculado ${brl(vinculado[d.id] ?: 0L)}", color = Color.White.copy(0.55f), fontSize = 11.sp)
                            }
                        }
                        if (dela) Text("já vinculado a $nomePessoa", color = NeonOrange, fontSize = 11.sp)
                        else if (sem) Text("sem saldo a vincular", color = NeonOrange, fontSize = 11.sp)
                        else if (marcado) {
                            CampoR58(
                                valores[d.id] ?: campoPadrao(d), { valores = valores + (d.id to it); erro = null }, "Valor a vincular (R$)",
                                modifier = Modifier.width(200.dp), numerico = true, maxChars = 14
                            )
                        }
                    }
                }
                if (filtrados.size > limite) {
                    item { TextButton(onClick = { limite += PAGINA }) { Text("Mostrar mais $PAGINA", color = NeonCyan) } }
                }
                if (filtrados.isEmpty()) item { Text("Nenhum lançamento encontrado.", color = Color.White.copy(0.5f), fontSize = 12.sp) }
            }
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (selecionados.isEmpty()) "Nenhum lançamento selecionado."
                    else "${selecionados.size} selecionado(s) · total ${brl(total)} — " +
                        if (recebe) "$nomePessoa passa a te dever esse valor." else "você passa a dever esse valor a $nomePessoa.",
                    color = Color.White, fontSize = 12.sp
                )
                erro?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) }
                    TextButton(
                        enabled = selecionados.isNotEmpty(),
                        onClick = {
                            val p = pessoa
                            if (p == null) { erro = "Escolha a pessoa."; return@TextButton }
                            val itens = ArrayList<Rateios.ItemVinculo>()
                            for (d in selecionados) {
                                val c = centavosDoItem(d)
                                if (c == null || c <= 0) { erro = "“${d.descricao}”: informe um valor maior que zero."; return@TextButton }
                                itens += Rateios.ItemVinculo(d.id, Dinheiro.reais(c))
                            }
                            onConfirmar(p, tipo, itens, total) { erro = it }
                        }
                    ) { Text(if (selecionados.isEmpty()) "Vincular" else "Vincular ${selecionados.size} (${brl(total)})", color = NeonCyan) }
                }
            }
        }
    }
}
