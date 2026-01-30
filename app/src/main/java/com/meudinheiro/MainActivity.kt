package com.meudinheiro

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.meudinheiro.componentes.CadastroUsuarioScreen
import com.meudinheiro.componentes.Configuracao
import com.meudinheiro.componentes.LoginScreen
import com.meudinheiro.componentes.MainScreen
import com.meudinheiro.componentes.PendenciasScreen
import com.meudinheiro.componentes.SplashScreen
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.DespesasDevidas
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleExpenseNotificationWorker()

        setContent {
            ShowApp()
        }
    }

    private fun scheduleExpenseNotificationWorker() {
        // Define a periodicidade do worker
        val workRequest = PeriodicWorkRequestBuilder<DespesasDevidas>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
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
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Sair do aplicativo") },
            text = { Text("Deseja realmente sair?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        val act = context as? Activity
                        if (act != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                act.finishAndRemoveTask() // remove de Recentes
                            } else {
                                act.finishAffinity()
                            }
                        }
                    }
                ) { Text("Sair") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancelar") }
            }
        )
    }
    when (stage) {
        AppStage.Splash -> {
            SplashScreen(
                onTimeout = {
                    if (hasUser == true) {
                        stage = AppStage.Login
                    } else if (hasUser == false) {
                        stage = AppStage.Cadastro
                    }
                    //stage = nextAfterSplash ?: AppStage.Cadastro
                }
            )
        }

        AppStage.Cadastro -> {
            CadastroUsuarioScreen(
                userPrefs = userPrefs,
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

