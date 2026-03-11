package com.meudinheiro.componentes

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.formatarMoedaBR

@Composable
fun BluMacawInfiniteCard(
    nomeUsuario: String,
    saldoTotal: Double,
    isPrivate: Boolean
) {
    // Estados para armazenar a rotação 3D
    var rotX by remember { mutableFloatStateOf(0f) }
    var rotY by remember { mutableFloatStateOf(0f) }

    // Animação com efeito "Mola" (Spring) para voltar ao centro suavemente
    val animatedRotX by animateFloatAsState(
        targetValue = rotX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "rotX"
    )
    val animatedRotY by animateFloatAsState(
        targetValue = rotY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "rotY"
    )

    // Gradiente Base do Cartão (Azul Escuro Profundo)
    val cardGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // O "Brilho Holográfico" que se move conforme a rotação
    val holoGradient = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xFF69F0AE).copy(alpha = 0.2f), // Verde Neon da Blu Macaw
            Color(0xFF00E5FF).copy(alpha = 0.2f), // Ciano
            Color.Transparent
        ),
        start = Offset(0f, 0f),
        end = Offset(500f + (animatedRotY * 20), 500f + (animatedRotX * 20))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // Lógica de detecção de arrasto (Touch)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Soltou o dedo? Volta pro centro!
                        rotX = 0f
                        rotY = 0f
                    },
                    onDragCancel = {
                        rotX = 0f
                        rotY = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    // Limita a rotação máxima para não virar o cartão de cabeça para baixo
                    rotY = (rotY + dragAmount.x * 0.5f).coerceIn(-25f, 25f)
                    rotX = (rotX - dragAmount.y * 0.5f).coerceIn(-25f, 25f)
                }
            }
            // A Mágica do 3D acontece aqui
            .graphicsLayer {
                rotationX = animatedRotX
                rotationY = animatedRotY
                cameraDistance = 16f * density // Distância da "câmera" para dar o efeito de profundidade
            }
            .clip(RoundedCornerShape(24.dp))
            .background(cardGradient)
    ) {
        // Camada 1: O Fundo de Brilho Holográfico
        Box(modifier = Modifier.fillMaxSize().background(holoGradient))

        // Camada 2: Grafismos do Cartão (Bolas estilizadas simulando a logo da Mastercard)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = 120f,
                center = Offset(size.width + 40f, size.height - 40f)
            )
            drawCircle(
                color = Color(0xFF69F0AE).copy(alpha = 0.1f),
                radius = 120f,
                center = Offset(size.width - 40f, size.height + 40f)
            )
        }

        // Camada 3: Os Dados do Cartão
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Topo do Cartão: Logo e Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLU MACAW",
                    color = Color(0xFF69F0AE),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 2.sp
                )

                // Desenho de um Chip de Cartão minimalista
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFD54F).copy(alpha = 0.8f)) // Dourado
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(Color.Black.copy(0.2f), Offset(size.width * 0.3f, 0f), Offset(size.width * 0.3f, size.height), 2f)
                        drawLine(Color.Black.copy(0.2f), Offset(size.width * 0.7f, 0f), Offset(size.width * 0.7f, size.height), 2f)
                        drawLine(Color.Black.copy(0.2f), Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 2f)
                    }
                }
            }

            // Meio: Saldo
            Column {
                Text("Saldo Disponível", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text(
                    text = if (isPrivate) "R$ •••••" else formatarMoedaBR(saldoTotal, false),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                )
            }

            // Rodapé: Nome do Usuário e Bandeira
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = nomeUsuario.uppercase(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "INFINITE",
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}