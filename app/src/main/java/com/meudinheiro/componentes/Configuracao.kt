package com.meudinheiro.componentes

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import com.meudinheiro.notif.DespesasDevidas
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.CategoriaViewModel
import com.meudinheiro.viewModel.CategoriaViewModelFactory
import com.meudinheiro.viewModel.DespesasViewModel
import com.meudinheiro.viewModel.DespesasViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- CORES BLU MACAW ---
private val NeonCyan = Color(0xFF00E5FF)
private val DeepSpaceBlue = Color(0xFF131E29)
private val CardGlass = Color(0xFF1B263B).copy(alpha = 0.8f)
private val NeonRed = Color(0xFFFF5252)
private val SuccessGreen = Color(0xFF69F0AE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Configuracao(
    userPrefs: UserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- ESTADOS DE CONTROLE ---
    var processingMessage by remember { mutableStateOf<String?>(null) }
    var exibirGerenciadorCategorias by remember { mutableStateOf(false) }

    // --- VIEWMODELS & REPOSITORY ---
    val mainRepository = remember { MainRepository(context) }
    val categoriaVm: CategoriaViewModel = viewModel(factory = CategoriaViewModelFactory(mainRepository))
    val despesasVM: DespesasViewModel = viewModel(factory = DespesasViewModelFactory(mainRepository))

    // --- DADOS (Flows) ---
    val enabled by userPrefs.notifEnabledFlow.collectAsState(initial = false)
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val hour by userPrefs.notifHourFlow.collectAsState(initial = 9)
    val minute by userPrefs.notifMinuteFlow.collectAsState(initial = 0)
    val onlyCredit by userPrefs.notifOnlyCreditFlow.collectAsState(initial = false)
    val biometriaEnabled by userPrefs.biometriaEnabledFlow.collectAsState(initial = true)

    val listaDespesas by despesasVM.despesasFiltradas.collectAsState()
    val mesIndex by despesasVM.mesSelecionado.collectAsState()
    val anoAtual by despesasVM.anoSelecionado.collectAsState()

    var exibirAlertaExclusao by remember { mutableStateOf(false) }

    val nomesMeses = remember {
        listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    }

    // --- LAUNCHERS (Backup e Restore) ---
    val createBackupLauncher = rememberLauncherForActivityResult<String, Uri?>(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { safeUri ->
            scope.launch {
                processingMessage = "Gerando Cofre de Backup..."
                try {
                    withContext(Dispatchers.IO) {
                        val jsonBackup = mainRepository.gerarBackup()
                        if (jsonBackup.isBlank()) throw Exception("O cofre gerado está vazio.")
                        context.contentResolver.openOutputStream(safeUri)?.use { outputStream ->
                            outputStream.write(jsonBackup.toByteArray(Charsets.UTF_8))
                        } ?: throw Exception("Não foi possível fechar o cofre.")
                    }
                    Toast.makeText(context, "✅ Backup salvo com sucesso no padrão Blu Macaw!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("BackupError", "Erro ao salvar", e)
                    Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    processingMessage = null
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { safeUri ->
            scope.launch {
                processingMessage = "Descriptografando Cofre..."
                try {
                    val jsonLimpo = withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(safeUri) ?: throw Exception("Arquivo inacessível.")
                        val conteudoCru = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        if (conteudoCru.isBlank()) throw Exception("O cofre selecionado está vazio.")
                        if (conteudoCru.startsWith("\uFEFF")) conteudoCru.substring(1).trim() else conteudoCru.trim()
                    }
                    processingMessage = "Restaurando base de dados..."
                    mainRepository.restaurarBackupCompleto(jsonLimpo)
                    Toast.makeText(context, "✅ Dados restaurados com sucesso!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("RestoreError", "Erro na restauração", e)
                    Toast.makeText(context, "Falha na leitura: Arquivo incompatível.", Toast.LENGTH_LONG).show()
                } finally {
                    processingMessage = null
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Permissão VIP concedida!", Toast.LENGTH_SHORT).show()
            if (enabled) AgendadorNotifDespesas.scheduleDaily(context, hour, minute)
        } else {
            Toast.makeText(context, "O motor financeiro precisa de permissão para alertar.", Toast.LENGTH_LONG).show()
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
            .background(DeepSpaceBlue) // Fundo Sólido Blu Macaw
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Configurações", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, DeepSpaceBlue, DeepSpaceBlue)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (!hasNotificationPermission()) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else DespesasDevidas.verificarEExibir(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("TESTAR ALERTA VIP AGORA", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp) // Espaço extra para o BottomBar
            ) {
                // SEÇÃO: ACESSO
                item { SectionTitle("SEGURANÇA E ACESSO") }
                item {
                    PremiumConfigCard {
                        ConfiguracaoSwitchItem(
                            icone = Icons.Default.Fingerprint,
                            titulo = "Desbloqueio Biométrico",
                            descricao = if (biometriaEnabled) "Ativo" else "Apenas senha mestra",
                            checked = biometriaEnabled,
                            onCheckedChange = { isChecked -> scope.launch { userPrefs.saveBiometriaEnabled(isChecked) } }
                        )
                    }
                }

                // SEÇÃO: MOTOR DE ALERTAS
                item { SectionTitle("MOTOR DE ALERTAS VIP") }
                item {
                    PremiumConfigCard {
                        ConfiguracaoSwitchItem(
                            icone = Icons.Default.Notifications,
                            titulo = "Ronda Financeira Diária",
                            descricao = if (enabled) "Motor Ligado" else "Motor Desligado",
                            checked = enabled,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    userPrefs.saveNotifEnabled(isChecked)
                                    if (isChecked) {
                                        if (!hasNotificationPermission()) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        else AgendadorNotifDespesas.scheduleDaily(context, hour, minute)
                                    } else AgendadorNotifDespesas.cancel(context)
                                }
                            }
                        )

                        if (enabled) {
                            HorizontalDivider(color = TextWhite.copy(0.05f), modifier = Modifier.padding(vertical = 12.dp))

                            PremiumStepperRow(
                                title = "Antecedência (Dias)",
                                value = daysAhead,
                                min = 1, max = 30,
                                onMinus = { scope.launch { userPrefs.saveNotifDaysAhead(daysAhead - 1) } },
                                onPlus = { scope.launch { userPrefs.saveNotifDaysAhead(daysAhead + 1) } }
                            )

                            HorizontalDivider(color = TextWhite.copy(0.05f), modifier = Modifier.padding(vertical = 12.dp))

                            PremiumTimeRowCompact(
                                hour = hour, minute = minute,
                                onHourChange = { h -> scope.launch { userPrefs.saveNotifHour(h); AgendadorNotifDespesas.scheduleDaily(context, h, minute) } },
                                onMinuteChange = { m -> scope.launch { userPrefs.saveNotifMinute(m); AgendadorNotifDespesas.scheduleDaily(context, hour, m) } }
                            )

                            HorizontalDivider(color = TextWhite.copy(0.05f), modifier = Modifier.padding(vertical = 12.dp))

                            ConfiguracaoSwitchItem(
                                icone = Icons.Default.FilterAlt,
                                titulo = "Filtro Rígido",
                                descricao = "Avisar apenas Faturas de Cartão",
                                checked = onlyCredit,
                                onCheckedChange = { v -> scope.launch { userPrefs.saveNotifOnlyCredit(v) } },
                                padding = PaddingValues(0.dp) // Sem padding extra para ficar alinhado no card expandido
                            )
                        }
                    }
                }

                // SEÇÃO: DADOS E CATEGORIAS
                item { SectionTitle("GESTÃO DE DADOS") }
                item {
                    PremiumConfigCard {
                        ConfiguracaoAcaoItem(
                            icone = Icons.Default.Category,
                            titulo = "Minhas Categorias",
                            onClick = { exibirGerenciadorCategorias = true }
                        )

                        HorizontalDivider(color = TextWhite.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp))

                        ConfiguracaoAcaoItem(
                            icone = Icons.Default.PictureAsPdf,
                            titulo = "Exportar Relatório (${nomesMeses.getOrElse(mesIndex) { "" }})",
                            iconTint = SuccessGreen,
                            onClick = {
                                if (listaDespesas.isNotEmpty()) {
                                    scope.launch {
                                        try {
                                            processingMessage = "Compilando Relatório VIP..."
                                            delay(500)
                                            withContext(Dispatchers.IO) {
                                                mainRepository.exportarExtratoPDF(context, nomesMeses.getOrElse(mesIndex) { "Mes" }, anoAtual, listaDespesas)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            processingMessage = null
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "O cofre não tem lançamentos neste mês.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        HorizontalDivider(color = TextWhite.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { createBackupLauncher.launch("Cofre_BluMacaw_${System.currentTimeMillis()}.json") },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                border = BorderStroke(1.dp, TextWhite.copy(0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Salvar", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { restoreBackupLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                border = BorderStroke(1.dp, TextWhite.copy(0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restaurar", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // SEÇÃO: DESTRUIÇÃO
                item { SectionTitle("ZONA DE RISCO", NeonRed) }
                item {
                    PremiumConfigCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, null, tint = NeonRed)
                                Spacer(Modifier.width(12.dp))
                                Text("Protocolo de Destruição", style = MaterialTheme.typography.titleMedium, color = NeonRed, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Apaga irreversivelmente todas as finanças, agendamentos e categorias deste dispositivo.", style = MaterialTheme.typography.bodySmall, color = TextWhite.copy(0.5f))
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { exibirAlertaExclusao = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                border = BorderStroke(1.dp, NeonRed.copy(0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("FORMATAR COFRE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- DIALOGS ---
        if (exibirGerenciadorCategorias) {
            GerenciarCategoriasDialog(viewModel = categoriaVm, onDismiss = { exibirGerenciadorCategorias = false })
        }
        if (exibirAlertaExclusao) {
            DialogDestruicaoTotal(
                onCancel = { exibirAlertaExclusao = false },
                onConfirm = {
                    exibirAlertaExclusao = false
                    scope.launch {
                        processingMessage = "Incineração iniciada..."
                        try {
                            withContext(Dispatchers.IO) { mainRepository.limparBancoDeDadosCompleto() }
                            delay(1000)
                            Toast.makeText(context, "O cofre foi resetado. Voltando ao início...", Toast.LENGTH_LONG).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Falha na destruição: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            processingMessage = null
                        }
                    }
                }
            )
        }
        if (processingMessage != null) {
            DialogProcessamentoPremium(processingMessage!!)
        }
    }
}

// --- SUB-COMPONENTES VISUAIS BLU MACAW ---

@Composable
private fun SectionTitle(text: String, color: Color = TextWhite.copy(0.5f)) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
}

@Composable
private fun PremiumConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardGlass)
            .border(1.dp, TextWhite.copy(0.05f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun ConfiguracaoSwitchItem(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    descricao: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    padding: PaddingValues = PaddingValues(16.dp)
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(padding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(DeepSpaceBlue.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = null, tint = if (checked) NeonCyan else TextWhite.copy(0.5f), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(descricao, color = TextWhite.copy(0.5f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepSpaceBlue,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = TextWhite.copy(0.5f),
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = TextWhite.copy(0.3f)
            )
        )
    }
}

@Composable
private fun ConfiguracaoAcaoItem(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    iconTint: Color = NeonCyan,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(DeepSpaceBlue.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(titulo, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextWhite.copy(0.3f))
    }
}

@Composable
private fun PremiumStepperRow(title: String, value: Int, min: Int, max: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = TextWhite, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(text = "-", onClick = onMinus, enabled = value > min)
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                Text("$value", color = TextWhite, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            StepperButton(text = "+", onClick = onPlus, enabled = value < max)
        }
    }
}

@Composable
private fun PremiumTimeRowCompact(hour: Int, minute: Int, onHourChange: (Int) -> Unit, onMinuteChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Horário de Disparo", color = TextWhite, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimePickerControlHorizontal(value = hour, range = 24, onChange = onHourChange)
            Text(":", color = TextWhite.copy(0.5f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
            TimePickerControlHorizontal(value = minute, range = 60, step = 5, onChange = onMinuteChange)
        }
    }
}

@Composable
private fun TimePickerControlHorizontal(value: Int, range: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(text = "▼", onClick = { onChange((value - step + range) % range) })
        Box(
            modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).background(DeepSpaceBlue.copy(0.5f)).size(width = 44.dp, height = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = String.format("%02d", value), color = NeonCyan, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        StepperButton(text = "▲", onClick = { onChange((value + step) % range) })
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (enabled) TextWhite.copy(0.1f) else Color.Transparent).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (enabled) TextWhite else TextWhite.copy(0.2f), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun DialogDestruicaoTotal(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardGlass).border(1.dp, NeonRed.copy(0.3f), RoundedCornerShape(24.dp)).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(72.dp).background(NeonRed.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = NeonRed, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Autorizar Destruição?", fontSize = 22.sp, color = TextWhite, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text("Esta ação é irreversível. O banco de dados do Blu Macaw será aniquilado.", color = TextWhite.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onCancel, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = DeepSpaceBlue), shape = RoundedCornerShape(12.dp)) { Text("CANCELAR", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = onConfirm, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed), border = BorderStroke(1.dp, NeonRed.copy(0.5f)), shape = RoundedCornerShape(12.dp)) { Text("ANIMAQUILAR", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun DialogProcessamentoPremium(mensagem: String) {
    Dialog(onDismissRequest = { }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardGlass).border(1.dp, NeonCyan.copy(0.3f), RoundedCornerShape(20.dp)).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NeonCyan)
                Spacer(Modifier.height(24.dp))
                Text(mensagem, color = TextWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 16.sp)
            }
        }
    }
}

// (O GerenciarCategoriasDialog pode ficar exatamente o mesmo, ou aplique o mesmo estilo 'CardGlass' ao seu 'Card' interno)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GerenciarCategoriasDialog(
    viewModel: CategoriaViewModel,
    onDismiss: () -> Unit
) {
    var novoNome by remember { mutableStateOf("") }
    val categorias by viewModel.categorias.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardGlass).border(1.dp, TextWhite.copy(0.1f), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text("Minhas Categorias", fontSize = 20.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                    Text("Personalize seu cofre", fontSize = 14.sp, color = TextWhite.copy(0.5f))

                    Spacer(Modifier.height(24.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = novoNome, onValueChange = { novoNome = it },
                            label = { Text("Nome da Categoria", color = TextWhite.copy(0.5f)) },
                            singleLine = true, modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan, unfocusedBorderColor = TextWhite.copy(0.2f),
                                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, cursorColor = NeonCyan
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(NeonCyan).clickable {
                                if (novoNome.isNotBlank()) { viewModel.adicionarCategoria(novoNome.trim(), "ic_default"); novoNome = "" }
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Add, null, tint = DeepSpaceBlue, modifier = Modifier.size(28.dp)) }
                    }

                    Spacer(Modifier.height(20.dp))

                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categorias) { cat ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TextWhite.copy(0.05f)).padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.title, color = TextWhite, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { viewModel.excluirCategoria(cat) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.8f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = DeepSpaceBlue), shape = RoundedCornerShape(14.dp)) { Text("CONCLUÍDO", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}