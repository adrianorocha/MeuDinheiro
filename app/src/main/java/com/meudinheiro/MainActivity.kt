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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.meudinheiro.componentes.SystemBootSplashScreen // 🚀 IMPORT NOVO
import com.meudinheiro.componentes.BotaGlassmorphic
import com.meudinheiro.componentes.PremiumDialogCard
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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Configuração de Canais de Notificação
        NotificacaoVIPHelper.criarCanalDeNotificacao(this)
        criarCanalNotificacao(this)

        // 2. Agendamento de Workers e Lembretes
        val tarefaPeriodica = PeriodicWorkRequestBuilder<TransferenciaWorker>(12, TimeUnit.HOURS)
            .addTag("check_agendamentos_diario")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CheckTransferencias",
            ExistingPeriodicWorkPolicy.KEEP,
            tarefaPeriodica
        )

        agendarLembreteBackupSemanal(this)

        // 3. Ativa o verificador diário (Coroutine atrelada à Activity)
        ativarNotificacoesDiarias()

        // 4. Inicia a Interface Jetpack Compose
        setContent {
            ShowApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Captura a ação vinda do Widget
        if (intent?.action == "ACTION_QUICK_ADD") {
            // Limpa a action para não reabrir ao girar a tela
            intent.action = null
        }
    }

    private fun ativarNotificacoesDiarias() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userPrefs = UserPreferences(applicationContext)

            val hora = userPrefs.notifHourFlow.firstOrNull() ?: 9
            val minuto = userPrefs.notifMinuteFlow.firstOrNull() ?: 0

            AgendadorNotifDespesas.scheduleDaily(applicationContext, hora, minuto)
            DespesasDevidas.verificarEExibir(applicationContext)
        }
    }
}

// 🚀 Adicionei o estado SystemBoot no seu enum
private enum class AppStage { SystemBoot, Splash, Cadastro, Login, Home, Avisos, Pendencias }

@Composable
fun ShowApp() {
    // 🚀 O app agora começa SEMPRE no SystemBoot!
    var stage by rememberSaveable { mutableStateOf(AppStage.SystemBoot) }

    val context = LocalContext.current
    val userPrefs = remember { UserPreferences(context) }

    var showExitDialog by remember { mutableStateOf(false) }
    val isBiometriaAtiva by userPrefs.biometriaEnabledFlow.collectAsState(initial = true)
    var hasUser by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val u = userPrefs.userNameFlow.firstOrNull().orEmpty().trim()
        val p = userPrefs.userPassFlow.firstOrNull().orEmpty()
        hasUser = u.isNotBlank() && p.isNotBlank()
    }

    // Controle de Voltar
    BackHandler(enabled = stage != AppStage.Splash && stage != AppStage.SystemBoot) {
        when (stage) {
            AppStage.Home -> showExitDialog = true
            AppStage.Avisos -> stage = AppStage.Home
            AppStage.Pendencias -> stage = AppStage.Home // Adicionado para fechar pendências
            AppStage.Login -> stage = AppStage.Cadastro
            AppStage.Cadastro -> showExitDialog = true
            else -> {}
        }
    }

    // Dialog de Saída (Mantido intacto)
    if (showExitDialog) {
        Dialog(onDismissRequest = { showExitDialog = false }) {
            PremiumDialogCard {
                // 🚀 O SEGREDO: Precisamos da Column para empilhar os itens verticalmente
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp) // Dá um respiro entre os itens
                ) {
                    // 1. ÍCONE DE "POWER"
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFFF4B4B).copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Desligar",
                            tint = Color(0xFFFF4B4B),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // 2. TÍTULO
                    Text(
                        text = "Encerrar Aplicativo",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    // 3. MENSAGEM (Apenas uma vez, leque!)
                    Text(
                        text = "Tem certeza que deseja sair do Meu Dinheiro?",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp)) // Espaço extra antes dos botões

                    // 4. BOTÕES (Em uma Row para ficarem lado a lado)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BotaGlassmorphic(
                            texto = "Cancelar",
                            corAcento = Color.White.copy(alpha = 0.6f),
                            animateIdleJump = false,
                            modifier = Modifier.weight(1f),
                            onClick = { showExitDialog = false }
                        )

                        BotaGlassmorphic(
                            texto = "Sair",
                            corAcento = Color(0xFFFF4B4B),
                            animateIdleJump = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showExitDialog = false
                                val act = context as? Activity
                                act?.let {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        it.finishAndRemoveTask()
                                    } else {
                                        it.finishAffinity()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    // O Fluxo de Telas da Navegação
    when (stage) {
        // 🚀 O NOVO ESTÁGIO INICIAL
        AppStage.SystemBoot -> {
            SystemBootSplashScreen(
                onBootComplete = {
                    // Quando o boot termina, vai para a sua lógica original
                    stage = AppStage.Splash
                }
            )
        }

        AppStage.Splash -> {
            SplashScreen(
                onTimeout = {
                    if (hasUser == true) {
                        if (isBiometriaAtiva) {
                            com.meudinheiro.componentes.solicitarBiometria(
                                context = context,
                                onSuccess = { stage = AppStage.Home },
                                onFallback = { stage = AppStage.Login }
                            )
                        } else {
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
                onBack = { stage = AppStage.Home },
                onFinished = { stage = AppStage.Home }
            )
        }

        AppStage.Login -> {
            LoginScreen(
                userPrefs = userPrefs,
                onLoginSuccess = { stage = AppStage.Home }
            )
        }

        AppStage.Home -> {
            MainScreen(
                userPrefs,
                onOpenAvisos = { stage = AppStage.Avisos },
                onOpenPendencias = { stage = AppStage.Pendencias }
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
    val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(7, TimeUnit.DAYS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "LembreteBackupSemanal",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

private fun criarCanalNotificacao(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "backup_channel",
            "Lembretes de Backup",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notificações para manter seus dados seguros" }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}