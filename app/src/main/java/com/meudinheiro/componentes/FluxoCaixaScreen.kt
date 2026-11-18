package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.domain.Cortes
import com.meudinheiro.domain.FluxoCaixa
import com.meudinheiro.domain.IndicesEconomicos
import com.meudinheiro.domain.IndicesEconomicos.pctParaFracao
import com.meudinheiro.domain.InsightsEconomia
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * R98-E — Projeção de fluxo de caixa (6 a 12 meses) a partir do saldo atual, com meses negativos destacados, "quanto cortar por
 * mês" e insights de economia explicáveis (regra + cálculo) e dispensáveis. Regras idênticas às do portal (R97): ver
 * [FluxoCaixa] e [InsightsEconomia]. Estimativas sobre os lançamentos; não é recomendação personalizada.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FluxoCaixaScreen(repository: MainRepository, userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = rememberIndicesLocais(userPrefs).conjunto
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoes by repository.cartoesFlow().collectAsState(initial = emptyList())
    val metas by repository.getTodasMetas().collectAsState(initial = emptyList())
    var fixas by remember { mutableStateOf<List<DespesaFixa>>(emptyList()) }
    LaunchedEffect(Unit) { fixas = repository.obterTodasRecorrencias() }
    val dispensadosTxt by userPrefs.insightsDispensadosFlow.collectAsState(initial = "")
    val dispensados = remember(dispensadosTxt) { InsightsEconomia.desserializarDispensados(dispensadosTxt) }
    val agora = remember { System.currentTimeMillis() }

    // Padrão da inflação: média mensal (geométrica) do IPCA dos meses disponíveis, 4 casas (evita extrapolar um mês atípico).
    val ipca = IndicesEconomicos.buscar(c, "ipca")
    val inflPadrao = IndicesEconomicos.mediaMensalPct(ipca.serie).let {
        if (it.isFinite()) BigDecimal(it).setScale(4, RoundingMode.HALF_UP).toDouble() else ipca.valorMensalPct
    }
    var meses by remember { mutableStateOf(12) }
    var infl by remember { mutableStateOf<String?>(null) }
    val inflTxt = infl ?: campoNum(inflPadrao)
    val inflFracao = pctParaFracao(lerNum(inflTxt))
    val inflOk = inflFracao.isFinite() && inflFracao > -0.5 && inflFracao < 1
    val cdi = pctParaFracao(IndicesEconomicos.buscar(c, "cdi").valorMensalPct)

    val r = remember(contas, cartoes, despesas, fixas, meses, inflFracao, inflOk) {
        if (inflOk) FluxoCaixa.projetar(contas, cartoes, despesas, fixas, agora, meses, inflFracao) else null
    }
    val insights = remember(despesas, fixas, metas, inflFracao, cdi, inflOk) {
        if (inflOk) InsightsEconomia.gerar(despesas, fixas, metas, agora, InsightsEconomia.Parametros(inflFracao, cdi, 1.0)) else emptyList()
    }
    var cortePctFlex by remember { mutableStateOf(campoNum(Cortes.PCT_FLEXIVEL_PADRAO * 100)) }
    var cortePctEss by remember { mutableStateOf(campoNum(Cortes.PCT_ESSENCIAL_PADRAO * 100)) }
    var corteAlvoTxt by remember { mutableStateOf<String?>(null) }
    fun lerPct(t: String): Double? = lerNum(t).takeIf { it.isFinite() && it in 0.0..100.0 }?.div(100)
    val pctFlex = lerPct(cortePctFlex)
    val pctEss = lerPct(cortePctEss)
    val deficit = r?.cortePorMes ?: 0L
    val alvoCortes = corteAlvoTxt.let { t ->
        if (t == null) deficit else lerNum(t).takeIf { it.isFinite() }?.let { maxOf(0L, Math.round(it * 100)) } ?: 0L
    }
    val dispensadosCortes = remember(dispensados) { dispensados.filter { it.startsWith(Cortes.PREFIXO_ID) }.toSet() }
    val cortes = remember(despesas, fixas, pctFlex, pctEss, alvoCortes, dispensadosCortes) {
        if (pctFlex != null && pctEss != null) Cortes.calcular(despesas, fixas, agora, Cortes.Parametros(pctFlex, pctEss, alvoCortes, dispensadosCortes)) else null
    }
    val visiveis = insights.filter { it.id !in dispensados }
    val ocultos = insights.size - visiveis.size
    val semDados = contas.isEmpty() && despesas.isEmpty()

    fun moeda(v: Long) = moedaC(v, isPrivate)
    fun mesRotulo(m: Int, a: Int) = mesAnoExtenso(a, m)
    fun salvarDispensados(novos: Set<String>) { scope.launch { userPrefs.salvarInsightsDispensados(InsightsEconomia.serializarDispensados(novos)) } }

    PlanTela("Fluxo de caixa e economia", onBack) {
        item { PlanAviso("Projeção e insights calculados neste aparelho, com base nos seus lançamentos e em premissas editáveis. Estimativas, não garantias.") }
        if (semDados) {
            item {
                PlanCartao("Sem dados ainda") {
                    PlanNota("Cadastre contas, lançamentos e recorrências para ver a projeção do seu fluxo de caixa e sugestões de economia.", Color.White.copy(0.7f))
                }
            }
        }
        item {
            PlanCartao("Projeção de fluxo de caixa") {
                Text("Horizonte", color = Color.White.copy(0.7f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (FluxoCaixa.MESES_MIN..FluxoCaixa.MESES_MAX).forEach { n ->
                        FilterChip(
                            selected = meses == n, onClick = { meses = n }, label = { Text("$n meses", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                        )
                    }
                }
                CampoR58(
                    inflTxt, { infl = it }, "Inflação mensal (%)", numerico = true, maxChars = 8,
                    apoio = if (inflOk) "Padrão: média mensal do IPCA dos meses da tela de índices. Corrige a despesa variável." else "Informe um percentual válido."
                )
            }
        }
        if (r != null) {
            if (FluxoCaixa.vazio(r)) {
                item { Text("Ainda não há saldo, recorrências ou histórico de gastos para projetar.", color = Color.White.copy(0.6f), fontSize = 13.sp) }
            } else {
                val ultimo = r.meses.last()
                item {
                    PlanCartao("Resumo") {
                        PlanLinha("Saldo atual das contas", moeda(r.saldoInicial), if (r.saldoInicial < 0) CorNegativa else Color.White)
                        PlanLinha("Despesa variável estimada (média 3 meses, em valores de hoje)", moeda(r.variavelBase))
                        PlanLinha("Saldo em ${mesRotulo(ultimo.mes, ultimo.ano)}", moeda(ultimo.saldoFinal), if (ultimo.saldoFinal < 0) CorNegativa else CorPositiva)
                        PlanLinha("Meses com saldo negativo", r.meses.count { it.negativo }.toString(), if (r.primeiroNegativo != null) CorNegativa else Color.White)
                        val pn = r.primeiroNegativo
                        if (pn != null) {
                            val m = r.meses[pn]
                            Text(
                                mascarar(
                                    "O saldo previsto fica negativo a partir de ${mesRotulo(m.mes, m.ano)}. Para fechar o déficit seria preciso cortar ou poupar cerca de " +
                                        "${FluxoCaixaFmt.brl(r.cortePorMes)} por mês, a partir deste mês (menor valor constante que mantém todos os saldos mensais em zero ou mais).",
                                    isPrivate
                                ),
                                color = CorNegativa, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text("Nenhum mês com saldo negativo nesta projeção.", color = CorPositiva, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (r.mesesComDados < 3) PlanNota("Há menos de 3 meses completos de histórico de gastos variáveis; a estimativa pode estar subavaliada.", NeonOrange)
                    }
                }
                items(r.meses, key = { "m${it.indice}" }) { m ->
                    val desc = "${mesRotulo(m.mes, m.ano)}: entradas ${moeda(m.entradas)}, saídas conhecidas ${moeda(m.saidas)}, variável estimada ${moeda(m.variavel)}, saldo final ${moeda(m.saldoFinal)}" +
                        if (m.negativo) ", saldo negativo" else ""
                    Column(
                        Modifier.fillMaxWidth().then(Modifier.semantics { contentDescription = desc })
                    ) {
                        PlanCartao(mesRotulo(m.mes, m.ano) + if (m.negativo) "  ·  saldo negativo" else "") {
                            PlanLinha("Entradas", moeda(m.entradas))
                            PlanLinha("Saídas conhecidas", moeda(m.saidas))
                            PlanLinha("Variável estimada", moeda(m.variavel))
                            PlanLinha("Resultado do mês", moeda(m.resultado), if (m.resultado < 0) CorNegativa else CorPositiva)
                            PlanLinha("Saldo final", moeda(m.saldoFinal), if (m.negativo) CorNegativa else Color.White)
                        }
                    }
                }
                item {
                    PlanCartao("Saldo projetado ao fim de cada mês") {
                        PlanGrafico(
                            series = listOf(SerieGrafico("Saldo projetado", NeonCyan, listOf(r.saldoInicial) + r.meses.map { it.saldoFinal })),
                            descricao = "Saldo projetado ao fim de cada mês, a partir do saldo atual", privado = isPrivate
                        )
                    }
                }
                item {
                    PlanCartao("Como a projeção é calculada") {
                        listOf(
                            "Saldo inicial: saldo atual das contas.",
                            "Entradas e saídas conhecidas: pendências (inclui parcelas futuras), faturas de cartão já lançadas (no vencimento) e recorrências ainda não lançadas.",
                            "Variável: média dos 3 últimos meses completos de gastos sem recorrência nem parcela, corrigida pela inflação informada; no mês atual conta só o que ainda falta.",
                            "Corte por mês: o menor valor constante c tal que saldo + c × (meses decorridos) fique em zero ou mais em todos os meses.",
                            "Não inclui receita variável nem imprevistos; o pagamento de compras novas no cartão é contado no mês da compra."
                        ).forEach { PlanNota("• $it") }
                    }
                }
            }
        }
        item {
            CortesSecao(
                cortes, r, cortePctFlex, { cortePctFlex = it }, pctFlex != null, cortePctEss, { cortePctEss = it }, pctEss != null,
                corteAlvoTxt ?: campoNum(deficit / 100.0, 2), { corteAlvoTxt = it }, dispensadosCortes.size, isPrivate,
                onDispensar = { id -> salvarDispensados(dispensados + id) },
                onRestaurar = { salvarDispensados(dispensados - dispensadosCortes) }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("INSIGHTS DE ECONOMIA", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (ocultos > 0) TextButton(onClick = { salvarDispensados(dispensadosCortes) }) { Text("Mostrar $ocultos dispensado(s)", color = NeonCyan, fontSize = 12.sp) }
            }
        }
        if (visiveis.isEmpty()) {
            item {
                Text(
                    if (insights.isEmpty()) "Nenhuma sugestão por enquanto. Com mais lançamentos, metas com prazo e recorrências, as regras passam a mostrar oportunidades."
                    else "Você dispensou todos os insights atuais.",
                    color = Color.White.copy(0.6f), fontSize = 13.sp
                )
            }
        } else {
            items(visiveis, key = { it.id }) { i -> CartaoInsight(i, isPrivate) { salvarDispensados(dispensados + i.id) } }
        }
        item {
            PlanNota(
                "Sugestões por regras simples sobre os seus lançamentos; não consideram sua situação completa nem constituem recomendação personalizada. " +
                    "Metas usam o rendimento líquido de ${fmtPct(cdi)} a.m. (CDI da tela de índices, 100%, IR regressivo). ${IndicesEconomicos.FONTE}."
            )
        }
    }
}

private object FluxoCaixaFmt {
    fun brl(c: Long) = com.meudinheiro.domain.FormatoCentavos.brl(c)
}

@Composable
private fun CartaoInsight(i: InsightsEconomia.Insight, privado: Boolean, onDispensar: () -> Unit) {
    var aberto by remember(i.id) { mutableStateOf(false) }
    PlanCartao {
        Text(i.titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(mascarar("Estimativa: ${FluxoCaixaFmt.brl(i.valorMensal)}/mês", privado), color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { aberto = !aberto }) { Text(if (aberto) "Ocultar regra e cálculo" else "Ver regra e cálculo", fontSize = 12.sp, color = NeonCyan) }
            TextButton(onClick = onDispensar, modifier = Modifier.semantics { contentDescription = "Dispensar: ${i.titulo}" }) {
                Text("Dispensar", color = Color.White.copy(0.7f), fontSize = 12.sp)
            }
        }
        if (aberto) {
            PlanNota("Regra: ${i.regra}", Color.White.copy(0.8f))
            i.calculo.forEach { PlanNota("• " + mascarar(it, privado), Color.White.copy(0.8f)) }
        }
    }
}

private fun rotuloTipo(t: Cortes.Tipo) = when (t) {
    Cortes.Tipo.EVITAVEL -> "Evitável"
    Cortes.Tipo.ASSINATURA -> "Assinatura"
    Cortes.Tipo.FREQUENTE -> "Compras frequentes"
    Cortes.Tipo.FLEXIVEL -> "Gasto flexível"
    Cortes.Tipo.ESSENCIAL -> "Essencial"
}

/** R103 — "Onde cortar para economizar": cortes por tipo, plano para a meta mensal e impacto no fluxo projetado. Regras em [Cortes]. */
@Composable
private fun CortesSecao(
    res: Cortes.Resultado?, fluxo: FluxoCaixa.Resultado?,
    flexTxt: String, aoFlex: (String) -> Unit, flexOk: Boolean,
    essTxt: String, aoEss: (String) -> Unit, essOk: Boolean,
    alvoTxt: String, aoAlvo: (String) -> Unit,
    ocultos: Int, privado: Boolean, onDispensar: (String) -> Unit, onRestaurar: () -> Unit
) {
    fun m(v: Long) = moedaC(v, privado)
    PlanCartao("Onde cortar para economizar") {
        PlanNota("Calculado sobre a média dos 3 últimos meses completos. Cada lançamento aparece em um só bloco, do corte menos doloroso (juros, assinaturas) ao mais difícil (essenciais).")
        CampoR58(flexTxt, aoFlex, "Reduzir gastos flexíveis (%)", numerico = true, maxChars = 6, apoio = if (flexOk) null else "Use de 0 a 100.")
        CampoR58(essTxt, aoEss, "Reduzir essenciais (%)", numerico = true, maxChars = 6, apoio = if (essOk) "Renegociar, pesquisar preço, evitar desperdício." else "Use de 0 a 100.")
        CampoR58(
            alvoTxt, aoAlvo, "Meta de economia por mês (R$)", numerico = true, maxChars = 14,
            apoio = if ((fluxo?.cortePorMes ?: 0L) > 0) "Padrão: o corte necessário para fechar o déficit da projeção." else "Digite quanto quer economizar por mês."
        )
        if (ocultos > 0) TextButton(onClick = onRestaurar, modifier = Modifier.semantics { contentDescription = "Mostrar cortes dispensados" }) {
            Text("Mostrar $ocultos dispensado(s)", color = NeonCyan, fontSize = 12.sp)
        }
        if (res == null) return@PlanCartao
        if (res.mesesComDados < 3) PlanNota("Há menos de 3 meses completos de gastos variáveis; as médias podem estar subavaliadas.", NeonOrange)
        if (res.cortes.isEmpty()) {
            PlanNota("Nenhum corte sugerido por enquanto. Com mais lançamentos nos últimos 3 meses, esta lista passa a mostrar onde economizar.", Color.White.copy(0.7f))
        } else {
            PlanLinha("Economia possível por mês", m(res.totalMensal), CorPositiva)
            PlanLinha("Por ano", m(res.totalAnual))
            PlanLinha("Cortes sugeridos", res.cortes.size.toString())
            val plano = res.plano
            if (plano != null) {
                val msg = "Plano para economizar ${m(plano.alvo)}/mês: ${plano.selecionados.size} corte(s), ${m(plano.economia)}/mês" +
                    if (plano.atinge) " — meta atingida." else " — faltam ${m(plano.falta)}/mês (os cortes sugeridos não bastam; veja receitas ou prazos)."
                Text(msg, color = if (plano.atinge) CorPositiva else NeonOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = msg })
                val ultimo = fluxo?.meses?.lastOrNull()
                if (fluxo != null && ultimo != null) {
                    val imp = Cortes.aplicarCorteNoFluxo(fluxo, plano.economia)
                    PlanNota(
                        "No fluxo de caixa: saldo em ${mesAnoExtenso(ultimo.ano, ultimo.mes)} de ${m(ultimo.saldoFinal)} para ${m(imp.saldoFinalDepois)}; " +
                            "meses negativos: ${imp.mesesNegativosAntes} → ${imp.mesesNegativosDepois}.",
                        Color.White.copy(0.8f)
                    )
                }
            }
            val noPlano = plano?.selecionados?.map { it.id }?.toSet().orEmpty()
            res.cortes.forEach { c -> CartaoCorte(c, c.id in noPlano, privado) { onDispensar(c.id) } }
        }
        if (res.maioresGastos.isNotEmpty()) {
            Text("Maiores gastos variáveis (média mensal)", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            res.maioresGastos.forEach { g -> PlanLinha("${g.nome} · ${g.categoria} · ${g.compras} compra(s)", m(g.mensal)) }
        }
        PlanNota("Estimativas por regras simples sobre os seus lançamentos (percentuais editáveis); não consideram sua situação completa nem são recomendação personalizada.")
    }
}

@Composable
private fun CartaoCorte(c: Cortes.Corte, noPlano: Boolean, privado: Boolean, onDispensar: () -> Unit) {
    var aberto by remember(c.id) { mutableStateOf(false) }
    val resumo = "Hoje ${moedaC(c.mensalAtual, privado)}/mês · economia estimada ${moedaC(c.economiaMensal, privado)}/mês"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(c.titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(rotuloTipo(c.tipo) + if (noPlano) " · No plano" else "", color = if (noPlano) NeonGreen else NeonCyan, fontSize = 12.sp)
        Text(resumo, color = Color.White.copy(0.85f), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { aberto = !aberto }, modifier = Modifier.semantics { contentDescription = (if (aberto) "Ocultar" else "Ver") + " cálculo: ${c.titulo}" }) {
                Text(if (aberto) "Ocultar cálculo" else "Ver cálculo", fontSize = 12.sp, color = NeonCyan)
            }
            TextButton(onClick = onDispensar, modifier = Modifier.semantics { contentDescription = "Dispensar: ${c.titulo}" }) {
                Text("Dispensar", color = Color.White.copy(0.7f), fontSize = 12.sp)
            }
        }
        if (aberto) c.calculo.forEach { PlanNota("• " + mascarar(it, privado), Color.White.copy(0.8f)) }
    }
}
