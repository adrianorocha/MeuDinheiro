package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meudinheiro.R
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.DateUtils
import com.meudinheiro.funcoes.formatarMoedaBR

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DespesasItem(
    item: DespesasDomain,
    onRemover: (Int) -> Unit,
    onTogglePago: ((Int, Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    val dataFormatada = remember(item.data) { DateUtils.formatarData(item.data) }

    val context = LocalContext.current
    val resId = remember(item.pic) {
        val id = context.resources.getIdentifier(item.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.user // fallback
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Remover despesa") },
            text = {
                Text(
                    "Deseja remover esta despesa? " +
                            "O valor será restituído ao saldo da conta."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemover(item.id)
                        showDialog = false
                    }
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }

    val cardColor =
        if (item.pago)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        else
            MaterialTheme.colorScheme.surface

    val valorColor =
        if (item.pago)
            MaterialTheme.colorScheme.onSurfaceVariant
        else
            MaterialTheme.colorScheme.onSurface

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = { showDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = item.descricao,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dataFormatada,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = formatarMoedaBR(item.valor),
                        style = MaterialTheme.typography.titleMedium,
                        color = valorColor
                    )
                    if(item.tipo == TipoDespesa.DEBITO){

                    Spacer(Modifier.height(4.dp))

                    // Chip de status / ação "Marcar como pago"
                    val chipBg: Color
                    val chipBorder: Color
                    val chipTextColor: Color
                    val chipText: String
                    val clickable = !item.pago && onTogglePago != null

                    if (item.pago) {
                        chipBg = Color(0xFF00C853).copy(alpha = 0.12f)
                        chipBorder = Color(0xFF00C853)
                        chipTextColor = Color(0xFF00C853)
                        chipText = "Pago"
                    } else {
                        chipBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        chipBorder = MaterialTheme.colorScheme.primary
                        chipTextColor = MaterialTheme.colorScheme.primary
                        chipText = "Marcar como pago"
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = chipBg,
                        border = BorderStroke(1.dp, chipBorder),
                        modifier = if (clickable) {
                            Modifier.clickable {
                                onTogglePago?.invoke(item.id, !item.pago)
//                                onTogglePago?.invoke(item.id, true)
                            }
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            color = chipTextColor,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
