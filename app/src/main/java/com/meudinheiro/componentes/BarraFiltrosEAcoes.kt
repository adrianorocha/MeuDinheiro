package com.meudinheiro.componentes

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.ui.theme.NeonCyan
import kotlinx.coroutines.delay

@Composable
fun BarraFiltrosEAcoes(
    filtroAtual: FiltroPeriodo, // Certifique-se de que este enum/classe existe no seu projeto
    onFiltroSelected: (FiltroPeriodo) -> Unit,
    onEvolucaoPatrimonial: () -> Unit,
    onSaudeFinanceiro: () -> Unit,
    onPreviaoMes: () -> Unit,
    onTransacoesAgendadas: () -> Unit,
    agendados: List<TransferenciaAgendada> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GRUPO DA ESQUERDA: Filtros Deslizáveis
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeletorPeriodo(
                filtroSelecionado = filtroAtual,
                onFiltroSelected = onFiltroSelected
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // GRUPO DA DIREITA: Orbitais Consolidados
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionOrbital(
                icone = Icons.AutoMirrored.Filled.ShowChart,
                cor = NeonCyan,
                tooltip = "Evolução",
                onClick = onEvolucaoPatrimonial
            )

            ActionOrbital(
                icone = Icons.AutoMirrored.Filled.ReceiptLong,
                cor = Color(0xFF69F0AE), // NeonGreen
                tooltip = "Saúde",
                onClick = onSaudeFinanceiro
            )

            ActionOrbital(
                icone = Icons.Default.AutoGraph,
                cor = Color(0xFFCE93D8), // Roxo
                tooltip = "Insights",
                onClick = onPreviaoMes
            )

            ActionOrbital(
                icone = Icons.Default.CalendarToday,
                // 🚀 Muda a cor do brilho se houver pendências!
                cor = if (agendados.isNotEmpty()) Color(0xFFFF4B4B) else NeonCyan,
                tooltip = "Agendados",
                badgeCount = agendados.size, // 🚀 Passamos apenas o número agora!
                onClick = onTransacoesAgendadas
            )
        }
    }
}

@Composable
fun ActionOrbital(
    icone: ImageVector,
    cor: Color,
    tooltip: String,
    badgeCount: Int = 0, // 🚀 Arquitetura limpa: recebe apenas o Int
    onClick: () -> Unit
) {
    // 💡 1. MOTOR DE PULSO (Anti-Burn-in e Estética Neon)
    val infiniteTransition = rememberInfiniteTransition(label = "orbital_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = InfiniteRepeatableSpec(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    // 💡 2. ANIMAÇÃO DO BADGE (O Pulo / Spring)
    var scaleFactor by remember { mutableFloatStateOf(0f) }
    val scaleAnim by animateFloatAsState(
        targetValue = scaleFactor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BadgeScale"
    )

    LaunchedEffect(key1 = badgeCount) {
        if (badgeCount > 0) {
            scaleFactor = 0f
            delay(50) // Pequeno delay para garantir o reset visual
            scaleFactor = 1f
        } else {
            scaleFactor = 0f
        }
    }

    // 💡 3. RENDERIZAÇÃO DO COMPONENTE
    Box(
        modifier = Modifier.size(42.dp), // Espaço total para o botão + badge não cortarem
        contentAlignment = Alignment.Center
    ) {
        // AURA NEON (No fundo)
        Surface(
            modifier = Modifier
                .size(30.dp)
                .alpha(glowAlpha)
                .blur(8.dp),
            shape = CircleShape,
            color = cor
        ) {}

        // O BOTÃO DE VIDRO (Glassmorphism)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)) // Fundo translúcido
                .border(1.dp, cor.copy(alpha = 0.3f), CircleShape) // Borda sutil
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // Tira aquele ripple cinza feio do Android
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icone,
                contentDescription = tooltip,
                tint = cor,
                modifier = Modifier.size(18.dp)
            )
        }

        // O BADGE ANIMADO (Por cima de tudo)
        if (scaleAnim > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .scale(scaleAnim)
                    .size(16.dp)
                    .background(Color(0xFFFF4B4B), CircleShape) // Vermelho Alerta
                    .border(1.dp, Color(0xFF0D1B2A), CircleShape), // Borda escura para destacar
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$badgeCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}