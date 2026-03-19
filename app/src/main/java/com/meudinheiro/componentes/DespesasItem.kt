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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
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
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.DateUtils
import com.meudinheiro.funcoes.compartilharComprovante
import com.meudinheiro.funcoes.formatarMoedaBR
import java.util.Date
import com.meudinheiro.ui.theme.*
import java.util.Calendar

// Cores Premium Locais
private val ItemBg = Color(0xFF1E2B3E).copy(alpha = 0.9f)
private val GreenColor = Color(0xFF69F0AE)
private val RedColor = Color(0xFFEF5350)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DespesasItem(
    item: DespesasDomain,
    isPrivate: Boolean = false,
    onRemover: (DespesasDomain) -> Unit,
    onTogglePago: ((DespesasDomain) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    // NOVO: Precisamos dos nomes reais do cartão/conta se quiser o recibo 100% fiel
    // Se não passar, o recibo usa os dados genéricos do item
    nomeCartao: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val dataFormatada = remember(item.data) { DateUtils.formatarData(Date(item.data)) }
    val context = LocalContext.current

    val resId = remember(item.pic) {
        val id = context.resources.getIdentifier(item.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.ic_launcher_foreground
    }

    if (showDialog) {
        val mensagemAviso = remember(item) {
            if (item.tipo == TipoDespesa.DEBITO) {
                if (item.pago) "O valor será restituído ao saldo."
                else "O saldo não será afetado (não estava pago)."
            } else {
                if (item.pago) "O valor será deduzido do saldo."
                else "O saldo não será afetado."
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1E2B3E),
            title = { Text("Ações da Movimentação", color = TextWhite) },
            text = {
                Column {
                    Text("O que deseja fazer com '${item.descricao}'?", color = TextWhite.copy(0.9f))
                    Spacer(Modifier.height(16.dp))

                    // 💡 BOTÃO DE COMPARTILHAR COMPROVANTE (Dentro do Dialog)
                    Button(
                        onClick = {
                            showDialog = false
                            // COMO CONVERTER DespesasDomain para Despesa (Se suas classes forem diferentes)
                            // Se forem a mesma, basta passar 'item'. Se não, crie uma função de mapeamento.
                            // Aqui assumo que você tem uma forma de passar o item para a função:

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
                    Text(mensagemAviso, color = TextWhite.copy(0.6f), fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRemover(item); showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor)
                ) { Text("Excluir") }
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
                onClick = { onClick?.invoke() }, // Clique rápido faz a ação padrão
                onLongClick = { showDialog = true } // Segurar o clique abre as opções!
            ),        shape = RoundedCornerShape(16.dp),
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

fun DespesasDomain.toDespesa(): Despesa {
    // Usamos o Calendar para extrair mês e ano de forma eficiente e segura
    val cal = Calendar.getInstance().apply {
        timeInMillis = this@toDespesa.data
    }

    return Despesa(
        descricao = this.descricao,
        valor = this.valor,
        data = Date(this.data),
        categoria = this.categoria ?: "Geral",
        pic = this.pic,
        conta = this.conta ?: "Conta Principal",

        // 💡 CORREÇÃO 1: Tipo dinâmico baseado no objeto original
        // Se o seu gerador de recibo espera String, use .name ou o texto formatado
        tipo = this.tipo,
        pago = this.pago,

        // 💡 CORREÇÃO 2: Extração de data sem usar Formatter/String (mais rápido)
        mes = cal.get(Calendar.MONTH) + 1, // Calendar.MONTH começa em 0
        ano = cal.get(Calendar.YEAR),

        cartaoId = this.cartaoId ?: 0
    )
}