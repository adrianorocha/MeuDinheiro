package com.meudinheiro.componentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.domain.ProjecaoFinanceira
import com.meudinheiro.domain.ProjecaoFinanceira.DiaCalendario
import com.meudinheiro.domain.ProjecaoFinanceira.EventoProjecao
import com.meudinheiro.domain.ProjecaoFinanceira.TipoEvento
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonRed
import java.util.Calendar
import java.util.Locale

private val TextoClaro = Color(0xFFE0E1DD)
private val CardFundo = Color(0xFF1B263B)
private val MESES = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
private val SEMANA = listOf("D", "S", "T", "Q", "Q", "S", "S")

private fun compacto(centavos: Long): String {
    val reais = Math.abs(centavos) / 100.0
    val t = if (reais >= 1000) String.format(Locale("pt", "BR"), "%.1fk", reais / 1000) else Math.round(reais).toString()
    return (if (centavos < 0) "-" else "") + t
}

private fun rotuloTipo(e: EventoProjecao) = when (e.tipo) {
    TipoEvento.RECEITA -> "A receber"
    TipoEvento.RECORRENCIA_RECEITA -> "Recebimento recorrente (ainda não lançado)"
    TipoEvento.RECORRENCIA_DESPESA -> "Despesa recorrente (ainda não lançada)"
    TipoEvento.FATURA -> "Fatura do cartão"
    TipoEvento.DESPESA -> "A pagar"
}

/** R52 — calendário financeiro mensal: vencimentos, recebimentos previstos e saldo projetado por dia (somente leitura). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarioFinanceiroScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoes by repository.cartoesFlow().collectAsState(initial = emptyList())
    var fixas by remember { mutableStateOf<List<DespesaFixa>>(emptyList()) }
    LaunchedEffect(despesas) { fixas = repository.obterTodasRecorrencias() }

    val agora = remember { System.currentTimeMillis() }
    val hojeCal = remember { Calendar.getInstance().apply { timeInMillis = agora } }
    var mes by remember { mutableStateOf(hojeCal.get(Calendar.MONTH) + 1) }
    var ano by remember { mutableStateOf(hojeCal.get(Calendar.YEAR)) }
    var escolhido by remember { mutableStateOf<Int?>(null) }

    val cal = remember(contas, cartoes, despesas, fixas, mes, ano) {
        ProjecaoFinanceira.calendarioDosDados(
            contas.map { ProjecaoFinanceira.ContaRef(it.id, it.conta, it.banco) }, cartoes, despesas, fixas, agora, mes, ano
        )
    }
    val hoje = ProjecaoFinanceira.serialDeMs(agora)
    val selecionado = cal.dias.firstOrNull { it.dia == escolhido } ?: cal.dias.firstOrNull { it.dia == hoje } ?: cal.dias.first()
    fun moeda(c: Long) = if (isPrivate) "••••••" else ProjecaoFinanceira.brlCentavos(c)
    fun mudarMes(delta: Int) {
        val idx = ano * 12 + (mes - 1) + delta
        ano = idx / 12
        mes = idx % 12 + 1
        escolhido = null
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Calendário financeiro", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { mudarMes(-1) }) { Icon(Icons.Default.ChevronLeft, "Mês anterior", tint = NeonCyan) }
                    Text("${MESES[mes - 1]} $ano", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { mudarMes(1) }) { Icon(Icons.Default.ChevronRight, "Próximo mês", tint = NeonCyan) }
                }
            }
            cal.alerta?.let { alerta ->
                item {
                    Text(
                        "⚠ $alerta", color = NeonRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().background(NeonRed.copy(0.12f), RoundedCornerShape(12.dp)).padding(12.dp)
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val menor = cal.menorSaldo
                    if (menor != null) {
                        Text(
                            "Menor saldo projetado: ${moeda(menor.centavos)} em ${ProjecaoFinanceira.ddmm(menor.dia)}",
                            color = if (menor.centavos < 0) NeonRed else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("Saldo projetado no fim do mês: ${moeda(cal.saldoFimDoMesCentavos ?: 0)}", color = Color.White.copy(0.8f), fontSize = 12.sp)
                    } else {
                        Text("Sem projeção para meses passados.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        SEMANA.forEach { Text(it, Modifier.weight(1f), color = Color.White.copy(0.5f), fontSize = 11.sp, textAlign = TextAlign.Center) }
                    }
                    val vazios = cal.dias.first().diaDaSemana
                    val celulas: List<DiaCalendario?> = List(vazios) { null } + cal.dias
                    celulas.chunked(7).forEach { semana ->
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            semana.forEach { d ->
                                if (d == null) Box(Modifier.weight(1f).height(58.dp))
                                else CelulaDia(d, d.dia == selecionado.dia, isPrivate, Modifier.weight(1f)) { escolhido = d.dia }
                            }
                            repeat(7 - semana.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                    Text(
                        "▲ recebimento previsto   ▼ vencimento (conta, fatura ou recorrência)   ⚠ saldo negativo (fundo vermelho)   número no rodapé: saldo projetado ao fim do dia (k = mil)",
                        color = Color.White.copy(0.55f), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item {
                val d = ProjecaoFinanceira.dataDeSerial(selecionado.dia)
                Text(
                    String.format(Locale.ROOT, "%02d/%02d/%d", d.dia, d.mes, d.ano) + (if (selecionado.ehHoje) " (hoje)" else "") +
                        " · ${selecionado.eventos.size} item(ns)",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
            if (selecionado.eventos.isEmpty()) {
                item { Text("Nenhum vencimento ou recebimento previsto neste dia.", color = Color.White.copy(0.6f), fontSize = 12.sp) }
            } else {
                items(selecionado.eventos.size) { i ->
                    val e = selecionado.eventos[i]
                    val entrada = e.centavos > 0
                    Row(
                        Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.descricao, color = Color.White, fontSize = 14.sp, maxLines = 1)
                            Text(
                                rotuloTipo(e) + " · " + e.origem + if (e.atrasado) " · ATRASADO" else "",
                                color = Color.White.copy(0.55f), fontSize = 11.sp
                            )
                        }
                        Text(
                            (if (entrada) "+ " else "- ") + moeda(Math.abs(e.centavos)),
                            color = if (entrada) NeonGreen else Color.White, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            selecionado.saldoProjetadoCentavos?.let { s ->
                item {
                    Text(
                        "Saldo projetado ao fim do dia: ${moeda(s)}",
                        color = if (s < 0) NeonRed else Color.White.copy(0.8f), fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CelulaDia(d: DiaCalendario, selecionado: Boolean, isPrivate: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val borda = when {
        selecionado -> NeonCyan
        d.negativo -> NeonRed.copy(0.7f)
        else -> Color.White.copy(0.12f)
    }
    val descricao = buildString {
        append("Dia ${d.diaDoMes}")
        if (d.ehHoje) append(", hoje")
        if (d.eventos.isNotEmpty()) append(", ${d.eventos.size} item(ns)")
        d.saldoProjetadoCentavos?.let { if (!isPrivate) append(", saldo projetado ${ProjecaoFinanceira.brlCentavos(it)}") }
        if (d.negativo) append(", negativo")
    }
    val forma = RoundedCornerShape(8.dp)
    Column(
        modifier
            .height(58.dp)
            .background(if (d.negativo) NeonRed.copy(0.16f) else Color.Transparent, forma)
            .border(1.dp, borda, forma)
            .semantics { contentDescription = descricao }
            .clickable(onClick = onClick)
            .padding(3.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                d.diaDoMes.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (d.ehHoje) NeonCyan else if (d.passado) Color.White.copy(0.4f) else Color.White
            )
            if (d.negativo) Text("⚠", color = NeonRed, fontSize = 10.sp)
        }
        Text(
            (if (d.entradasCentavos > 0) "▲" else "") + (if (d.saidasCentavos > 0) "▼" else "") +
                (if (d.eventos.isNotEmpty()) d.eventos.size.toString() else ""),
            color = Color.White.copy(0.7f), fontSize = 9.sp
        )
        Text(
            d.saldoProjetadoCentavos?.let { if (isPrivate) "•••" else compacto(it) } ?: "",
            color = if (d.negativo) NeonRed else Color.White.copy(0.85f), fontSize = 9.sp, maxLines = 1
        )
    }
}
