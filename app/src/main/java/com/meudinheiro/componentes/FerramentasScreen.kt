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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
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

private enum class Tela { HUB, RELATORIOS, PLANEJAMENTO, LIXEIRA, BACKUPS, CONFERENCIA, CALENDARIO, RESUMO, REGRAS, PROJETOS, DIVIDAS, PESSOAS, IMPOSTO, SUGESTOES,
    // R98 — índices econômicos e planejamento
    INDICES, RENDIMENTO, AVISTA_PARCELADO, DIVIDAS_SIMULADOR, FLUXO_CAIXA, REAJUSTE,
    // R100 — câmbio (PTAX do Banco Central)
    CAMBIO }

private val TextoClaro = Color(0xFFE0E1DD)
private val CardFundo = Color(0xFF1B263B)

/** Central de ferramentas: busca global (R24), relatórios, planejamento, lixeira e backups automáticos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FerramentasScreen(repository: MainRepository, userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    var tela by remember { mutableStateOf(Tela.HUB) }
    BackHandler { if (tela == Tela.HUB) onBack() else tela = Tela.HUB }
    // R64: o toque na notificação de uma sugestão abre direto a fila.
    androidx.compose.runtime.LaunchedEffect(com.meudinheiro.AtalhosCaptura.sugestoesPedidas) {
        if (com.meudinheiro.AtalhosCaptura.sugestoesPedidas) {
            tela = Tela.SUGESTOES
            com.meudinheiro.AtalhosCaptura.consumirSugestoes()
        }
    }

    when (tela) {
        Tela.RELATORIOS -> RelatoriosScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.PLANEJAMENTO -> PlanejamentoScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.RESUMO -> ResumoInteligenteScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.LIXEIRA -> LixeiraScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.BACKUPS -> BackupsAutomaticosScreen(repository, userPrefs) { tela = Tela.HUB }
        Tela.CONFERENCIA -> ConferenciaSaldosScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.CALENDARIO -> CalendarioFinanceiroScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.REGRAS -> RegrasCategoriaScreen(repository) { tela = Tela.HUB }
        Tela.PROJETOS -> ProjetosScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.DIVIDAS -> DividasScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.PESSOAS -> PessoasScreen(repository, isPrivate) { tela = Tela.HUB }
        Tela.IMPOSTO -> ImpostoRendaScreen(repository, userPrefs, isPrivate) { tela = Tela.HUB }
        Tela.SUGESTOES -> SugestoesLancamentoScreen(isPrivate, repository) { tela = Tela.HUB }
        Tela.INDICES -> IndicesEconomicosScreen(userPrefs, isPrivate) { tela = Tela.HUB }
        Tela.RENDIMENTO -> RendimentoScreen(userPrefs, isPrivate) { tela = Tela.HUB }
        Tela.AVISTA_PARCELADO -> AvistaParceladoScreen(userPrefs, isPrivate) { tela = Tela.HUB }
        Tela.DIVIDAS_SIMULADOR -> DividasSimuladorScreen(repository, userPrefs, isPrivate) { tela = Tela.HUB }
        Tela.FLUXO_CAIXA -> FluxoCaixaScreen(repository, userPrefs, isPrivate) { tela = Tela.HUB }
        Tela.REAJUSTE -> ReajusteScreen(userPrefs, isPrivate) { tela = Tela.HUB }
        Tela.CAMBIO -> CambioScreen(userPrefs, isPrivate) { tela = Tela.HUB }
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
                item { SecaoHub("ÍNDICES E PLANEJAMENTO") }
                item { Atalho(Icons.Default.TrendingUp, "Índices e taxas", "Selic, CDI, TR, poupança, IPCA, IGP-M, INPC, salário mínimo… atualizados pelo Banco Central ao abrir a tela (a cada 6 h) e editáveis, com anual equivalente e link para o histórico") { abrir(Tela.INDICES) } }
                item { Atalho(Icons.Default.CurrencyExchange, "Câmbio", "Dólar, euro e mais 8 moedas pela PTAX do Banco Central, com conversor R$ ↔ moeda e gráfico dos últimos fechamentos") { abrir(Tela.CAMBIO) } }
                item { Atalho(Icons.Default.Insights, "Onde o dinheiro rende mais?", "Simule poupança x produto atrelado ao CDI (com IR) x dinheiro parado, com ganho real descontada a inflação") { abrir(Tela.RENDIMENTO) } }
                item { Atalho(Icons.Default.CompareArrows, "À vista x parcelado", "Taxa de juros embutida, total pago e se compensa pagar à vista frente ao rendimento do dinheiro") { abrir(Tela.AVISTA_PARCELADO) } }
                item { Atalho(Icons.Default.MoneyOff, "Dívidas: custo e quitação", "Custo do rotativo e do cheque especial; simule avalanche x bola de neve com valor extra mensal") { abrir(Tela.DIVIDAS_SIMULADOR) } }
                item { Atalho(Icons.Default.Timeline, "Fluxo de caixa e economia", "Projeção de 6 a 12 meses, meses no vermelho, quanto cortar por mês e sugestões de economia explicadas") { abrir(Tela.FLUXO_CAIXA) } }
                item { Atalho(Icons.Default.Calculate, "Reajuste de aluguel e mensalidades", "Novo valor por IGP-M, IPCA ou INPC e comparação entre os índices") { abrir(Tela.REAJUSTE) } }
                item { SecaoHub("FERRAMENTAS") }
                item { Atalho(Icons.Default.Assessment, "Relatórios", "Filtre por período, cartão, categoria… e exporte PDF, PNG ou CSV") { abrir(Tela.RELATORIOS) } }
                item { Atalho(Icons.Default.CalendarMonth, "Calendário financeiro", "Vencimentos, recebimentos previstos e saldo projetado dia a dia") { abrir(Tela.CALENDARIO) } }
                item { Atalho(Icons.Default.Insights, "Resumo inteligente", "O que mudou no mês ou na semana: totais, categorias em alta, gasto fora do padrão e projeção") { abrir(Tela.RESUMO) } }
                item { Atalho(Icons.Default.Insights, "Planejamento", "Reserva de emergência, 50/30/20 por categoria, assinaturas, metas com prazo e simulador") { abrir(Tela.PLANEJAMENTO) } }
                item { Atalho(Icons.Default.Insights, "Regras de categoria", "Categorize sozinho pela descrição (ex.: contém “uber” → Transporte), com teste e aplicação às existentes") { abrir(Tela.REGRAS) } }
                item { Atalho(Icons.Default.Assessment, "Projetos", "Acompanhe reformas, viagens… pelas tags dos lançamentos, com orçamento") { abrir(Tela.PROJETOS) } }
                item { Atalho(Icons.Default.Assessment, "Dívidas", "Saldo devedor, pagamentos e simulador de quitação (bola de neve x avalanche)") { abrir(Tela.DIVIDAS) } }
                item { Atalho(Icons.Default.Assessment, "Pessoas e rateios", "Quem te deve e quem você deve: despesas divididas, empréstimos e cobrança pronta para enviar") { abrir(Tela.PESSOAS) } }
                item { Atalho(Icons.Default.Assessment, "Imposto de Renda", "Triagem para a declaração: rendimentos, despesas dedutíveis, bens e dívidas do ano (exporta PDF/CSV)") { abrir(Tela.IMPOSTO) } }
                item { Atalho(Icons.Default.DeleteSweep, "Lixeira", if (lixeira.isEmpty()) "Lançamentos excluídos ficam 30 dias" else "${lixeira.size} item(ns) que podem ser restaurados") { abrir(Tela.LIXEIRA) } }
                item { Atalho(Icons.Default.FactCheck, "Conferência de saldos", "Compare saldos e limites gravados com o extrato e recalcule se houver diferença") { abrir(Tela.CONFERENCIA) } }
                item { Atalho(Icons.Default.FactCheck, "Sugestões de lançamento", "Pix, compras e saques detectados nas notificações do banco (opcional, em Configurações): revise e confirme em lote, ou descarte") { abrir(Tela.SUGESTOES) } }
                item { Atalho(Icons.Default.Backup, "Backups e histórico", "Backup diário opcional, restauração segura, exportar tudo (ZIP) e histórico de alterações") { abrir(Tela.BACKUPS) } }
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
private fun SecaoHub(titulo: String) {
    Text(titulo, color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
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
