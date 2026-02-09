package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meudinheiro.R
import com.meudinheiro.funcoes.formatarMoedaBR
import java.io.File
import java.text.NumberFormat
import java.util.Locale

// Definições de Cores
private val CardBg = Color(0xFF1E2B3E)
private val BadgeRed = Color(0xFFFF3D00)
enum class HeaderChipStyle {
    PRIMARY,
    SUCCESS,
    NEUTRAL
}
@Composable
fun HeaderSection(
    nome: String,
    fotoUri: String?,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    chipText: String? = "Sincronizado",
    chipStyle: HeaderChipStyle = HeaderChipStyle.SUCCESS,
    showNotifications: Boolean = true,
    hasUnreadNotifications: Boolean = false,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    receitaTotal: Double = 0.0,
    despesaTotal: Double = 0.0,
    isPrivateMode: Boolean = false,
    onTogglePrivate: () -> Unit = {}
) {
    val containerShape = RoundedCornerShape(22.dp)
    val containerBg = CardBg.copy(alpha = 0.60f)
    val containerBorder = Color.White.copy(alpha = 0.1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = containerShape,
        color = containerBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, containerBorder)
    ) {
        Column(
            modifier = Modifier.padding(all = 14.dp)
        ) {
            // --- Linha Superior ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Coluna do Nome e Chip
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Olá, $nome",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        ),
                        color = TextWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!chipText.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        PremiumChip(text = chipText, style = chipStyle)
                    }
                }

                // Ícones (Olho, Sino e Avatar)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Botão Modo Privado
                    PremiumIconButton(
                        contentDescription = if (isPrivateMode) "Mostrar" else "Ocultar",
                        showBadge = false,
                        badgeCount = 0,
                        onClick = onTogglePrivate
                    ) {
                        Icon(
                            imageVector = if (isPrivateMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (showNotifications) {
                        PremiumIconButton(
                            contentDescription = "Notificações",
                            showBadge = hasUnreadNotifications,
                            badgeCount = notificationCount,
                            onClick = onNotificationsClick
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    PremiumAvatarButton(fotoUri = fotoUri, onClick = onProfileClick)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            // Alerta se as saídas forem maiores que as entradas (apenas se não estiver privado)
            val orcamentoNegativo = despesaTotal > receitaTotal && receitaTotal > 0

            // --- Resumo Global ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniSummaryItem(
                    label = "Entradas",
                    valorTexto = formatarMoedaBR(receitaTotal, isPrivateMode),
                    color = Color(0xFF4CAF50),
                    iconUp = true
                )

                Box(modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.1f)))

                MiniSummaryItem(
                    label = "Saídas",
                    valorTexto = formatarMoedaBR(despesaTotal, isPrivateMode),
                    color = Color(0xFFEF5350),
                    iconUp = false,
                    isAlert = orcamentoNegativo && !isPrivateMode
                )
            }
        }
    }
}

@Composable
private fun MiniSummaryItem(
    label: String,
    valorTexto: String, // Recebe a String formatada
    color: Color,
    iconUp: Boolean,
    isAlert: Boolean = false
) {
    val finalColor = if (isAlert) Color(0xFFFF5252) else color
    val backgroundColor = if (isAlert) Color(0xFFFF5252).copy(alpha = 0.25f) else color.copy(alpha = 0.15f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(backgroundColor, CircleShape)
                .then(if (isAlert) Modifier.border(1.dp, finalColor, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isAlert) Icons.Rounded.PriorityHigh else (if (iconUp) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward),
                contentDescription = null,
                tint = finalColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isAlert) finalColor else TextWhite.copy(alpha = 0.7f),
                fontWeight = if (isAlert) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = valorTexto,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isAlert) finalColor else TextWhite
            )
        }
    }
}

@Composable
private fun PremiumChip(text: String, style: HeaderChipStyle, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val (bg, txtColor) = when (style) {
        HeaderChipStyle.SUCCESS -> Color(0xFF00C853).copy(alpha = 0.2f) to Color(0xFF69F0AE)
        else -> Color.White.copy(alpha = 0.1f) to TextWhite
    }

    Surface(
        color = bg,
        shape = shape,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            color = txtColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PremiumIconButton(
    contentDescription: String,
    showBadge: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            content()
        }

        if (showBadge || badgeCount > 0) {
            val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
            val hasCount = badgeCount > 0

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .then(
                        if (hasCount) Modifier.defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        else Modifier.size(8.dp)
                    )
                    .background(BadgeRed, CircleShape)
                    .border(2.dp, PremiumDarkBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (hasCount) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumAvatarButton(fotoUri: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick)
    ) {
        val file = remember(fotoUri) { fotoUri?.trim()?.takeIf { it.isNotBlank() }?.let { File(it) } }
        if (file != null && file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(file).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.user),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}