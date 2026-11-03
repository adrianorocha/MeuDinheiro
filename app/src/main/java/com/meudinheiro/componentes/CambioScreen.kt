package com.meudinheiro.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.Bcb
import com.meudinheiro.funcoes.BcbCachePrefs
import com.meudinheiro.funcoes.BcbClient
import com.meudinheiro.funcoes.BcbControle
import com.meudinheiro.funcoes.HttpBcb
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.temRede
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange
import kotlinx.coroutines.launch

/**
 * R100 — Câmbio: cotações PTAX do Banco Central de 10 moedas (compra, venda, variação, data/hora e tipo de boletim), conversor
 * R$ ↔ moeda com spread/IOF informado pelo usuário e mini gráfico dos últimos ~30 fechamentos.
 *
 * Só consulta ao ABRIR a tela com cache vencido (TTL 6 h) ou em "Atualizar agora"; o histórico só ao tocar "Ver histórico".
 * A PTAX é referência de mercado, não a taxa de bancos/casas de câmbio. Nenhum dado do usuário é enviado.
 */
@Composable
fun CambioScreen(userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val controle = remember(userPrefs) { BcbControle(BcbCachePrefs(userPrefs), BcbClient(HttpBcb())) }
    val estado by controle.cambio.collectAsState()
    val historicos by controle.historico.collectAsState()
    var moedaGraf by remember { mutableStateOf("USD") }
    var verGrafico by remember { mutableStateOf(false) }

    // só ao abrir a tela: carrega o cache e, se passaram 6 h, consulta o Banco Central
    LaunchedEffect(controle) { controle.atualizarCambio(forcar = false, conectado = temRede(ctx)) }

    // o histórico só é consultado depois que o usuário pede o gráfico (e a cada troca de moeda)
    LaunchedEffect(verGrafico, moedaGraf) {
        if (!verGrafico) return@LaunchedEffect
        val h = controle.historico.value[moedaGraf]
        if (h == null || (h.pontos.isEmpty() && !h.carregando && h.erro == null)) controle.carregarHistorico(moedaGraf, temRede(ctx))
    }

    val snap = estado.snapshot
    val texto = Bcb.descreverEstado(Bcb.EstadoAtualizacao(snap?.atualizadoEm, estado.carregando, estado.erro, estado.offline))

    PlanTela("Câmbio", onBack) {
        item {
            PlanAviso(
                "Cotações PTAX do Banco Central (referência de mercado, não a taxa cobrada por bancos ou casas de câmbio).",
                fonte = "Fonte: ${Bcb.FONTE_BCB}; confira antes de decidir"
            )
        }
        item {
            PlanCartao {
                Text(texto, color = Color.White, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                estado.avisoParcial?.let { PlanNota(it, NeonOrange) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { scope.launch { controle.atualizarCambio(forcar = true, conectado = temRede(ctx)) } },
                        enabled = !estado.carregando,
                        modifier = Modifier.semantics { contentDescription = "Atualizar cotações agora com o Banco Central" }
                    ) { Text("Atualizar agora", fontSize = 12.sp, color = NeonCyan) }
                    if (estado.carregando) {
                        CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Atualizando" }, color = NeonCyan, strokeWidth = 2.dp)
                    }
                }
            }
        }
        item {
            PlanCartao("Cotações (R$ por 1 unidade da moeda)") {
                Bcb.MOEDAS.forEachIndexed { i, m ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.08f)))
                    LinhaCotacao(m, snap?.cotacoes?.firstOrNull { it.moeda == m.codigo }, estado.carregando)
                }
                PlanNota("Fonte: ${Bcb.FONTE_BCB}. Variação = venda contra o fechamento anterior. Fora de dia útil, vale o último fechamento disponível.")
            }
        }
        item { Conversor(snap?.cotacoes.orEmpty(), isPrivate) }
        item {
            PlanCartao("Histórico (últimos ~30 fechamentos)") {
                SeletorMoeda(moedaGraf) { moedaGraf = it }
                if (!verGrafico) {
                    PlanNota("O histórico só é consultado quando você pede (uma consulta ao Banco Central por moeda).")
                    Button(
                        onClick = { verGrafico = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue),
                        modifier = Modifier.semantics { contentDescription = "Ver histórico de ${Bcb.nomeMoeda(moedaGraf)}" }
                    ) { Text("Ver histórico") }
                } else {
                    val h = historicos[moedaGraf]
                    when {
                        h == null || h.carregando -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                            Text("Carregando histórico…", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        }
                        h.erro != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Não foi possível carregar o histórico: ${h.erro}.", color = CorNegativa, fontSize = 12.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            OutlinedButton(onClick = { scope.launch { controle.carregarHistorico(moedaGraf, temRede(ctx)) } }) { Text("Tentar de novo", fontSize = 12.sp, color = NeonCyan) }
                        }
                        h.pontos.isNotEmpty() -> MiniGrafico(moedaGraf, h.pontos)
                    }
                }
            }
        }
    }
}

/** "2026-10-28 13:05:31" -> "28/10/2026 13:05". */
private fun fmtDH(dh: String): String {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}:\\d{2})").find(dh) ?: return dh
    return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]} ${m.groupValues[4]}"
}

private fun fmtDia(iso: String) = "${iso.substring(8, 10)}/${iso.substring(5, 7)}"

/** Variação com seta e sinal (a cor nunca é a única pista). */
private fun textoVariacao(v: Double?): String = when {
    v == null -> "—"
    v > 0 -> "▲ +${fmtNum(v, 2)}%"
    v < 0 -> "▼ ${fmtNum(v, 2)}%"
    else -> "0,00%"
}

@Composable
private fun LinhaCotacao(m: Bcb.MoedaDef, c: Bcb.CotacaoMoeda?, carregando: Boolean) {
    val casas = Bcb.casas(m.codigo)
    val desc = if (c == null) "${m.nome}: sem cotação"
    else "${m.nome}: compra ${fmtNum(c.compra, casas)}, venda ${fmtNum(c.venda, casas)} reais por unidade; variação " +
        (c.variacaoPct?.let { (if (it > 0) "alta de " else if (it < 0) "queda de " else "de ") + fmtNum(Math.abs(it), 2) + " por cento" } ?: "indisponível") +
        "; ${fmtDH(c.dataHora)}, boletim ${if (c.intermediario) "intermediário, sem fechamento ainda" else c.tipo.rotulo.lowercase()}"
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = desc }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${m.codigo}  ${m.nome}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val v = c?.variacaoPct
            Text(
                textoVariacao(v), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = when { v == null -> Color.White.copy(0.5f); v > 0 -> CorPositiva; v < 0 -> CorNegativa; else -> Color.White.copy(0.7f) }
            )
        }
        if (c == null) {
            PlanNota(if (carregando) "Carregando…" else "Sem cotação (atualize).")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Compra ${fmtNum(c.compra, casas)}", color = Color.White.copy(0.85f), fontSize = 13.sp)
                Text("Venda ${fmtNum(c.venda, casas)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            PlanNota("${fmtDH(c.dataHora)} · " + if (c.intermediario) "intermediário (sem fechamento ainda)" else c.tipo.rotulo.lowercase())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SeletorMoeda(selecionada: String, aoEscolher: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Bcb.MOEDAS.forEach { m ->
            FilterChip(
                selected = m.codigo == selecionada, onClick = { aoEscolher(m.codigo) },
                label = { Text(m.codigo, fontSize = 12.sp) },
                modifier = Modifier.semantics { contentDescription = "Moeda ${m.nome}" },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f)
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Conversor(cotacoes: List<Bcb.CotacaoMoeda>, isPrivate: Boolean) {
    var moeda by remember { mutableStateOf("USD") }
    var deReais by remember { mutableStateOf(true) }
    var valor by remember { mutableStateOf("1000") }
    var spread by remember { mutableStateOf("0") }
    val cot = cotacoes.firstOrNull { it.moeda == moeda }
    val v = lerNum(valor)
    val sp = if (spread.isBlank()) 0.0 else lerNum(spread)
    val spOk = Bcb.spreadValido(sp)
    val res = if (cot != null && spOk) (if (deReais) Bcb.reaisParaMoeda(v, cot.venda, sp) else Bcb.moedaParaReais(v, cot.venda, sp)) else Double.NaN
    val casas = Bcb.casas(moeda)
    PlanCartao("Conversor") {
        SeletorMoeda(moeda) { moeda = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = deReais, onClick = { deReais = true }, label = { Text("R$ → $moeda", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
            )
            FilterChip(
                selected = !deReais, onClick = { deReais = false }, label = { Text("$moeda → R$", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
            )
        }
        CampoR58(valor, { valor = it }, if (deReais) "Valor em R$" else "Valor em $moeda", numerico = true, maxChars = 14)
        CampoR58(
            spread, { spread = it }, "Spread / IOF (%) — informe sua taxa", numerico = true, maxChars = 7,
            apoio = "Padrão 0%. Acréscimo da sua compra internacional (cartão, corretora, IOF); confira a taxa com a sua instituição."
        )
        if (!spOk) Text("Informe um percentual entre 0 e 100.", color = CorNegativa, fontSize = 12.sp)
        val resultado = when {
            cot == null -> "Sem cotação de $moeda ainda; atualize o câmbio."
            !spOk || !res.isFinite() -> "Informe um valor válido."
            isPrivate -> "Resultado oculto (modo privado)."
            deReais -> "R$ ${fmtNum(v, 2)} ≈ ${fmtNum(res, 2)} $moeda"
            else -> "${fmtNum(v, 2)} $moeda ≈ R$ ${fmtNum(res, 2)}"
        }
        Text(
            resultado, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Resultado da conversão: $resultado" }
        )
        if (cot != null) {
            PlanNota(
                "Usa a venda PTAX de R$ ${fmtNum(cot.venda, casas)} por 1 $moeda" + (if (sp > 0 && spOk) ", mais ${fmtNum(sp, 2)}% informado por você" else "") +
                    ". A PTAX é referência do mercado, não a cotação que você obterá; sem impostos ou tarifas além do que você informar."
            )
        }
    }
}

/** Mini gráfico de linha (Canvas) dos fechamentos + tabela equivalente para leitores de tela. */
@Composable
private fun MiniGrafico(moeda: String, pontos: List<Bcb.PontoHistorico>) {
    if (pontos.size < 2) {
        PlanNota("Poucos pontos para desenhar o gráfico.")
        return
    }
    val casas = Bcb.casas(moeda)
    val vs = pontos.map { it.venda }
    val min = vs.min()
    val max = vs.max()
    val folga = if (max == min) (if (max * 0.01 != 0.0) max * 0.01 else 1.0) else 0.0
    val lo = min - folga
    val hi = max + folga
    var tabela by remember { mutableStateOf(false) }
    val desc = "Venda PTAX de ${Bcb.nomeMoeda(moeda)} em reais nos últimos ${pontos.size} fechamentos, de ${fmtNum(vs.first(), casas)} em ${fmtDia(pontos.first().data)} " +
        "a ${fmtNum(vs.last(), casas)} em ${fmtDia(pontos.last().data)}; mínimo ${fmtNum(min, casas)}, máximo ${fmtNum(max, casas)}"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).background(Color.White.copy(0.04f), RoundedCornerShape(10.dp)).padding(8.dp)
                .semantics { contentDescription = desc }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                fun x(i: Int) = w * i / (pontos.size - 1)
                fun y(v: Double) = (h - ((v - lo) / (hi - lo)).toFloat() * h)
                drawLine(Color.White.copy(0.15f), Offset(0f, h), Offset(w, h), strokeWidth = 1f)
                val path = Path()
                pontos.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.venda)) else path.lineTo(x(i), y(p.venda)) }
                drawPath(path, NeonCyan, style = Stroke(width = 4f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("mín. ${fmtNum(min, casas)}", color = Color.White.copy(0.5f), fontSize = 10.sp)
            Text("${fmtDia(pontos.first().data)} a ${fmtDia(pontos.last().data)}", color = Color.White.copy(0.5f), fontSize = 10.sp)
            Text("máx. ${fmtNum(max, casas)}", color = Color.White.copy(0.5f), fontSize = 10.sp)
        }
        TextButton(onClick = { tabela = !tabela }, modifier = Modifier.semantics { contentDescription = if (tabela) "Ocultar valores em tabela" else "Ver valores em tabela" }) {
            Text(if (tabela) "Ocultar valores" else "Ver valores em tabela", color = NeonCyan, fontSize = 12.sp)
        }
        if (tabela) {
            pontos.reversed().forEach { PlanLinha(fmtDia(it.data), "R$ ${fmtNum(it.venda, casas)}") }
        }
    }
}
