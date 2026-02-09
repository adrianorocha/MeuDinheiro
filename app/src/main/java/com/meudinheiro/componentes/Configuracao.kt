package com.meudinheiro.componentes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.AgendadorNotifDespesas
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.CategoriaViewModel
import com.meudinheiro.viewModel.CategoriaViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory
import kotlinx.coroutines.launch

// Cores locais mantendo o padrão Premium
private val CardBg = Color(0xFF1E2B3E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Configuracao(
    userPrefs: UserPreferences,
    onBack: () -> Unit
) {
    var isExporting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estados
    val enabled by userPrefs.notifEnabledFlow.collectAsState(initial = false)
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val hour by userPrefs.notifHourFlow.collectAsState(initial = 9)
    val minute by userPrefs.notifMinuteFlow.collectAsState(initial = 0)
    val onlyCredit by userPrefs.notifOnlyCreditFlow.collectAsState(initial = false)
    val mainRepository = remember { MainRepository(context) }
    val categoriaVm: CategoriaViewModel =
        viewModel(factory = CategoriaViewModelFactory(mainRepository))
    val despesasVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(mainRepository))
    var exibirGerenciadorCategorias by remember { mutableStateOf(false) }

    val listaDespesas by despesasVM.despesasFiltradas.collectAsState()
    val mesIndex by despesasVM.mesSelecionado.collectAsState()
    val anoAtual by despesasVM.anoSelecionado.collectAsState()

    val nomesMeses = remember { listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro") }

    // Launcher de Permissão
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Permissão concedida!", Toast.LENGTH_SHORT).show()
            if (enabled) AgendadorNotifDespesas.scheduleDaily(context, hour, minute)
        } else {
            Toast.makeText(context, "Permissão necessária para alertas.", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // Cores do Switch
    val premiumSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = PremiumDarkBlue,
        checkedTrackColor = TextWhite,
        uncheckedThumbColor = TextWhite.copy(alpha = 0.8f),
        uncheckedTrackColor = Color.Transparent,
        uncheckedBorderColor = TextWhite.copy(alpha = 0.4f)
    )

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
                    title = {
                        Text(
                            "Configurar Alertas",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Voltar", tint = TextWhite)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    windowInsets = WindowInsets(0, 0, 0, 0) // Controlado manualmente se precisar
                )
            },
            bottomBar = {
                // --- REFATORADO: BOTÕES EMPILHADOS PARA MELHOR RESPONSIVIDADE ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PremiumDarkBlue.copy(alpha = 0.95f)) // Fundo para contraste no fim da lista
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botão Principal: Testar
                    Button(
                        onClick = {
                            scope.launch {
                                if (!hasNotificationPermission()) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    AgendadorNotifDespesas.runNow(context)
                                    Toast.makeText(
                                        context,
                                        "Notificação enviada!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp), // Altura maior para toque fácil
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextWhite,
                            contentColor = PremiumDarkBlue
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Testar Notificação Agora", fontWeight = FontWeight.Bold)
                    }

                    // Botão Secundário: Voltar
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Voltar")
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->


            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)
            ) {
                // 1. CARD DE ATIVAÇÃO
                item {
                    PremiumConfigCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Notificações Diárias",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (enabled) "Ativado" else "Desativado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (enabled) Color(0xFF69F0AE) else TextWhite.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        userPrefs.saveNotifEnabled(isChecked)
                                        if (isChecked) {
                                            if (!hasNotificationPermission()) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                AgendadorNotifDespesas.scheduleDaily(
                                                    context,
                                                    hour,
                                                    minute
                                                )
                                            }
                                        } else {
                                            AgendadorNotifDespesas.cancel(context)
                                        }
                                    }
                                },
                                colors = premiumSwitchColors
                            )
                        }
                    }
                }

                // 2. CARD DE CONFIGURAÇÕES (Só mostra se ativado)
                if (enabled) {
                    item {
                        PremiumConfigCard {
                            Text(
                                "Personalização",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(20.dp))

                            // Dias de Antecedência
                            PremiumStepperRow(
                                title = "Avisar antes (dias)",
                                value = daysAhead,
                                min = 1, max = 30,
                                onMinus = { scope.launch { userPrefs.saveNotifDaysAhead(daysAhead - 1) } },
                                onPlus = { scope.launch { userPrefs.saveNotifDaysAhead(daysAhead + 1) } }
                            )

                            Spacer(Modifier.height(20.dp))
                            Divider(color = TextWhite.copy(alpha = 0.1f))
                            Spacer(Modifier.height(20.dp))

                            // Horário
                            PremiumTimeRow(
                                hour = hour,
                                minute = minute,
                                onHourChange = { h ->
                                    scope.launch {
                                        userPrefs.saveNotifHour(h)
                                        AgendadorNotifDespesas.scheduleDaily(context, h, minute)
                                    }
                                },
                                onMinuteChange = { m ->
                                    scope.launch {
                                        userPrefs.saveNotifMinute(m)
                                        AgendadorNotifDespesas.scheduleDaily(context, hour, m)
                                    }
                                }
                            )
                        }
                    }

                    item {
                        PremiumConfigCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Filtro Inteligente",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextWhite
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Ignorar Débitos, avisar apenas Cartão de Crédito.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextWhite.copy(alpha = 0.6f),
                                        lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = onlyCredit,
                                    onCheckedChange = { v ->
                                        scope.launch {
                                            userPrefs.saveNotifOnlyCredit(
                                                v
                                            )
                                        }
                                    },
                                    colors = premiumSwitchColors
                                )
                            }
                        }
                    }
                }

                item {
                    PremiumConfigCard {
                        Text(
                            "Ferramentas de Dados",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite
                        )
                        Spacer(Modifier.height(16.dp))

                        // Botão Categorias
                        OutlinedButton(
                            onClick = { exibirGerenciadorCategorias = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, TextWhite.copy(0.2f))
                        ) {
                            Icon(Icons.Default.Category, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Personalizar Categorias")
                        }

                        Spacer(Modifier.height(12.dp))

                        // Botão Exportar
                        Button(
                            onClick = {
                                // Verifica se há dados para exportar
                                if (listaDespesas.isNotEmpty()) {
                                    val nomeMes = nomesMeses.getOrElse(mesIndex) { "Mes_Atual" }

                                    // Chama a função do Repositório (que criamos anteriormente)
                                    // Certifique-se de que a função 'exportarExtratoPDF' está no MainRepository
                                    mainRepository.exportarExtratoPDF(
                                        context = context,
                                        mes = nomeMes,
                                        ano = anoAtual,
                                        despesas = listaDespesas
                                    )
                                } else {
                                    Toast.makeText(context, "Não há despesas para exportar neste mês.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp) // Mantendo padrão visual
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Exportar PDF de ${nomesMeses.getOrElse(mesIndex) { "" }}")
                        }
                    }
                }

                item {
                    Text(
                        "O sistema verificará contas a vencer entre hoje e os próximos $daysAhead dias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }
            }
        }
        if (exibirGerenciadorCategorias) {
            GerenciarCategoriasDialog(
                viewModel = categoriaVm, // A variável que inicializamos no passo anterior
                onDismiss = { exibirGerenciadorCategorias = false }
            )
        }

    }
}

// --- COMPONENTES VISUAIS ---

@Composable
private fun PremiumConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun PremiumStepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextWhite)

        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(text = "-", onClick = onMinus, enabled = value > min)

            Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            StepperButton(text = "+", onClick = onPlus, enabled = value < max)
        }
    }
}

@Composable
private fun PremiumTimeRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Horário do Alerta",
            style = MaterialTheme.typography.bodyMedium,
            color = TextWhite.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Horas
            TimePickerControl(value = hour, range = 24, onChange = onHourChange)

            Text(
                " : ",
                style = MaterialTheme.typography.headlineMedium,
                color = TextWhite.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .offset(y = (-4).dp)
            )

            // Minutos (Pulo de 5 em 5)
            TimePickerControl(value = minute, range = 60, step = 5, onChange = onMinuteChange)
        }
    }
}

@Composable
private fun TimePickerControl(
    value: Int,
    range: Int,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepperButton(text = "+", onClick = { onChange((value + step) % range) })

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier.size(width = 50.dp, height = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", value),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        StepperButton(text = "-", onClick = { onChange((value - step + range) % range) })
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp), // Aumentei um pouco para facilitar o toque
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextWhite,
            disabledContentColor = TextWhite.copy(alpha = 0.2f),
            containerColor = if (enabled) Color.White.copy(alpha = 0.05f) else Color.Transparent
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) TextWhite.copy(alpha = 0.3f) else TextWhite.copy(alpha = 0.1f)
        )
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun GerenciarCategoriasDialog(
    viewModel: CategoriaViewModel,
    onDismiss: () -> Unit
) {
    var novoNome by remember { mutableStateOf("") }
    val categorias by viewModel.categorias.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), // Padding de segurança das bordas da tela
            contentAlignment = Alignment.Center // Centraliza o conteúdo (o Card)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth() // Ocupa a largura disponível dentro do Box (respeitando o padding de 16dp)
                    .heightIn(min = 200.dp, max = 500.dp), // Define limites de altura para o diálogo
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp) // Padding interno do conteúdo do Card
                        .fillMaxWidth()
                ) {
                    // Cabeçalho
                    Text("Minhas Categorias", style = MaterialTheme.typography.headlineSmall, color = TextWhite, fontWeight = FontWeight.Bold)
                    Text("Adicione ou remova categorias personalizadas.", style = MaterialTheme.typography.bodySmall, color = TextWhite.copy(0.6f))

                    Spacer(Modifier.height(20.dp))

                    // Input
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = novoNome,
                            onValueChange = { novoNome = it },
                            label = { Text("Nova Categoria") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TextWhite,
                                unfocusedBorderColor = TextWhite.copy(0.3f),
                                focusedLabelColor = TextWhite,
                                unfocusedLabelColor = TextWhite.copy(0.6f),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = TextWhite // Adicionado para garantir visibilidade do cursor
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (novoNome.isNotBlank()) {
                                    viewModel.adicionarCategoria(novoNome.trim(), "ic_default")
                                    novoNome = ""
                                }
                            },
                            modifier = Modifier.background(Color(0xFF4CAF50), CircleShape)
                        ) {
                            Icon(Icons.Default.Add, null, tint = TextWhite)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Lista de Categorias
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false), // Scrollável mas não expande infinitamente
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categorias) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.title, color = TextWhite, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { viewModel.excluirCategoria(cat) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Botão Concluído
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PremiumDarkBlue)
                    ) {
                        Text("Concluído", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}