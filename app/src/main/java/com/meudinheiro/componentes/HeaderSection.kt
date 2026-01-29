package com.meudinheiro.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Divider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meudinheiro.R
import java.io.File
import java.text.NumberFormat
import java.util.Locale

enum class HeaderChipStyle { PRIMARY, SUCCESS, NEUTRAL }

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
    // Novos parâmetros para totais globais
    receitaTotal: Double = 0.0,
    despesaTotal: Double = 0.0
) {
    val containerShape = RoundedCornerShape(22.dp)
    val containerBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val containerBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = containerShape,
        color = containerBg,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, containerBorder)
    ) {
        Box(
            modifier = Modifier
                .clip(containerShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                // Linha Superior: Perfil, Infos e Sino
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Meu Dinheiro",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Olá, $nome.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Clip
                        )
                        if (!chipText.isNullOrBlank()) {
                            Spacer(Modifier.size(8.dp))
                            PremiumChip(
                                text = chipText,
                                style = chipStyle,
                                modifier = Modifier.fillMaxWidth(0.72f)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                    }

                    PremiumAvatarButton(fotoUri = fotoUri, onClick = onProfileClick)
                }

                // Linha Inferior: Resumo Global (Receitas vs Despesas)
                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MiniSummaryItem(
                        label = "Receitas",
                        value = receitaTotal,
                        color = Color(0xFF4CAF50), // Verde
                        iconUp = true
                    )
                    MiniSummaryItem(
                        label = "Despesas",
                        value = despesaTotal,
                        color = Color(0xFFEF5350), // Vermelho
                        iconUp = false
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniSummaryItem(
    label: String,
    value: Double,
    color: Color,
    iconUp: Boolean
) {
    val nf = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (iconUp) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = nf.format(value),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}

// --- Componentes auxiliares (PremiumChip, PremiumIconButton, PremiumAvatarButton) ---
// (Mantenha o código deles exatamente como estava no seu arquivo original)
@Composable
private fun PremiumChip(text: String, style: HeaderChipStyle, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    val (bg, border, dot) = when (style) {
        HeaderChipStyle.PRIMARY -> Triple(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.colorScheme.primary)
        HeaderChipStyle.SUCCESS -> Triple(Color(0xFF00C853).copy(alpha = 0.12f), Color(0xFF00C853).copy(alpha = 0.32f), Color(0xFF00C853))
        HeaderChipStyle.NEUTRAL -> Triple(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(modifier = modifier, shape = shape, color = bg, border = BorderStroke(1.dp, border), tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dot.copy(alpha = 0.95f))); Spacer(Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun PremiumIconButton(contentDescription: String, showBadge: Boolean, badgeCount: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(if (pressed) 1f else 0f, label = "g"); val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "s")
    val ringColor by animateColorAsState(if (pressed) MaterialTheme.colorScheme.primary.copy(0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(0.55f), label = "r")
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.22f*glowAlpha), Color.Transparent))))
        Box(Modifier.size(42.dp).scale(scale).clip(CircleShape).border(1.2.dp, ringColor, CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.45f)).clickable(interactionSource = interaction, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
            content(); if (pressed) Box(Modifier.fillMaxSize().background(Color.White.copy(0.08f)))
        }
        if (badgeCount > 0) {
            Surface(Modifier.align(Alignment.TopEnd).padding(top=2.dp, end=2.dp), shape=RoundedCornerShape(999.dp), color=Color(0xFFFF3D00), border=BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)) {
                Text(text = if(badgeCount>99)"99+" else badgeCount.toString(), modifier = Modifier.padding(6.dp, 2.dp), style=MaterialTheme.typography.labelSmall, color=Color.White)
            }
        } else if (showBadge) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(10.dp).clip(CircleShape).background(Color(0xFFFF3D00)).border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape))
        }
    }
}

@Composable
private fun PremiumAvatarButton(fotoUri: String?, onClick: () -> Unit) {
    val context = LocalContext.current; val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(if (pressed) 1f else 0f, label="g"); val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label="s")
    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.28f*glowAlpha), Color.Transparent))))
        Box(Modifier.size(50.dp).scale(scale).clip(CircleShape).border(1.5.dp, if(pressed)MaterialTheme.colorScheme.primary.copy(0.65f) else MaterialTheme.colorScheme.outlineVariant.copy(0.6f), CircleShape).clickable(interactionSource = interaction, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
            val file = remember(fotoUri) { fotoUri?.trim()?.takeIf { it.isNotBlank() && it.startsWith("/") }?.let { File(it) } }
            if (file != null && file.exists()) AsyncImage(model = ImageRequest.Builder(context).data(file).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Image(painter = painterResource(id = R.drawable.user), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (pressed) Box(Modifier.fillMaxSize().background(Color.White.copy(0.1f)))
        }
    }
}
