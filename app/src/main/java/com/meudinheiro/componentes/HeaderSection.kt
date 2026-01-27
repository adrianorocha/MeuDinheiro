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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meudinheiro.R
import java.io.File

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
    onNotificationsClick: () -> Unit = {}
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
                            modifier = Modifier.fillMaxWidth(0.72f) // evita encostar nos botões da direita
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                if (showNotifications) {
                    PremiumIconButton(
                        contentDescription = "Notificações",
                        showBadge = hasUnreadNotifications,
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

                PremiumAvatarButton(
                    fotoUri = fotoUri,
                    onClick = onProfileClick
                )
            }
        }
    }


}

@Composable
private fun PremiumChip(
    text: String,
    style: HeaderChipStyle,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)

    val (bg, border, dot) = when (style) {
        HeaderChipStyle.PRIMARY -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.primary
        )
        HeaderChipStyle.SUCCESS -> Triple(
            Color(0xFF00C853).copy(alpha = 0.12f),
            Color(0xFF00C853).copy(alpha = 0.32f),
            Color(0xFF00C853)
        )
        HeaderChipStyle.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = bg,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dot.copy(alpha = 0.95f))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun PremiumIconButton(
    contentDescription: String,
    showBadge: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        label = "notifGlowAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "notifScale"
    )

    val ringColor by animateColorAsState(
        targetValue = if (pressed)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        label = "notifRing"
    )

    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f * glowAlpha),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f * glowAlpha),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(42.dp)
                .scale(scale)
                .clip(CircleShape)
                .border(1.2.dp, ringColor, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            content()

            if (pressed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }

        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3D00))
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }


}

@Composable
private fun PremiumAvatarButton(
    fotoUri: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        label = "avatarGlowAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "avatarScale"
    )

    val ringColor by animateColorAsState(
        targetValue = if (pressed)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
        label = "avatarRingColor"
    )

    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f * glowAlpha),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * glowAlpha),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(50.dp)
                .scale(scale)
                .clip(CircleShape)
                .border(width = 1.5.dp, color = ringColor, shape = CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!fotoUri.isNullOrBlank()) {
                val model = if (fotoUri.startsWith("/")) File(fotoUri) else fotoUri

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.user),
                    error = painterResource(id = R.drawable.user),
                    fallback = painterResource(id = R.drawable.user)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.user),
                    contentDescription = "Avatar padrão",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (pressed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.10f))
                )
            }
        }
    }
}