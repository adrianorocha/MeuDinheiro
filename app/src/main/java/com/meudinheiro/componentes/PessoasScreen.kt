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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
    var cobrando by remember { mutableStateOf<Pessoa?>(null) }
    var excluirPessoa by remember { mutableStateOf<Pessoa?>(null) }
    var excluirAcerto by remember { mutableStateOf<Acerto?>(null) }
    var periodo by remember { mutableStateOf(PeriodoRateio.MES) }
    var abertos by remember { mutableStateOf(setOf<Int>()) }
    var aviso by remember { mutableStateOf<String?>(null) }

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
    val totalReceber = saldos.sumOf { it.aReceber }
    val totalPagar = saldos.sumOf { it.aPagar }

    @Composable
    fun LinhaAcerto(a: Acerto) {
        val orfao = a.despesaId != null && a.despesaId !in idsDespesas
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.descricao, color = Color.White, fontSize = 13.sp, maxLines = 1)
                Text(
                    listOfNotNull(
                        fmt.format(Date(a.data)), if (a.tipo == Rateios.A_RECEBER) "a receber" else "a pagar",
                        if (a.grupoRateio != null) "rateio" else null, if (orfao) "lançamento removido" else null,
                        if (a.quitado && a.quitadoEm != null) "quitado em ${fmt.format(Date(a.quitadoEm))}" else null
                    ).joinToString(" · "),
                    color = if (orfao) NeonOrange else Color.White.copy(0.55f), fontSize = 11.sp
                )
            }
            Text(formatarMoedaBR(a.valor, isPrivate), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (!a.quitado) TextButton(onClick = { quitando = a }) { Text(if (a.tipo == Rateios.A_RECEBER) "Receber" else "Pagar", color = NeonCyan, fontSize = 12.sp) }
            IconButton(onClick = { excluirAcerto = a }) { Icon(Icons.Default.Delete, "Excluir acerto", tint = NeonRed) }
        }
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Pessoas", color = TextoClaroR58, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaroR58) } },
                actions = { IconButton(onClick = { criandoPessoa = true }) { Icon(Icons.Default.Add, "Nova pessoa", tint = NeonCyan) } },
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
            title = { Text("Excluir acerto", color = Color.White) },
            text = { Text("Excluir “${a.descricao}” (${formatarMoedaBR(a.valor, false)})? O lançamento de origem, se houver, não é alterado.", color = Color.White.copy(0.8f)) },
            confirmButton = { TextButton(onClick = { scope.launch { repository.excluirAcerto(a.id) }; excluirAcerto = null }) { Text("Excluir", color = NeonRed) } },
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
