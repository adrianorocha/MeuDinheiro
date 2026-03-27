package com.meudinheiro.componentes

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.Haptics
import com.meudinheiro.funcoes.formatarMoedaBR
import kotlin.math.atan2

// --- PALETA GRID POWER ---
private val NeonCyan = Color(0xFF00E5FF)
private val NeonGreen = Color(0xFF69F0AE)
private val NeonRed = Color(0xFFFF4B4B)
private val NeonYellow = Color(0xFFFFD54F)
private val NeonPurple = Color(0xFFB388FF)
private val CardBg = Color(0xFF1B263B)

@Composable
fun AnelHolografico(
    dados: List<Pair<String, Double>>, // Ex: listOf("Alimentação" to 500.0, "Transporte" to 200.0)
    isPrivate: Boolean
) {
    val context = LocalContext.current
    val total = dados.sumOf { it.second }

    // Cores dinâmicas baseadas na quantidade de itens
    val cores = listOf(NeonCyan, NeonPurple, NeonYellow, NeonGreen, NeonRed)

    // Qual fatia está selecionada no momento (nulo = mostra o total)
    var categoriaSelecionada by remember { mutableStateOf<String?>(null) }
    var valorSelecionado by remember { mutableStateOf(total) }
    var corSelecionada by remember { mutableStateOf(NeonCyan) }

    // --- MOTOR 3D SENSORIZADO ---
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var roll by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }

    val animatedRoll by animateFloatAsState(targetValue = roll, animationSpec = tween(400, easing = LinearOutSlowInEasing), label = "roll")
    val animatedPitch by animateFloatAsState(targetValue = pitch, animationSpec = tween(400, easing = LinearOutSlowInEasing), label = "pitch")

    // Animação de entrada do anel (desenha de 0 a 360 graus)
    var animationPlayed by remember { mutableStateOf(false) }
    val sweepProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "sweep"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    // Hook no Acelerômetro
    DisposableEffect(Unit) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                roll = (x * 3f).coerceIn(-20f, 20f)
                pitch = ((y - 5f) * 3f).coerceIn(-20f, 20f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f)),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ANÁLISE DE RECURSOS //",
                color = Color.White.copy(0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 🚀 O NÚCLEO HOLOGRÁFICO
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        rotationX = animatedPitch
                        rotationY = animatedRoll
                        cameraDistance = 12f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                // CANVAS DO ANEL
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(dados) {
                            detectTapGestures { offset ->
                                // Matemática pura para descobrir onde o usuário clicou!
                                val dx = offset.x - size.width / 2
                                val dy = offset.y - size.height / 2
                                var anguloToque = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (anguloToque < 0) anguloToque += 360f

                                // Ajusta para o nosso ponto de início (-90 graus = topo)
                                var anguloAjustado = (anguloToque + 90f) % 360f
                                if (anguloAjustado < 0) anguloAjustado += 360f

                                var anguloAtual = 0f
                                for ((index, item) in dados.withIndex()) {
                                    val sweep = (item.second / total).toFloat() * 360f
                                    if (anguloAjustado >= anguloAtual && anguloAjustado <= anguloAtual + sweep) {
                                        // ACHOU A FATIA!
                                        Haptics.vibrar(context, "clique")
                                        if (categoriaSelecionada == item.first) {
                                            // Se clicar de novo na mesma, reseta pro Total
                                            categoriaSelecionada = null
                                            valorSelecionado = total
                                            corSelecionada = NeonCyan
                                        } else {
                                            categoriaSelecionada = item.first
                                            valorSelecionado = item.second
                                            corSelecionada = cores[index % cores.size]
                                        }
                                        break
                                    }
                                    anguloAtual += sweep
                                }
                            }
                        }
                ) {
                    val strokeFino = 20.dp.toPx()
                    val strokeGlow = 40.dp.toPx()
                    val raio = size.width / 2 - strokeGlow / 2

                    var startAngle = -90f

                    dados.forEachIndexed { index, item ->
                        val sweepAngle = (item.second / total).toFloat() * 360f * sweepProgress
                        val cor = cores[index % cores.size]

                        // Calcula um pequeno gap (espaço) entre as fatias
                        val gap = if (dados.size > 1) 3f else 0f
                        val actualSweep = (sweepAngle - gap).coerceAtLeast(1f)

                        // Se esta fatia estiver selecionada, ela brilha mais e fica mais grossa
                        val isSelected = categoriaSelecionada == item.first
                        val currentStroke = if (isSelected) strokeFino * 1.5f else strokeFino
                        val alphaBase = if (categoriaSelecionada == null || isSelected) 1f else 0.3f

                        // 🌟 1. Desenha o Glow (Brilho Externo)
                        drawArc(
                            color = cor.copy(alpha = alphaBase * 0.2f),
                            startAngle = startAngle + (gap / 2),
                            sweepAngle = actualSweep,
                            useCenter = false,
                            style = Stroke(width = strokeGlow, cap = StrokeCap.Round),
                            size = Size(raio * 2, raio * 2),
                            topLeft = Offset(strokeGlow / 2, strokeGlow / 2)
                        )

                        // 🌟 2. Desenha o Núcleo Sólido
                        drawArc(
                            color = cor.copy(alpha = alphaBase),
                            startAngle = startAngle + (gap / 2),
                            sweepAngle = actualSweep,
                            useCenter = false,
                            style = Stroke(width = currentStroke, cap = StrokeCap.Round),
                            size = Size(raio * 2, raio * 2),
                            topLeft = Offset(strokeGlow / 2, strokeGlow / 2)
                        )

                        startAngle += sweepAngle
                    }
                }

                // VALOR NO CENTRO (Com animação de transição)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        targetState = categoriaSelecionada ?: "TOTAL",
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "TituloCenter"
                    ) { targetTitle ->
                        Text(
                            text = targetTitle.uppercase(),
                            color = Color.White.copy(0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    AnimatedContent(
                        targetState = valorSelecionado,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "ValorCenter"
                    ) { targetValue ->
                        Text(
                            text = if (isPrivate) "R$ •••••" else formatarMoedaBR(targetValue, false),
                            color = corSelecionada,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // LEGENDAS CYBER
            Column(modifier = Modifier.fillMaxWidth()) {
                dados.forEachIndexed { index, item ->
                    val cor = cores[index % cores.size]
                    val isSelected = categoriaSelecionada == item.first || categoriaSelecionada == null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .alpha(if (isSelected) 1f else 0.3f), // Apaga os que não estão selecionados
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(cor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.first.uppercase(),
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }

                        val porcentagem = (item.second / total) * 100
                        Text(
                            text = "${"%.1f".format(porcentagem)}%",
                            color = cor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}