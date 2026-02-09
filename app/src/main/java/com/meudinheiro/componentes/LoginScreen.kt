package com.meudinheiro.componentes

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.viewModel.AuthViewModel
import com.meudinheiro.viewModel.AuthViewModelFactory

// Cores "Premium" Locais
private val PremiumAccent = Color(0xFF415A77)

@Composable
fun LoginScreen(
    userPrefs: UserPreferences,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Estado para o Dialog de Recuperação
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryStep by remember { mutableIntStateOf(0) } // 0=Auth, 1=ShowPass
    var recoveredPassword by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Verificação de Activity para Biometria
    val activity = remember {
        (context as? FragmentActivity)
            ?: throw IllegalStateException("LoginScreen precisa estar hospedado em uma FragmentActivity.")
    }

    val authVm: AuthViewModel = viewModel(factory = AuthViewModelFactory(userPrefs))

    // Coleta de dados
    val savedUser by userPrefs.userLoginFlow.collectAsState(initial = "")
    val savedPass by userPrefs.userPassFlow.collectAsState(initial = "")
    val biometricEnabled by userPrefs.biometricEnabledFlow.collectAsState(initial = false)

    val hasUser = savedUser.isNotBlank() && savedPass.isNotBlank()

    // Verifica se pode usar biometria
    val canBiometric = remember(biometricEnabled, hasUser) {
        biometricEnabled && hasUser && authVm.canUseBiometric(context)
    }

    // Função de Autenticação Biométrica para Recuperação
    fun authenticateForRecovery() {
        if (!canBiometric) {
            Toast.makeText(context, "Biometria não disponível para recuperação.", Toast.LENGTH_SHORT).show()
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Sucesso: Mostra a senha recuperada
                    recoveredPassword = savedPass
                    recoveryStep = 1
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(context, "Erro: $errString", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Recuperar Senha")
            .setSubtitle("Use sua biometria para ver sua senha")
            .setNegativeButtonText("Cancelar")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // --- DIALOG DE RECUPERAÇÃO ---
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = {
                showRecoveryDialog = false
                recoveryStep = 0
                recoveredPassword = ""
            },
            containerColor = Color(0xFF1E2B3E),
            title = { Text("Recuperar Acesso", color = TextWhite) },
            text = {
                Column {
                    if (recoveryStep == 0) {
                        Text(
                            "Para sua segurança, autentique-se com a biometria para visualizar sua senha salva.",
                            color = TextWhite.copy(0.8f)
                        )
                    } else {
                        Text("Sua senha atual é:", color = TextWhite.copy(0.6f))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = recoveredPassword,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF69F0AE),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Anote-a em local seguro.", color = TextWhite.copy(0.5f), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                if (recoveryStep == 0) {
                    Button(
                        onClick = { authenticateForRecovery() },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
                    ) {
                        Icon(Icons.Default.Fingerprint, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Autenticar")
                    }
                } else {
                    Button(
                        onClick = {
                            showRecoveryDialog = false
                            recoveryStep = 0
                            // Preenche automaticamente para facilitar
                            password = recoveredPassword
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
                    ) { Text("Fechar e Usar") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancelar", color = TextWhite.copy(0.7f))
                }
            }
        )
    }

    // --- TELA PRINCIPAL ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(PremiumDarkBlue, PremiumLightBlue)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // --- LOGO ---
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = PremiumAccent.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachMoney,
                    contentDescription = "Logo",
                    tint = TextWhite,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Bem-vindo",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Text(
                text = "Controle suas finanças com segurança",
                fontSize = 14.sp,
                color = TextWhite.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // --- INPUTS ---
            PremiumTextField(
                value = username,
                onValueChange = { username = it },
                label = "Usuário",
                icon = Icons.Default.AccountCircle
            )

            Spacer(modifier = Modifier.height(16.dp))

            PremiumTextField(
                value = password,
                onValueChange = { password = it },
                label = "Senha",
                icon = Icons.Default.Lock,
                isPassword = true,
                isVisible = isPasswordVisible,
                onVisibilityChange = { isPasswordVisible = !isPasswordVisible }
            )

            // Link de "Esqueci minha senha"
            if (canBiometric) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "Esqueci minha senha",
                        color = TextWhite.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { showRecoveryDialog = true }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- BOTÃO ENTRAR ---
            Button(
                onClick = {
                    when {
                        !hasUser -> {
                            Toast.makeText(context, "Nenhum usuário cadastrado.", Toast.LENGTH_SHORT).show()
                        }
                        username.isBlank() || password.isBlank() -> {
                            Toast.makeText(context, "Preencha todos os campos!", Toast.LENGTH_SHORT).show()
                        }
                        username.trim() != savedUser.trim() || password != savedPass -> {
                            Toast.makeText(context, "Usuário ou senha inválidos.", Toast.LENGTH_SHORT).show()
                        }
                        else -> onLoginSuccess()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = PremiumDarkBlue
                ),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Text(text = "ENTRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // --- BOTÃO BIOMETRIA ---
            if (canBiometric) {
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        authVm.promptBiometric(
                            activity = activity,
                            onAuthenticated = { onLoginSuccess() },
                            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextWhite)
                ) {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Entrar com biometria", fontSize = 16.sp)
                }
            }
        }

        // --- RODAPÉ ---
        Text(
            text = "Meu Dinheiro App v1.1",
            color = TextWhite.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

// Componente TextField Premium (Mantido igual)
@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isVisible: Boolean = false,
    onVisibilityChange: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = Color.White.copy(alpha = 0.7f)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onVisibilityChange) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Alternar senha",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
            cursorColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLeadingIconColor = Color.White,
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f)
        )
    )
}