package com.meudinheiro.componentes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.AgendadorNotifDespesas
import kotlinx.coroutines.launch

// Cores Premium
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

    Scaffold(
        containerColor = Color.Transparent, // Transparente para ver o gradiente
        modifier = Modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumDarkBlue, PremiumLightBlue)
                )
            ),
        topBar = {
            TopAppBar(
                title = { Text("Avisos e parâmetros", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextWhite,
                    actionIconContentColor = TextWhite
                ),
                windowInsets = WindowInsets(0, 25, 0, 0)
            )
        },
        bottomBar = {
            // Botões sempre visíveis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.5f))
                ) { Text("Voltar") }

                Button(
                    onClick = {
                        if (!hasNotificationPermission()) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@Button
                        }
                        AgendadorNotifDespesas.runNow(context)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextWhite,
                        contentColor = PremiumDarkBlue
                    )
                ) { Text("Testar") }
            }
        },
        contentWindowInsets = WindowInsets(0, 40, 0, 0)
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PremiumCard {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Avisar despesas a vencer",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite
                                )
                                Text(
                                    "Envia um aviso diário no horário configurado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite.copy(alpha = 0.7f)
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
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PremiumDarkBlue,
                                    checkedTrackColor = TextWhite,
                                    uncheckedThumbColor = TextWhite,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }

                        if (enabled && !hasNotificationPermission()) {
                            OutlinedButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
                            ) { Text("Permitir notificações") }
                        }
                    }
                }
            }

            item {
                PremiumCard {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Parâmetros",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite
                        )

                        StepperRow(
                            title = "Antecedência (dias)",
                            value = daysAhead,
                            min = 1,
                            max = 30,
                            onChange = { v -> scope.launch { userPrefs.saveNotifDaysAhead(v) } }
                        )

                        TimeRow(
                            hour = hour,
                            minute = minute,
                            onHourChange = { h ->
                                scope.launch { userPrefs.saveNotifHour(h) }
                                if (enabled && hasNotificationPermission()) {
                                    AgendadorNotifDespesas.scheduleDaily(context, h, minute)
                                }
                            },
                            onMinuteChange = { m ->
                                scope.launch { userPrefs.saveNotifMinute(m) }
                                if (enabled && hasNotificationPermission()) {
                                    AgendadorNotifDespesas.scheduleDaily(context, hour, m)
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Somente Crédito",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextWhite
                                )
                                Text(
                                    "Avisa só despesas do tipo Crédito.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = onlyCredit,
                                onCheckedChange = { v -> scope.launch { userPrefs.saveNotifOnlyCredit(v) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PremiumDarkBlue,
                                    checkedTrackColor = TextWhite,
                                    uncheckedThumbColor = TextWhite,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Dica: “a vencer” = data entre hoje e a janela configurada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }

            item { Spacer(Modifier.height(2.dp)) }
        }
    }
}

// Wrapper para Cards Premium
@Composable
private fun PremiumCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        content = { content() }
    )
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextWhite)
            Text(
                "$value",
                style = MaterialTheme.typography.bodySmall,
                color = TextWhite.copy(alpha = 0.7f)
            )
        }

        OutlinedButton(
            onClick = {
                val newVal = (value - 1).coerceAtLeast(min)
                onChange(newVal)
            },
            enabled = value > min,
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextWhite,
                disabledContentColor = TextWhite.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
        ) { Text("-") }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = {
                val newVal = (value + 1).coerceAtLeast(max)
                onChange(newVal)
            },
            enabled = value < max,
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextWhite,
                disabledContentColor = TextWhite.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
        ) { Text("+") }
    }
}

@Composable
private fun TimeRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Horário", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                Text(
                    String.format("%02d:%02d", hour, minute),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite.copy(alpha = 0.7f)
                )
            }

            OutlinedButton(
                onClick = { onHourChange((hour + 23) % 24) },
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
            ) { Text("Hora -") }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = { onHourChange((hour + 1) % 24) },
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
            ) { Text("Hora +") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onMinuteChange((minute + 55) % 60) },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
            ) { Text("Min -") }

            OutlinedButton(
                onClick = { onMinuteChange((minute + 1) % 60) },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.3f))
            ) { Text("Min +") }
        }
    }
}