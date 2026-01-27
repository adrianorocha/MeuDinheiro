package com.meudinheiro.componentes

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import android.content.pm.PackageManager
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.DespesasDevidas
import com.meudinheiro.notif.AgendadorNotifDespesas
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
        // se o usuário conceder e estiver habilitado, agenda
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
                title = { Text("Avisos e parâmetros") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        ) {
                            Text("Permitir notificações")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Parâmetros", style = MaterialTheme.typography.titleMedium)

                    StepperRow(
                        title = "Avisar com antecedência (dias)",
                        value = daysAhead,
                        min = 1,
                        max = 30,
                        onChange = { v ->
                            scope.launch { userPrefs.saveNotifDaysAhead(v) }
                        }
                    )

                    TimeRow(
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Somente despesas no Crédito", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Filtra para avisar apenas itens do tipo Crédito.",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        ExpenseNotif.show(
                            context = context,
                            title = "Teste de aviso",
                            text = "Seus avisos estão funcionando.",
                            bigText = "Este é um teste manual. Se você tiver despesas com data futura, o aviso diário também aparecerá."
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Testar") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Dica: as despesas “a vencer” são as que têm data entre agora e a janela configurada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Text("$value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedButton(
            onClick = { onChange((value - 1).coerceAtLeast(min)) },
            enabled = value > min
        ) { Text("-") }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = { onChange((value + 1).coerceAtMost(max)) },
            enabled = value < max
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Horário do aviso", style = MaterialTheme.typography.bodyLarge)
            Text(
                String.format("%02d:%02d", hour, minute),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(onClick = { onHourChange((hour + 23) % 24) }) { Text("Hora -") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onHourChange((hour + 1) % 24) }) { Text("Hora +") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onMinuteChange(((minute + 55) % 60)) }) { Text("Min -") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onMinuteChange((minute + 5) % 60) }) { Text("Min +") }
    }


}