package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.Despesa
import com.meudinheiro.funcoes.compartilharComprovante
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.CardGlass
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonRed
import java.text.SimpleDateFormat
import java.util.Locale


@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ItemExtratoNeon(
    despesa: Despesa, cartao: CartaoComConta, mostrarOrigem: Boolean = false,
    /** Menu de ações do lançamento (toque longo e ⋮): ver [LancamentoAcoesHost]. */
    onAcoes: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Mapeamento de estilo baseado na categoria (String que vem do banco)
    val (corNeon, icone) = when (despesa.categoria.uppercase()) {
        "ALIMENTAÇÃO" -> Color(0xFFFFD54F) to Icons.Rounded.Restaurant
        "TRANSPORTE" -> Color(0xFF00E5FF) to Icons.Rounded.DirectionsCar
        "SAÚDE" -> Color(0xFFEF5350) to Icons.Rounded.LocalHospital
        "LAZER" -> Color(0xFFE040FB) to Icons.Rounded.SportsEsports
        "COMPRAS", "SHOPPING" -> Color(0xFF69F0AE) to Icons.Rounded.ShoppingBag
        else -> Color.White to Icons.Rounded.Payments
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                onClick = { compartilharComprovante(context, despesa, cartao.nomeCartao, cartao.nomeConta) },
                onLongClick = onAcoes
            )
            .clip(RoundedCornerShape(16.dp))
            .background(CardGlass.copy(alpha = 0.4f))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ícone com brilho Neon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NeonRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = corNeon,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info da Despesa Real
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = despesa.descricao,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // 💡 Formatando a Date do banco para texto legível
                text = SimpleDateFormat("dd MMM, HH:mm", Locale("pt", "BR")).format(despesa.data),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            if (mostrarOrigem) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${cartao.nomeCartao} •••• ${cartao.finalCartao}",
                        color = NeonCyan.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (cartao.ehVirtual) {
                        Text(
                            "VIRTUAL", color = Color(0xFF131E29), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF7000FF).copy(alpha = 0.9f)).padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // Valor Formatado
        Text(
            text = formatarMoedaBR(despesa.valor, false),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))

        // 💡 2. ÍCONE EXPLÍCITO DE COMPARTILHAR (A "Reimpressão")
        IconButton(
            onClick = {
                compartilharComprovante(context, despesa, cartao.nomeCartao, cartao.nomeConta)
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Rounded.Share,
                contentDescription = "Reimprimir Comprovante",
                tint = NeonCyan.copy(0.5f), // Ciano sutil para não brigar com o valor
                modifier = Modifier.size(16.dp) // Ícone pequeno e elegante
            )
        }
        if (onAcoes != null) {
            IconButton(onClick = onAcoes, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Mais ações", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }
    }
}