package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.DateUtils
import com.meudinheiro.funcoes.formatarMoedaBR

// Cores
private val ItemBg = Color(0xFF1E2B3E).copy(alpha = 0.8f)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DespesasItem(
    item: DespesasDomain,
    onRemover: (Int) -> Unit,
    onTogglePago: ((DespesasDomain) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val dataFormatada = remember(item.data) { DateUtils.formatarData(item.data) }
    val context = LocalContext.current

    val resId = remember(item.pic) {
        val id = context.resources.getIdentifier(item.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.user
    }

    if (showDialog) {
        // Dialog de remoção
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1E2B3E),
            titleContentColor = TextWhite,
            textContentColor = TextWhite.copy(0.8f),
            title = { Text("Remover despesa") },
            text = { Text("Deseja remover esta despesa? O valor será restituído.") },
            confirmButton = {
                Button(onClick = { onRemover(item.id); showDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = TextWhite)) { Text("Cancelar") }
            }
        )
    }

    // Visual Card
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(onClick = { onClick?.invoke() }, onLongClick = { showDialog = true }),
        shape = RoundedCornerShape(18.dp),
        color = ItemBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icone
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    tint = Color.Unspecified, // Usa cor original do drawable
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descricao,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dataFormatada,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                // Valor
                val corValor = if (item.tipo == TipoDespesa.CREDITO) Color(0xFF69F0AE) else TextWhite
                Text(
                    text = formatarMoedaBR(item.valor),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = corValor
                )

                if (item.tipo == TipoDespesa.DEBITO && onTogglePago != null) {
                    Spacer(Modifier.height(8.dp))

                    val (bg, txtColor, txt) = if (item.pago) {
                        Triple(Color(0xFF00C853).copy(alpha = 0.2f), Color(0xFF69F0AE), "Pago")
                    } else {
                        Triple(Color(0xFFFF3D00).copy(alpha = 0.15f), Color(0xFFFF9E80), "Pagar")
                    }

                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onTogglePago(item) }
                    ) {
                        Text(
                            text = txt,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = txtColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}