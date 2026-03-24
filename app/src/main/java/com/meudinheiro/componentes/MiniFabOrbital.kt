package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MiniFabOrbital(
    iconeRes: Int, // 🚀 MUDOU AQUI: Agora recebe o ID do Drawable (ex: R.drawable.bank)
    cor: Color,
    offsetX: Dp,
    offsetY: Dp,
    progresso: Float,
    onClick: () -> Unit
) {
    if (progresso > 0f) {
        Box(
            modifier = Modifier
                .offset(x = offsetX * progresso, y = offsetY * progresso)
                .scale(progresso)
                .alpha(progresso)
                .size(48.dp)
                .clip(CircleShape)
                .background(cor.copy(alpha = 0.15f))
                .border(1.dp, cor.copy(alpha = 0.5f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                // 🚀 MUDOU AQUI: Usamos painterResource com o iconeRes
                painter = painterResource(id = iconeRes),
                contentDescription = null,
                tint = cor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}