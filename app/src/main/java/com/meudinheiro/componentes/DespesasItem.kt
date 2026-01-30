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

// Cores Premium Locais
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
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1E2B3E),
            titleContentColor = TextWhite,
            textContentColor = TextWhite.copy(0.8f),
            title = { Text("Remover despesa") },
            text = { Text("Deseja remover esta despesa? O valor será restituído.") },
            confirmButton = {
                Button(
                    onClick = { onRemover(item.id); showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextWhite)
                ) { Text("Cancelar") }
            }
        )
    }

    // Visual Card Compacto
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // REDUÇÃO: Padding vertical externo de 6dp para 3dp
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .combinedClickable(onClick = { onClick?.invoke() }, onLongClick = { showDialog = true }),
        shape = RoundedCornerShape(16.dp),
        color = ItemBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 2.dp
    ) {
        Row(
            // REDUÇÃO: Padding interno de 16dp para 12dp
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // REDUÇÃO: Ícone de 48dp para 40dp
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descricao,
                    // AJUSTE: Fonte levemente menor e compacta
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dataFormatada,
                    style = MaterialTheme.typography.labelSmall, // Fonte menor para data
                    color = TextWhite.copy(alpha = 0.5f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val corValor = if (item.tipo == TipoDespesa.CREDITO) Color(0xFF69F0AE) else TextWhite
                Text(
                    text = formatarMoedaBR(item.valor),
                    // AJUSTE: Fonte de valor um pouco mais compacta
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = corValor
                )

                if (item.tipo == TipoDespesa.DEBITO && onTogglePago != null) {
                    // REDUÇÃO: Spacer de 8dp para 4dp
                    Spacer(Modifier.height(4.dp))

                    val (bg, txtColor, txt) = if (item.pago) {
                        Triple(Color(0xFF00C853).copy(alpha = 0.2f), Color(0xFF69F0AE), "Pago")
                    } else {
                        Triple(Color(0xFFFF3D00).copy(alpha = 0.15f), Color(0xFFFF9E80), "Pagar")
                    }

                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onTogglePago(item) }
                    ) {
                        Text(
                            text = txt,
                            fontSize = 9.sp, // Fonte micro para o botão
                            fontWeight = FontWeight.Bold,
                            color = txtColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}