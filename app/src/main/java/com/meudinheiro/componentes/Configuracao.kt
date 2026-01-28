package com.meudinheiro.componentes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.AgendadorNotifDespesas
import com.meudinheiro.notif.ExpenseNotif
import kotlinx.coroutines.launch

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
        topBar = {
            TopAppBar(
                title = { Text("Avisos e parâmetros") },
                windowInsets = WindowInsets(0, 0, 0, 0) // evita “inset duplo” em alguns layouts
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
                    modifier = Modifier.weight(1f)
                ) { Text("Voltar") }

                Button(
                    onClick = {
                        if (!hasNotificationPermission()) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@Button
                        }
                        AgendadorNotifDespesas.runNow(context)
                        /*ExpenseNotif.show(
                            context = context,
                            title = "Teste de aviso",
                            text = "Seus avisos estão funcionando.",
                            bigText = "Se você tiver despesas pendentes com data futura, o aviso diário também aparecerá."
                        )*/
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Testar") }
            }
        },
        // deixa o Scaffold não colocar padding extra automaticamente
        contentWindowInsets = WindowInsets(0, 25, 0, 0)
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)              // respeita topBar/bottomBar
                .padding(horizontal = 16.dp)   // sem padding vertical extra
                .padding(top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Avisar despesas a vencer", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Envia um aviso diário no horário configurado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                }
                            )
                        }

                        if (enabled && !hasNotificationPermission()) {
                            OutlinedButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Permitir notificações") }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Parâmetros", style = MaterialTheme.typography.titleMedium)

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
                                Text("Somente Crédito", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Avisa só despesas do tipo Crédito.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = onlyCredit,
                                onCheckedChange = { v -> scope.launch { userPrefs.saveNotifOnlyCredit(v) } }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Dica: “a vencer” = data entre hoje e a janela configurada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }

            // Espaço mínimo (o bottomBar já garante área segura)
            item { Spacer(Modifier.height(2.dp)) }
        }
    }
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$value",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = { val newVal = (value - 1).coerceAtLeast(min)
                onChange(newVal) },
            enabled = value > min,
            modifier = Modifier.height(36.dp)
        ) { Text("-") }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = { val newVal = (value + 1).coerceAtLeast(max)
                onChange(newVal) },
            enabled = value < max,
            modifier = Modifier.height(36.dp)
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
                Text("Horário", style = MaterialTheme.typography.bodyLarge)
                Text(
                    String.format("%02d:%02d", hour, minute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { onHourChange((hour + 23) % 24) },
                modifier = Modifier.height(36.dp)
            ) { Text("Hora -") }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = { onHourChange((hour + 1) % 24) },
                modifier = Modifier.height(36.dp)
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
                    .height(36.dp)
            ) { Text("Min -") }

            OutlinedButton(
                onClick = { onMinuteChange((minute + 1) % 60) },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) { Text("Min +") }
        }
    }
}
