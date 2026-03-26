package com.meudinheiro

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.meudinheiro.componentes.CadastroUsuarioScreen
import com.meudinheiro.componentes.Configuracao
import com.meudinheiro.componentes.LoginScreen
import com.meudinheiro.componentes.MainScreen
import com.meudinheiro.componentes.PendenciasScreen
import com.meudinheiro.componentes.SplashScreen
import com.meudinheiro.funcoes.NotificacaoVIPHelper
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.AgendadorNotifDespesas
import com.meudinheiro.notif.BackupReminderWorker
import com.meudinheiro.notif.DespesasDevidas
import com.meudinheiro.worker.TransferenciaWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.componentes.BotaGlassmorphic
import com.meudinheiro.componentes.PremiumDialogCard

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificacaoVIPHelper.criarCanalDeNotificacao(this)

        enableEdgeToEdge()
        val tarefaPeriodica = PeriodicWorkRequestBuilder<TransferenciaWorker>(12, TimeUnit.HOURS)
            .addTag("check_agendamentos_diario")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CheckTransferencias",
            ExistingPeriodicWorkPolicy.KEEP, // Mantém a que já existe se já estiver agendada
            tarefaPeriodica
        )
        criarCanalNotificacao(this)
        agendarLembreteBackupSemanal(this)
        // Substituímos o WorkManager antigo pelo nosso novo Agendador Exato
        ativarNotificacoesDiarias()

        setContent {
            ShowApp()
        }
    }
    override fun onResume() {
        super.onResume()
        // 🚀 Captura a ação vinda do Widget
        if (intent?.action == "ACTION_QUICK_ADD") {
            // Altere para a lógica de navegação do seu app (Ex: abrir Dialog ou navegar para tela)
            //exibirFormularioDespesa = true
            // Limpa a action para não reabrir ao girar a tela
            intent.action = null
        }
    }

    private fun ativarNotificacoesDiarias() {
        // Lança uma coroutine atrelada ao ciclo de vida da Activity
        lifecycleScope.launch(Dispatchers.IO) {
            val userPrefs = UserPreferences(applicationContext)

            // Lê o horário salvo. Se não existir, usa 09:00 como padrão
            val hora = userPrefs.notifHourFlow.firstOrNull() ?: 9
            val minuto = userPrefs.notifMinuteFlow.firstOrNull() ?: 0

            // 1. Arma o despertador para o horário configurado para amanhã e os próximos dias
            AgendadorNotifDespesas.scheduleDaily(applicationContext, hora, minuto)

            // 2. CHAMA IMEDIATAMENTE AGORA! (Assim que o app abre)
            DespesasDevidas.verificarEExibir(applicationContext)
        }
    }
}

private enum class AppStage { Splash, Cadastro, Login, Home, Avisos, Pendencias }

@Composable
fun ShowApp() {
    var stage by rememberSaveable { mutableStateOf(AppStage.Splash) }

    val context = LocalContext.current
    val userPrefs = remember { UserPreferences(context) }

    var nextAfterSplash by remember { mutableStateOf<AppStage?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    val isBiometriaAtiva by userPrefs.biometriaEnabledFlow.collectAsState(initial = true)

    var hasUser by remember { mutableStateOf<Boolean?>(null) }

    // Carrega do DataStore uma vez (valor real), sem depender de "initial = """
    LaunchedEffect(Unit) {
        val u = userPrefs.userNameFlow.firstOrNull().orEmpty().trim()
        val p = userPrefs.userPassFlow.firstOrNull().orEmpty()
        hasUser = u.isNotBlank() && p.isNotBlank()
        nextAfterSplash =
            if (u.isNotBlank() && p.isNotBlank()) AppStage.Login else AppStage.Cadastro
    }

    // Intercepta o botão voltar/gesture
    BackHandler(enabled = stage != AppStage.Splash) {
        when (stage) {
            AppStage.Home -> showExitDialog = true
            AppStage.Avisos -> stage = AppStage.Home
            AppStage.Login -> stage = AppStage.Cadastro
            AppStage.Cadastro -> showExitDialog = true
            else -> { /* Splash não intercepta */
            }
        }
    }

    if (showExitDialog) {
        Dialog(onDismissRequest = { showExitDialog = false }) {
            PremiumDialogCard {
                // 🚀 ÍCONE DE "POWER" (Aviso visual de encerramento)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFFF4B4B).copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew, // Ícone de desligar
                            contentDescription = "Desligar",
                            tint = Color(0xFFFF4B4B),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 🚀 TEXTOS MAIS PROFISSIONAIS E IMERSIVOS
                Text(
                    text = "Encerrar Aplicativo",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tem certeza que deseja sair do Meu Dinheiro?",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                // 🚀 BOTÕES GLASSMORPHIC ALINHADOS COM O DESIGN
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BotaGlassmorphic(
                        texto = "Cancelar",
                        corAcento = Color.White.copy(alpha = 0.6f),
                        animateIdleJump = false, // Parado, sem pulo
                        modifier = Modifier.weight(1f),
                        onClick = { showExitDialog = false }
                    )

                    BotaGlassmorphic(
                        texto = "Sair",
                        corAcento = Color(0xFFFF4B4B), // Vermelho Alerta
                        animateIdleJump = false, // Parado, sem pulo
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showExitDialog = false
                            val act = context as? Activity
                            if (act != null) {
                                // Funcionalidade de saída mantida e segura
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    act.finishAndRemoveTask()
                                } else {
                                    act.finishAffinity()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    when (stage) {
        AppStage.Splash -> {
            SplashScreen(
                onTimeout = {
                    if (hasUser == true) {
                        // VERIFICA SE O USUÁRIO QUER BIOMETRIA
                        if (isBiometriaAtiva) {
                            com.meudinheiro.componentes.solicitarBiometria(
                                context = context,
                                onSuccess = { stage = AppStage.Home },
                                onFallback = { stage = AppStage.Login }
                            )
                        } else {
                            // Se a biometria estiver desligada na config, vai direto pra senha
                            stage = AppStage.Login
                        }
                    } else if (hasUser == false) {
                        stage = AppStage.Cadastro
                    }
                }
            )
        }

        AppStage.Cadastro -> {
            CadastroUsuarioScreen(
                userPrefs = userPrefs,
                onBack = {
                    stage = AppStage.Home
                },
                onFinished = {
                    stage = AppStage.Home
                }
            )
        }

        AppStage.Login -> {
            LoginScreen(
                userPrefs = userPrefs,
                onLoginSuccess = {
                    stage = AppStage.Home
                }
            )
        }

        AppStage.Home -> {
            MainScreen(
                userPrefs,
                onOpenAvisos = {
                    stage = AppStage.Avisos
                },
                onOpenPendencias = {
                    stage = AppStage.Pendencias
                }
            )
        }

        AppStage.Avisos -> {
            Configuracao(
                userPrefs = userPrefs,
                onBack = { stage = AppStage.Home }
            )
        }

        AppStage.Pendencias -> {
            PendenciasScreen(
                userPrefs = userPrefs,
                onBack = { stage = AppStage.Home }
            )
        }
    }
}

fun agendarLembreteBackupSemanal(context: Context) {
    val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(
        7, TimeUnit.DAYS // Executa a cada 7 dias
    ).setConstraints(
        Constraints.Builder()
            .setRequiresBatteryNotLow(true) // Só avisa se tiver bateria
            .build()
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "LembreteBackupSemanal",
        ExistingPeriodicWorkPolicy.KEEP, // Mantém o agendamento existente
        request
    )
}

// Chame isto no onCreate da MainActivity
private fun criarCanalNotificacao(context: Context) {
    // Canais de notificação só existem do Android 8.0 (Oreo) em diante
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Lembretes de Backup"
        val descriptionText = "Notificações para manter seus dados seguros"
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val channel = NotificationChannel("backup_channel", name, importance).apply {
            description = descriptionText
        }

        // Obtendo o Manager do SISTEMA (não o Compat)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }
}
