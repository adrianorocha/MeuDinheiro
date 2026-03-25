package com.meudinheiro.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meudinheiro.R
import com.meudinheiro.funcoes.Haptics
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange

@Composable
fun PowerCoreFab(
    isMenuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onOpcaoSelected: (String) -> Unit
) {
    val context = LocalContext.current

    // --- 1. ANIMAÇÕES DE TRANSIÇÃO (Menu Aberto/Fechado) ---
    val expansionProgress by animateFloatAsState(
        targetValue = if (isMenuOpen) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "expansion"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 135f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "rotation"
    )

    // --- 2. MOTOR DE PULSO DO GLOW (Anti-Burn-in) ---
    val infiniteTransition = rememberInfiniteTransition(label = "power_core_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = InfiniteRepeatableSpec(
            tween(2000, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "glow"
    )

    // --- 3. MOTOR DE PULO IDLE (Flutuação Magnética) ---
    // Ele só pula se o menu estiver FECHADO (para facilitar o clique nas opções)
    val idleOffset by if (!isMenuOpen) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -8f, // Flutua 8dp para cima
            animationSpec = InfiniteRepeatableSpec(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idle_jump"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // --- 4. RENDERIZAÇÃO ---
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .navigationBarsPadding()
            .offset(y = 75.dp)
            .size(220.dp)
    ) {

        // 🪐 SATÉLITES (Mantêm a posição deles, pois já têm sua própria lógica no MiniFab)
        MiniFabOrbital(
            iconeRes = R.drawable.bank,
            cor = Color(0xFFFF4B4B),
            offsetX = (-70).dp,
            offsetY = (-70).dp,
            progresso = expansionProgress,
            onClick = {
                Haptics.vibrar(context, "impacto")
                onOpcaoSelected("minha conta")
            })

        MiniFabOrbital(
            iconeRes = R.drawable.extrato,
            cor = NeonGreen,
            offsetX = (-25).dp,
            offsetY = (-110).dp,
            progresso = expansionProgress,
            onClick = {
                Haptics.vibrar(context, "sucesso")
                onOpcaoSelected("extrato")
            })

        MiniFabOrbital(
            iconeRes = R.drawable.metas,
            cor = Color(0xFFBB86FC),
            offsetX = (25).dp,
            offsetY = (-110).dp,
            progresso = expansionProgress,
            onClick = {
                Haptics.vibrar(context, "energia")
                onOpcaoSelected("meta")
            })

        MiniFabOrbital(
            iconeRes = R.drawable.transferencia,
            cor = NeonOrange,
            offsetX = (70).dp,
            offsetY = (-70).dp,
            progresso = expansionProgress,
            onClick = {
                Haptics.vibrar(context, "movimento")
                onOpcaoSelected("transferencia")
            })

        // 🌟 O NÚCLEO CENTRAL (Power Core)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                // 🚀 APLICAÇÃO DO PULO
                .graphicsLayer {
                    translationY = idleOffset.dp.toPx()
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) {
                    Haptics.vibrar(context, "click_menu")
                    onToggleMenu()
                }
        ) {

            // GLOW RADIAL
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan.copy(alpha = 0.5f), Color.Transparent),
                            center = Offset.Unspecified,
                            radius = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // BOTÃO FÍSICO NEON
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonCyan, Color(0xFF00B8D4))))
                    .border(1.dp, Color.White.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Menu",
                    tint = DeepSpaceBlue,
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}