package com.meudinheiro.componentes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.AgendadorNotifDespesas
import kotlinx.coroutines.launch

// --- CORES PREMIUM LOCAIS ---
private val CardBg = Color(0xFF1E2B3E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Configuracao(
    userPrefs: UserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val enabled by userPrefs.notifEnabledFlow.collectAsState(initial = false)
    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val hour by userPrefs.notifHourFlow.collectAsState(initial = 9)
    val minute by userPrefs.notifMinuteFlow.collectAsState(initial = 0)
    val onlyCredit by userPrefs.notifOnlyCreditFlow.collectAsState(initial = false)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && enabled) {
            AgendadorNotifDespesas.scheduleDaily(context, hour, minute)
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

    // Estilo personalizado do Switch para o tema escuro
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
                    title = { Text("Avisos e Parâmetros", color = TextWhite, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    windowInsets = WindowInsets(0, 25, 0, 0)
                )
            },
            bottomBar = {
                // Área de Botões Inferiores
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Voltar") }

                    Button(
                        onClick = {
                            if (!hasNotificationPermission()) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@Button
                            }
                            AgendadorNotifDespesas.runNow(context)
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextWhite,
                            contentColor = PremiumDarkBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Testar Notificação") }
                }
            },
            contentWindowInsets = WindowInsets(0, 40, 0, 0)
        ) { padding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // CARD 1: Ativação Principal
                item {
                    PremiumConfigCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notificações de Vencimento", style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Receba alertas diários sobre contas a pagar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { v ->
                                    scope.launch { userPrefs.saveNotifEnabled(v) }
                                    if (v) {
                                        if (!hasNotificationPermission()) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            AgendadorNotifDespesas.scheduleDaily(context, hour, minute)
                                        }
                                    } else {
                                        AgendadorNotifDespesas.cancel(context)
                                    }
                                },
                                colors = premiumSwitchColors
                            )
                        }

                        if (enabled && !hasNotificationPermission()) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)), // Vermelho claro para alerta
                                border = BorderStroke(1.dp, Color(0xFFFF8A80).copy(alpha = 0.5f))
                            ) { Text("Conceder Permissão") }
                        }
                    }
                }

                // CARD 2: Parâmetros
                item {
                    PremiumConfigCard {
                        Text("Configurações", style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(16.dp))

                        // Stepper Dias
                        PremiumStepperRow(
                            title = "Antecedência (dias)",
                            value = daysAhead,
                            min = 1, max = 30,
                            onMinus = { scope.launch { userPrefs.saveNotifDaysAhead((daysAhead - 1).coerceAtLeast(1)) } },
                            onPlus = { scope.launch { userPrefs.saveNotifDaysAhead((daysAhead + 1).coerceAtMost(30)) } }
                        )

                        Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Divider(color = TextWhite.copy(alpha = 0.1f))
                        Spacer(Modifier.height(16.dp))

                        // Time Picker Customizado
                        PremiumTimeRow(
                            hour = hour,
                            minute = minute,
                            onHourChange = { h ->
                                scope.launch { userPrefs.saveNotifHour(h) }
                                if (enabled && hasNotificationPermission()) AgendadorNotifDespesas.scheduleDaily(context, h, minute)
                            },
                            onMinuteChange = { m ->
                                scope.launch { userPrefs.saveNotifMinute(m) }
                                if (enabled && hasNotificationPermission()) AgendadorNotifDespesas.scheduleDaily(context, hour, m)
                            }
                        )

                        Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Divider(color = TextWhite.copy(alpha = 0.1f))
                        Spacer(Modifier.height(16.dp))

                        // Switch Somente Crédito
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Apenas Cartão de Crédito", style = MaterialTheme.typography.bodyMedium, color = TextWhite)
                                Text(
                                    "Ignorar despesas de débito nos avisos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = onlyCredit,
                                onCheckedChange = { v -> scope.launch { userPrefs.saveNotifOnlyCredit(v) } },
                                colors = premiumSwitchColors
                            )
                        }
                    }
                }

                // Dica Rodapé
                item {
                    Text(
                        "Dica: O alerta verifica contas entre hoje e a antecedência definida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                item { Spacer(Modifier.height(60.dp)) } // Espaço extra para o bottomBar
            }
        }
    }
}

// --- COMPONENTES AUXILIARES VISUAIS ---

@Composable
private fun PremiumConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), // Borda sutil
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextWhite)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(text = "-", onClick = onMinus, enabled = value > min)

            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                Text("$value", style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.Bold)
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
    Column {
        Text("Horário do Alerta", style = MaterialTheme.typography.bodyMedium, color = TextWhite)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Controles de Hora
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(text = "-", onClick = { onHourChange((hour + 23) % 24) })
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                    Text(String.format("%02d", hour), style = MaterialTheme.typography.headlineSmall, color = TextWhite)
                }
                StepperButton(text = "+", onClick = { onHourChange((hour + 1) % 24) })
            }

            Text(" : ", style = MaterialTheme.typography.headlineSmall, color = TextWhite.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 8.dp))

            // Controles de Minuto
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(text = "-", onClick = { onMinuteChange((minute + 55) % 60) })
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                    Text(String.format("%02d", minute), style = MaterialTheme.typography.headlineSmall, color = TextWhite)
                }
                StepperButton(text = "+", onClick = { onMinuteChange((minute + 5) % 60) }) // Pula de 5 em 5 para ser mais útil
            }
        }
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextWhite,
            disabledContentColor = TextWhite.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, if(enabled) TextWhite.copy(alpha = 0.3f) else TextWhite.copy(alpha = 0.1f))
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Light)
    }
}