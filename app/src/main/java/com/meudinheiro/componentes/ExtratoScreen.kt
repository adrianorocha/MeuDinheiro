package com.meudinheiro.componentes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.DespesasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtratoScreen(
    despesasVM: DespesasViewModel,
    categorias: List<String>,
    isPrivate: Boolean,
    onBack: () -> Unit // Este parâmetro será usado no botão
) {
    val despesas by despesasVM.despesasFiltradas.collectAsState()
    val mesAtual by despesasVM.mesSelecionado.collectAsState()
    val anoAtual by despesasVM.anoSelecionado.collectAsState()

    var categoriaFiltro by remember { mutableStateOf<String?>(null) }

    val listaFiltrada = remember(despesas, categoriaFiltro) {
        if (categoriaFiltro == null) despesas
        else despesas.filter { it.categoria == categoriaFiltro }
    }

    // Cálculo do saldo do período filtrado
    val totalMes = listaFiltrada.sumOf {
        when (it.tipo.name) {
            "RECEITA", "CREDITO" ->  it.valor  // Soma se for dinheiro entrando
            "DEBITO" -> -it.valor // Subtrai se for gasto (independente da forma)
            else -> -it.valor // Por segurança, trata qualquer outro tipo como saída
        }
    }

    Scaffold(
        containerColor = PremiumDarkBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Extrato Detalhado", color = TextWhite, fontWeight = FontWeight.Bold) },
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

            // Card de Resumo do Filtro
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val labelTexto = if (categoriaFiltro == null) "Saldo Total do Mês" else "Total em $categoriaFiltro"
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
                            onRemover = { /* Ação de remover */ },
                            onTogglePago = { /* Ação de pagar */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(0.05f),
    labelColor = TextWhite.copy(0.7f),
    selectedContainerColor = Color(0xFF69F0AE).copy(0.2f),
    selectedLabelColor = Color(0xFF69F0AE)
)@Composable
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
