package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meudinheiro.R

// Cores Premium Locais
private val DockBg = Color(0xFF1E2B3E).copy(alpha = 0.95f)
private val AccentGold = Color(0xFFFFD700)

@Composable
fun NavigationSection(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(32.dp)
    val dockBorder = Color.White.copy(alpha = 0.15f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = dockShape,
        color = DockBg,
        tonalElevation = 0.dp,
        shadowElevation = 20.dp,
        border = BorderStroke(1.dp, dockBorder)
    ) {
        Box(
            modifier = Modifier
                .clip(dockShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center, // Centralizado (ou SpaceEvenly se tiver mais itens)
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ITEM 1
                PremiumSingleDockItem(
                    selected = selectedIndex == 0,
                    label = "Minha Conta",
                    iconRes = R.drawable.bank, // Certifique-se que o ícone existe
                    onClick = { onItemSelected(0) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                PremiumSingleDockItem(
                    selected = selectedIndex == 1,
                    label = "Extrato",
                    iconRes = R.drawable.extrato,
                    onClick = { onItemSelected(1) }
                )

                Spacer(modifier = Modifier.width(8.dp))
                PremiumSingleDockItem(
                    selected = selectedIndex == 2,
                    label = "Metas",
                    iconRes = R.drawable.metas,
                    onClick = { onItemSelected(2) }
                )

                // Exemplo de como adicionar um segundo item futuramente:
                /*
                Spacer(modifier = Modifier.width(8.dp))
                PremiumSingleDockItem(
                    selected = selectedIndex == 1,
                    label = "Relatórios",
                    iconRes = R.drawable.chart,
                    onClick = { onItemSelected(1) }
                )
                */
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
    // Animação de Cor (Fundo)
    val bg by animateColorAsState(
        targetValue = if (selected) AccentGold.copy(alpha = 0.15f) else Color.Transparent,
        label = "bg"
    )

    // Animação de Cor (Borda)
    val border by animateColorAsState(
        targetValue = if (selected) AccentGold.copy(alpha = 0.3f) else Color.Transparent,
        label = "border"
    )

    // Animação de Cor (Ícone e Texto)
    val contentColor by animateColorAsState(
        targetValue = if (selected) AccentGold else TextWhite.copy(alpha = 0.5f),
        label = "content"
    )

    // Animação de Escala com efeito de "Mola" (Bounce) ao clicar
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.95f, // Leve encolhimento quando não selecionado
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val shape = RoundedCornerShape(24.dp)

    // Interaction Source para remover o efeito de "ripple" padrão se desejar (opcional)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .scale(scale)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Sem ripple padrão, usamos a animação de escala
                onClick = onClick
            ),
        shape = shape,
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}