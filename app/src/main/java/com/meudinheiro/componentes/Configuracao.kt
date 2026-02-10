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
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Cores locais mantendo o padrão Premium
private val CardBg = Color(0xFF1E2B3E)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Configuracao(
    userPrefs: UserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- ESTADOS DE CONTROLE ---
    var isExporting by remember { mutableStateOf(false) }
    var exibirGerenciadorCategorias by remember { mutableStateOf(false) }

    // --- VIEWMODELS & REPOSITORY ---
    val mainRepository = remember { MainRepository(context) }
    val categoriaVm: CategoriaViewModel = viewModel(factory = CategoriaViewModelFactory(mainRepository))
    val despesasVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(mainRepository))

    // --- DADOS ---
    val enabled by userPrefs.notifEnabledFlow.collectAsState(initial = false)
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val hour by userPrefs.notifHourFlow.collectAsState(initial = 9)
    val minute by userPrefs.notifMinuteFlow.collectAsState(initial = 0)
    val onlyCredit by userPrefs.notifOnlyCreditFlow.collectAsState(initial = false)

    val listaDespesas by despesasVM.despesasFiltradas.collectAsState()
    val mesIndex by despesasVM.mesSelecionado.collectAsState()
    val anoAtual by despesasVM.anoSelecionado.collectAsState()

    val nomesMeses = remember { listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro") }

    // --- PERMISSÕES ---
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // --- LAYOUT PRINCIPAL ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(PremiumDarkBlue, PremiumLightBlue)))
    ) {
        // CAMADA 1: CONTEÚDO DA TELA
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Configurar Alertas", color = TextWhite, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextWhite) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PremiumDarkBlue.copy(alpha = 0.95f))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (!hasNotificationPermission()) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    AgendadorNotifDespesas.runNow(context)
                                    Toast.makeText(context, "Notificação enviada!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PremiumDarkBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Testar Notificação Agora", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Voltar") }
                }
            }
        ) { padding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), // Reduzi espaçamento entre cards
                contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)
            ) {
                // CARD 1: Ativação
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
                                    color = if (enabled) Color(0xFF69F0AE) else TextWhite.copy(0.6f)
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        userPrefs.saveNotifEnabled(isChecked)
                                        if (isChecked) {
                                            if (!hasNotificationPermission()) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            else AgendadorNotifDespesas.scheduleDaily(context, hour, minute)
                                        } else AgendadorNotifDespesas.cancel(context)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PremiumDarkBlue,
                                    checkedTrackColor = TextWhite,
                                    uncheckedThumbColor = TextWhite.copy(0.8f),
                                    uncheckedTrackColor = Color.Transparent,
                                    uncheckedBorderColor = TextWhite.copy(0.4f)
                                )
                            )
                        }
                    }
                }

                // CARD 2: Configurações (Compactado)
                if (enabled) {
                    item {
                        PremiumConfigCard {
                            Text("Personalização", style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(16.dp))

                            // Dias de Antecedência
                            PremiumStepperRow(
                                title = "Avisar antes (dias)",
                                value = daysAhead,
                                min = 1, max = 30,
                                onMinus = { scope.launch { userPrefs.saveNotifDaysAhead(daysAhead - 1) } },
                                onPlus = { scope.launch { userPrefs.saveNotifDaysAhead(daysAhead + 1) } }
                            )

                            Spacer(Modifier.height(12.dp))
                            Divider(color = TextWhite.copy(0.1f))
                            Spacer(Modifier.height(12.dp))

                            // Horário (Agora Horizontal e Compacto)
                            PremiumTimeRowCompact(
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
                                    Text("Filtro Inteligente", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Ignorar Débitos, avisar apenas Cartão de Crédito.", style = MaterialTheme.typography.labelSmall, color = TextWhite.copy(0.6f))
                                }
                                Switch(
                                    checked = onlyCredit,
                                    onCheckedChange = { v -> scope.launch { userPrefs.saveNotifOnlyCredit(v) } },
                                    colors = SwitchDefaults.colors(checkedThumbColor = PremiumDarkBlue, checkedTrackColor = TextWhite)
                                )
                            }
                        }
                    }
                }

                // CARD 3: Ferramentas
                item {
                    PremiumConfigCard {
                        Text("Ferramentas de Dados", style = MaterialTheme.typography.titleMedium, color = TextWhite)
                        Spacer(Modifier.height(16.dp))

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

                        Button(
                            onClick = {
                                if (listaDespesas.isNotEmpty()) {
                                    scope.launch {
                                        try {
                                            isExporting = true
                                            delay(500)
                                            withContext(Dispatchers.IO) {
                                                val nomeMes = nomesMeses.getOrElse(mesIndex) { "Mes" }
                                                mainRepository.exportarExtratoPDF(context, nomeMes, anoAtual, listaDespesas)
                                            }
                                            delay(1000)
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) { Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                                        } finally { isExporting = false }
                                    }
                                } else { Toast.makeText(context, "Sem dados.", Toast.LENGTH_SHORT).show() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
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
                        color = TextWhite.copy(0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )
                }
            }
        }

        // DIALOGS E LOADERS
        if (exibirGerenciadorCategorias) {
            GerenciarCategoriasDialog(viewModel = categoriaVm, onDismiss = { exibirGerenciadorCategorias = false })
        }

        if (isExporting) {
            Dialog(onDismissRequest = { }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, TextWhite.copy(0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                        Spacer(Modifier.height(16.dp))
                        Text("Gerando extrato...", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- COMPONENTES VISUAIS OTIMIZADOS ---

@Composable
private fun PremiumConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), // Radius levemente menor para economizar espaço visual
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp), // Padding reduzido de 20 para 16
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

            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
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

// --- NOVO SELETOR DE TEMPO COMPACTO (HORIZONTAL) ---
@Composable
private fun PremiumTimeRowCompact(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Título à esquerda, alinhado com o padrão
        Text(
            "Horário do Alerta",
            style = MaterialTheme.typography.bodyMedium,
            color = TextWhite
        )

        // Controles à direita, alinhados horizontalmente
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Horas: [-] 09 [+]
            TimePickerControlHorizontal(value = hour, range = 24, onChange = onHourChange)

            Text(
                " : ",
                style = MaterialTheme.typography.headlineSmall,
                color = TextWhite.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-2).dp)
            )

            // Minutos: [-] 00 [+]
            TimePickerControlHorizontal(value = minute, range = 60, step = 5, onChange = onMinuteChange)
        }
    }
}

// Componente Horizontal: [-] 00 [+]
@Composable
private fun TimePickerControlHorizontal(
    value: Int,
    range: Int,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(text = "-", onClick = { onChange((value - step + range) % range) })

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier.size(width = 40.dp, height = 32.dp), // Tamanho reduzido
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", value),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        StepperButton(text = "+", onClick = { onChange((value + step) % range) })
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp), // Tamanho reduzido de 40dp para 32dp
        shape = RoundedCornerShape(8.dp),
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
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
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
        // Box para centralizar e dar margem
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 500.dp), // Altura dinâmica
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    // Cabeçalho
                    Text(
                        "Minhas Categorias",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Adicione ou remova categorias personalizadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite.copy(0.6f)
                    )

                    Spacer(Modifier.height(20.dp))

                    // Input de Nova Categoria
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
                                cursorColor = TextWhite
                            )
                        )
                        Spacer(Modifier.width(8.dp))

                        // Botão Adicionar
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
                        modifier = Modifier.weight(1f, fill = false),
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

                                IconButton(
                                    onClick = { viewModel.excluirCategoria(cat) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Botão Concluído
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextWhite,
                            contentColor = PremiumDarkBlue
                        )
                    ) {
                        Text("Concluído", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}