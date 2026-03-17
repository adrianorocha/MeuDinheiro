package com.meudinheiro.componentes

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.meudinheiro.funcoes.PremiumSnackbar
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.viewModel.AuthViewModel
import com.meudinheiro.viewModel.AuthViewModelFactory
import kotlinx.coroutines.launch

// Cores Padrão Blu Macaw
private val NeonCyan = Color(0xFF00E5FF)
private val DeepSpaceBlue = Color(0xFF131E29)
private val CardGlass = Color(0xFF1B263B).copy(alpha = 0.8f)

@Composable
fun LoginScreen(
    userPrefs: UserPreferences,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryStep by remember { mutableIntStateOf(0) }
    var recoveredPassword by remember { mutableStateOf("") }

    // 1. MOTORES DA SNACKBAR PREMIUM
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val activity = remember {
        (context as? FragmentActivity) ?: throw IllegalStateException("FragmentActivity requerida.")
    }

    // Função auxiliar para exibir mensagens sem repetir código
    fun mostrarMensagem(texto: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss() // Limpa a anterior se houver
            snackbarHostState.showSnackbar(texto)
        }
    }

    val authVm: AuthViewModel = viewModel(factory = AuthViewModelFactory(userPrefs))
    val savedUser by userPrefs.userLoginFlow.collectAsState(initial = "")
    val savedPass by userPrefs.userPassFlow.collectAsState(initial = "")
    val biometricEnabled by userPrefs.biometricEnabledFlow.collectAsState(initial = false)

    val hasUser = savedUser.isNotBlank() && savedPass.isNotBlank()
    val canBiometric = remember(biometricEnabled, hasUser) {
        biometricEnabled && hasUser && authVm.canUseBiometric(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_anim"
    )

    fun authenticateForRecovery() {
        if (!canBiometric) {
            mostrarMensagem("Erro: Biometria indisponível.")
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    recoveredPassword = savedPass
                    recoveryStep = 1
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    mostrarMensagem("Falha: $errString")
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Recuperar Senha")
            .setNegativeButtonText("Cancelar")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // --- TELA PRINCIPAL COM SCAFFOLD PARA A SNACKBAR ---
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                PremiumSnackbar(data) // Chamando o componente VIP
            }
        },
        containerColor = DeepSpaceBlue
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // LOGO NEON
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                        .background(Color.White.copy(0.02f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AttachMoney, null, tint = NeonCyan, modifier = Modifier.size(40.dp))
                }

                Spacer(Modifier.height(24.dp))
                Text("Bem-vindo", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Controle suas finanças com segurança", color = Color.White.copy(0.7f), fontSize = 14.sp)
                Spacer(Modifier.height(40.dp))

                // INPUTS
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(CardGlass)
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    NeonTextField(username, { username = it }, "Usuário", Icons.Default.AccountCircle)
                    Spacer(Modifier.height(16.dp))
                    NeonTextField(password, { password = it }, "Senha", Icons.Default.Lock, true, isPasswordVisible, { isPasswordVisible = !isPasswordVisible })

                    if (canBiometric) {
                        Text(
                            "Esqueci minha senha", color = Color.White.copy(0.6f), fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.End).clickable { showRecoveryDialog = true }.padding(4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // BOTÃO ENTRAR (Com as mensagens Premium)
                Button(
                    onClick = {
                        when {
                            !hasUser -> mostrarMensagem("Atenção: Nenhum usuário cadastrado.")
                            username.isBlank() || password.isBlank() -> mostrarMensagem("Preencha todos os campos!")
                            username.trim() != savedUser.trim() || password != savedPass -> mostrarMensagem("Erro: Usuário ou senha inválidos.")
                            else -> onLoginSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("ENTRAR", color = DeepSpaceBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // BIOMETRIA
                if (canBiometric) {
                    Spacer(Modifier.height(24.dp))
                    IconButton(
                        onClick = {
                            authVm.promptBiometric(activity, { onLoginSuccess() }, { mostrarMensagem(it) })
                        },
                        modifier = Modifier.size(64.dp).scale(scaleAnim)
                            .background(Color.White.copy(0.05f), CircleShape)
                            .border(1.dp, NeonCyan.copy(0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.Fingerprint, null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                    }
                }
            }

            Text(
                "Meu Dinheiro App v1.1", color = Color.White.copy(0.3f), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    }

    if (showRecoveryDialog) {
        DialogRecuperacao(recoveryStep, recoveredPassword, { authenticateForRecovery() }, {
            showRecoveryDialog = false; recoveryStep = 0
            if(recoveredPassword.isNotEmpty()) password = recoveredPassword
            recoveredPassword = ""
        })
    }
}
// --- COMPONENTES AUXILIARES BLU MACAW ---

@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    isVisible: Boolean = false,
    onVisibilityChange: () -> Unit = {},
    // 📍 1. ADICIONAMOS ESTES DOIS PARÂMETROS NOVOS AQUI:
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(0.7f)) },
        leadingIcon = { Icon(icon, null, tint = Color.White.copy(0.7f)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onVisibilityChange) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Alternar senha",
                        tint = Color.White.copy(0.7f)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isVisible) PasswordVisualTransformation() else VisualTransformation.None,

        // 📍 2. AGORA ELE USA O TECLADO QUE VOCÊ PEDIR (Ou o de senha, se for o caso)
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else keyboardOptions,
        keyboardActions = keyboardActions,

        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00E5FF), // Neon Cyan
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF00E5FF),
            focusedContainerColor = Color.White.copy(0.05f),
            unfocusedContainerColor = Color.White.copy(0.02f)
        )
    )
}
@Composable
fun DialogRecuperacao(
    step: Int,
    password: String,
    onAuthenticate: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DeepSpaceBlue,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("Recuperar Acesso", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (step == 0) {
                    Text("Para sua segurança, autentique-se com a biometria para visualizar sua senha salva.", color = Color.White.copy(0.8f))
                } else {
                    Text("Sua senha atual é:", color = Color.White.copy(0.6f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = password,
                        style = MaterialTheme.typography.headlineMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Anote-a em local seguro.",
                        color = Color.White.copy(0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = if (step == 0) onAuthenticate else onClose,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                if (step == 0) {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(16.dp), tint = DeepSpaceBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Autenticar", color = DeepSpaceBlue)
                } else {
                    Text("Fechar e Usar", color = DeepSpaceBlue)
                }
            }
        },
        dismissButton = {
            if(step == 0) {
                TextButton(onClick = onClose) { Text("Cancelar", color = Color.White.copy(0.7f)) }
            }
        }
    )
}