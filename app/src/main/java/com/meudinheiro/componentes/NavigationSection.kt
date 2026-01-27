package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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

@Composable
fun NavigationSection(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = selectedIndex == 0

    val dockShape = RoundedCornerShape(26.dp)

// “Glass” sem blur (compatível com qualquer Android), com gradiente e transparência
    val dockBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val dockBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = dockShape,
        color = dockBg,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, dockBorder)
    ) {
        Box(
            modifier = Modifier
                .clip(dockShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumSingleDockItem(
                    selected = selected,
                    label = "Conta",
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
    val shape = RoundedCornerShape(18.dp)

    val bg by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        label = "dockItemBg"
    )

    val border by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        label = "dockItemBorder"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dockItemContent"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        label = "dockItemScale"
    )

    val padH by animateDpAsState(
        targetValue = if (selected) 18.dp else 16.dp,
        label = "dockItemPadH"
    )

    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 6.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = padH, vertical = 10.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Se seus ícones forem PNG coloridos e você NÃO quiser tint:
            // troque o tint por Color.Unspecified.
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )

            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))

            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }


}