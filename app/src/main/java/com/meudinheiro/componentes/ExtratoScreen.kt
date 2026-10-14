package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.DespesasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtratoScreen(
    despesasVM: DespesasViewModel,
    categorias: List<String>,
    isPrivate: Boolean,
    onBack: () -> Unit, // Este parâmetro será usado no botão
    /** Com o ViewModel de contas, cada item ganha o menu de ações (editar, excluir, duplicar, repetir, antecipar…). */
    contaViewModel: ContaSaldoViewModel? = null,
    getPicCategoria: (String) -> String = { "" }
) {
    var alvo by remember { mutableStateOf<AlvoLancamento?>(null) }
    val despesas by despesasVM.despesasFiltradas.collectAsState()
    val mesAtual by despesasVM.mesSelecionado.collectAsState()
    val anoAtual by despesasVM.anoSelecionado.collectAsState()

    var categoriaFiltro by remember { mutableStateOf<String?>(null) }

    // R67: conta compartilhada — "por <nome>" e filtro "Quem lançou" / "Minhas × Conjunta" só quando há membros.
    val storage = (androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.meudinheiro.MyApplication)?.storageManager
    val papelAtual by remember(storage) { storage?.papel ?: kotlinx.coroutines.flow.MutableStateFlow(com.meudinheiro.domain.Papel.DONO) }.collectAsState()
    // R67: LEITOR só consulta (sem menu de ações); as regras do servidor barram de qualquer forma.
    val contaViewModel = contaViewModel.takeIf { com.meudinheiro.domain.Permissoes.podeExecutar(papelAtual, com.meudinheiro.domain.Acao.LANCAMENTO_EDITAR) }
    val temMembros by remember(storage) { storage?.temMembros ?: kotlinx.coroutines.flow.MutableStateFlow(false) }.collectAsState()
    val meuUid = storage?.auth?.let { runCatching { it.uidAtual }.getOrNull() }
    var quem by remember { mutableStateOf<com.meudinheiro.domain.QuemLancou.Selecao>(com.meudinheiro.domain.QuemLancou.Selecao.Todos) }
    val outrosAutores = remember(despesas, meuUid) {
        com.meudinheiro.domain.QuemLancou.autoresDistintos(despesas, meuUid, { it.autorUid }, { it.autor })
    }

    // R56: filtro por tag (chave normalizada); sem tag selecionada, a lista é exatamente a de antes.
    var tagFiltro by remember { mutableStateOf<String?>(null) }
    val tagsDoMes = remember(despesas) { com.meudinheiro.domain.Tags.usadasDeListas(despesas.map { it.tags }) }
    val listaFiltrada = remember(despesas, categoriaFiltro, tagFiltro, quem, temMembros) {
        despesas
            .let { l -> if (temMembros) com.meudinheiro.domain.QuemLancou.filtrar(l, quem, meuUid) { it.autorUid } else l }
            .let { l -> if (categoriaFiltro == null) l else l.filter { it.categoria == categoriaFiltro } }
            .let { l -> tagFiltro?.let { t -> l.filter { com.meudinheiro.domain.Tags.tem(it.tags, t) } } ?: l }
    }

    // Cálculo do saldo do período filtrado
    val totalMes = listaFiltrada.sumOf {
        when (it.tipo.name) {
            "RECEITA", "CREDITO" -> it.valor  // Soma se for dinheiro entrando
            "DEBITO" -> -it.valor // Subtrai se for gasto (independente da forma)
            else -> -it.valor // Por segurança, trata qualquer outro tipo como saída
        }
    }

    Scaffold(
        containerColor = PremiumDarkBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Extrato Detalhado",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                // --- ADICIONADO: ÍCONE DE VOLTAR ---
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            // Seletor de Mês (Agora público)
            MonthSelector(
                mesAtual = mesAtual,
                anoAtual = anoAtual,
                onPrevious = { despesasVM.mesAnterior() },
                onNext = { despesasVM.proximoMes() }
            )

            // Chips de Filtro
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = categoriaFiltro == null,
                        onClick = { categoriaFiltro = null },
                        label = { Text("Tudo") },
                        colors = chipColors()
                    )
                }
                items(categorias) { cat ->
                    FilterChip(
                        selected = categoriaFiltro == cat,
                        onClick = { categoriaFiltro = if (categoriaFiltro == cat) null else cat },
                        label = { Text(cat) },
                        colors = chipColors()
                    )
                }
            }

            if (temMembros) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = quem == com.meudinheiro.domain.QuemLancou.Selecao.Todos,
                            onClick = { quem = com.meudinheiro.domain.QuemLancou.Selecao.Todos },
                            label = { Text("Conjunta") }, colors = chipColors()
                        )
                    }
                    item {
                        FilterChip(
                            selected = quem == com.meudinheiro.domain.QuemLancou.Selecao.Eu,
                            onClick = { quem = com.meudinheiro.domain.QuemLancou.Selecao.Eu },
                            label = { Text("Minhas despesas") }, colors = chipColors()
                        )
                    }
                    items(outrosAutores) { a ->
                        FilterChip(
                            selected = (quem as? com.meudinheiro.domain.QuemLancou.Selecao.Membro)?.uid == a.uid,
                            onClick = { quem = com.meudinheiro.domain.QuemLancou.Selecao.Membro(a.uid) },
                            label = { Text(a.nome) }, colors = chipColors()
                        )
                    }
                }
            }

            if (tagsDoMes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tagsDoMes) { t ->
                        FilterChip(
                            selected = tagFiltro == t.chave,
                            onClick = { tagFiltro = if (tagFiltro == t.chave) null else t.chave },
                            label = { Text("#${t.rotulo}") },
                            colors = chipColors()
                        )
                    }
                }
            }

            // Card de Resumo do Filtro
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val labelTexto =
                            if (categoriaFiltro == null && tagFiltro == null) "Saldo Total do Mês"
                            else "Total em " + listOfNotNull(categoriaFiltro, tagFiltro?.let { "#$it" }).joinToString(" · ")
                        Text(labelTexto, fontSize = 12.sp, color = TextWhite.copy(0.6f))
                        Text(
                            text = formatarMoedaBR(totalMes, isPrivate),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = if (totalMes >= 0) Color(0xFF69F0AE) else Color(0xFFEF5350)
                        )
                    }
                }
            }

            // Lista de Itens
            if (listaFiltrada.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma transação encontrada.", color = TextWhite.copy(0.4f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(listaFiltrada, key = { it.id }) { item ->
                        DespesasItem(
                            item = item,
                            isPrivate = isPrivate,
                            onRemover = { contaViewModel?.removerDespesa(it) },
                            onTogglePago = contaViewModel?.let { vm -> { d -> vm.alternarStatusDespesa(d) } },
                            onAcoes = contaViewModel?.let { { d -> alvo = AlvoLancamento(d.id.toLong()) } },
                            onPedirExclusao = contaViewModel?.let { { d -> alvo = AlvoLancamento(d.id.toLong(), excluirDireto = true) } },
                            rotuloAutor = com.meudinheiro.domain.QuemLancou.rotuloAutor(item.autor, temMembros)
                        )
                    }
                }
            }
        }
    }

    if (contaViewModel != null) {
        LancamentoAcoesHost(
            alvo = alvo, onFechar = { alvo = null }, viewModel = contaViewModel,
            categorias = categorias, getPicCategoria = getPicCategoria
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(0.05f),
    labelColor = TextWhite.copy(0.7f),
    selectedContainerColor = Color(0xFF69F0AE).copy(0.2f),
    selectedLabelColor = Color(0xFF69F0AE)
)

@Composable
fun MonthSelector(
    mesAtual: Int,
    anoAtual: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val meses = remember {
        listOf(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, "Anterior", tint = TextWhite)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = meses.getOrElse(mesAtual) { "" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
            Text(
                text = "$anoAtual",
                style = MaterialTheme.typography.labelSmall,
                color = TextWhite.copy(alpha = 0.6f)
            )
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, "Próximo", tint = TextWhite)
        }
    }
}
