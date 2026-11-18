package com.meudinheiro.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.Bcb
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.IndicesEconomicos
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * R98 — peças compartilhadas das telas de índices e planejamento (Ferramentas > Índices e planejamento): estrutura de tela,
 * aviso de referência, cartões, formatação pt-BR determinística e gráfico de linhas em Canvas. Segue o visual das demais
 * telas de Ferramentas (fundo DeepSpaceBlue, cartões escuros, detalhes neon).
 */

internal val CardPlan = Color(0xFF1B263B)
internal val TextoPlan = Color(0xFFE0E1DD)

/** Tela com barra superior (botão Voltar com descrição acessível) e lista rolável. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanTela(titulo: String, onBack: () -> Unit, conteudo: LazyListScope.() -> Unit) {
    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text(titulo, color = TextoPlan, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoPlan) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { conteudo() }
    }
}

/** Aviso de valores de referência (sempre com a fonte). [extra] substitui a frase padrão. */
@Composable
internal fun PlanAviso(extra: String? = null, fonte: String = IndicesEconomicos.FONTE) {
    Column(
        Modifier.fillMaxWidth().background(NeonOrange.copy(0.12f), RoundedCornerShape(12.dp)).padding(12.dp)
            .semantics { contentDescription = "Aviso: valores de referência" }
    ) {
        Text(
            (extra ?: "Simulação com valores de referência editáveis. Não é recomendação de investimento nem oferta de crédito.") + " " +
                fonte + ".",
            color = NeonOrange, fontSize = 12.sp
        )
    }
}

@Composable
internal fun PlanCartao(titulo: String? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(CardPlan, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (titulo != null) Text(titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        conteudo()
    }
}

@Composable
internal fun PlanNota(texto: String, cor: Color = Color.White.copy(0.55f)) {
    Text(texto, color = cor, fontSize = 11.sp)
}

/** Linha "rótulo ........ valor". */
@Composable
internal fun PlanLinha(rotulo: String, valor: String, cor: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, color = Color.White.copy(0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(valor, color = cor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// ------------------------------------------------------------------ formatação e leitura (pt-BR, sem Locale)

/** `1234.5, 2` -> "1.234,50"; não finito -> "—". */
internal fun fmtNum(n: Double, casas: Int = 2): String {
    if (!n.isFinite()) return "—"
    val bd = BigDecimal(n).setScale(casas, RoundingMode.HALF_UP)
    val neg = bd.signum() < 0
    val plain = bd.abs().toPlainString()
    val inteiro = plain.substringBefore('.')
    val frac = if ('.' in plain) plain.substringAfter('.') else ""
    val grupos = inteiro.reversed().chunked(3).joinToString(".").reversed()
    return (if (neg && bd.signum() != 0) "-" else "") + grupos + if (casas > 0) ",$frac" else ""
}

/** Fração -> "13,76%". */
internal fun fmtPct(fracao: Double, casas: Int = 2): String = if (fracao.isFinite()) fmtNum(fracao * 100, casas) + "%" else "—"

/** Percentual (já em %, ex.: 1,08) -> texto do campo editável ("1,08"), sem zeros à direita nem milhar. */
internal fun campoNum(n: Double, maxCasas: Int = 4): String =
    if (!n.isFinite()) "" else BigDecimal(n).setScale(maxCasas, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

/** Texto digitado em pt-BR -> número; vazio/inválido -> NaN. */
internal fun lerNum(t: String): Double = parseValorBR(t) ?: Double.NaN

internal fun moedaC(centavos: Long, privado: Boolean): String = formatarMoedaBR(Dinheiro.reais(centavos), privado)

/** Esconde valores monetários "R$ 1.234,56" de um texto quando o modo privado está ativo. */
internal fun mascarar(texto: String, privado: Boolean): String =
    if (privado) texto.replace(Regex("-?R\\$ [\\d.]+,\\d{2}"), "••••••") else texto

// ------------------------------------------------------------------ persistência dos índices (DataStore)

/**
 * Estado dos índices. [conjunto] = valores EFETIVOS (base + Banco Central onde o usuário não editou): é o que as calculadoras
 * consomem. [base] = só semente + edições manuais (é o que se grava). [bcb] = último cache do Banco Central, se houver.
 * [salvar] grava uma nova BASE (nunca valores do BCB). Só LÊ o cache local: nenhuma consulta de rede acontece aqui.
 */
internal class IndicesLocais(
    val conjunto: IndicesEconomicos.Conjunto,
    val base: IndicesEconomicos.Conjunto,
    val bcb: Bcb.SnapshotIndices?,
    val salvar: (IndicesEconomicos.Conjunto) -> Unit
)

@Composable
internal fun rememberIndicesLocais(prefs: UserPreferences): IndicesLocais {
    val scope = rememberCoroutineScope()
    val texto by prefs.indicesEconomicosFlow.collectAsState(initial = "")
    val cacheTxt by prefs.bcbIndicesCacheFlow.collectAsState(initial = "")
    val base = remember(texto) { IndicesEconomicos.desserializar(texto) }
    val bcb = remember(cacheTxt) { Bcb.lerSnapshotIndices(cacheTxt) }
    val efetivo = remember(base, bcb) { Bcb.aplicarBcb(base, bcb) }
    return remember(efetivo, base, bcb, prefs) {
        IndicesLocais(efetivo, base, bcb) { novo -> scope.launch { prefs.salvarIndicesEconomicos(IndicesEconomicos.serializar(novo)) } }
    }
}

// ------------------------------------------------------------------ gráfico de linhas (Canvas)

/** [valores] em centavos, um por mês (índice 0 = hoje). */
internal class SerieGrafico(val nome: String, val cor: Color, val valores: List<Long>, val tracejada: Boolean = false)

/**
 * Gráfico simples de linhas em Canvas, com legenda (nome + cor + traço) e descrição acessível com os valores finais.
 * A cor nunca é a única informação: cada série tem nome na legenda e o estilo do traço varia.
 */
@Composable
internal fun PlanGrafico(series: List<SerieGrafico>, descricao: String, privado: Boolean, rotuloEixoX: String = "meses") {
    val todos = series.flatMap { it.valores }
    if (todos.isEmpty()) return
    val minV = minOf(todos.min(), 0L)
    val maxV = maxOf(todos.max(), minV + 1)
    val n = series.maxOf { it.valores.size }
    val resumo = descricao + ". " + series.joinToString("; ") { s ->
        "${s.nome}: " + if (privado) "valor oculto" else "termina em ${moedaC(s.valores.last(), false)}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(170.dp).background(Color.White.copy(0.04f), RoundedCornerShape(10.dp)).padding(8.dp)
                .semantics { contentDescription = resumo }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // linha do zero (se houver negativos) e base
                fun y(v: Long) = h - ((v - minV).toFloat() / (maxV - minV).toFloat()) * h
                drawLine(Color.White.copy(0.15f), Offset(0f, h), Offset(w, h), strokeWidth = 1f)
                if (minV < 0) drawLine(Color.White.copy(0.3f), Offset(0f, y(0)), Offset(w, y(0)), strokeWidth = 1.5f)
                for (s in series) {
                    if (s.valores.size < 2) continue
                    val path = Path()
                    s.valores.forEachIndexed { i, v ->
                        val x = if (n <= 1) 0f else w * i / (n - 1)
                        if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
                    }
                    drawPath(
                        path, s.cor,
                        style = Stroke(width = 4f, pathEffect = if (s.tracejada) PathEffect.dashPathEffect(floatArrayOf(18f, 10f)) else null)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("máx. ${if (privado) "••••••" else moedaC(maxV, false)}", color = Color.White.copy(0.5f), fontSize = 10.sp)
            Text("0 a ${n - 1} $rotuloEixoX", color = Color.White.copy(0.5f), fontSize = 10.sp)
            Text("mín. ${if (privado) "••••••" else moedaC(minV, false)}", color = Color.White.copy(0.5f), fontSize = 10.sp)
        }
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(s.cor, RoundedCornerShape(2.dp)))
                Text(s.nome + if (s.tracejada) " (tracejado)" else "", color = Color.White.copy(0.75f), fontSize = 11.sp)
            }
        }
    }
}

internal val CorPositiva = NeonGreen
internal val CorNegativa = NeonRed
internal val CorDestaque = NeonCyan
