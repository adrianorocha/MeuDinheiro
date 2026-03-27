package com.meudinheiro.componentes

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.PieChartData
import com.meudinheiro.funcoes.HorizontalBalanceBarSlim
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.funcoes.lembrarEstadoPerformance
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonRed

// Cores locais da paleta Grid Power para o anel
private val coresNeon = listOf(
    Color(0xFF00E5FF), // Cyan
    Color(0xFFB388FF), // Purple
    Color(0xFFFFD54F), // Yellow
    Color(0xFF69F0AE), // Green
    Color(0xFFFF4B4B)  // Red
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ResumoGeralCard(
    receitaTotal: Double,
    despesaMesAnterior: Double,
    despesaTotal: Double,
    metasTotal: Double,
    isPrivate: Boolean = false,
    dadosGrafico: List<PieChartData> // Mantemos o seu modelo de dados original
) {
    // --- LÓGICA DE DADOS ---
    val saldoDisponivel = receitaTotal - despesaTotal
    val patrimonioTotal = saldoDisponivel + metasTotal
    val maxVal = maxOf(receitaTotal, despesaTotal, metasTotal, 1.0)

    // --- ANIMAÇÕES E PRIVACIDADE ---
    val modoEconomia = lembrarEstadoPerformance()
    var animationPlayed by remember { mutableStateOf(false) }
    val duracaoAnim = if (modoEconomia) 0 else 800

    // 🚀 A MÁGICA DO BLUR: Se estiver privado, desfoque 16dp. Se não, 0dp.
    val animBlur by animateDpAsState(
        targetValue = if (isPrivate) 16.dp else 0.dp,
        animationSpec = tween(500),
        label = "blurPrivacy"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    // Animações das barras (Mantidas conforme seu original)
    val animBarReceita by animateFloatAsState(
        targetValue = if (animationPlayed) (receitaTotal / maxVal).toFloat() else 0f,
        animationSpec = tween(duracaoAnim)
    )
    val animBarMetas by animateFloatAsState(
        targetValue = if (animationPlayed) (metasTotal / maxVal).toFloat() else 0f,
        animationSpec = tween(duracaoAnim, 150)
    )
    val animBarDespesa by animateFloatAsState(
        targetValue = if (animationPlayed) (despesaTotal / maxVal).toFloat() else 0f,
        animationSpec = tween(duracaoAnim, 300)
    )

    val sweepDespesa = if (receitaTotal > 0) (despesaTotal / receitaTotal).toFloat()
        .coerceIn(0f, 1f) * 360f else 0f
    val animSweep by animateFloatAsState(
        targetValue = if (animationPlayed) sweepDespesa else 0f,
        animationSpec = tween(1200)
    )

    // --- ALERTA VISUAL (Pulso Vermelho) ---
    val estaNoVermelho = despesaTotal > receitaTotal && receitaTotal > 0
    val infiniteTransition = rememberInfiniteTransition(label = "alerta")
    val economizou = despesaTotal < despesaMesAnterior
    val excessoGrave = despesaTotal > despesaMesAnterior && despesaMesAnterior > 0

    val corAlerta by infiniteTransition.animateColor(
        initialValue = Color(0xFFEF5350).copy(alpha = 0.2f),
        targetValue = Color(0xFFEF5350).copy(alpha = 0.8f),
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corPulso"
    )

    // --- 🚀 SENSORES PARA O MINI ANEL HOLOGRÁFICO ---
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var roll by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                roll = (event.values[0] * 3f).coerceIn(-20f, 20f)
                pitch = ((event.values[1] - 5f) * 3f).coerceIn(-20f, 20f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E).copy(alpha = 0.98f)),
        border = if (estaNoVermelho) BorderStroke(2.dp, corAlerta) else BorderStroke(
            1.dp,
            Color.White.copy(0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // =================================================
            // SEÇÃO 1: ESQUERDA - Indicador de Consumo (Donut)
            // =================================================
            Box(
                modifier = Modifier
                    .size(80.dp) // Aumentamos um pouco o container para respirar
                    .blur(animBlur),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(60.dp)) {
                    val espessuraBorda = 6.dp.toPx()
                    val tamanhoAjustado = size.width - espessuraBorda
                    val deslocamento = espessuraBorda / 2

                    drawArc(
                        color = Color.White.copy(alpha = 0.05f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(deslocamento, deslocamento),
                        size = Size(tamanhoAjustado, tamanhoAjustado),
                        style = Stroke(width = espessuraBorda, cap = StrokeCap.Round)
                    )

                    drawArc(
                        brush = Brush.linearGradient(listOf(Color(0xFFFF8A80), Color(0xFFEF5350))),
                        startAngle = -90f,
                        sweepAngle = animSweep,
                        useCenter = false,
                        topLeft = Offset(deslocamento, deslocamento),
                        size = Size(tamanhoAjustado, tamanhoAjustado),
                        style = Stroke(width = espessuraBorda, cap = StrokeCap.Round)
                    )
                }

                val percent = if (receitaTotal > 0) ((despesaTotal / receitaTotal) * 100).toInt() else 0
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            // =================================================
            // SEÇÃO 2: Dados (Centro)
            // =================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text("Patrimônio Líquido", fontSize = 10.sp, color = Color.White.copy(0.6f))

                AnimatedContent(
                    targetState = patrimonioTotal,
                    transitionSpec = {
                        (fadeIn(tween(duracaoAnim)) + slideInVertically { it / 2 }) with fadeOut(
                            tween(duracaoAnim)
                        )
                    },
                    label = "animSaldo"
                ) { valor ->
                    Text(
                        text = formatarMoedaBR(valor, false),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (estaNoVermelho) Color(0xFFEF5350) else Color.White,
                        modifier = Modifier.blur(animBlur)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Column(modifier = Modifier.blur(animBlur)) {
                    HorizontalBalanceBarSlim(
                        label = "Entradas", value = receitaTotal, progress = animBarReceita,
                        color = Color(0xFF69F0AE), isPrivate = false, isLoading = !animationPlayed
                    )
                    HorizontalBalanceBarSlim(
                        label = "Poupado", value = metasTotal, progress = animBarMetas,
                        color = Color(0xFF00E676), isPrivate = false, isLoading = !animationPlayed
                    )
                    HorizontalBalanceBarSlim(
                        label = "Saídas", value = despesaTotal, progress = animBarDespesa,
                        color = Color(0xFFEF5350), isPrivate = false, isLoading = !animationPlayed
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp).blur(animBlur),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tendência", fontSize = 10.sp, color = Color.White.copy(0.4f))
                    Spacer(Modifier.width(8.dp))

                    if (economizou) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚀🚀🚀", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                            Text("Economia VIP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                    } else if (excessoGrave) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥🔥🔥", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                            Text("Alerta de Gasto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonRed)
                        }
                    } else {
                        Text("Estável", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(0.6f))
                    }
                }
            }

            // =================================================
            // 🚀 SEÇÃO 3: Mini Anel Holográfico (Direita)
            // =================================================
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(80.dp)
                    .padding(start = 8.dp)
                    .blur(animBlur)
                    .graphicsLayer {
                        // 🌟 Efeito Parallax no mini gráfico também!
                        rotationX = pitch
                        rotationY = roll
                        cameraDistance = 12f * density
                    }
            ) {
                if (dadosGrafico.isNotEmpty()) {
                    val totalGrafico = dadosGrafico.sumOf { it.valor.toDouble() }

                    // Animação de Sweep do anel
                    val ringSweep by animateFloatAsState(
                        targetValue = if (animationPlayed) 1f else 0f,
                        animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "ring"
                    )

                    Canvas(modifier = Modifier.size(70.dp)) {
                        val strokeFino = 8.dp.toPx()
                        val strokeGlow = 16.dp.toPx()
                        val raio = size.width / 2 - strokeGlow / 2
                        var startAngle = -90f

                        dadosGrafico.forEachIndexed { index, item ->
                            val sweepAngle = (item.valor.toFloat() / totalGrafico.toFloat()) * 360f * ringSweep
                            val cor = coresNeon[index % coresNeon.size]
                            val gap = if (dadosGrafico.size > 1) 4f else 0f
                            val actualSweep = (sweepAngle - gap).coerceAtLeast(1f)

                            // 1. Glow (Brilho translúcido)
                            drawArc(
                                color = cor.copy(alpha = 0.2f),
                                startAngle = startAngle + (gap / 2), sweepAngle = actualSweep,
                                useCenter = false, style = Stroke(width = strokeGlow, cap = StrokeCap.Round),
                                size = Size(raio * 2, raio * 2), topLeft = Offset(strokeGlow / 2, strokeGlow / 2)
                            )
                            // 2. Núcleo Sólido
                            drawArc(
                                color = cor,
                                startAngle = startAngle + (gap / 2), sweepAngle = actualSweep,
                                useCenter = false, style = Stroke(width = strokeFino, cap = StrokeCap.Round),
                                size = Size(raio * 2, raio * 2), topLeft = Offset(strokeGlow / 2, strokeGlow / 2)
                            )
                            startAngle += sweepAngle
                        }
                    }
                } else {
                    // Estado Vazio (Círculo apagado)
                    Canvas(modifier = Modifier.size(65.dp)) {
                        drawCircle(color = Color.White.copy(alpha = 0.05f), style = Stroke(width = 20f))
                    }
                }
            }
        }
    }
}