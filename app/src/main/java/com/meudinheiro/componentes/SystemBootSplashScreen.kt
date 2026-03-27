package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.funcoes.Haptics
import kotlinx.coroutines.delay
import kotlin.random.Random

// --- CORES DO BOOT VIP ---
private val BootBg = Color(0xFF04070D) // Preto abissal
private val TerminalGreen = Color(0xFF69F0AE)
private val NeonCyan = Color(0xFF00E5FF)
private val GridColor = Color(0xFF00E5FF).copy(alpha = 0.05f)

@Composable
fun SystemBootSplashScreen(onBootComplete: () -> Unit) {
    val context = LocalContext.current

    // O Terminal Hacker (Ainda mais detalhado)
    val bootSequence = listOf(
        "BLU_MACAW_OS v3.1.4 [INICIALIZANDO KERNEL]",
        "VERIFICANDO MEMÓRIA.................. [OK]",
        "MONTANDO COFRE CRIPTOGRAFADO......... [OK]",
        "CARREGANDO MÓDULOS FINANCEIROS....... [OK]",
        "INICIANDO PROTOCOLO PARALLAX......... [ATIVO]",
        "CONECTANDO AO GRID CENTRAL........... [ESTABELECIDO]",
        "AUTENTICANDO MAINFRAME............... [ACESSO LIBERADO]",
        "CALIBRANDO MOTORES HÁPTICOS.......... [PRONTO]",
        "INTEGRIDADE DO SISTEMA: 100%",
        "OVERRIDE DE SEGURANÇA: EXECUTADO",
        "GRID POWER: ATIVADO ⚡"
    )

    val visibleLines = remember { mutableStateListOf<String>() }
    var showCursor by remember { mutableStateOf(true) }
    var showLogo by remember { mutableStateOf(false) }
    var glitchAlpha by remember { mutableStateOf(1f) }

    // --- ANIMAÇÕES DE FUNDO (GRID E SCANNER) ---
    val infiniteTransition = rememberInfiniteTransition(label = "fundo")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 2500f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "scanline"
    )
    val gridOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "gridoffset"
    )

    // Efeito do cursor piscando
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            showCursor = !showCursor
        }
    }

    // 🚀 O MOTOR DA SEQUÊNCIA DE BOOT
    LaunchedEffect(Unit) {
        delay(400) // Pausa dramática inicial para a tela preta

        // Imprime as linhas
        for (line in bootSequence) {
            visibleLines.add(line)
            Haptics.vibrar(context, "movimento") // Estalo tático rápido

            // Ritmo dinâmico de leitura
            val pauseTime = when {
                line.contains("..........") -> Random.nextLong(150, 350)
                line.contains("100%") -> 600L
                else -> 200L
            }
            delay(pauseTime)
        }

        delay(300)

        // ⚡ EFEITO GLITCH NO LOGO ANTES DE LIGAR
        showLogo = true
        Haptics.vibrar(context, "alerta") // Primeiro tranco
        glitchAlpha = 0.2f; delay(50)
        glitchAlpha = 0.8f; delay(50)
        glitchAlpha = 0.1f; delay(50)
        glitchAlpha = 1f
        Haptics.vibrar(context, "sucesso") // Estabilizou! Trovão final!

        // Tempo para admirar a arte
        delay(1800)
        onBootComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BootBg)
    ) {
        // --- 1. O CYBER GRID (Fundo animado) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 100f
            // Desenha linhas verticais
            for (x in 0..size.width.toInt() step step.toInt()) {
                drawLine(GridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 2f)
            }
            // Desenha linhas horizontais com movimento (gridOffset)
            var y = gridOffset % step
            while (y < size.height) {
                drawLine(GridColor, Offset(0f, y), Offset(size.width, y), 2f)
                y += step
            }

            // --- 2. SCANNER LASER ---
            // Linha principal brilhante
            drawLine(
                color = NeonCyan.copy(alpha = 0.5f),
                start = Offset(0f, scanlineY),
                end = Offset(size.width, scanlineY),
                strokeWidth = 4f
            )
            // Rastro de luz (Fade up) do scanner
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, NeonCyan.copy(alpha = 0.15f)),
                    startY = scanlineY - 150f,
                    endY = scanlineY
                ),
                topLeft = Offset(0f, scanlineY - 150f),
                size = androidx.compose.ui.geometry.Size(size.width, 150f)
            )
        }

        // --- 3. O TERMINAL DE DADOS ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .padding(top = 40.dp)
        ) {
            visibleLines.forEach { line ->
                Text(
                    text = "> $line",
                    color = TerminalGreen.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (!showLogo) {
                Text(
                    text = if (showCursor) "> █" else ">", // Bloco sólido pro cursor Hacker
                    color = TerminalGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        // --- 4. O NÚCLEO GRID POWER (Logo central) ---
        AnimatedVisibility(
            visible = showLogo,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(600, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = glitchAlpha } // Aplica o Glitch
            ) {
                // Sombra/Brilho atrás do ícone
                //BluMacawLogo(glitchAlpha = glitchAlpha)
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = NeonCyan.copy(alpha = 0.3f),
                        modifier = Modifier.size(100.dp).graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }
                    )
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "System Ready",
                        tint = NeonCyan,
                        modifier = Modifier.size(90.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Meu Dinheiro",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp,
                    modifier = Modifier.graphicsLayer {
                        shadowElevation = 10f // Brilho no texto
                        ambientShadowColor = NeonCyan
                    }
                )
                Text(
                    text = "S Y S T E M   O N L I N E",
                    color = TerminalGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// ==========================================
// 🚀 COMPONENTE DO LOGO (AUXILIAR)
// ==========================================
@Composable
fun BluMacawLogo(glitchAlpha: Float) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer { alpha = glitchAlpha }, // Aplica o efeito Glitch
        contentAlignment = Alignment.Center
    ) {
        // Fundo com brilho difuso (Glow) neon cyan
        Canvas(modifier = Modifier.size(90.dp).blur(8.dp)) {
            drawCircle(color = NeonCyan.copy(alpha = 0.15f))
        }

        // A IMAGEM DO LOGO (Cofrinho com Gráfico)
        Image(
            painter = painterResource(id = R.drawable.ic_blu_macaw_logo), // <-- SALVE A IMAGEM AQUI
            contentDescription = "Blu Macaw Logo",
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        )
    }
}