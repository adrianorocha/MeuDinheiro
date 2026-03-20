package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan


@Composable
fun NeonBottomNavigation(
    abaSelecionada: Int,
    onTabSelected: (Int) -> Unit
) {
    val itens = listOf(
        Triple("Minha Conta", R.drawable.bank, 0),
        Triple("Extrato", R.drawable.extrato, 1),
        Triple("Metas", R.drawable.metas, 2)
    )
// 🚀 O MOTOR DE ANIMAÇÃO INFINITA
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // Animação de Alpha: Faz o glow oscilar (Fade In/Out)
    val infiniteAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Animação de Scale: Faz o ícone "respirar" (Pulsar o tamanho)
    val infinitePulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f, // 8% de aumento para o efeito ser sutil
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    // Barra com efeito de vidro e borda neon superior
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .graphicsLayer { shadowElevation = 20f },
        color = DeepSpaceBlue.copy(alpha = 0.95f), // Quase opaco para esconder o conteúdo atrás
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)) // Linha ultra fina
    ) {
        Column {
            // Linha Neon de destaque no topo da barra
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, NeonCyan, Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                itens.forEach { (titulo, icone, index) ->
                    val selecionado = abaSelecionada == index
                    val animColor by animateColorAsState(
                        targetValue = if (selecionado) NeonCyan else Color.White.copy(alpha = 0.4f),
                        label = "color"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null // Remove o ripple cinza padrão
                            ) { onTabSelected(index) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = icone),
                            contentDescription = titulo,
                            tint = animColor,
                            modifier = Modifier.size(if (selecionado) 28.dp else 24.dp)
                        )

                        AnimatedVisibility(visible = selecionado) {
                            Text(
                                text = titulo,
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}