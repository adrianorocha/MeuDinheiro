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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.meudinheiro.R
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
    // --- ANIMAÇÕES (Mantidas) ---
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

    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.4f,
        animationSpec = InfiniteRepeatableSpec(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    // 🚀 O AJUSTE DE PROFUNDIDADE
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .navigationBarsPadding()
            .offset(y = 75.dp) // 🚀 Aumentamos para 75.dp para descer até o limite
            .size(180.dp)      // Reduzimos o container para 180dp para ficar mais compacto
    ) {

        // 🪐 SATÉLITES (Ajustamos o offsetY para eles continuarem subindo bem)

        // Esquerda: Minha Conta
        MiniFabOrbital(
            iconeRes = R.drawable.bank,
            cor = Color(0xFFFF4B4B),
            offsetX = (-65).dp,
            offsetY = (-75).dp, // Subimos mais o satélite para compensar o botão mais baixo
            progresso = expansionProgress,
            onClick = { onOpcaoSelected("minha conta") }
        )

        // Centro: Depósito
        MiniFabOrbital(
            iconeRes = R.drawable.extrato,
            cor = NeonGreen,
            offsetX = 0.dp,
            offsetY = (-105).dp, // Subimos mais aqui também
            progresso = expansionProgress,
            onClick = { onOpcaoSelected("extrato") }
        )

        // Direita: Metas
        MiniFabOrbital(
            iconeRes = R.drawable.metas,
            cor = Color(0xFFBB86FC),
            offsetX = (65).dp,
            offsetY = (-75).dp, // Subimos mais aqui também
            progresso = expansionProgress,
            onClick = { onOpcaoSelected("meta") }
        )

        // Direita: Transferências
        MiniFabOrbital(
            iconeRes = R.drawable.transferencia,
            cor = NeonOrange,
            offsetX = (105).dp,
            offsetY = (-15).dp, // Subimos mais aqui também
            progresso = expansionProgress,
            onClick = { onOpcaoSelected("transferencia") }
        )

        // 🌟 O NÚCLEO CENTRAL (Botão que você vê)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleMenu() }
        ) {
            Surface(modifier = Modifier.size(40.dp).alpha(glowAlpha).blur(10.dp), shape = CircleShape, color = NeonCyan) {}

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
                    modifier = Modifier.size(26.dp).rotate(rotation)
                )
            }
        }
    }
}