package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MonthPill(
    mes: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Transição de cor suave para o fundo e texto
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.05f),
        animationSpec = tween(400), label = "bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0F172A) else Color.White.copy(alpha = 0.6f),
        animationSpec = tween(400), label = "text"
    )

    Surface(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Text(
            text = mes,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        )
    }
}