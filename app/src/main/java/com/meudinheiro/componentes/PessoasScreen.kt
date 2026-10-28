package com.meudinheiro.componentes

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Acerto
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Pessoa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Rateios
import com.meudinheiro.domain.RelatorioPessoas
import com.meudinheiro.funcoes.RelatorioPessoasExport
import com.meudinheiro.data.Despesa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
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

private enum class PeriodoRateio { MES, TUDO }

/**
 * R59 — pessoas, rateios e acertos: saldo líquido por pessoa (a receber − a pagar), acertos pendentes/quitados, "Emprestei"/"Devo",
 * receber/pagar com lançamento opcional e "Cobrar" (texto pronto para compartilhar). Mostra também "quanto você realmente gastou"
 * nos lançamentos divididos (indicador à parte: os relatórios continuam com o valor total). Mesmas regras do portal (`/pessoas`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PessoasScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val pessoas by repository.obterPessoasFlow().collectAsState(initial = emptyList())
    val acertos by repository.obterAcertosFlow().collectAsState(initial = emptyList())
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())

    var formPessoa by remember { mutableStateOf<Pessoa?>(null) }
    var criandoPessoa by remember { mutableStateOf(false) }
    var avulso by remember { mutableStateOf<Pair<String, Int?>?>(null) }
    var quitando by remember { mutableStateOf<Acerto?>(null) }
    var vinculando by remember { mutableStateOf<Pair<Boolean, Int?>?>(null) }
    var cobrando by remember { mutableStateOf<Pessoa?>(null) }
    var relatorio by remember { mutableStateOf<Pair<Boolean, Int?>?>(null) }
    var excluirPessoa by remember { mutableStateOf<Pessoa?>(null) }
    var excluirAcerto by remember { mutableStateOf<Acerto?>(null) }
    var periodo by remember { mutableStateOf(PeriodoRateio.MES) }
    var abertos by remember { mutableStateOf(setOf<Int>()) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var avisoOk by remember { mutableStateOf<String?>(null) }

    fun moeda(c: Long) = formatarMoedaBR(Dinheiro.reais(c), isPrivate)
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val saldos = remember(pessoas, acertos) {
        Rateios.saldosPorPessoa(pessoas, acertos).sortedWith(compareByDescending<Rateios.SaldoPessoa> { Math.abs(it.liquido) }.thenBy { it.pessoa.nome.lowercase() })
    }
    val resumo = remember(despesas, acertos, periodo) {
        val c = Calendar.getInstance()
        val ini = Calendar.getInstance().apply { clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), 1, 0, 0, 0) }.timeInMillis
        val fim = Calendar.getInstance().apply { timeInMillis = ini; add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }.timeInMillis
        Rateios.resumo(despesas, acertos, if (periodo == PeriodoRateio.MES) ini..fim else null)
    }
    val idsDespesas = remember(despesas) { despesas.map { it.id }.toHashSet() }
    val despesasPorId = remember(despesas) { despesas.associateBy { it.id } }
    val totalReceber = saldos.sumOf { it.aReceber }
    val totalPagar = saldos.sumOf { it.aPagar }

    @Composable
    fun LinhaAcerto(a: Acerto) {
        val orfao = a.despesaId != null && a.despesaId !in idsDespesas
        val origem = a.despesaId?.let { despesasPorId[it] }
        val vinculo = a.grupoRateio?.startsWith("vinculo:") == true
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.descricao, color = Color.White, fontSize = 13.sp, maxLines = 1)
                Text(
                    listOfNotNull(
                        fmt.format(Date(a.data)), if (a.tipo == Rateios.A_RECEBER) "a receber" else "a pagar",
                        if (a.grupoRateio != null) (if (vinculo) "vinculado" else "rateio") else null, if (orfao) "lançamento removido" else null,
                        if (a.quitado && a.quitadoEm != null) "quitado em ${fmt.format(Date(a.quitadoEm))}" else null
                    ).joinToString(" · "),
                    color = if (orfao) NeonOrange else Color.White.copy(0.55f), fontSize = 11.sp
                )
                origem?.let { Text("lançamento de ${SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(it.data)} — ${it.descricao}", color = Color.White.copy(0.55f), fontSize = 11.sp, maxLines = 1) }
            }
            Text(formatarMoedaBR(a.valor, isPrivate), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (!a.quitado) TextButton(onClick = { quitando = a }) { Text(if (a.tipo == Rateios.A_RECEBER) "Receber" else "Pagar", color = NeonCyan, fontSize = 12.sp) }
            if (vinculo) {
                if (!a.quitado) TextButton(onClick = { excluirAcerto = a }) { Text("Desvincular", color = NeonRed, fontSize = 12.sp) }
            } else {
                IconButton(onClick = { excluirAcerto = a }) { Icon(Icons.Default.Delete, "Excluir acerto", tint = NeonRed) }
            }
        }
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Pessoas", color = TextoClaroR58, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaroR58) } },
                actions = {
                    IconButton(onClick = { relatorio = true to null }, enabled = pessoas.isNotEmpty()) { Icon(Icons.Default.PictureAsPdf, "Relatório", tint = NeonCyan) }
                    IconButton(onClick = { criandoPessoa = true }) { Icon(Icons.Default.Add, "Nova pessoa", tint = NeonCyan) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Rateios, empréstimos e acertos: quem te deve e quem você deve. Para dividir uma despesa, abra o lançamento no extrato e escolha \"Dividir\".",
                    color = Color.White.copy(0.6f), fontSize = 12.sp
                )
            }
            aviso?.let { msg -> item { Text(msg, color = NeonRed, fontSize = 13.sp) } }
            avisoOk?.let { msg -> item { Text(msg, color = NeonGreen, fontSize = 13.sp) } }
            item {
                Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("A receber (pendente)", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Text(moeda(totalReceber), color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("A pagar (pendente)", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Text(moeda(totalPagar), color = NeonOrange, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { avulso = Rateios.A_RECEBER to null }, enabled = pessoas.isNotEmpty()) { Text("Emprestei", color = NeonCyan) }
                        TextButton(onClick = { avulso = Rateios.A_PAGAR to null }, enabled = pessoas.isNotEmpty()) { Text("Devo", color = NeonCyan) }
                        TextButton(onClick = { vinculando = true to null }, enabled = pessoas.isNotEmpty()) { Text("Vincular lançamentos", color = NeonCyan) }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Quanto você realmente gastou", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Só nos lançamentos divididos. O lançamento e os relatórios continuam com o valor total que saiu da conta; aqui o líquido é o total menos o que as pessoas devem a você.",
                        color = Color.White.copy(0.55f), fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PeriodoRateio.MES to "Este mês", PeriodoRateio.TUDO to "Tudo").forEach { (p, rotulo) ->
                            FilterChip(
                                selected = periodo == p, onClick = { periodo = p }, label = { Text(rotulo, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                            )
                        }
                    }
                    if (resumo.itens.isEmpty()) {
                        Text("Nenhuma despesa dividida ${if (periodo == PeriodoRateio.MES) "neste mês" else "ainda"}.", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total das despesas divididas", color = Color.White.copy(0.7f), fontSize = 12.sp)
                            Text(moeda(resumo.totalRateado), color = Color.White, fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("A receber de terceiros", color = Color.White.copy(0.7f), fontSize = 12.sp)
                            Text(moeda(resumo.aReceberTerceiros), color = Color.White, fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Você realmente gastou", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(moeda(resumo.liquido), color = NeonCyan, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        resumo.itens.take(30).forEach { i ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${fmt.format(Date(i.data))} · ${i.descricao}" + if (i.orfao) " (lançamento removido)" else "",
                                    color = if (i.orfao) NeonOrange else Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (i.orfao) "a receber ${moeda(i.aReceber)}" else "${moeda(i.total)} → ${moeda(i.liquido)}",
                                    color = Color.White.copy(0.8f), fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
            if (pessoas.isEmpty()) {
                item { Text("Nenhuma pessoa cadastrada. Toque em + para criar.", color = Color.White.copy(0.5f), fontSize = 13.sp) }
            }
            items(saldos, key = { it.pessoa.id }) { s ->
                Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.pessoa.nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            s.pessoa.contato?.let { Text(it, color = Color.White.copy(0.5f), fontSize = 11.sp) }
                        }
                        IconButton(onClick = { formPessoa = s.pessoa }) { Icon(Icons.Default.Edit, "Editar", tint = Color.White.copy(0.7f)) }
                        IconButton(onClick = { excluirPessoa = s.pessoa }) { Icon(Icons.Default.Delete, "Excluir", tint = NeonRed) }
                    }
                    Text(
                        moeda(s.liquido), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (s.liquido > 0) NeonGreen else if (s.liquido < 0) NeonOrange else Color.White
                    )
                    Text(
                        if (s.liquido > 0) "te deve (a receber − a pagar)" else if (s.liquido < 0) "você deve (a pagar − a receber)" else "sem saldo pendente",
                        color = Color.White.copy(0.55f), fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { cobrando = s.pessoa }, enabled = s.aReceber > 0) { Text("Cobrar", color = NeonCyan) }
                        TextButton(onClick = { avulso = Rateios.A_RECEBER to s.pessoa.id }) { Text("Emprestei", color = NeonCyan) }
                        TextButton(onClick = { avulso = Rateios.A_PAGAR to s.pessoa.id }) { Text("Devo", color = NeonCyan) }
                        TextButton(onClick = { vinculando = true to s.pessoa.id }) { Text("Vincular", color = NeonCyan) }
                        TextButton(onClick = { relatorio = true to s.pessoa.id }) { Text("Relatório", color = NeonCyan) }
                    }
                    if (s.pendentes.isNotEmpty()) {
                        Text("Pendentes", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        s.pendentes.sortedByDescending { it.data }.forEach { LinhaAcerto(it) }
                    }
                    if (s.quitados.isNotEmpty()) {
                        val aberto = s.pessoa.id in abertos
                        TextButton(onClick = { abertos = if (aberto) abertos - s.pessoa.id else abertos + s.pessoa.id }) {
                            Text(if (aberto) "Ocultar quitados" else "Ver quitados (${s.quitados.size})", color = NeonCyan)
                        }
                        if (aberto) s.quitados.sortedByDescending { it.data }.forEach { LinhaAcerto(it) }
                    }
                }
            }
        }
    }

    if (criandoPessoa || formPessoa != null) {
        FormularioPessoa(
            inicial = formPessoa, onDismiss = { criandoPessoa = false; formPessoa = null },
            onSalvar = { p ->
                scope.launch {
                    try { repository.salvarPessoa(p); aviso = null; criandoPessoa = false; formPessoa = null }
                    catch (e: RegraFinanceiraException) { aviso = e.message }
                }
            }
        )
    }

    avulso?.let { (tipo, pessoaFixa) ->
        AvulsoDialog(
            tipo = tipo, pessoas = pessoas, pessoaInicial = pessoaFixa, contas = contas, onDismiss = { avulso = null },
            onConfirmar = { pessoaId, valor, descricao, conta ->
                scope.launch {
                    try {
                        repository.criarAcertoAvulso(pessoaId, tipo, valor, descricao, contaLancamento = conta, categoria = Rateios.CATEGORIA_EMPRESTIMOS)
                        aviso = null
                    } catch (e: RegraFinanceiraException) { aviso = e.message }
                    avulso = null
                }
            }
        )
    }

    quitando?.let { a ->
        QuitarDialog(
            acerto = a, nome = pessoas.firstOrNull { it.id == a.pessoaId }?.nome ?: "pessoa", contas = contas, onDismiss = { quitando = null },
            onConfirmar = { conta, categoria ->
                scope.launch {
                    try { repository.quitarAcerto(a.id, contaLancamento = conta, categoria = categoria); aviso = null }
                    catch (e: RegraFinanceiraException) { aviso = e.message }
                    quitando = null
                }
            }
        )
    }

    vinculando?.let { (_, pessoaFixa) ->
        VincularLancamentosDialog(
            pessoas = pessoas, pessoaInicial = pessoaFixa, despesas = despesas, acertos = acertos, contas = contas, onDismiss = { vinculando = null },
            onConfirmar = { pessoaId, tipo, itens, total, aoErro ->
                scope.launch {
                    try {
                        val n = repository.vincularLancamentos(pessoaId, tipo, itens)
                        aviso = null
                        vinculando = null
                        avisoOk = "$n lançamento(s) vinculado(s) (${FormatoCentavos.brl(total)}). Saldo e lançamentos não mudaram."
                    } catch (e: RegraFinanceiraException) { aoErro(e.message) }
                }
            }
        )
    }

    relatorio?.let { (_, pid) -> RelatorioPessoasDialog(pessoas, acertos, despesas, pid, isPrivate) { relatorio = null } }
    cobrando?.let { p -> CobrarDialog(p, acertos.filter { it.pessoaId == p.id }) { cobrando = null } }

    excluirPessoa?.let { p ->
        AlertDialog(
            onDismissRequest = { excluirPessoa = null }, containerColor = DeepSpaceBlue,
            title = { Text("Excluir pessoa", color = Color.White) },
            text = { Text("Excluir “${p.nome}”? Só é possível se não houver acertos.", color = Color.White.copy(0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { repository.excluirPessoa(p.id); aviso = null } catch (e: RegraFinanceiraException) { aviso = e.message }
                    }
                    excluirPessoa = null
                }) { Text("Excluir", color = NeonRed) }
            },
            dismissButton = { TextButton(onClick = { excluirPessoa = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }

    excluirAcerto?.let { a ->
        AlertDialog(
            onDismissRequest = { excluirAcerto = null }, containerColor = DeepSpaceBlue,
            title = { Text(if (a.grupoRateio?.startsWith("vinculo:") == true) "Desvincular lançamento" else "Excluir acerto", color = Color.White) },
            text = {
                Text(
                    if (a.grupoRateio?.startsWith("vinculo:") == true) "Desvincular “${a.descricao}” (${formatarMoedaBR(a.valor, false)})? Só o controle é removido; o lançamento e o saldo não mudam."
                    else "Excluir “${a.descricao}” (${formatarMoedaBR(a.valor, false)})? O lançamento de origem, se houver, não é alterado.",
                    color = Color.White.copy(0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Desvincular exclui só o acerto NÃO quitado; quitado nunca é apagado por esta ação.
                    if (!(a.quitado && a.grupoRateio?.startsWith("vinculo:") == true)) scope.launch { repository.excluirAcerto(a.id) }
                    excluirAcerto = null
                }) { Text(if (a.grupoRateio?.startsWith("vinculo:") == true) "Desvincular" else "Excluir", color = NeonRed) }
            },
            dismissButton = { TextButton(onClick = { excluirAcerto = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }
}

@Composable
private fun FormularioPessoa(inicial: Pessoa?, onDismiss: () -> Unit, onSalvar: (Pessoa) -> Unit) {
    var nome by remember { mutableStateOf(inicial?.nome.orEmpty()) }
    var contato by remember { mutableStateOf(inicial?.contato.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text(if (inicial == null) "Nova pessoa" else "Editar pessoa", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(nome, { nome = it }, "Nome", maxChars = 60)
                CampoR58(contato, { contato = it }, "Contato (opcional)", apoio = "Telefone, e-mail… só para você lembrar", maxChars = 80)
            }
        },
        confirmButton = { TextButton(onClick = { onSalvar(Pessoa(id = inicial?.id ?: 0, nome = nome, contato = contato.ifBlank { null })) }) { Text("Salvar", color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvulsoDialog(
    tipo: String, pessoas: List<Pessoa>, pessoaInicial: Int?, contas: List<ContaSaldo>, onDismiss: () -> Unit,
    onConfirmar: (pessoaId: Int, valor: Double, descricao: String, conta: String?) -> Unit
) {
    val recebe = tipo == Rateios.A_RECEBER
    var pessoa by remember { mutableStateOf(pessoaInicial ?: pessoas.firstOrNull()?.id) }
    var valor by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var lancar by remember { mutableStateOf(false) }
    var conta by remember { mutableStateOf(contas.firstOrNull()?.conta) }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text(if (recebe) "Emprestei" else "Devo", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pessoas.forEach { p ->
                        FilterChip(
                            selected = pessoa == p.id, onClick = { pessoa = p.id }, label = { Text(p.nome, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                        )
                    }
                }
                CampoR58(valor, { valor = it }, "Valor", numerico = true, maxChars = 14)
                CampoR58(descricao, { descricao = it }, "Descrição (opcional)", maxChars = 80)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = lancar, onCheckedChange = { lancar = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Text(
                        if (recebe) "Gerar a saída na conta (o dinheiro saiu da minha conta)" else "Gerar a entrada na conta (o dinheiro entrou na minha conta)",
                        color = Color.White, fontSize = 12.sp
                    )
                }
                if (lancar) {
                    SeletorContaR58(contas, conta) { conta = it }
                    Text("Cria um lançamento \"${Rateios.CATEGORIA_EMPRESTIMOS}\" já efetivado; ele entra nos relatórios como qualquer lançamento comum.", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
                erro?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = parseValorBR(valor)
                val p = pessoa
                if (p == null) { erro = "Escolha a pessoa."; return@TextButton }
                if (v == null) { erro = "Informe o valor."; return@TextButton }
                onConfirmar(p, v, descricao, if (lancar) conta else null)
            }) { Text("Registrar", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
private fun QuitarDialog(acerto: Acerto, nome: String, contas: List<ContaSaldo>, onDismiss: () -> Unit, onConfirmar: (conta: String?, categoria: String?) -> Unit) {
    val recebe = acerto.tipo == Rateios.A_RECEBER
    var lancar by remember { mutableStateOf(contas.isNotEmpty()) }
    var conta by remember { mutableStateOf(contas.firstOrNull()?.conta) }
    var categoria by remember { mutableStateOf(if (recebe) Rateios.CATEGORIA_REEMBOLSO else Rateios.CATEGORIA_ACERTOS) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text(if (recebe) "Receber acerto" else "Pagar acerto", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (if (recebe) "Confirmar que $nome pagou " else "Confirmar que você pagou a $nome: ") +
                        "${formatarMoedaBR(acerto.valor, false)} (${acerto.descricao})?",
                    color = Color.White.copy(0.85f), fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = lancar, onCheckedChange = { lancar = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Text(
                        if (recebe) "Criar a receita na conta (para o saldo bater com o banco)" else "Criar a despesa na conta (para o saldo bater com o banco)",
                        color = Color.White, fontSize = 12.sp
                    )
                }
                if (lancar) {
                    SeletorContaR58(contas, conta) { conta = it }
                    CampoR58(categoria, { categoria = it }, "Categoria", maxChars = 40)
                    Text("O lançamento entra já ${if (recebe) "recebido (receita)" else "pago (despesa)"} e aparece nos relatórios.", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(if (lancar) conta else null, categoria) }, enabled = !lancar || conta != null) {
                Text(if (recebe) "Confirmar recebimento" else "Confirmar pagamento", color = NeonCyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

/** Texto pronto para WhatsApp/copiar; a chave PIX só entra se você digitar (nunca é inventada). */
@Composable
private fun CobrarDialog(pessoa: Pessoa, acertos: List<Acerto>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var pix by remember { mutableStateOf("") }
    val texto = Rateios.textoCobranca(pessoa.nome, acertos, pix)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Cobrar ${pessoa.nome}", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(pix, { pix = it }, "Chave PIX (opcional)", apoio = "Digite a sua chave; ela só entra no texto se você preencher.", maxChars = 80)
                Text(texto ?: "Nada pendente para cobrar.", color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.06f), RoundedCornerShape(8.dp)).padding(10.dp))
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { texto?.let { clipboard.setText(AnnotatedString(it)) } }, enabled = texto != null) { Text("Copiar", color = NeonCyan) }
                TextButton(
                    onClick = {
                        texto?.let {
                            val envio = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, it) }
                            context.startActivity(Intent.createChooser(envio, "Cobrar ${pessoa.nome}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    enabled = texto != null
                ) { Text("Compartilhar", color = NeonCyan) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White.copy(0.7f)) } }
    )
}

private enum class PeriodoRel(val rotulo: String, val atalho: RelatorioPessoas.Atalho?) {
    ESTE_MES("Este mês", RelatorioPessoas.Atalho.ESTE_MES), MES_PASSADO("Mês passado", RelatorioPessoas.Atalho.MES_PASSADO),
    TRES_MESES("3 meses", RelatorioPessoas.Atalho.TRES_MESES), ANO("Este ano", RelatorioPessoas.Atalho.ANO),
    TUDO("Tudo", RelatorioPessoas.Atalho.TUDO), PERSONALIZADO("Personalizado", null)
}

/** "dd/MM/aaaa" -> epoch ms (início ou fim do dia, horário local); inválido -> null. */
private fun dataDigitada(texto: String, fimDoDia: Boolean): Long? {
    val f = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }
    val d = runCatching { f.parse(texto.trim()) }.getOrNull() ?: return null
    return Calendar.getInstance().apply {
        time = d
        if (fimDoDia) { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        else { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    }.timeInMillis
}

/**
 * R69 — relatório detalhado de Pessoas (PDF/PNG/JPEG): pessoas, período, situação, detalhes, agrupamento e formato, com pré-visualização
 * dos totais. O modo privado vale só para esta tela; o arquivo gerado leva os valores reais (avisado no diálogo).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatorioPessoasDialog(pessoas: List<Pessoa>, acertos: List<Acerto>, despesas: List<Despesa>, pessoaInicial: Int?, isPrivate: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agora = remember { System.currentTimeMillis() }
    var escolher by remember { mutableStateOf(pessoaInicial != null) }
    var ids by remember { mutableStateOf(setOfNotNull(pessoaInicial)) }
    var periodoSel by remember { mutableStateOf(PeriodoRel.TUDO) }
    var de by remember { mutableStateOf("") }
    var ate by remember { mutableStateOf("") }
    var situacao by remember { mutableStateOf(RelatorioPessoas.Situacao.TODOS) }
    var detalhes by remember { mutableStateOf(true) }
    var agrupar by remember { mutableStateOf(RelatorioPessoas.Agrupar.POR_PESSOA) }
    var rateios by remember { mutableStateOf(false) }
    var formato by remember { mutableStateOf(RelatorioPessoasExport.Formato.PDF) }
    var gerando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val chipCores = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))

    // período: atalho ou datas digitadas (erro em pt-BR, sem gerar)
    var erroPeriodo: String? = null
    val periodo: LongRange? = if (periodoSel == PeriodoRel.PERSONALIZADO) {
        val a = dataDigitada(de, false)
        val b = dataDigitada(ate, true)
        when {
            a == null || b == null -> { erroPeriodo = "Informe as duas datas (dd/mm/aaaa)."; null }
            a > b -> { erroPeriodo = "A data inicial deve ser anterior à final."; null }
            else -> a..b
        }
    } else periodoSel.atalho?.let { RelatorioPessoas.periodoPorAtalho(it, agora) }
    val periodoInvalido = erroPeriodo != null

    val relatorio = remember(pessoas, acertos, despesas, escolher, ids, periodo, periodoInvalido, situacao, detalhes, agrupar, rateios) {
        if (periodoInvalido) null
        else RelatorioPessoas.montar(
            pessoas, acertos, despesas,
            RelatorioPessoas.Opcoes(if (escolher) ids else emptySet(), periodo, situacao, detalhes, agrupar, rateios, agora)
        )
    }
    val semEscolha = escolher && ids.isEmpty()
    val podeGerar = !gerando && relatorio != null && !relatorio.vazio && !semEscolha
    fun moeda(c: Long) = formatarMoedaBR(Dinheiro.reais(c), isPrivate)

    AlertDialog(
        onDismissRequest = { if (!gerando) onDismiss() }, containerColor = DeepSpaceBlue,
        title = { Text("Relatório de pessoas", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pessoas", color = Color.White.copy(0.7f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !escolher, onClick = { escolher = false }, label = { Text("Todas", fontSize = 12.sp) }, colors = chipCores)
                    FilterChip(selected = escolher, onClick = { escolher = true }, label = { Text("Escolher", fontSize = 12.sp) }, colors = chipCores)
                }
                if (escolher) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pessoas.forEach { p ->
                        FilterChip(
                            selected = p.id in ids, onClick = { ids = if (p.id in ids) ids - p.id else ids + p.id },
                            label = { Text(p.nome, fontSize = 12.sp) }, colors = chipCores
                        )
                    }
                }
                Text("Período (data do acerto)", color = Color.White.copy(0.7f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PeriodoRel.values().forEach { p ->
                        FilterChip(selected = periodoSel == p, onClick = { periodoSel = p }, label = { Text(p.rotulo, fontSize = 12.sp) }, colors = chipCores)
                    }
                }
                if (periodoSel == PeriodoRel.PERSONALIZADO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoR58(de, { de = it }, "De (dd/mm/aaaa)", Modifier.weight(1f), maxChars = 10)
                        CampoR58(ate, { ate = it }, "Até (dd/mm/aaaa)", Modifier.weight(1f), maxChars = 10)
                    }
                }
                erroPeriodo?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
                Text("Situação", color = Color.White.copy(0.7f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RelatorioPessoas.Situacao.TODOS to "Todos", RelatorioPessoas.Situacao.PENDENTES to "Pendentes", RelatorioPessoas.Situacao.QUITADOS to "Quitados"
                    ).forEach { (v, r) -> FilterChip(selected = situacao == v, onClick = { situacao = v }, label = { Text(r, fontSize = 12.sp) }, colors = chipCores) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = detalhes, onCheckedChange = { detalhes = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Text("Incluir detalhes (uma linha por acerto)", color = Color.White, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rateios, onCheckedChange = { rateios = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Text("Incluir bloco \"Rateios\" (quanto você realmente gastou, informativo)", color = Color.White, fontSize = 12.sp)
                }
                if (detalhes) {
                    Text("Agrupar", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(RelatorioPessoas.Agrupar.POR_PESSOA to "Por pessoa", RelatorioPessoas.Agrupar.CRONOLOGICO to "Cronológico").forEach { (v, r) ->
                            FilterChip(selected = agrupar == v, onClick = { agrupar = v }, label = { Text(r, fontSize = 12.sp) }, colors = chipCores)
                        }
                    }
                }
                Text("Formato", color = Color.White.copy(0.7f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RelatorioPessoasExport.Formato.values().forEach { f ->
                        FilterChip(selected = formato == f, onClick = { formato = f }, label = { Text(f.name, fontSize = 12.sp) }, colors = chipCores)
                    }
                }
                Column(Modifier.fillMaxWidth().background(Color.White.copy(0.06f), RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Pré-visualização do resumo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    val r = relatorio?.resumo
                    when {
                        semEscolha -> Text("Escolha ao menos uma pessoa.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        relatorio == null || r == null -> Text("Corrija o período para ver o resumo.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        relatorio.vazio -> Text(RelatorioPessoas.MSG_SEM_ACERTOS, color = NeonOrange, fontSize = 12.sp)
                        else -> {
                            @Composable fun linha(rot: String, v: String, cor: Color = Color.White) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(rot, color = Color.White.copy(0.7f), fontSize = 12.sp); Text(v, color = cor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            linha("A receber (pendente)", moeda(r.aReceberPendente), NeonGreen)
                            linha("A pagar (pendente)", moeda(r.aPagarPendente), NeonOrange)
                            linha(
                                "Saldo líquido",
                                moeda(Math.abs(r.saldoLiquido)) + if (r.saldoLiquido > 0) " (a seu favor)" else if (r.saldoLiquido < 0) " (contra você)" else " (quites)"
                            )
                            linha("Quitado (a receber / a pagar)", "${moeda(r.quitadoAReceber)} / ${moeda(r.quitadoAPagar)}")
                            linha("Acertos / pessoas", "${r.nAcertos} / ${r.nPessoas} (${r.nPessoasComSaldo} com saldo)")
                        }
                    }
                }
                if (isPrivate) Text("O modo privado vale só para a tela: o arquivo gerado leva os valores reais.", color = NeonOrange, fontSize = 11.sp)
                erro?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = podeGerar,
                onClick = {
                    val rel = relatorio ?: return@TextButton
                    erro = null
                    gerando = true
                    scope.launch {
                        try {
                            val arquivos = withContext(Dispatchers.Default) { RelatorioPessoasExport.gerar(context, rel, formato, agora) }
                            RelatorioPessoasExport.compartilhar(context, arquivos, formato)
                            onDismiss()
                        } catch (e: Exception) {
                            erro = "Não foi possível gerar o relatório: " + (e.message ?: "tente de novo.")
                        } finally {
                            gerando = false
                        }
                    }
                }
            ) {
                if (gerando) { CircularProgressIndicator(Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp); Text("  Gerando…", color = NeonCyan) }
                else Text("Gerar", color = if (podeGerar) NeonCyan else Color.White.copy(0.4f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !gerando) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}
