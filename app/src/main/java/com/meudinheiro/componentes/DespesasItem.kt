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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import java.util.Date

// Cores Premium Locais
private val ItemBg = Color(0xFF1E2B3E).copy(alpha = 0.9f)
private val GreenColor = Color(0xFF69F0AE)
private val RedColor = Color(0xFFEF5350)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DespesasItem(
    item: DespesasDomain,
    isPrivate: Boolean = false,
    // MUDANÇA 1: Passamos o objeto inteiro para o ViewModel decidir a lógica
    onRemover: (DespesasDomain) -> Unit,
    onTogglePago: ((DespesasDomain) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val dataFormatada = remember(item.data) { DateUtils.formatarData(Date(item.data)) }
    val context = LocalContext.current

    val resId = remember(item.pic) {
        val id = context.resources.getIdentifier(item.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.ic_launcher_foreground
    }

    if (showDialog) {
        // MUDANÇA 2: Texto dinâmico baseado no status de pagamento
        val mensagemAviso = remember(item) {
            if (item.tipo == TipoDespesa.DEBITO) {
                if (item.pago) "O valor será restituído ao saldo."
                else "O saldo não será afetado (não estava pago)."
            } else {
                // Para receitas (Crédito)
                if (item.pago) "O valor será deduzido do saldo."
                else "O saldo não será afetado."
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1E2B3E),
            title = { Text("Remover movimentação", color = TextWhite) },
            text = {
                Column {
                    Text("Deseja remover '${item.descricao}'?", color = TextWhite.copy(0.9f))
                    Spacer(Modifier.height(8.dp))
                    Text(mensagemAviso, color = TextWhite.copy(0.6f), fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    // MUDANÇA 3: Passa o item completo
                    onClick = { onRemover(item); showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor)
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar", color = TextWhite) }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = { showDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ItemBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descricao,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dataFormatada,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextWhite.copy(alpha = 0.5f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val corValor = if (item.tipo == TipoDespesa.CREDITO) GreenColor else TextWhite

                Text(
                    text = formatarMoedaBR(item.valor, isPrivate),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = corValor
                )

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
                            text = txtStatus,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = txtColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}