package com.meudinheiro.componentes

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun solicitarBiometria(
    context: Context,
    onSuccess: () -> Unit,
    onFallback: () -> Unit
) {
    // A biometria exige que o contexto seja uma FragmentActivity (nossa MainActivity é!)
    val activity = context as? FragmentActivity ?: return onFallback()

    val biometricManager = BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )

    // Se o celular não tem biometria ou não tem senha cadastrada, vai direto pro Login normal
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        onFallback()
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)

    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Se o usuário clicar em "Cancelar" ou der erro, vai pro Login de senha
                onFallback()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Digital reconhecida com sucesso!
                onSuccess()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear App")
        .setSubtitle("Use sua digital ou rosto para acessar suas contas")
        // Permite usar a senha padrão do celular se a biometria falhar
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()

    biometricPrompt.authenticate(promptInfo)
}