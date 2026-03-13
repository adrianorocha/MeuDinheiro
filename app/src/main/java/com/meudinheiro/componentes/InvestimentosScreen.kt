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

// --- CORES PREMIUM ---
private val BgDark = Color(0xFF1B263B)
private val CardBg = Color(0xFF263248)
//private val NeonCyan = Color(0xFF00E5FF)
//private val NeonGreen = Color(0xFF69F0AE)

// Modelo temporário para a interface
data class AtivoInvestimento(
    val id: Int,
    val nome: String,
    val tipo: String, // "Renda Fixa", "Ações", "FIIs", "Cripto"
    val valorAtual: Double,
    val rentabilidadeMes: Double // Porcentagem (ex: 1.5, -0.3)
)

@Composable
fun InvestimentosTab(
    viewModel: InvestimentoViewModel, // Receba o ViewModel aqui
    isPrivate: Boolean
) {
    // 1. Coleta os dados em tempo real do SQLite (Substitui os dados simulados!)
    val meusAtivos by viewModel.investimentos.collectAsState()
    val patrimonioTotal by viewModel.patrimonioTotal.collectAsState()
    val rendimentoTotal by viewModel.rendimentoTotal.collectAsState()
    val porcentagemTotal by viewModel.porcentagemTotal.collectAsState()
    val distribuicao by viewModel.distribuicaoPorTipo.collectAsState()

    var investimentoParaEditar by remember { mutableStateOf<com.meudinheiro.data.Investimento?>(null) }

    // 2. Controlo do Diálogo
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER COM GRÁFICO (Passando os dados reais agora)
        item {
            PatrimonioChartCard(
                total = patrimonioTotal,
                rendimento = rendimentoTotal,
                porcentagem = porcentagemTotal,
                isPrivate = isPrivate
            )
        }
        if (distribuicao.isNotEmpty()) {
            item { DiversificacaoCard(distribuicao = distribuicao) }
        }
        // BOTÃO DE NOVO APORTE
        item {
            Button(
                onClick = { showAddDialog = true }, // Abre o diálogo
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00E5FF))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Novo Investimento",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // TÍTULO DA LISTA
        item {
            Text(
                text = "Minha Carteira",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        // LISTA DE ATIVOS REAIS DO BANCO DE DADOS
        items(meusAtivos, key = { it.id }) { ativo ->
            AtivoRowCard(
                ativo = ativo,
                isPrivate = isPrivate,
                onClick = { investimentoParaEditar = ativo } // Define qual será editado
            )
        }
    }

// Se houver um ativo selecionado, mostra o diálogo
    investimentoParaEditar?.let { ativo ->
        EditValorDialog(
            investimento = ativo,
            onDismiss = { investimentoParaEditar = null },
            onConfirmar = { novoValor ->
                viewModel.atualizarValorAtivo(ativo, novoValor)
                investimentoParaEditar = null
            }
        )
    }

    // A MÁGICA: Renderiza o Diálogo se o estado for verdadeiro
    if (showAddDialog) {
        AddInvestimentoDialog(
            onDismiss = { showAddDialog = false },
            onGuardar = { nome, tipo, valorInvestido, valorAtual ->
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
    // Animação para o gráfico desenhar suavemente
    var animationPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label = "ChartAnim"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // VALORES
            Text(
                text = "Patrimônio Investido",
                color = TextWhite.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            Text(
                text = if (isPrivate) "R$ •••••" else formatarMoedaBR(total, false),
                color = TextWhite,
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
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isPrivate) "R$ ••• (••%)" else "+${
                        formatarMoedaBR(
                            rendimento,
                            false
                        )
                    } (+$porcentagem%)",
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // GRÁFICO DE LINHA NEON COM CURVA BEZIER
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Pontos simulados de crescimento do patrimônio (escala Y invertida no Canvas)
                    val points = listOf(
                        size.height * 0.8f, size.height * 0.7f, size.height * 0.75f,
                        size.height * 0.5f, size.height * 0.4f, size.height * 0.2f
                    )

                    val stepX = size.width / (points.size - 1)
                    val path = Path()

                    // Calcula os pontos da curva
                    val curvePoints = mutableListOf<Offset>()
                    for (i in points.indices) {
                        curvePoints.add(Offset(i * stepX, points[i]))
                    }

                    // Desenha a linha Bezier suave
                    path.moveTo(curvePoints.first().x, curvePoints.first().y)
                    for (i in 0 until curvePoints.size - 1) {
                        val p1 = curvePoints[i]
                        val p2 = curvePoints[i + 1]
                        // Pontos de controle para suavizar a curva
                        val cp1 = Offset((p1.x + p2.x) / 2f, p1.y)
                        val cp2 = Offset((p1.x + p2.x) / 2f, p2.y)
                        path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                    }

                    // Preenchimento gradiente abaixo da linha (Efeito Wall Street)
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }

                    // Só desenha se a animação já começou
                    if (animProgress > 0) {
                        // O ClipRect esconde o resto do gráfico baseando-se na animação (da esquerda para direita)
                        clipRect(right = size.width * animProgress) {
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )
                            drawPath(
                                path = path,
                                color = NeonCyan,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AtivoRowCard(
    ativo: com.meudinheiro.data.Investimento, // Mudou para a Entidade real do Room
    isPrivate: Boolean,
    onClick: () -> Unit
) {
    // Agora usamos a matemática que o Room faz pra gente
    val isPositivo = ativo.rendimentoReal >= 0
    val corRentabilidade = if (isPositivo) Color(0xFF69F0AE) else Color(0xFFEF5350)
    val sinal = if (isPositivo) "+" else ""

    // Formata a porcentagem para ter apenas 2 casas decimais (ex: 1.54%)
    val porcentagemFormatada = "%.2f".format(ativo.rentabilidadePercentual)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF263248)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icone/Cor do Tipo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.05f)),
                contentAlignment = Alignment.Center
            ) {
                // Primeira letra do ativo
                Text(
                    text = ativo.nome.take(1).uppercase(),
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Textos Esquerda
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ativo.nome,
                    color = Color(0xFFE0E1DD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ativo.tipo,
                    color = Color(0xFFE0E1DD).copy(0.5f),
                    fontSize = 12.sp
                )
            }

            // Valores Direita
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPrivate) "R$ •••••" else formatarMoedaBR(ativo.valorAtual, false),
                    color = Color(0xFFE0E1DD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "$sinal$porcentagemFormatada%",
                    color = corRentabilidade,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
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