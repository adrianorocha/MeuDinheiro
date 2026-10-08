package com.meudinheiro.componentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Analises
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import java.text.SimpleDateFormat
import java.util.Locale

private enum class Tela { HUB, RELATORIOS, PLANEJAMENTO, LIXEIRA, BACKUPS, CONFERENCIA }

private val TextoClaro = Color(0xFFE0E1DD)
private val CardFundo = Color(0xFF1B263B)

/** Central de ferramentas: busca global (R24), relatórios, planejamento, lixeira e backups automáticos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FerramentasScreen(repository: MainRepository, userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    var tela by remember { mutableStateOf(Tela.HUB) }
    BackHandler { if (tela == Tela.HUB) onBack() else tela = Tela.HUB }

    when (tela) {
        Tela.RELATORIOS -> RelatoriosScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.PLANEJAMENTO -> PlanejamentoScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.LIXEIRA -> LixeiraScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.BACKUPS -> BackupsAutomaticosScreen(repository, userPrefs) { tela = Tela.HUB }
        Tela.CONFERENCIA -> ConferenciaSaldosScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.HUB -> Hub(repository, isPrivate, onBack) { tela = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Hub(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit, abrir: (Tela) -> Unit) {
    var termo by remember { mutableStateOf("") }
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoes by repository.cartoesFlow().collectAsState(initial = emptyList())
    val metas by repository.getTodasMetas().collectAsState(initial = emptyList())
    val lixeira by repository.lixeiraFlow().collectAsState(initial = emptyList())
    val busca = remember(termo, despesas, contas, cartoes, metas) { Analises.buscar(termo, despesas, contas, cartoes, metas) }
    val bancoPorConta = remember(contas) { contas.associate { it.conta to it.banco } }
    val data = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Ferramentas", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = termo, onValueChange = { termo = it }, singleLine = true,
                    placeholder = { Text("Buscar lançamentos, contas, cartões, metas, valor ou data…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = NeonCyan) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan,
                        focusedPlaceholderColor = Color.White.copy(0.4f), unfocusedPlaceholderColor = Color.White.copy(0.4f)
                    )
                )
            }

            if (termo.isBlank()) {
                item { Atalho(Icons.Default.Assessment, "Relatórios", "Filtre por período, cartão, categoria… e exporte PDF, PNG ou CSV") { abrir(Tela.RELATORIOS) } }
                item { Atalho(Icons.Default.Insights, "Planejamento", "Reserva de emergência, 50/30/20, assinaturas, metas com prazo e simulador") { abrir(Tela.PLANEJAMENTO) } }
                item { Atalho(Icons.Default.DeleteSweep, "Lixeira", if (lixeira.isEmpty()) "Lançamentos excluídos ficam 30 dias" else "${lixeira.size} item(ns) que podem ser restaurados") { abrir(Tela.LIXEIRA) } }
                item { Atalho(Icons.Default.FactCheck, "Conferência de saldos", "Compare saldos e limites gravados com o extrato e recalcule se houver diferença") { abrir(Tela.CONFERENCIA) } }
                item { Atalho(Icons.Default.Backup, "Backups automáticos", "Cópia semanal no aparelho, com restauração") { abrir(Tela.BACKUPS) } }
            } else if (busca.vazio) {
                item { Text("Nada encontrado para \"$termo\".", color = Color.White.copy(0.7f), modifier = Modifier.padding(top = 16.dp)) }
            } else {
                if (busca.contas.isNotEmpty() || busca.cartoes.isNotEmpty() || busca.metas.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        busca.contas.forEach { Text("🏦 ${it.banco} · conta ${it.conta} · ${formatarMoedaBR(it.saldo, isPrivate)}", color = Color.White, fontSize = 13.sp) }
                        busca.cartoes.forEach { Text("💳 ${it.nome} ••${it.finalCartao}" + if (it.cartaoPrincipalId != null) " (virtual)" else "", color = Color.White, fontSize = 13.sp) }
                        busca.metas.forEach { Text("🎯 ${it.nome} · ${formatarMoedaBR(it.valorGuardado, isPrivate)} de ${formatarMoedaBR(it.valorObjetivo, isPrivate)}", color = Color.White, fontSize = 13.sp) }
                    }
                }
                item { Text("${busca.lancamentos.size} lançamento(s)", color = Color.White.copy(0.5f), fontSize = 12.sp) }
                items(busca.lancamentos.size) { i ->
                    val d = busca.lancamentos[i]
                    val entrada = d.tipo == TipoDespesa.CREDITO
                    Row(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.descricao, color = Color.White, fontSize = 14.sp, maxLines = 1)
                            Text("${data.format(d.data)} · ${d.categoria} · ${bancoPorConta[d.conta] ?: d.conta}" + (d.autor?.let { " · $it" } ?: ""), color = Color.White.copy(0.5f), fontSize = 11.sp)
                        }
                        Text((if (entrada) "+ " else "- ") + formatarMoedaBR(d.valor, isPrivate), color = if (entrada) NeonGreen else Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Atalho(icone: ImageVector, titulo: String, descricao: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, null, tint = NeonCyan, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(descricao, color = Color.White.copy(0.6f), fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(0.dp))
}
