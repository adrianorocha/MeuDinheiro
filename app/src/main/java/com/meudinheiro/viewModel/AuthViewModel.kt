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

    val userNameFlow       = prefs.userNameFlow
    val userPassFlow       = prefs.userPassFlow
    val biometricEnabledFlow = prefs.biometricEnabledFlow

    val nomeState    = mutableStateOf("")
    val passState    = mutableStateOf("")
    val confirmState = mutableStateOf("")
    val useBiometric = mutableStateOf(false)

    fun loadInitial() {
        viewModelScope.launch {
            nomeState.value    = prefs.userNameFlow.firstOrNull().orEmpty()
            passState.value    = ""
            confirmState.value = ""
            useBiometric.value = prefs.biometricEnabledFlow.firstOrNull() ?: false
        }
    }

    fun saveCredentials(onSuccess: ()->Unit) {
        if (nomeState.value.isBlank() || passState.value != confirmState.value) return
        viewModelScope.launch {
            prefs.saveUserName(nomeState.value.trim())
            prefs.saveUserPass(passState.value)
            prefs.saveBiometricEnabled(useBiometric.value)
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
        onAuthenticated: ()->Unit,
        onError: (String)->Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    useBiometric.value = true
                    onAuthenticated()
                }
                override fun onAuthenticationError(err: Int, msg: CharSequence) {
                    onError(msg.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticação biométrica")
            .setSubtitle("Use sua digital ou PIN para ativar biometria")
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}
