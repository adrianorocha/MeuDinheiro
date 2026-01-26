package com.meudinheiro

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.meudinheiro.componentes.CadastroUsuarioScreen
import com.meudinheiro.componentes.LoginScreen
import com.meudinheiro.componentes.MainScreen
import com.meudinheiro.componentes.SplashScreen
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.flow.firstOrNull

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ShowApp()
        }
    }
}

private enum class AppStage { Splash, Cadastro, Login, Home }

@Composable
fun ShowApp() {
    var stage by rememberSaveable { mutableStateOf(AppStage.Splash) }

    val context = LocalContext.current
    val userPrefs = remember { UserPreferences(context) }

    var nextAfterSplash by remember { mutableStateOf<AppStage?>(null) }

    // Carrega do DataStore uma vez (valor real), sem depender de "initial = """
    LaunchedEffect(Unit) {
        val u = userPrefs.userNameFlow.firstOrNull().orEmpty().trim()
        val p = userPrefs.userPassFlow.firstOrNull().orEmpty()
        nextAfterSplash = if (u.isNotBlank() && p.isNotBlank()) AppStage.Login else AppStage.Cadastro
    }

    when (stage) {
        AppStage.Splash -> {
            SplashScreen(
                onTimeout = {
                    stage = nextAfterSplash ?: AppStage.Cadastro
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
            MainScreen(userPrefs)
        }
    }
}