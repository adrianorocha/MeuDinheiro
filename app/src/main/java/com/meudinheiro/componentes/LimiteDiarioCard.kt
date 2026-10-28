package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.domain.ProjecaoFinanceira
import com.meudinheiro.domain.ProjecaoFinanceira.StatusLimite
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch

/** R51 — card compacto "Quanto posso gastar hoje" da tela inicial. */
@Composable
fun LimiteDiarioCard(
    repository: MainRepository,
    userPrefs: UserPreferences,
    isPrivate: Boolean,
    onAbrirCalendario: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoes by repository.cartoesFlow().collectAsState(initial = emptyList())
    val ateReceita by userPrefs.limiteDiarioAteReceitaFlow.collectAsState(initial = false)
    var fixas by remember { mutableStateOf<List<DespesaFixa>>(emptyList()) }
    LaunchedEffect(despesas) { fixas = repository.obterTodasRecorrencias() }

    // Sem nenhuma conta ainda, não há o que projetar.
    if (contas.isEmpty()) return

    val r = remember(contas, cartoes, despesas, fixas, ateReceita) {
        ProjecaoFinanceira.limiteDiarioDosDados(
            contas = contas.map { ProjecaoFinanceira.ContaRef(it.id, it.conta, it.banco) },
            cartoes = cartoes, despesas = despesas, fixas = fixas,
            agora = System.currentTimeMillis(), ateProximaReceita = ateReceita
        )
    }
    val (rotulo, cor) = when {
        r.mesNoVermelho -> "Mês no vermelho" to NeonRed
        r.status == StatusLimite.VERMELHO -> "Acima do limite" to NeonRed
        r.status == StatusLimite.AMARELO -> "Perto do limite" to NeonOrange
        else -> "Dentro do limite" to NeonGreen
    }
    fun moeda(c: Long) = if (isPrivate) "••••••" else ProjecaoFinanceira.brlCentavos(c)
    val mensagem = if (isPrivate) rotulo else r.mensagem

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = "Quanto posso gastar hoje: $rotulo. $mensagem" },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2B3E).copy(alpha = 0.60f),
        border = BorderStroke(1.dp, cor.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Quanto posso gastar hoje", color = Color.White.copy(0.7f), fontSize = 12.sp)
                Text(rotulo, color = cor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(moeda(r.restanteHojeCentavos), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "  restante de hoje · limite ${moeda(r.limiteDiarioCentavos)}",
                    color = Color.White.copy(0.55f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(mensagem, color = Color.White.copy(0.85f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (ateReceita) "Período: até a próxima receita ▸ (toque p/ alternar)" else "Período: fim do mês ▸ (toque p/ alternar)",
                    color = Color.White.copy(0.5f), fontSize = 11.sp,
                    modifier = Modifier.clickable { scope.launch { userPrefs.saveLimiteDiarioAteReceita(!ateReceita) } }
                )
                Text("Calendário ▸", color = NeonCyan, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onAbrirCalendario))
            }
        }
    }
}
