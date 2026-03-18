package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room3.util.copy
import com.meudinheiro.data.Despesa
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.CardGlass
import java.text.SimpleDateFormat
import java.util.Locale
import com.meudinheiro.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.* // Para pegar todos os ícones arredondados


@Composable
fun ItemExtratoNeon(despesa: Despesa) {

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
                text = despesa.descricao, // 👈 Antes era 'estabelecimento'
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
        }

        // Valor Formatado
        Text(
            text = "R$ ${String.format("%.2f", despesa.valor)}",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}