package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

// Cores Premium
private val CardBg = Color(0xFF1E2B3E)
private val ExpenseRed = Color(0xFFEF5350)
private val IncomeGreen = Color(0xFF69F0AE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendenciasScreen(
    userPrefs: UserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { MainRepository(context) }

    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)

    // 1. Obtemos a lista de contas para poder descobrir o nome do banco
    val listaContas by repo.obterContaSaldo().collectAsState(initial = emptyList())

    var items by remember { mutableStateOf<List<Despesa>>(emptyList()) }

    LaunchedEffect(daysAhead) {
        items = withContext(Dispatchers.IO) {
            repo.listarPendencias(daysAhead, onlyCredit = false)
        }
    }

    val nf = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val total = items.sumOf { it.valor }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumDarkBlue, PremiumLightBlue)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Pendências Próximas", color = TextWhite) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextWhite
                    ),
                    windowInsets = WindowInsets(0, 25, 0, 0)
                )
            },
            contentWindowInsets = WindowInsets(0, 40, 0, 0)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header com Resumo
                PremiumSummaryCard(count = items.size, total = nf.format(total))

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                ) { Text("Voltar para Home") }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = TextWhite.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Nenhuma pendência encontrada.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextWhite.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Tudo em dia para os próximos $daysAhead dias.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite.copy(alpha = 0.3f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
                    ) {
                        items(items, key = { it.id }) { d ->
                            // 2. Encontramos o nome do banco comparando o número da conta
                            val nomeBanco = listaContas.find { it.conta == d.conta }?.banco ?: "Banco"

                            // 3. Passamos o nomeBanco para o Card
                            PremiumPendenciaCard(d, nf, df, nomeBanco)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSummaryCard(count: Int, total: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Quantidade", style = MaterialTheme.typography.labelMedium, color = TextWhite.copy(alpha = 0.7f))
                Text("$count pendente(s)", style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Valor Total", style = MaterialTheme.typography.labelMedium, color = TextWhite.copy(alpha = 0.7f))
                Text(total, style = MaterialTheme.typography.titleMedium, color = ExpenseRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PremiumPendenciaCard(
    d: Despesa,
    nf: NumberFormat,
    df: SimpleDateFormat,
    nomeBanco: String // Parâmetro novo
) {
    val valorColor = if (d.tipo == TipoDespesa.CREDITO) IncomeGreen else ExpenseRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Linha 1: Descrição e Valor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = d.descricao,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextWhite,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = nf.format(d.valor),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = valorColor
                )
            }

            // Linha 2: Data de Vencimento
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(valorColor.copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Vence: ${df.format(d.data)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = valorColor
                    )
                }
            }

            // Separador sutil
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Divider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(4.dp))

            // Linha 3: Detalhes (Banco, Conta e Categoria)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Aqui mostramos: "Nubank • 12345-6"
                Text(
                    text = "$nomeBanco • ${d.conta}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite.copy(alpha = 0.6f)
                )
                Text(
                    text = d.categoria,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite.copy(alpha = 0.6f)
                )
            }
        }
    }
}