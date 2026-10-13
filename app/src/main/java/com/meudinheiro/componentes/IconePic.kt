package com.meudinheiro.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meudinheiro.domain.IconesCategoria

/**
 * Desenha o ícone de uma CATEGORIA a partir do `pic` gravado (R50). Ordem de resolução:
 * 1) drawable existente com esse nome (comportamento original, cores próprias);
 * 2) catálogo novo ([IconesCategoria], vetor tingido com [tint]);
 * 3) [fallbackRes] (o mesmo fallback que cada tela já usava). Nunca lança por `pic` desconhecido.
 * Não usar para ícones de bancos/contas.
 */
@Composable
fun IconePic(
    pic: String?,
    @DrawableRes fallbackRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color(0xFF00E5FF),
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val resId = remember(pic) {
        if (pic.isNullOrEmpty()) 0
        else context.resources.getIdentifier(pic, "drawable", context.packageName)
    }
    if (resId != 0) {
        Icon(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = modifier.size(size)
        )
        return
    }
    val item = remember(pic) { IconesCategoria.porChave(pic) }
    if (item != null) {
        Icon(
            imageVector = item.vetor,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            painter = painterResource(fallbackRes),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = modifier.size(size)
        )
    }
}
