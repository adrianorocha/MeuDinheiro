package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.data.Despesa
import com.meudinheiro.domain.Agendados
import com.meudinheiro.domain.ehEntrada
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonRed
import java.text.SimpleDateFormat
import java.util.Locale

private val MESES_AGENDADOS = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

/**
 * Atalho "Lançamentos agendados": despesas/receitas de data futura ainda em aberto (a pagar / a receber), de todas as contas.
 * Não aparece quando não há nenhum. Toque abre a lista completa por mês ([DialogLancamentosAgendados]).
 */
@Composable
fun CardLancamentosAgendados(resumo: Agendados.Resumo, isPrivate: Boolean, onClick: () -> Unit) {
    if (resumo.quantidade == 0) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B).copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Lançamentos agendados", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${resumo.quantidade}", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Spacer(Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("A pagar ${formatarMoedaBR(resumo.aPagar, isPrivate)}", color = NeonRed, fontSize = 13.sp)
                Text("A receber ${formatarMoedaBR(resumo.aReceber, isPrivate)}", color = NeonGreen, fontSize = 13.sp)
            }
            Text("Datas futuras ainda em aberto. Não entram no saldo até serem pagas. Toque para ver todos.", color = Color.White.copy(0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun DialogLancamentosAgendados(
    resumo: Agendados.Resumo,
    isPrivate: Boolean,
    onAbrir: (Despesa) -> Unit,
    onFechar: () -> Unit
) {
    val dia = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    Dialog(onDismissRequest = onFechar) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))) {
            Column(Modifier.padding(16.dp)) {
                Text("Lançamentos agendados", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "A pagar ${formatarMoedaBR(resumo.aPagar, isPrivate)}  ·  A receber ${formatarMoedaBR(resumo.aReceber, isPrivate)}",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                if (resumo.quantidade == 0) {
                    Text("Nenhum lançamento futuro em aberto.", color = Color.White.copy(0.6f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    resumo.meses.forEach { m ->
                        item(key = "m-${m.ano}-${m.mes}") {
                            Text(
                                "${MESES_AGENDADOS[m.mes - 1]} ${m.ano}", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(m.itens, key = { it.id }) { d ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onAbrir(d) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dia.format(d.data), color = Color.White.copy(0.6f), fontSize = 12.sp, modifier = Modifier.width(44.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.descricao, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(d.categoria, color = Color.White.copy(0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    (if (d.ehEntrada) "+ " else "- ") + formatarMoedaBR(d.valor, isPrivate),
                                    color = if (d.ehEntrada) NeonGreen else NeonRed, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onFechar) { Text("Fechar", color = NeonCyan) }
                }
            }
        }
    }
}
