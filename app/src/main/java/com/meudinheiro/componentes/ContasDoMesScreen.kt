package com.meudinheiro.componentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.ContasMes
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class DialogoContas {
    object Nova : DialogoContas()
    data class Baixar(val linha: ContasMes.Linha) : DialogoContas()
    data class Alterar(val linha: ContasMes.Linha) : DialogoContas()
    data class Provisionar(val linha: ContasMes.Linha) : DialogoContas()
    data class Desabilitar(val linha: ContasMes.Linha) : DialogoContas()
}

private val ROTULO_ESTADO = mapOf(
    ContasMes.Estado.PAGA to "Paga", ContasMes.Estado.VENCIDA to "Vencida", ContasMes.Estado.PENDENTE to "Pendente",
    ContasMes.Estado.PROVISIONADA to "Provisionada", ContasMes.Estado.NAO_LANCADA to "Não lançada", ContasMes.Estado.DESABILITADA to "Desabilitada"
)

private fun corEstado(e: ContasMes.Estado) = when (e) {
    ContasMes.Estado.PAGA -> NeonGreen
    ContasMes.Estado.VENCIDA -> NeonRed
    ContasMes.Estado.PENDENTE -> NeonOrange
    else -> Color.White.copy(0.6f)
}

/** "dd/MM/aaaa" -> meio-dia local; inválido -> null. */
private fun dataDigitada(texto: String): Long? {
    val f = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }
    val d = runCatching { f.parse(texto.trim()) }.getOrNull() ?: return null
    return Calendar.getInstance().apply { time = d; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
}

private fun dataParaCampo(ms: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(ms))

/**
 * R108 — Contas do mês: luz, água, telefone, gás… provisione os próximos meses, altere valor e data, dê baixa escolhendo a conta de onde
 * o dinheiro sai e desabilite quando quiser. Funciona sobre as recorrências (R16): sem formato novo de dados.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContasDoMesScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    var regras by remember { mutableStateOf<List<DespesaFixa>>(emptyList()) }
    var versao by remember { mutableIntStateOf(0) }
    LaunchedEffect(versao, despesas) { regras = repository.obterTodasRecorrencias() }

    val agora = remember(versao, despesas) { System.currentTimeMillis() }
    val hoje = remember { Calendar.getInstance() }
    var mes by remember { mutableIntStateOf(hoje.get(Calendar.MONTH) + 1) }
    var ano by remember { mutableIntStateOf(hoje.get(Calendar.YEAR)) }
    var dialogo by remember { mutableStateOf<DialogoContas?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var avisoOk by remember { mutableStateOf<String?>(null) }

    val resumo = remember(despesas, regras, mes, ano, agora) { ContasMes.contasDoMes(despesas, regras, mes, ano, agora) }
    fun moeda(v: Double) = formatarMoedaBR(v, isPrivate)
    fun moedaC(c: Long) = formatarMoedaBR(Dinheiro.reais(c), isPrivate)
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val nomeMes = remember(mes, ano) { SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR")).format(Calendar.getInstance().apply { clear(); set(ano, mes - 1, 1) }.time).replaceFirstChar { it.uppercase() } }

    fun executar(ok: String, bloco: suspend () -> String?) {
        scope.launch {
            aviso = null; avisoOk = null
            try {
                val extra = bloco()
                avisoOk = extra ?: ok
                versao++
            } catch (e: com.meudinheiro.repository.RegraFinanceiraException) {
                aviso = e.message
            } catch (e: Exception) {
                aviso = e.message ?: "Não foi possível concluir."
            }
        }
    }

    fun deslocar(d: Int) {
        val idx = ano * 12 + (mes - 1) + d
        mes = idx % 12 + 1; ano = idx / 12
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Contas do mês", color = TextoClaroR58, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaroR58) } },
                actions = { IconButton(onClick = { dialogo = DialogoContas.Nova }, enabled = contas.isNotEmpty()) { Icon(Icons.Default.Add, "Nova conta mensal", tint = NeonCyan) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Luz, água, telefone, gás… Provisione os próximos meses, altere valor e data quando precisar e dê baixa escolhendo a conta de onde o dinheiro sai.",
                    color = Color.White.copy(0.6f), fontSize = 12.sp
                )
            }
            aviso?.let { msg -> item { Text(msg, color = NeonRed, fontSize = 13.sp) } }
            avisoOk?.let { msg -> item { Text(msg, color = NeonGreen, fontSize = 13.sp) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { deslocar(-1) }) { Icon(Icons.Default.ChevronLeft, "Mês anterior", tint = NeonCyan) }
                    Text(nomeMes, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { deslocar(1) }) { Icon(Icons.Default.ChevronRight, "Próximo mês", tint = NeonCyan) }
                }
            }
            if (resumo.linhas.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Previsto no mês", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Text(moedaC(resumo.previstoCentavos), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pago", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Text(moedaC(resumo.pagoCentavos), color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("A pagar", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Text(moedaC(resumo.aPagarCentavos), color = NeonOrange, fontWeight = FontWeight.Bold)
                    }
                    if (resumo.vencidas > 0) Text("${resumo.vencidas} vencida(s)", color = NeonRed, fontSize = 12.sp)
                }
            }
            if (resumo.linhas.isEmpty()) item {
                Text(
                    "Nenhuma conta neste mês. Cadastre luz, água, telefone, gás… uma vez (botão +); todo mês elas aparecem aqui.",
                    color = Color.White.copy(0.7f), fontSize = 13.sp
                )
            }
            items(resumo.linhas, key = { it.regra.id }) { l ->
                val oc = l.ocorrencia
                val contaNome = contas.firstOrNull { it.conta == (oc?.conta ?: l.regra.conta) }?.banco ?: (oc?.conta ?: l.regra.conta)
                Column(
                    Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.regra.descricao, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                            Text(
                                (if (l.estado == ContasMes.Estado.PAGA) "Paga em ${fmt.format(Date(l.data))}" else "Vence ${fmt.format(Date(l.data))}") +
                                    " · " + (if (l.noCartao) "no cartão (pague pela fatura)" else contaNome),
                                color = Color.White.copy(0.55f), fontSize = 11.sp
                            )
                            if (l.valorDifere) Text("Previsto na conta: ${moeda(l.regra.valor)}", color = Color.White.copy(0.55f), fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(ROTULO_ESTADO.getValue(l.estado), color = corEstado(l.estado), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(moeda(l.valor), color = Color.White, fontWeight = FontWeight.Bold)
                            if (l.desabilitada && l.estado != ContasMes.Estado.DESABILITADA) Text("conta desabilitada", color = NeonOrange, fontSize = 10.sp)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (oc != null && !oc.pago && !l.noCartao) TextButton(onClick = { dialogo = DialogoContas.Baixar(l) }) { Text("Dar baixa", color = NeonGreen, fontSize = 12.sp) }
                        if (oc != null && !oc.pago) TextButton(onClick = { dialogo = DialogoContas.Alterar(l) }) { Text("Alterar", color = NeonCyan, fontSize = 12.sp) }
                        if (oc == null && l.estado != ContasMes.Estado.DESABILITADA) TextButton(onClick = { dialogo = DialogoContas.Provisionar(l) }) { Text("Provisionar", color = NeonCyan, fontSize = 12.sp) }
                        if (l.desabilitada) {
                            TextButton(onClick = { executar("Conta habilitada. Os meses desabilitados não são recuperados.") { repository.retomarRecorrencia(l.regra.id); null } }) { Text("Habilitar", color = NeonCyan, fontSize = 12.sp) }
                        } else {
                            TextButton(onClick = { dialogo = DialogoContas.Desabilitar(l) }) { Text("Desabilitar", color = NeonOrange, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    when (val d = dialogo) {
        null -> Unit
        DialogoContas.Nova -> NovaContaDialog(contas, onDismiss = { dialogo = null }) { descricao, valor, dia, conta, categoria ->
            dialogo = null
            executar("Conta mensal criada. Use Provisionar para já deixar os próximos meses prontos.") {
                repository.salvarDespesaFixa(
                    DespesaFixa(descricao = descricao.trim(), valor = valor, conta = conta, categoria = categoria.trim(), pic = "", tipo = TipoDespesa.DEBITO, diaVencimento = dia)
                )
                null
            }
        }
        is DialogoContas.Baixar -> BaixaDialog(d.linha, contas, baixar = true, onDismiss = { dialogo = null }) { conta, valor, data, atualizar ->
            dialogo = null
            executar("Baixa registrada.") {
                repository.baixarContaMensal(d.linha.ocorrencia!!.id, conta, valor, data, atualizar, d.linha.regra.id)
                "${d.linha.regra.descricao}: baixa de ${moeda(valor)} na conta ${contas.firstOrNull { it.conta == conta }?.banco ?: conta}."
            }
        }
        is DialogoContas.Alterar -> BaixaDialog(d.linha, contas, baixar = false, onDismiss = { dialogo = null }) { _, valor, data, atualizar ->
            dialogo = null
            executar("Conta atualizada.") {
                val oc = d.linha.ocorrencia!!
                repository.atualizarLancamento(oc.copy(valor = valor, data = Date(data)))
                if (atualizar) repository.obterTodasRecorrencias().firstOrNull { it.id == d.linha.regra.id }?.let { repository.salvarDespesaFixa(it.copy(valor = valor)) }
                null
            }
        }
        is DialogoContas.Provisionar -> ProvisionarDialog(d.linha, onDismiss = { dialogo = null }) { meses ->
            dialogo = null
            executar("") {
                val n = repository.provisionarRecorrencia(d.linha.regra.id, meses)
                if (n > 0) "$n mês(es) provisionado(s). Altere valor e data quando precisar." else "Esses meses já estão provisionados."
            }
        }
        is DialogoContas.Desabilitar -> DesabilitarDialog(d.linha, onDismiss = { dialogo = null }) { ate, remover ->
            dialogo = null
            executar("") {
                val n = repository.desabilitarContaMensal(d.linha.regra.id, ate, remover)
                if (n > 0) "Conta desabilitada; $n provisionada(s) futura(s) foram para a lixeira." else "Conta desabilitada."
            }
        }
    }
}

@Composable
private fun NovaContaDialog(contas: List<ContaSaldo>, onDismiss: () -> Unit, onConfirmar: (String, Double, Int, String, String) -> Unit) {
    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("10") }
    var conta by remember { mutableStateOf(contas.firstOrNull()?.conta) }
    var categoria by remember { mutableStateOf("Moradia") }
    val v = parseValorBR(valor)
    val d = dia.toIntOrNull()
    val ok = descricao.isNotBlank() && v != null && v > 0 && d != null && d in 1..31 && conta != null && categoria.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Nova conta mensal", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(descricao, { descricao = it }, "Descrição (ex.: Conta de luz)", maxChars = 60)
                CampoR58(valor, { valor = it }, "Valor previsto (R$)", numerico = true, apoio = "Você ajusta o valor real na hora da baixa.")
                CampoR58(dia, { dia = it.filter(Char::isDigit).take(2) }, "Dia do vencimento (1 a 31)", numerico = true)
                Text("Conta de onde costuma sair", color = Color.White.copy(0.7f), fontSize = 12.sp)
                SeletorContaR58(contas, conta) { conta = it }
                CampoR58(categoria, { categoria = it }, "Categoria", maxChars = 40)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmar(descricao, v!!, d!!, conta!!, categoria) }, enabled = ok) { Text("Criar", color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
private fun BaixaDialog(linha: ContasMes.Linha, contas: List<ContaSaldo>, baixar: Boolean, onDismiss: () -> Unit, onConfirmar: (String, Double, Long, Boolean) -> Unit) {
    val oc = linha.ocorrencia!!
    var valor by remember { mutableStateOf(valorParaCampo(oc.valor)) }
    var data by remember { mutableStateOf(dataParaCampo(if (baixar) System.currentTimeMillis() else oc.data.time)) }
    var conta by remember { mutableStateOf(oc.conta.takeIf { c -> contas.any { it.conta == c } } ?: contas.firstOrNull()?.conta) }
    var atualizar by remember { mutableStateOf(false) }
    val v = parseValorBR(valor)
    val ms = dataDigitada(data)
    val difere = v != null && Dinheiro.centavos(v) != Dinheiro.centavos(linha.regra.valor)
    val ok = v != null && v > 0 && ms != null && conta != null
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text(if (baixar) "Dar baixa: ${linha.regra.descricao}" else "Alterar: ${linha.regra.descricao}", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vencimento ${dataParaCampo(oc.data.time)} · previsto ${formatarMoedaBR(linha.regra.valor, false)}", color = Color.White.copy(0.7f), fontSize = 12.sp)
                CampoR58(valor, { valor = it }, if (baixar) "Valor cobrado (R$)" else "Novo valor (R$)", numerico = true)
                CampoR58(data, { data = it.take(10) }, if (baixar) "Data do pagamento (dd/mm/aaaa)" else "Nova data (dd/mm/aaaa)", apoio = if (ms == null) "Use o formato dd/mm/aaaa." else null)
                if (baixar) {
                    Text("Pagar com a conta (o valor sai do saldo dela)", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    SeletorContaR58(contas, conta) { conta = it }
                }
                if (difere) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = atualizar, onCheckedChange = { atualizar = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Text("Usar ${formatarMoedaBR(v ?: 0.0, false)} como valor previsto dos próximos meses", color = Color.White, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmar(conta!!, v!!, ms!!, atualizar && difere) }, enabled = ok) { Text(if (baixar) "Dar baixa" else "Salvar", color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
private fun ProvisionarDialog(linha: ContasMes.Linha, onDismiss: () -> Unit, onConfirmar: (Int) -> Unit) {
    var meses by remember { mutableStateOf("3") }
    val n = meses.toIntOrNull()
    val ok = n != null && n in 1..ContasMes.MAX_MESES_PROVISAO
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Provisionar: ${linha.regra.descricao}", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(meses, { meses = it.filter(Char::isDigit).take(2) }, "Quantos meses (a partir do mês corrente)", numerico = true, apoio = "De 1 a ${ContasMes.MAX_MESES_PROVISAO}. Cria pendências com o valor e o dia da conta; o que já existe é mantido.")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmar(n!!) }, enabled = ok) { Text("Provisionar", color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
private fun DesabilitarDialog(linha: ContasMes.Linha, onDismiss: () -> Unit, onConfirmar: (Long?, Boolean) -> Unit) {
    var ate by remember { mutableStateOf("") }
    var remover by remember { mutableStateOf(true) }
    val ms = if (ate.isBlank()) null else dataDigitada(ate)
    val ok = ate.isBlank() || (ms != null && ms > System.currentTimeMillis())
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Desabilitar: ${linha.regra.descricao}", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(ate, { ate = it.take(10) }, "Voltar a lançar em (opcional, dd/mm/aaaa)", apoio = if (!ok) "Use uma data futura (dd/mm/aaaa)." else "Sem data, fica desabilitada até você habilitar.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remover, onCheckedChange = { remover = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Text("Enviar para a lixeira as provisionadas futuras ainda em aberto", color = Color.White, fontSize = 12.sp)
                }
                Text("Contas já vencidas ou pagas nunca são tocadas. Os meses desabilitados não são lançados nem recuperados depois.", color = Color.White.copy(0.5f), fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmar(ms, remover) }, enabled = ok) { Text("Desabilitar", color = NeonOrange) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}
