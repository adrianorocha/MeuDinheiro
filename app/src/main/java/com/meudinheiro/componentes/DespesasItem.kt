package com.meudinheiro.componentes

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.random
import com.meudinheiro.funcoes.DateUtils
import com.meudinheiro.funcoes.compartilharComprovante
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen // Certifique-se de ter essa cor!
import com.meudinheiro.funcoes.Haptics // Nosso motor de vibração
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

// Cores Premium Locais (Mantidas)
private val ItemBg = Color(0xFF1E2B3E).copy(alpha = 0.9f)
private val GreenColor = Color(0xFF69F0AE)
private val RedColor = Color(0xFFEF5350)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DespesasItem(
    item: DespesasDomain,
    isPrivate: Boolean = false,
    onRemover: (DespesasDomain) -> Unit,
    onTogglePago: ((DespesasDomain) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    nomeCartao: String? = null
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ==========================================
    // 🔒 TRAVA DE SEGURANÇA DO LASER (Swipe)
    // ==========================================
    val podePagarPorSwipe = onTogglePago != null &&
            item.tipo == TipoDespesa.DEBITO &&
            !item.pago

    // ==========================================
    // 🚀 O MOTOR LASER-CUT (SWIPE)
    // ==========================================
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // 🟢 ARRASTO PARA PAGAR (DIREITA)
                    if (podePagarPorSwipe) {
                        scope.launch {
                            Haptics.vibrar(context, "sucesso")
                            kotlinx.coroutines.delay(150)
                            onTogglePago?.invoke(item)
                        }
                    }
                    false // Quica de volta pro centro
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // 🔴 ARRASTO PARA EXCLUIR (ESQUERDA)
                    Haptics.vibrar(context, "alerta")
                    onRemover(item)
                    true // O Laser corta o card
                }
                else -> false
            }
        }
    )

    // ==========================================
    // 🚀 INTEGRAÇÃO GHOST MODE: O "CHOQUE" TÁTICO
    // ==========================================
    // Dispara a vibração de interferência sempre que o modo privado for ativado
    LaunchedEffect(isPrivate) {
        if (isPrivate) {
            // Vibração curta de erro/interferência
            Haptics.vibrar(context, "alerta")
        }
    }

    // O Envoltório do Swipe envolve todo o card
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = podePagarPorSwipe, // Trava o lado direito condicionalmente
        enableDismissFromEndToStart = true, // Excluir sempre livre
        backgroundContent = {
            LaserSwipeBackground(dismissState = dismissState)
        },
        content = {
            // O SEU CARD ORIGINAL FICA AQUI DENTRO (Separado por organização)
            CardItemContent(
                item = item,
                isPrivate = isPrivate,
                onTogglePago = onTogglePago,
                onClick = onClick,
                onLongClick = { showDialog = true }
            )
        }
    )

    // ==========================================
    // DIALOG DE COMPARTILHAMENTO (MANTIDO INTACTO)
    // ==========================================
    if (showDialog) {
        val mensagemAviso = remember(item) {
            if (item.tipo == TipoDespesa.DEBITO) {
                if (item.pago) "O valor será restituído ao saldo." else "O saldo não será afetado (não estava pago)."
            } else {
                if (item.pago) "O valor será deduzido do saldo." else "O saldo não será afetado."
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1E2B3E),
            title = { Text("Ações da Movimentação", color = Color.White) },
            text = {
                Column {
                    Text("O que deseja fazer com '${item.descricao}'?", color = Color.White.copy(0.9f))
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            showDialog = false
                            compartilharComprovante(context, item.toDespesa(), nomeCartao, item.conta)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, tint = NeonCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Ver Comprovante", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(mensagemAviso, color = Color.White.copy(0.6f), fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRemover(item); showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor)
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar", color = Color.White) }
            }
        )
    }
}

// ==========================================
// 🚀 O FUNDO REATIVO LASER-CUT (Swipe)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaserSwipeBackground(dismissState: SwipeToDismissBoxState) {
    val direction = dismissState.dismissDirection

    // Pulso Neon que fica "respirando" enquanto arrasta
    val infiniteTransition = rememberInfiniteTransition(label = "neon_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // A cor do Laser depende de pra onde você tá puxando
    val corLaser = when (dismissState.targetValue) {
        SwipeToDismissBoxValue.StartToEnd -> NeonCyan // Pagar
        SwipeToDismissBoxValue.EndToStart -> RedColor // Deletar
        else -> Color.Transparent
    }

    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    val icon = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.CheckCircle
        SwipeToDismissBoxValue.EndToStart -> Icons.Default.DeleteSweep
        else -> Icons.Default.CheckCircle // Fake
    }

    // O ícone dá um leve "pulo" de tamanho quando atinge o ponto de corte
    val scale = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.8f else 1.2f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        corLaser.copy(alpha = pulseAlpha * 0.3f),
                        Color.Transparent,
                        corLaser.copy(alpha = pulseAlpha * 0.3f)
                    )
                )
            )
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = corLaser,
            modifier = Modifier
                .size(32.dp)
                .scale(scale) // Aplica o crescimento reativo
        )
    }
}

// ==========================================
// O SEU CARD ORIGINAL (Com Integração Ghost)
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardItemContent(
    item: DespesasDomain,
    isPrivate: Boolean,
    onTogglePago: ((DespesasDomain) -> Unit)?,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val dataFormatada = remember(item.data) { DateUtils.formatarData(Date(item.data)) }
    val resId = remember(item.pic) {
        val id = context.resources.getIdentifier(item.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.ic_launcher_foreground
    }

    // ==========================================
    // 🚀 INTEGRAÇÃO GHOST MODE: ANIMAÇÃO VISUAL
    // ==========================================
    // Motor da animação de interferência
    val glitchProgress = remember { Animatable(0f) }

    // Dispara a interferência visual sempre que o modo privado for ativado
    LaunchedEffect(isPrivate) {
        if (isPrivate) {
            glitchProgress.snapTo(1f) // Começa com intensidade máxima
            // A interferência visual diminui rápido (300ms)
            glitchProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = LinearEasing)
            )
        } else {
            glitchProgress.snapTo(0f) // Reseta visual
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ItemBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ÍCONE DA CATEGORIA
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painter = painterResource(id = resId), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(12.dp))

            // DESCRIÇÃO E DATA
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descricao,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = dataFormatada, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }

            // ==========================================
            // 🚀 INTEGRAÇÃO GHOST MODE: ENVOLTÓRIO VISUAL
            // ==========================================
            Column(horizontalAlignment = Alignment.End) {
                val corValor = if (item.tipo == TipoDespesa.CREDITO) GreenColor else Color.White

                // Envelopamos o valor no GlitchAnimation
                Box(contentAlignment = Alignment.CenterEnd) {

                    // Standard Text (Ele é **** ou R$ dependendo do isPrivate)
                    Text(
                        text = formatarMoedaBR(item.valor, isPrivate),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = corValor,
                        modifier = Modifier
                            .alpha(if (glitchProgress.value > 0.5f) 0f else 1f) // Esconde o texto real durante glitch intenso
                    )

                    // 👻 O TEXTO DE GLITCH OVERLAY (Os caracteres criptografados que embaralham)
                    if (isPrivate && glitchProgress.value > 0.1f) {
                        // Gera uma string de caracteres criptografados aleatórios baseado no valor real
                        // (Exemplo simplificado: usa caracteres fixos)
                        Text(
                            text = "!@#$%^&*", // Caracteres embaralhados
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = NeonCyan.copy(alpha = glitchProgress.value),
                            modifier = Modifier
                                .graphicsLayer {
                                    // Offset horizontal e vertical aleatório baseado na animação
                                    translationX = ((-1f..1f).random() * glitchProgress.value * 10).dp.toPx()
                                    translationY = ((-1f..1f).random() * glitchProgress.value * 5).dp.toPx()
                                }
                                .blur(if (glitchProgress.value > 0.8f) 2.dp else 0.dp) // Blur leve no pico
                        )
                    }
                }

                // STATUS DE PAGO (Pagar/Pago)
                if (item.tipo == TipoDespesa.DEBITO && onTogglePago != null) {
                    Spacer(Modifier.height(4.dp))
                    val (bgStatus, txtColor, txtStatus) = if (item.pago) {
                        Triple(GreenColor.copy(alpha = 0.2f), GreenColor, "Pago")
                    } else {
                        Triple(RedColor.copy(alpha = 0.15f), RedColor.copy(alpha = 0.8f), "Pagar")
                    }

                    Surface(
                        color = bgStatus,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onTogglePago(item) }
                    ) {
                        Text(
                            text = txtStatus, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = txtColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// SUA FUNÇÃO DE CONVERSÃO (MANTIDA)
fun DespesasDomain.toDespesa(): Despesa {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toDespesa.data }
    return Despesa(
        descricao = this.descricao,
        valor = this.valor,
        data = Date(this.data),
        categoria = this.categoria ?: "Geral",
        pic = this.pic,
        conta = this.conta ?: "Conta Principal",
        tipo = this.tipo,
        pago = this.pago,
        mes = cal.get(Calendar.MONTH) + 1,
        ano = cal.get(Calendar.YEAR),
        cartaoId = this.cartaoId ?: 0
    )
}