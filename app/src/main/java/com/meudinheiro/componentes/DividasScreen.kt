package com.meudinheiro.componentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Divida
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Dividas
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch
import java.util.Calendar

private val ROTULO_ESTRATEGIA = mapOf(
    Dividas.Estrategia.MINIMOS to "Apenas mínimos",
    Dividas.Estrategia.BOLA_DE_NEVE to "Bola de neve (menor saldo primeiro)",
    Dividas.Estrategia.AVALANCHE to "Avalanche (maior taxa primeiro)"
)
private val COR_ESTRATEGIA = mapOf(
    Dividas.Estrategia.MINIMOS to NeonOrange,
    Dividas.Estrategia.BOLA_DE_NEVE to NeonCyan,
    Dividas.Estrategia.AVALANCHE to NeonGreen
)

/**
 * R58 — dívidas em quitação: saldo devedor e progresso, "Registrar pagamento" (lançamento de despesa em "Dívidas" com
 * `grupoId = divida:<id>` + abate do saldo devedor, na mesma transação), juros do mês e simulador (mínimos x bola de neve x
 * avalanche, com economia de juros). Mesmas regras do portal (`/dividas`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DividasScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val dividas by repository.obterDividasFlow().collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())

    var criando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Divida?>(null) }
    var pagando by remember { mutableStateOf<Divida?>(null) }
    var jurando by remember { mutableStateOf<Divida?>(null) }
    var excluir by remember { mutableStateOf<Divida?>(null) }
    var verQuitadas by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var extraTxt by remember { mutableStateOf("0") }
    var estrategiaVista by remember { mutableStateOf(Dividas.Estrategia.AVALANCHE) }

    fun moeda(c: Long) = formatarMoedaBR(Dinheiro.reais(c), isPrivate)
    val ativas = remember(dividas) { dividas.filter { it.ativa && it.saldoDevedor > 0 }.sortedBy { it.nome.lowercase() } }
    val inativas = remember(dividas) { dividas.filter { !(it.ativa && it.saldoDevedor > 0) }.sortedBy { it.nome.lowercase() } }
    val totalSaldo = ativas.sumOf { Dinheiro.centavos(it.saldoDevedor) }
    val totalMinimos = ativas.sumOf { Dinheiro.centavos(it.parcelaMinima ?: 0.0) }
    val jurosMes = ativas.sumOf { d -> Dividas.paraSim(d)?.let { Dividas.jurosDoMes(it.saldo, it.taxaBp) } ?: 0L }
    val extra = parseValorBR(extraTxt)?.takeIf { it >= 0 } ?: 0.0
    val comp = remember(dividas, extra) { Dividas.compararDasDividas(dividas, extra) }
    val hoje = remember { Calendar.getInstance() }
    val anoRef = hoje.get(Calendar.YEAR)
    val mesRef = hoje.get(Calendar.MONTH) + 1

    @Composable
    fun CartaoDivida(d: Divida) {
        val pr = Dividas.progresso(d)
        val quitada = !(d.ativa && d.saldoDevedor > 0)
        Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text((Dividas.ROTULOS[d.tipo] ?: "Outra") + if (quitada) " · quitada" else "", color = if (quitada) NeonGreen else NeonCyan, fontSize = 12.sp)
                }
                IconButton(onClick = { editando = d }) { Icon(Icons.Default.Edit, "Editar", tint = Color.White.copy(0.7f)) }
                IconButton(onClick = { excluir = d }) { Icon(Icons.Default.Delete, "Excluir", tint = NeonRed) }
            }
            Text(
                formatarMoedaBR(d.saldoDevedor, isPrivate) + (d.valorOriginal?.takeIf { it > 0 }?.let { " de ${formatarMoedaBR(it, isPrivate)}" } ?: ""),
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(0.08f), RoundedCornerShape(5.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pr.toFloat().coerceIn(0.01f, 1f)).background(NeonGreen, RoundedCornerShape(5.dp)))
            }
            Text("${Math.round(pr * 100)}% quitado", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    d.credor,
                    if (d.taxaJurosMensal > 0) "${valorParaCampo(d.taxaJurosMensal)}% ao mês" else "sem juros informados",
                    d.parcelaMinima?.let { "parcela/mínimo ${formatarMoedaBR(it, isPrivate)}" } ?: "sem parcela informada",
                    d.parcelasRestantes?.let { "$it parcela(s) restante(s)" },
                    d.diaVencimento?.let { "vence dia $it" }
                ).joinToString(" · "),
                color = Color.White.copy(0.6f), fontSize = 11.sp
            )
            if (!quitada) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pagando = d }, enabled = contas.isNotEmpty()) { Text("Registrar pagamento", color = NeonCyan) }
                    TextButton(onClick = { jurando = d }) { Text("Juros do mês", color = NeonCyan) }
                }
            }
        }
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Dívidas", color = TextoClaroR58, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaroR58) } },
                actions = { IconButton(onClick = { criando = true }) { Icon(Icons.Default.Add, "Nova dívida", tint = NeonCyan) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Acompanhe o saldo devedor, registre pagamentos (viram despesas em \"Dívidas\") e simule a melhor forma de quitar.",
                    color = Color.White.copy(0.6f), fontSize = 12.sp
                )
            }
            aviso?.let { msg -> item { Text(msg, color = NeonRed, fontSize = 13.sp) } }
            if (dividas.isEmpty()) {
                item { Text("Nenhuma dívida cadastrada. Toque em + para criar.", color = Color.White.copy(0.5f), fontSize = 13.sp) }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Saldo devedor total", color = Color.White.copy(0.7f), fontSize = 12.sp)
                            Text(moeda(totalSaldo), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Parcelas / mínimos por mês", color = Color.White.copy(0.7f), fontSize = 12.sp)
                            Text(moeda(totalMinimos), color = Color.White)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Juros estimados no próximo mês", color = Color.White.copy(0.7f), fontSize = 12.sp)
                            Text(moeda(jurosMes), color = NeonOrange)
                        }
                    }
                }
                items(ativas, key = { it.id }) { CartaoDivida(it) }
                if (inativas.isNotEmpty()) {
                    item {
                        TextButton(onClick = { verQuitadas = !verQuitadas }) {
                            Text(if (verQuitadas) "Ocultar quitadas/inativas" else "Ver quitadas/inativas (${inativas.size})", color = NeonCyan)
                        }
                    }
                    if (verQuitadas) items(inativas, key = { it.id }) { CartaoDivida(it) }
                }

                if (ativas.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().background(CardFundoR58, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Simulador de quitação", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Juros compostos mensais, arredondados em centavos; o 1º pagamento é no mês atual. Nas estratégias, o mínimo de uma dívida quitada é repassado para as outras.",
                                color = Color.White.copy(0.6f), fontSize = 11.sp
                            )
                            CampoR58(extraTxt, { extraTxt = it }, "Orçamento extra por mês (além dos mínimos)", numerico = true, maxChars = 12)
                            comp.avisos.forEach { Text("⚠ $it", color = NeonOrange, fontSize = 12.sp) }
                            listOf(comp.minimos, comp.bolaDeNeve, comp.avalanche).forEach { r ->
                                Column {
                                    Text(ROTULO_ESTRATEGIA.getValue(r.estrategia), color = COR_ESTRATEGIA.getValue(r.estrategia), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    if (r.nuncaQuita) {
                                        Text("Nunca quita (limite de ${Dividas.MAX_MESES} meses)", color = NeonRed, fontSize = 12.sp)
                                    } else {
                                        val q = Dividas.dataQuitacao(anoRef, mesRef, r.meses)
                                        Text(
                                            "${r.meses} mes(es)" + (q?.let { " · quita em ${mesAnoExtenso(it.ano, it.mes)}" } ?: "") +
                                                " · juros ${moeda(r.jurosTotal)} · total pago ${moeda(r.pagoTotal)}",
                                            color = Color.White, fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            comp.economia.avalancheVsMinimos?.let { Text("Avalanche economiza ${moeda(it)} de juros frente a pagar só os mínimos.", color = NeonGreen, fontSize = 12.sp) }
                            comp.economia.bolaVsMinimos?.let { Text("Bola de neve economiza ${moeda(it)} de juros frente a pagar só os mínimos.", color = NeonGreen, fontSize = 12.sp) }
                            comp.economia.avalancheVsBola?.let { Text("Avalanche vs. bola de neve: ${moeda(it)} de diferença nos juros.", color = NeonGreen, fontSize = 12.sp) }
                            GraficoSaldo(totalSaldo, listOf(comp.minimos, comp.bolaDeNeve, comp.avalanche))
                            Text("Saldo devedor total mês a mês (laranja: só mínimos; ciano: bola de neve; verde: avalanche).", color = Color.White.copy(0.5f), fontSize = 10.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Dividas.Estrategia.entries.forEach { e ->
                                    FilterChip(
                                        selected = estrategiaVista == e, onClick = { estrategiaVista = e },
                                        label = { Text(ROTULO_ESTRATEGIA.getValue(e).substringBefore(" ("), fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                                    )
                                }
                            }
                        }
                    }
                    val vista = when (estrategiaVista) {
                        Dividas.Estrategia.MINIMOS -> comp.minimos
                        Dividas.Estrategia.BOLA_DE_NEVE -> comp.bolaDeNeve
                        Dividas.Estrategia.AVALANCHE -> comp.avalanche
                    }
                    if (vista.quitacoes.isNotEmpty()) {
                        item { Text("Ordem de quitação: " + vista.quitacoes.joinToString(" → ") { "${it.nome} (mês ${it.mes})" }, color = Color.White.copy(0.7f), fontSize = 12.sp) }
                    }
                    item { Text("Cronograma (mês · juros · pago · saldo)", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    items(vista.cronograma.take(36), key = { "m${estrategiaVista.name}${it.mes}" }) { c ->
                        val q = Dividas.dataQuitacao(anoRef, mesRef, c.mes)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(q?.let { "${it.mes.toString().padStart(2, '0')}/${it.ano}" } ?: "${c.mes}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                            Text(moeda(c.juros), color = NeonOrange, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(moeda(c.pago), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(moeda(c.saldoFinal), color = Color.White.copy(0.8f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    if (vista.cronograma.size > 36) item { Text("Mostrando os primeiros 36 de ${vista.cronograma.size} meses.", color = Color.White.copy(0.5f), fontSize = 11.sp) }
                }
                item {
                    Text(
                        "O saldo devedor é um controle próprio: o pagamento é uma despesa comum e o saldo da conta já cai por ela. Excluir esse lançamento não devolve o saldo devedor (ajuste editando a dívida).",
                        color = Color.White.copy(0.45f), fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (criando || editando != null) {
        FormularioDivida(
            inicial = editando, contas = contas,
            onDismiss = { criando = false; editando = null },
            onSalvar = { d ->
                scope.launch {
                    try {
                        repository.salvarDivida(d)
                        aviso = null; criando = false; editando = null
                    } catch (e: RegraFinanceiraException) { aviso = e.message }
                }
            }
        )
    }

    pagando?.let { d ->
        PagamentoDividaDialog(
            divida = d, contas = contas,
            onDismiss = { pagando = null },
            onConfirmar = { valor, conta, categoria, juros ->
                scope.launch {
                    try {
                        repository.pagarDivida(d.id, valor, conta, categoria = categoria, juros = juros)
                        aviso = null; pagando = null
                    } catch (e: RegraFinanceiraException) { aviso = e.message; pagando = null }
                }
            }
        )
    }

    jurando?.let { d ->
        val estimado = Dividas.paraSim(d)?.let { Dividas.jurosDoMes(it.saldo, it.taxaBp) } ?: 0L
        var texto by remember(d.id) { mutableStateOf(if (estimado > 0) valorParaCampo(Dinheiro.reais(estimado)) else "") }
        AlertDialog(
            onDismissRequest = { jurando = null }, containerColor = DeepSpaceBlue,
            title = { Text("Juros: ${d.nome}", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Soma os juros do mês ao saldo devedor. Nada é lançado em conta.", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    CampoR58(texto, { texto = it }, "Juros do mês", numerico = true, maxChars = 12, apoio = if (estimado > 0) "Estimativa pela taxa: ${FormatoCentavos.brl(estimado)}" else null)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = parseValorBR(texto)
                    scope.launch {
                        try {
                            repository.registrarJurosDivida(d.id, v ?: 0.0)
                            aviso = null
                        } catch (e: RegraFinanceiraException) { aviso = e.message }
                        jurando = null
                    }
                }) { Text("Somar ao saldo", color = NeonCyan) }
            },
            dismissButton = { TextButton(onClick = { jurando = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }

    excluir?.let { d ->
        AlertDialog(
            onDismissRequest = { excluir = null }, containerColor = DeepSpaceBlue,
            title = { Text("Excluir dívida", color = Color.White) },
            text = { Text("Excluir “${d.nome}”? Os lançamentos de pagamento já feitos continuam no extrato.", color = Color.White.copy(0.8f)) },
            confirmButton = { TextButton(onClick = { scope.launch { repository.excluirDivida(d.id) }; excluir = null }) { Text("Excluir", color = NeonRed) } },
            dismissButton = { TextButton(onClick = { excluir = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }
}

@Composable
private fun GraficoSaldo(inicial: Long, series: List<Dividas.ResultadoSim>) {
    Canvas(Modifier.fillMaxWidth().height(140.dp).background(Color.White.copy(0.04f), RoundedCornerShape(8.dp))) {
        val meses = maxOf(1, series.maxOfOrNull { it.cronograma.size } ?: 1)
        val topo = maxOf(1.0, inicial * 1.25)
        series.forEach { s ->
            val pontos = listOf(inicial) + s.cronograma.map { it.saldoFinal }
            val path = Path()
            pontos.forEachIndexed { m, v ->
                val x = m.toFloat() / meses * size.width
                val y = ((1.0 - minOf(v.toDouble(), topo) / topo) * size.height).toFloat()
                if (m == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val traco = when (s.estrategia) {
                Dividas.Estrategia.MINIMOS -> PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                Dividas.Estrategia.AVALANCHE -> PathEffect.dashPathEffect(floatArrayOf(20f, 8f))
                else -> null
            }
            drawPath(path, COR_ESTRATEGIA.getValue(s.estrategia), style = Stroke(width = 4f, pathEffect = traco))
        }
        drawLine(Color.White.copy(0.2f), Offset(0f, size.height), Offset(size.width, size.height))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FormularioDivida(inicial: Divida?, contas: List<ContaSaldo>, onDismiss: () -> Unit, onSalvar: (Divida) -> Unit) {
    var nome by remember { mutableStateOf(inicial?.nome.orEmpty()) }
    var tipo by remember { mutableStateOf(inicial?.tipo ?: "EMPRESTIMO") }
    var credor by remember { mutableStateOf(inicial?.credor.orEmpty()) }
    var saldo by remember { mutableStateOf(inicial?.let { valorParaCampo(it.saldoDevedor) }.orEmpty()) }
    var original by remember { mutableStateOf(inicial?.valorOriginal?.let { valorParaCampo(it) }.orEmpty()) }
    var taxa by remember { mutableStateOf(inicial?.taxaJurosMensal?.takeIf { it > 0 }?.let { valorParaCampo(it) }.orEmpty()) }
    var parcela by remember { mutableStateOf(inicial?.parcelaMinima?.let { valorParaCampo(it) }.orEmpty()) }
    var parcelas by remember { mutableStateOf(inicial?.parcelasRestantes?.toString().orEmpty()) }
    var dia by remember { mutableStateOf(inicial?.diaVencimento?.toString().orEmpty()) }
    var conta by remember { mutableStateOf(inicial?.conta) }
    var ativa by remember { mutableStateOf(inicial?.ativa ?: true) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text(if (inicial == null) "Nova dívida" else "Editar dívida", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(nome, { nome = it }, "Nome", maxChars = 60)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dividas.TIPOS.forEach { t ->
                        FilterChip(
                            selected = tipo == t, onClick = { tipo = t }, label = { Text(Dividas.ROTULOS.getValue(t), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                        )
                    }
                }
                CampoR58(credor, { credor = it }, "Credor (opcional)", maxChars = 60)
                CampoR58(saldo, { saldo = it }, "Saldo devedor", numerico = true, maxChars = 14)
                CampoR58(original, { original = it }, "Valor original (opcional, só para o progresso)", numerico = true, maxChars = 14)
                CampoR58(taxa, { taxa = it }, "Juros ao mês em % (opcional)", numerico = true, maxChars = 7)
                CampoR58(parcela, { parcela = it }, "Parcela / mínimo mensal (opcional)", numerico = true, maxChars = 14)
                CampoR58(parcelas, { parcelas = it.filter(Char::isDigit) }, "Parcelas restantes (opcional)", numerico = true, maxChars = 4)
                CampoR58(dia, { dia = it.filter(Char::isDigit) }, "Dia do vencimento 1-31 (opcional)", numerico = true, maxChars = 2)
                Text("Conta de pagamento (opcional)", color = Color.White.copy(0.6f), fontSize = 12.sp)
                SeletorContaR58(contas, conta) { conta = if (conta == it) null else it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dívida ativa", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = ativa, onCheckedChange = { ativa = it }, colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan.copy(0.5f), checkedThumbColor = NeonCyan))
                }
                erro?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = parseValorBR(saldo)
                if (s == null) { erro = "Informe o saldo devedor."; return@TextButton }
                val invalido = listOf(original, taxa, parcela).any { it.isNotBlank() && parseValorBR(it) == null }
                if (invalido) { erro = "Há um número inválido no formulário."; return@TextButton }
                erro = null
                onSalvar(
                    Divida(
                        id = inicial?.id ?: 0, nome = nome, tipo = tipo, credor = credor.ifBlank { null }, saldoDevedor = s,
                        valorOriginal = parseValorBR(original), taxaJurosMensal = parseValorBR(taxa) ?: 0.0, parcelaMinima = parseValorBR(parcela),
                        parcelasRestantes = parcelas.toIntOrNull(), diaVencimento = dia.toIntOrNull(), conta = conta, ativa = ativa
                    )
                )
            }) { Text("Salvar", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
private fun PagamentoDividaDialog(
    divida: Divida, contas: List<ContaSaldo>, onDismiss: () -> Unit,
    onConfirmar: (valor: Double, conta: String, categoria: String, juros: Double) -> Unit
) {
    val sugerido = divida.parcelaMinima?.takeIf { it > 0 }?.let { minOf(it, divida.saldoDevedor) } ?: divida.saldoDevedor
    var valor by remember { mutableStateOf(valorParaCampo(sugerido)) }
    var juros by remember { mutableStateOf("") }
    var conta by remember { mutableStateOf(divida.conta?.takeIf { c -> contas.any { it.conta == c } } ?: contas.firstOrNull()?.conta) }
    var categoria by remember { mutableStateOf(Dividas.CATEGORIA) }
    var erro by remember { mutableStateOf<String?>(null) }
    val v = parseValorBR(valor)
    val j = if (juros.isBlank()) 0.0 else parseValorBR(juros)
    val novoSaldo = if (v != null && j != null) Dinheiro.centavos(divida.saldoDevedor) + Dinheiro.centavos(j) - Dinheiro.centavos(v) else null

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Pagamento: ${divida.nome}", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Cria uma despesa paga na conta (categoria \"${Dividas.CATEGORIA}\" por padrão) e abate o saldo devedor. O saldo da conta cai uma vez só, pelo lançamento.",
                    color = Color.White.copy(0.7f), fontSize = 12.sp
                )
                CampoR58(valor, { valor = it }, "Valor pago", numerico = true, maxChars = 14)
                CampoR58(juros, { juros = it }, "Juros do mês (opcional)", numerico = true, maxChars = 14, apoio = "Soma ao saldo devedor antes de abater")
                SeletorContaR58(contas, conta) { conta = it }
                CampoR58(categoria, { categoria = it }, "Categoria", maxChars = 40)
                novoSaldo?.let { Text("Saldo devedor depois: ${FormatoCentavos.brl(maxOf(0L, it))}", color = NeonCyan, fontSize = 13.sp) }
                erro?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (v == null || j == null) { erro = "Informe valores válidos."; return@TextButton }
                val c = conta ?: run { erro = "Escolha a conta."; return@TextButton }
                onConfirmar(v, c, categoria, j)
            }) { Text("Registrar", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}
