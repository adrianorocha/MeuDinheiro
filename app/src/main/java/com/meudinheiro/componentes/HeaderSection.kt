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
import java.io.File
import java.text.NumberFormat
import java.util.Locale

// Definições de Cores Locais (Garanta que combinem com seu MainScreen)
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
    despesaTotal: Double = 0.0
) {
    val containerShape = RoundedCornerShape(24.dp)
    val containerBg = CardBg.copy(alpha = 0.60f)
    val containerBorder = Color.White.copy(alpha = 0.1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = containerShape,
        color = containerBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, containerBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
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
                        .padding(end = 8.dp) // Espaço para não colar nos ícones
                ) {
                    Text(
                        text = "Olá, $nome",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 32.sp // Altura de linha para quando quebrar
                        ),
                        color = TextWhite,
                        // AJUSTE 1: Permitir 2 linhas para nomes longos
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    if (!chipText.isNullOrBlank()) {
                        PremiumChip(
                            text = chipText,
                            style = chipStyle
                        )
                    }
                }

                // Ícones (Sino e Avatar)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    PremiumAvatarButton(fotoUri = fotoUri, onClick = onProfileClick)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // --- Resumo Global ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniSummaryItem(
                    label = "Entradas",
                    value = receitaTotal,
                    color = Color(0xFF4CAF50),
                    iconUp = true
                )

                Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.1f)))

                MiniSummaryItem(
                    label = "Saídas",
                    value = despesaTotal,
                    color = Color(0xFFEF5350),
                    iconUp = false
                )
            }
        }
    }
}

@Composable
private fun MiniSummaryItem(label: String, value: Double, color: Color, iconUp: Boolean) {
    val nf = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (iconUp) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextWhite.copy(alpha = 0.7f)
            )
            Text(
                text = nf.format(value),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
        }
    }
}

@Composable
private fun PremiumChip(text: String, style: HeaderChipStyle, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = txtColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
    // Container clicável
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        // Círculo de fundo do ícone
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            content()
        }

        // AJUSTE 2: Lógica do Badge Numérico
        if (showBadge || badgeCount > 0) {
            val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
            val hasCount = badgeCount > 0

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp) // Desloca levemente para fora para ficar igual a imagem
                    .then(
                        if (hasCount) Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        else Modifier.size(10.dp) // Bolinha pequena se não tiver numero (só aviso)
                    )
                    .background(BadgeRed, CircleShape)
                    .border(2.dp, PremiumDarkBlue, CircleShape), // Borda da cor do fundo para "recortar" visualmente
                contentAlignment = Alignment.Center
            ) {
                if (hasCount) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 3.dp)
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
            .size(48.dp)
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