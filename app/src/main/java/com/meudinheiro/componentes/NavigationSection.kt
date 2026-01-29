package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meudinheiro.R

// Cores Premium Locais
private val DockBg = Color(0xFF1E2B3E).copy(alpha = 0.95f) // Fundo escuro translúcido
private val AccentGold = Color(0xFFFFD700) // Dourado para seleção

@Composable
fun NavigationSection(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = selectedIndex == 0

    val dockShape = RoundedCornerShape(32.dp)

    // Dock flutuante escuro com borda sutil
    val dockBorder = Color.White.copy(alpha = 0.15f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp), // Aumentei o padding lateral para ficar mais "ilhado"
        shape = dockShape,
        color = DockBg,
        tonalElevation = 0.dp,
        shadowElevation = 20.dp, // Sombra mais forte para destacar do fundo escuro
        border = BorderStroke(1.dp, dockBorder)
    ) {
        Box(
            modifier = Modifier
                .clip(dockShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f), // Reflexo superior sutil
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f)   // Sombra inferior interna
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumSingleDockItem(
                    selected = selected,
                    label = "Minha Conta", // Texto um pouco mais descritivo fica elegante
                    iconRes = R.drawable.bank,
                    onClick = { onItemSelected(0) }
                )
            }
        }
    }
}

@Composable
private fun PremiumSingleDockItem(
    selected: Boolean,
    label: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    // Animação de Fundo: Dourado translúcido se selecionado, Transparente se não
    val bg by animateColorAsState(
        targetValue = if (selected)
            AccentGold.copy(alpha = 0.15f)
        else
            Color.Transparent,
        label = "dockItemBg"
    )

    // Animação de Borda: Dourado sutil se selecionado, Branco muito sutil se não
    val border by animateColorAsState(
        targetValue = if (selected)
            AccentGold.copy(alpha = 0.3f)
        else
            Color.White.copy(alpha = 0.05f),
        label = "dockItemBorder"
    )

    // Animação de Cor do Texto/Ícone: Dourado vs Branco acinzentado
    val contentColor by animateColorAsState(
        targetValue = if (selected)
            AccentGold
        else
            TextWhite.copy(alpha = 0.6f),
        label = "dockItemContent"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        label = "dockItemScale"
    )

    val padH by animateDpAsState(
        targetValue = if (selected) 24.dp else 16.dp, // Mais espaçamento quando selecionado
        label = "dockItemPadH"
    )

    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp, // Item interno flat
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = padH, vertical = 12.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Se seus ícones forem coloridos, use tint = Color.Unspecified
            // Se forem monocromáticos (recomendado para esse visual), use o tint dinâmico
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            if (selected) {
                Spacer(Modifier.size(10.dp))

                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}