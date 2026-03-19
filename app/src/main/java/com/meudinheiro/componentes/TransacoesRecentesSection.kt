package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TransacaoModel
import com.meudinheiro.funcoes.DateUtils
import com.meudinheiro.funcoes.formatarMoedaBR
import kotlin.Int

// Cores do Tema Blu Macaw
private val CardBg = Color(0xFF1B263B)
private val NeonGreen = Color(0xFF69F0AE)
private val NeonRed = Color(0xFFFF8A80)

@Composable
fun TransacoesRecentesSection(
    transacoes: List<TransacaoModel>,
    isPrivate: Boolean
) {
    // Só renderiza a seção INTEIRA se houver transações
    if (transacoes.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // CABEÇALHO (Igual ao seu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Últimas Movimentações",
                    color = Color.White, // Garanta que TextWhite esteja definido
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ver tudo",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { /* Abrir extrato */ }
                )
            }

            // LISTA
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    transacoes.forEachIndexed { index, transacao ->
                        TransacaoItem(transacao = transacao, isPrivate = isPrivate)
                        if (index < transacoes.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                color = Color.White.copy(alpha = 0.05f)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // OPCIONAL: Mostrar um placeholder "Nenhuma movimentação este mês"
        // Isso evita que a tela pareça quebrada
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhuma movimentação encontrada.", color = Color.White.copy(0.3f), fontSize = 14.sp)
        }
    }
}
@Composable
private fun TransacaoItem(transacao: TransacaoModel, isPrivate: Boolean) {
    val isDespesa = transacao.valor < 0
    val corValor = if (isDespesa) NeonRed else NeonGreen
    val sinal = if (isDespesa) "" else "+"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ÍCONE DA CATEGORIA COM GLOW
        Box(
            modifier = Modifier
                .size(45.dp)
                .clip(CircleShape)
                .background(transacao.categoriaCor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = transacao.getIcon(),
                contentDescription = null,
                tint = transacao.categoriaCor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // TEXTOS (NOME E BANCO)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transacao.descricao,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1
            )
            Text(
                text = transacao.bancoNome,
                color = TextWhite.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        // VALOR
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isPrivate) "R$ •••" else "$sinal${
                    formatarMoedaBR(
                        transacao.valor,
                        false
                    )
                }",
                color = corValor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Text(
                text = transacao.dataHora,
                color = TextWhite.copy(alpha = 0.3f),
                fontSize = 11.sp
            )
        }
    }
}