package com.meudinheiro.viewModel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AuthViewModel(private val prefs: UserPreferences) : ViewModel() {

    val userNameFlow = prefs.userNameFlow          // nome completo
    val userPassFlow = prefs.userPassFlow
    val userLoginFlow = prefs.userLoginFlow        // nome do usuário (login)
    val biometricEnabledFlow = prefs.biometricEnabledFlow

    val nomeCompletoState = mutableStateOf("")
    val usuarioState = mutableStateOf("")          // login
    val passState = mutableStateOf("")
    val confirmState = mutableStateOf("")
    val useBiometric = mutableStateOf(false)
    val userPhotoState = mutableStateOf("")
    fun loadInitial() {
        viewModelScope.launch {
            nomeCompletoState.value = prefs.userNameFlow.firstOrNull().orEmpty()
            usuarioState.value = prefs.userLoginFlow.firstOrNull().orEmpty()
            passState.value = prefs.userPassFlow.firstOrNull().orEmpty()
            userPhotoState.value = prefs.userPhotoFlow.firstOrNull().orEmpty()
            confirmState.value = ""
            useBiometric.value = prefs.biometricEnabledFlow.firstOrNull() ?: false
        }
    }

    fun saveCredentials(onSuccess: () -> Unit) {
        val nome = nomeCompletoState.value.trim()
        val user = usuarioState.value.trim()
        val pass = passState.value

        if (nome.isBlank()) return
        if (user.isBlank()) return
        if (pass.isBlank()) return
        if (pass != confirmState.value) return

        viewModelScope.launch {
            prefs.saveUserName(nome)
            prefs.saveUserLogin(user)
            prefs.saveUserPass(pass)
            prefs.saveBiometricEnabled(useBiometric.value)
            prefs.saveUserPhoto(userPhotoState.value)
            onSuccess()
        }
    }

    fun canUseBiometric(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // agora recebe uma FragmentActivity de verdade
    fun promptBiometric(
        activity: FragmentActivity,
        onAuthenticated: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("A atividade está inativa.")
            return
        }

        // Criar executor para o prompt
        val executor = ContextCompat.getMainExecutor(activity)

        // Criar o prompt de biometria
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    useBiometric.value = true
                    onAuthenticated()
                }

                override fun onAuthenticationError(err: Int, msg: CharSequence) {
                    onError("Erro: $msg") // Mensagem de erro mais clara
                }

                override fun onAuthenticationFailed() {
                    onError("Falha na autenticação.")
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticação biométrica")
            .setSubtitle("Use sua digital ou PIN para ativar biometria")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}
