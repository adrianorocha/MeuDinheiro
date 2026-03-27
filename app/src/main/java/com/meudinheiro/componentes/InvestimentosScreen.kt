package com.meudinheiro.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.viewModel.InvestimentoViewModel
import com.meudinheiro.funcoes.Haptics // Nosso motor de vibração

// --- CORES PREMIUM ---
private val BgDark = Color(0xFF0D1B2A)
private val CardBg = Color(0xFF1B263B)
private val NeonRed = Color(0xFFFF4B4B)
@Composable
fun InvestimentosTab(
    viewModel: InvestimentoViewModel,
    isPrivate: Boolean
) {
    val context = LocalContext.current

    val meusAtivos by viewModel.investimentos.collectAsState()
    val patrimonioTotal by viewModel.patrimonioTotal.collectAsState()
    val rendimentoTotal by viewModel.rendimentoTotal.collectAsState()
    val porcentagemTotal by viewModel.porcentagemTotal.collectAsState()
    val distribuicao by viewModel.distribuicaoPorTipo.collectAsState()

    var investimentoParaEditar by remember { mutableStateOf<com.meudinheiro.data.Investimento?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 📈 HEADER COM GRÁFICO LASER
        item {
            PatrimonioChartCard(
                total = patrimonioTotal,
                rendimento = rendimentoTotal,
                porcentagem = porcentagemTotal,
                isPrivate = isPrivate
            )
        }

        // 📊 DISTRIBUIÇÃO CYBER
        if (distribuicao.isNotEmpty()) {
            item { DiversificacaoCyberCard(distribuicao = distribuicao) }
        }

        // ➕ BOTÃO DE NOVO APORTE
        item {
            Button(
                onClick = {
                    Haptics.vibrar(context, "movimento")
                    showAddDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "NOVO INVESTIMENTO",
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace, // Fonte de código
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        // TÍTULO DA LISTA
        item {
            Text(
                text = "MINHA CARTEIRA //",
                color = TextWhite.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        // LISTA DE ATIVOS REAIS
        items(meusAtivos, key = { it.id }) { ativo ->
            AtivoRowCard(
                ativo = ativo,
                isPrivate = isPrivate,
                onClick = {
                    Haptics.vibrar(context, "clique")
                    investimentoParaEditar = ativo
                }
            )
        }
    }

    // DIÁLOGOS
    investimentoParaEditar?.let { ativo ->
        EditValorDialog(
            investimento = ativo,
            onDismiss = { investimentoParaEditar = null },
            onConfirmar = { novoValor ->
                Haptics.vibrar(context, "sucesso")
                viewModel.atualizarValorAtivo(ativo, novoValor)
                investimentoParaEditar = null
            }
        )
    }

    if (showAddDialog) {
        AddInvestimentoDialog(
            onDismiss = { showAddDialog = false },
            onGuardar = { nome, tipo, valorInvestido, valorAtual ->
                Haptics.vibrar(context, "sucesso")
                viewModel.salvarInvestimento(nome, tipo, valorInvestido, valorAtual)
                showAddDialog = false
            }
        )
    }
}

// ============================================================================
// COMPONENTES DA TELA
// ============================================================================

@Composable
fun PatrimonioChartCard(
    total: Double,
    rendimento: Double,
    porcentagem: Double,
    isPrivate: Boolean
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "ChartAnim"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // VALORES
            Text(text = "PATRIMÔNIO INVESTIDO", color = TextWhite.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            Text(
                text = if (isPrivate) "R$ •••••" else formatarMoedaBR(total, false),
                color = TextWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )

            // RENDIMENTO BADGE
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .background(NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.TrendingUp, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isPrivate) "R$ ••• (••%)" else "+${formatarMoedaBR(rendimento, false)} (+$porcentagem%)",
                    color = NeonGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 🚀 GRÁFICO DE LINHA NEON COM GLOW VERDADEIRO
            Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val points = listOf(
                        size.height * 0.8f, size.height * 0.7f, size.height * 0.75f,
                        size.height * 0.5f, size.height * 0.4f, size.height * 0.2f
                    )

                    val stepX = size.width / (points.size - 1)
                    val path = Path()
                    val curvePoints = points.mapIndexed { i, p -> Offset(i * stepX, p) }

                    path.moveTo(curvePoints.first().x, curvePoints.first().y)
                    for (i in 0 until curvePoints.size - 1) {
                        val p1 = curvePoints[i]
                        val p2 = curvePoints[i + 1]
                        val cp1 = Offset((p1.x + p2.x) / 2f, p1.y)
                        val cp2 = Offset((p1.x + p2.x) / 2f, p2.y)
                        path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                    }

                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }

                    if (animProgress > 0) {
                        clipRect(right = size.width * animProgress) {
                            // Fundo degradê
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(listOf(NeonCyan.copy(alpha = 0.3f), Color.Transparent), endY = size.height)
                            )
                            // 🌟 O GLOW EFFECT (Linhas grossas com baixa opacidade)
                            drawPath(path, color = NeonCyan.copy(alpha = 0.15f), style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))
                            drawPath(path, color = NeonCyan.copy(alpha = 0.3f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                            // A linha principal fina
                            drawPath(path, color = NeonCyan, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AtivoRowCard(
    ativo: com.meudinheiro.data.Investimento,
    isPrivate: Boolean,
    onClick: () -> Unit
) {
    val isPositivo = ativo.rendimentoReal >= 0
    val corRentabilidade = if (isPositivo) NeonGreen else NeonRed
    val sinal = if (isPositivo) "+" else ""
    val porcentagemFormatada = "%.2f".format(ativo.rentabilidadePercentual)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // 🚀 FAIXA DE STATUS LATERAL (Grid Power Style)
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(corRentabilidade))

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ícone Ativo
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(corRentabilidade.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ativo.nome.take(2).uppercase(),
                        color = corRentabilidade,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Textos Esquerda
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ativo.nome,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = ativo.tipo.uppercase(), color = TextWhite.copy(0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
                }

                // Valores Direita (Estilo Terminal)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isPrivate) "R$ •••••" else formatarMoedaBR(ativo.valorAtual, false),
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "$sinal$porcentagemFormatada%",
                        color = corRentabilidade,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 🚀 DIVERSIFICAÇÃO CYBER (Corrigido para List<Pair>)
// ==========================================
@Composable
fun DiversificacaoCyberCard(distribuicao: List<Pair<String, Double>>) {
    val cores = listOf(NeonCyan, NeonGreen, NeonRed, Color(0xFFFFD54F), Color(0xFFB388FF))

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("ALOCAÇÃO DE ATIVOS //", color = TextWhite.copy(0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        // A Barra Colorida de "Uso de Memória"
        Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            distribuicao.forEachIndexed { index, pair ->
                val cor = cores[index % cores.size]
                Box(modifier = Modifier.weight(pair.second.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(cor))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // As Legendas
        distribuicao.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowItems.forEach { pair ->
                    // Busca o índice original para bater a cor da legenda com a cor da barra
                    val originalIndex = distribuicao.indexOf(pair)
                    val cor = cores[originalIndex % cores.size]

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(cor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${pair.first.uppercase()} (${"%.1f".format(pair.second)}%)", color = TextWhite.copy(0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}@Composable
fun EditValorDialog(
    investimento: com.meudinheiro.data.Investimento,
    onDismiss: () -> Unit,
    onConfirmar: (Double) -> Unit
) {
    var novoValorStr by remember { mutableStateOf(investimento.valorAtual.toString()) }

    // Cálculo em tempo real para o feedback visual
    val novoValor = novoValorStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val lucroSimulado = novoValor - investimento.valorInvestido
    val corLucro = if (lucroSimulado >= 0) Color(0xFF69F0AE) else Color(0xFFEF5350)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E)),
            border = BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Atualizar Valor",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(investimento.nome, color = Color(0xFF00E5FF), fontSize = 14.sp)

                Spacer(modifier = Modifier.height(24.dp))

                // Info de Custo (Não editável aqui, apenas para referência)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.6f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Custo de Compra:", color = Color.White, fontSize = 12.sp)
                    Text(
                        formatarMoedaBR(investimento.valorInvestido, false),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo de Entrada do Novo Valor
                OutlinedTextField(
                    value = novoValorStr,
                    onValueChange = { novoValorStr = it },
                    label = { Text("Novo Valor Atual", color = Color.White.copy(0.5f)) },
                    prefix = { Text("R$ ", color = Color.White) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Feedback de lucro instantâneo
                Text(
                    text = "Resultado: ${if (lucroSimulado >= 0) "+" else ""}${
                        formatarMoedaBR(
                            lucroSimulado,
                            false
                        )
                    }",
                    color = corLucro,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Cancelar",
                            color = Color.White.copy(0.5f)
                        )
                    }
                    Button(
                        onClick = { onConfirmar(novoValor) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("Atualizar", color = Color(0xFF1B263B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}