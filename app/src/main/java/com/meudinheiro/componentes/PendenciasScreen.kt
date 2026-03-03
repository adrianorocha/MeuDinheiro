package com.meudinheiro.componentes

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.Despesa
import com.meudinheiro.funcoes.DateUtils
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Cores Premium
private val WarningColor = Color(0xFFFFAB40)
private val DangerColor = Color(0xFFEF5350)
private val SuccessColor = Color(0xFF00C853)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendenciasScreen(
    userPrefs: UserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application

    val scope = rememberCoroutineScope()
    val repository = remember { MainRepository(context) }
    val contaVM: ContaSaldoViewModel = viewModel(factory = ContaSaldoViewModelFactory(application, repository))

    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val onlyCredit by userPrefs.notifOnlyCreditFlow.collectAsState(initial = false)

    var listaPendencias by remember { mutableStateOf<List<Despesa>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun carregarDados() {
        isLoading = true
        listaPendencias = withContext(Dispatchers.IO) {
            repository.listarPendencias(daysAhead, onlyCredit)
        }
        isLoading = false
    }

    LaunchedEffect(daysAhead, onlyCredit) {
        carregarDados()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PremiumDarkBlue, PremiumLightBlue)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Pendências", color = TextWhite, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    windowInsets = WindowInsets(0, 25, 0, 0)
                )
            },
            // ADIÇÃO 1: Barra inferior fixa com botão VOLTAR grande
            bottomBar = {
                Surface(
                    color = PremiumDarkBlue.copy(alpha = 0.9f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars) // Respeita botões do Android
                    ) {
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextWhite,
                                contentColor = PremiumDarkBlue
                            )
                        ) {
                            Text("Voltar para Início", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TextWhite)
                }
            } else if (listaPendencias.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.meudinheiro.R.drawable.sim_chip),
                            contentDescription = null,
                            tint = TextWhite.copy(0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tudo em dia!",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite.copy(0.7f)
                        )
                        Text(
                            "Nenhuma conta vencendo nos próximos $daysAhead dias.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite.copy(0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Text(
                            "Contas a Pagar",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextWhite.copy(0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(listaPendencias, key = { it.id }) { item ->
                        PendenciaItem(
                            item = item,
                            onBaixar = {
                                scope.launch {
                                    repository.atualizarStatusPago(item.id.toLong(), true)
                                    repository.recalcularSaldoTotal(item.conta)
                                    contaVM.carregarResumoFinanceiro()
                                    carregarDados()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendenciaItem(
    item: Despesa,
    onBaixar: () -> Unit
) {
    val hoje = remember { java.util.Date() }
    val isAtrasada = item.data.before(hoje)

    val corBorda = if (isAtrasada) DangerColor else WarningColor
    val textoStatus = if (isAtrasada) "ATRASADO" else "VENCE EM BREVE"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B3E).copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, corBorda.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(corBorda.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = corBorda,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descricao,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 1 // Limita nome para não quebrar layout
                )
                Text(
                    text = "${DateUtils.formatarData(item.data)} • ${item.conta}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = textoStatus,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = corBorda,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = formatarMoedaBR(item.valor, false),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )

                Spacer(Modifier.height(8.dp))

                // ADIÇÃO 2: Botão "Dar Baixa" MAIOR e mais VISÍVEL
                Surface(
                    color = SuccessColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SuccessColor.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onBaixar() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), // Padding maior
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = SuccessColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Dar Baixa",
                            fontSize = 13.sp, // Fonte maior
                            fontWeight = FontWeight.Bold,
                            color = SuccessColor
                        )
                    }
                }
            }
        }
    }
}