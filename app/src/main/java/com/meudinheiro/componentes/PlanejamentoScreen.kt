package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Meta
import com.meudinheiro.domain.Analises
import com.meudinheiro.domain.Financas
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

private val TextoClaro = Color(0xFFE0E1DD)

private fun parseBR(s: String): Double? = s.trim().replace(".", "").replace(',', '.').toDoubleOrNull()

/** Planejamento: reserva de emergência, 50/30/20, assinaturas, metas com prazo, melhor dia de compra e simulador. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanejamentoScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val agora = remember { System.currentTimeMillis() }
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoes by repository.cartoesFlow().collectAsState(initial = emptyList())
    val metas by repository.getTodasMetas().collectAsState(initial = emptyList())
    val investimentos by repository.investimentosFlow().collectAsState(initial = emptyList())
    var fixas by remember { mutableStateOf<List<DespesaFixa>>(emptyList()) }
    LaunchedEffect(Unit) { fixas = repository.obterTodasRecorrencias() }
    var metaEmEdicao by remember { mutableStateOf<Meta?>(null) }
    val data = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    val reserva = remember(despesas, contas, investimentos) {
        Analises.reservaEmergencia(agora, despesas, contas.map { it.saldo }, investimentos)
    }
    val mes = Financas.mesDe(agora)
    val ano = Financas.anoDe(agora)
    val doMes = remember(despesas) { despesas.filter { it.dataMs in Financas.inicioDoMes(mes, ano)..Financas.fimDoMes(mes, ano) } }
    val receitasMes = remember(despesas) { Financas.kpisPeriodo(despesas, Financas.inicioDoMes(mes, ano), Financas.fimDoMes(mes, ano)).receitasRealizadas }
    val regra = remember(doMes, receitasMes) { Analises.regra503020(receitasMes, doMes) }
    val assinaturas = remember(despesas, fixas) { Analises.assinaturas(agora, despesas, fixas) }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Planejamento", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------- reserva de emergência (R25)
            item {
                Secao("RESERVA DE EMERGÊNCIA") {
                    when (reserva.status) {
                        Analises.StatusReserva.SEM_DADOS -> Texto("Registre despesas de pelo menos um mês completo para calcular.", Color.White.copy(0.6f))
                        else -> {
                            val cor = when (reserva.status) { Analises.StatusReserva.OK -> NeonGreen; Analises.StatusReserva.ATENCAO -> NeonOrange; else -> NeonRed }
                            Text("%.1f meses".format(Locale("pt", "BR"), reserva.meses ?: 0.0), color = cor, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Barra(((reserva.meses ?: 0.0) / Analises.META_RESERVA_MESES).toFloat(), cor)
                            Texto("Liquidez ${formatarMoedaBR(reserva.liquidez, isPrivate)} (contas + renda fixa) ÷ gasto médio ${formatarMoedaBR(reserva.mediaDespesas3m ?: 0.0, isPrivate)}/mês (últimos 3 meses).")
                            if (reserva.faltante > 0) Texto("Faltam ${formatarMoedaBR(reserva.faltante, isPrivate)} para ${Analises.META_RESERVA_MESES} meses de reserva.", NeonOrange)
                        }
                    }
                }
            }

            // ---------------- 50/30/20 (R29)
            item {
                Secao("REGRA 50 / 30 / 20 — ESTE MÊS") {
                    if (receitasMes <= 0) Texto("Sem receitas registradas neste mês.", Color.White.copy(0.6f))
                    listOf(
                        Triple("Necessidades", regra.necessidades, false),
                        Triple("Desejos", regra.desejos, false),
                        Triple("Poupança", regra.poupanca, true)
                    ).forEach { (nome, g, poupanca) ->
                        val ok = g.status == Analises.StatusGrupo.OK
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(nome, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                "%.0f%% (meta %s%d%%) · %s".format(Locale("pt", "BR"), g.percentual, if (poupanca) "≥ " else "≤ ", g.alvo, formatarMoedaBR(g.valor, isPrivate)),
                                color = if (ok) NeonGreen else NeonRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Barra((g.percentual / 100).toFloat(), if (ok) NeonGreen else NeonRed)
                    }
                }
            }

            // ---------------- assinaturas (R26)
            item {
                Secao("ASSINATURAS E GASTOS RECORRENTES") {
                    if (assinaturas.isEmpty()) Texto("Nenhuma assinatura ou gasto recorrente identificado.", Color.White.copy(0.6f))
                    else {
                        val mensal = assinaturas.sumOf { it.totalMensal }
                        Text("${formatarMoedaBR(mensal, isPrivate)} por mês · ${formatarMoedaBR(mensal * 12, isPrivate)} por ano", color = NeonCyan, fontWeight = FontWeight.Bold)
                        assinaturas.forEach { a ->
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.nome, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                    Text(
                                        (if (a.origem == Analises.OrigemAssinatura.FIXA) "Despesa fixa" else "Detectada") + " · ${a.categoria}" +
                                            (a.ultimaData?.let { " · última em ${data.format(Date(it))}" } ?: ""),
                                        color = Color.White.copy(0.5f), fontSize = 11.sp
                                    )
                                }
                                Text(formatarMoedaBR(a.valorMedio, isPrivate), color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ---------------- metas com prazo (R27)
            item {
                Secao("METAS COM PRAZO") {
                    if (metas.isEmpty()) Texto("Você ainda não tem metas. Crie uma na aba Cofrinhos.", Color.White.copy(0.6f))
                    metas.forEach { m ->
                        val p = Analises.prazoDaMeta(m, agora, despesas)
                        val (rotulo, cor) = when (p.status) {
                            Analises.StatusMeta.CONCLUIDA -> "Concluída 🎉" to NeonGreen
                            Analises.StatusMeta.ATRASADA -> "Prazo vencido" to NeonRed
                            Analises.StatusMeta.NO_RITMO -> "No ritmo" to NeonGreen
                            Analises.StatusMeta.ABAIXO -> "Abaixo do ritmo" to NeonOrange
                            Analises.StatusMeta.SEM_PRAZO -> "Sem prazo" to Color.White.copy(0.6f)
                        }
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.nome, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(rotulo, color = cor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Barra((if (m.valorObjetivo > 0) m.valorGuardado / m.valorObjetivo else 0.0).toFloat(), cor)
                            Texto("${formatarMoedaBR(m.valorGuardado, isPrivate)} de ${formatarMoedaBR(m.valorObjetivo, isPrivate)} · faltam ${formatarMoedaBR(p.restante, isPrivate)}")
                            p.aporteMensalNecessario?.let {
                                Texto("Guarde ${formatarMoedaBR(it, isPrivate)} por mês até ${data.format(Date(m.dataAlvo!!))} (${p.mesesRestantes} meses). Seu ritmo: ${formatarMoedaBR(p.ritmoMensal, isPrivate)}/mês.")
                            }
                            TextButton(onClick = { metaEmEdicao = m }) { Text(if (m.dataAlvo == null) "Definir prazo" else "Alterar prazo", color = NeonCyan) }
                        }
                    }
                }
            }

            // ---------------- melhor dia de compra (R30)
            val fisicos = cartoes.filter { it.cartaoPrincipalId == null }
            if (fisicos.isNotEmpty()) item {
                Secao("MELHOR DIA PARA COMPRAR NO CARTÃO") {
                    fisicos.forEach { c ->
                        val m = Analises.melhorDiaDeCompra(c.diaFechamento, c.diaVencimento, agora)
                        Texto("${c.nome} (fecha dia ${c.diaFechamento}): compre a partir do dia ${m.dia} e tenha até ${m.prazoMaximoDias} dias para pagar.", Color.White)
                    }
                }
            }

            // ---------------- simulador (R28)
            item { Simulador(isPrivate) }
        }
    }

    metaEmEdicao?.let { meta ->
        CustomCalendarDialog(
            onDismiss = { metaEmEdicao = null },
            onDateSelected = { y, m, d ->
                val ms = Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0, 0) }.timeInMillis
                scope.launch { runCatching { repository.salvarMeta(meta.copy(dataAlvo = ms)) } }
                metaEmEdicao = null
            }
        )
    }
}

@Composable
private fun Simulador(isPrivate: Boolean) {
    var aVista by remember { mutableStateOf("") }
    var parcelas by remember { mutableStateOf("10") }
    var parcela by remember { mutableStateOf("") }
    var taxa by remember { mutableStateOf("1,0") }
    var entrada by remember { mutableStateOf("") }

    val resultado = remember(aVista, parcelas, parcela, taxa, entrada) {
        val v = parseBR(aVista); val n = parcelas.toIntOrNull(); val p = parseBR(parcela)
        if (v == null || v <= 0 || n == null || n < 1 || p == null) null
        else runCatching { Analises.simularParcelamento(v, n, p, (parseBR(taxa) ?: 1.0) / 100, parseBR(entrada) ?: 0.0) }.getOrNull()
    }

    Secao("SIMULADOR: PARCELAR × À VISTA") {
        Campo("Preço à vista (R$)", aVista) { aVista = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Campo("Nº de parcelas", parcelas, KeyboardType.Number) { parcelas = it.filter(Char::isDigit) } }
            Box(Modifier.weight(1f)) { Campo("Valor da parcela (R$)", parcela) { parcela = it } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Campo("Entrada (R$)", entrada) { entrada = it } }
            Box(Modifier.weight(1f)) { Campo("Rendimento (% ao mês)", taxa) { taxa = it } }
        }
        resultado?.let { r ->
            Spacer(Modifier.height(8.dp))
            val cor = if (r.parcelarVale) NeonGreen else NeonRed
            Text(
                if (r.parcelarVale) "Parcelar compensa" else "Pagar à vista compensa",
                color = cor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
            )
            Texto("Valor presente das parcelas: ${formatarMoedaBR(r.valorPresente, isPrivate)} (total pago ${formatarMoedaBR(r.totalParcelado, isPrivate)}).")
            Texto(
                (if (r.diferenca >= 0) "Economia de " else "Custo extra de ") + formatarMoedaBR(kotlin.math.abs(r.diferenca), isPrivate) +
                    " em valor presente, rendendo o dinheiro a ${taxa}% ao mês."
            )
            if (r.jurosImplicitosMensais > 0) Texto("Juros embutidos no parcelamento: %.2f%% ao mês.".format(Locale("pt", "BR"), r.jurosImplicitosMensais * 100))
        }
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, teclado: KeyboardType = KeyboardType.Decimal, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = onChange, singleLine = true, label = { Text(rotulo, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = teclado), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
            unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f)
        )
    )
}

@Composable
private fun Texto(t: String, cor: Color = Color.White.copy(0.7f)) {
    Text(t, color = cor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Barra(fracao: Float, cor: Color) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).background(Color.White.copy(0.08f), RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fracao.coerceIn(0.01f, 1f)).background(cor, RoundedCornerShape(4.dp)))
    }
}
