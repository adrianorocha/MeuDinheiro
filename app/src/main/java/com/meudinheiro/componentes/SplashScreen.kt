package com.meudinheiro.componentes

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Estado de início da animação
    var startAnimation by remember { mutableStateOf(false) }

    // 1. Animação de Entrada do Logo (Elasticidade)
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    // 2. Animação de Opacidade Geral
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000),
        label = "screenAlpha"
    )

    // 3. Texto subindo suavemente
    val textOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 50.dp,
        animationSpec = tween(1000, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "textSlide"
    )

    // Efeito de pulso contínuo (Aura)
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500) // Tempo total da splash
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Gradiente de fundo sutil e moderno
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // --- 1. Partículas de Fundo (Money dust) ---
        ParticleBackground(modifier = Modifier.fillMaxSize())

        // --- 2. Conteúdo Central ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Aura 1 (Externa)
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulse)
                        .blur(30.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            CircleShape
                        )
                )

                // Aura 2 (Interna mais forte)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse * 0.9f)
                        .blur(15.dp)
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                            CircleShape
                        )
                )

                // Logo Principal (Explosão Elástica)
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground), // Seu ícone
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(130.dp)
                        .scale(scale)
                        .graphicsLayer {
                            shadowElevation = 20.dp.toPx()
                            shape = CircleShape
                        }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Nome do App Animado
            Column(
                modifier = Modifier
                    .offset(y = textOffsetY)
                    .alpha(if (startAnimation) 1f else 0f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Meu Dinheiro",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Gestão Financeira \nPessoal e Inteligente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // --- 3. Rodapé (Copyright ou Versão) ---
        Text(
            text = "Versão 1.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(alpha)
        )
    }
}

// --- Componente Extra: Partículas Flutuantes ---
@Composable
private fun ParticleBackground(modifier: Modifier = Modifier) {
    val particles = remember { List(15) { Particle() } }
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    // Animação global de "tempo" para mover as partículas
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        particles.forEach { p ->
            // Movimento suave baseado em seno/cosseno
            val offsetX = p.initialX + cos(time + p.randomOffset) * 50f
            val offsetY = p.initialY + sin(time + p.randomOffset) * 50f

            // Reaparece do outro lado se sair da tela (efeito loop visual simples)
            val x = (offsetX % width).let { if (it < 0) it + width else it }
            val y = (offsetY % height).let { if (it < 0) it + height else it }

            drawCircle(
                color = p.color.copy(alpha = 0.4f),
                radius = p.size,
                center = Offset(x, y)
            )
        }
    }
}

// Classe de dados simples para as partículas
private data class Particle(
    val initialX: Float = Random.nextFloat() * 1000f,
    val initialY: Float = Random.nextFloat() * 2000f,
    val size: Float = Random.nextFloat() * 15f + 5f,
    val randomOffset: Float = Random.nextFloat() * 10f,
    val color: Color = if (Random.nextBoolean()) Color(0xFF00C853) else Color(0xFFFFD54F) // Verde e Dourado
)
