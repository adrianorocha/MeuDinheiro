package com.meudinheiro.componentes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.viewModel.AuthViewModel
import com.meudinheiro.viewModel.AuthViewModelFactory
import com.meudinheiro.viewModel.HomeViewModel
import com.meudinheiro.viewModel.HomeViewModelFactory
import java.io.File
import java.util.UUID

// --- CORES BLU MACAW ---
private val NeonCyan = Color(0xFF00E5FF)
private val DeepSpaceBlue = Color(0xFF131E29)
private val CardGlass = Color(0xFF1B263B).copy(alpha = 0.8f)

// --- LÓGICA DE SENHA ---
private data class PasswordStrengthUi(
    val label: String,
    val color: Color,
    val progress: Float
)

// Motor de Força de Senha Aprimorado
private fun evaluatePasswordStrength(pass: String): PasswordStrengthUi {
    if (pass.isBlank()) return PasswordStrengthUi("", Color.Transparent, 0f)

    var score = 0
    if (pass.length >= 6) score += 2
    if (pass.length >= 10) score += 2
    if (pass.any { it.isDigit() }) score += 2
    if (pass.any { !it.isLetterOrDigit() }) score += 2
    if (pass.any { it.isUpperCase() } && pass.any { it.isLowerCase() }) score += 2

    // Cores Neon para combinar com o tema
    val weakColor = Color(0xFFFF5252) // Neon Red
    val mediumColor = Color(0xFFFFB74D) // Neon Orange
    val strongColor = Color(0xFF69F0AE) // Neon Green
    val eliteColor = NeonCyan // Força Máxima

    return when (score) {
        in 0..3 -> PasswordStrengthUi("Senha Fraca", weakColor, 0.25f)
        in 4..5 -> PasswordStrengthUi("Razoável", mediumColor, 0.50f)
        in 6..8 -> PasswordStrengthUi("Senha Forte", strongColor, 0.75f)
        in 9..10 -> PasswordStrengthUi("Cofre Impenetrável", eliteColor, 1.0f)
        else -> PasswordStrengthUi("", Color.Transparent, 0f)
    }
}

@Composable
fun CadastroUsuarioScreen(
    userPrefs: UserPreferences,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember {
        (context as? FragmentActivity)
            ?: throw IllegalStateException("Composable must be hosted in a FragmentActivity")
    }

    val homeVm: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val vm: AuthViewModel = viewModel(factory = AuthViewModelFactory(userPrefs))

    var nomeCompleto by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirma by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadInitial()
        nomeCompleto = vm.nomeCompletoState.value
        usuario = vm.usuarioState.value
        senha = ""
        confirma = ""
    }

    val savedUri by homeVm.userPhoto.collectAsState(initial = "")
    var fotoUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savedUri) { if (savedUri.isNotBlank()) fotoUri = savedUri }

    val pickLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val dir = File(context.filesDir, "profile")
                    if (!dir.exists()) dir.mkdirs()
                    val destFile = File(dir, "profile_${UUID.randomUUID()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    fotoUri = destFile.absolutePath
                    homeVm.updateUserPhoto(destFile.absolutePath)
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao processar imagem.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    val strengthUi = remember(senha) { evaluatePasswordStrength(senha) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue) // Fundo Blu Macaw
    ) {
        // --- BOTÃO VOLTAR (Z-Index alto para não sumir no scroll) ---
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 8.dp)
                .background(Color.Transparent, CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text("Cofre Pessoal", fontSize = 28.sp, fontWeight = FontWeight.Black, color = TextWhite, letterSpacing = 1.sp)
            Text("Configure seu acesso seguro", fontSize = 14.sp, color = TextWhite.copy(0.5f))

            Spacer(modifier = Modifier.height(32.dp))

            AvatarPicker(fotoUri = fotoUri) { pickLauncher.launch(arrayOf("image/*")) }

            Spacer(modifier = Modifier.height(40.dp))

            // Formulário Glassmorphism
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardGlass)
                    .border(1.dp, TextWhite.copy(0.05f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                PremiumRegistrationInput(
                    value = nomeCompleto, onValueChange = { nomeCompleto = it },
                    label = "Nome Completo", icon = Icons.Default.Badge
                )
                Spacer(modifier = Modifier.height(16.dp))

                PremiumRegistrationInput(
                    value = usuario, onValueChange = { usuario = it },
                    label = "Usuário", icon = Icons.Default.AccountCircle,
                    keyboardType = KeyboardType.Ascii
                )
                Spacer(modifier = Modifier.height(16.dp))

                PremiumRegistrationInput(
                    value = senha, onValueChange = { senha = it },
                    label = "Senha Mestre", icon = Icons.Default.Lock,
                    isPassword = true, isVisible = isPasswordVisible,
                    onVisibilityChange = { isPasswordVisible = !isPasswordVisible }
                )

                // Indicador Inteligente (Só aparece se digitar algo)
                if (senha.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordStrengthIndicator(strengthUi)
                }

                Spacer(modifier = Modifier.height(16.dp))

                PremiumRegistrationInput(
                    value = confirma, onValueChange = { confirma = it },
                    label = "Confirme a Senha", icon = Icons.Default.LockReset,
                    isPassword = true, isVisible = isPasswordVisible,
                    onVisibilityChange = { isPasswordVisible = !isPasswordVisible }
                )

                // Feedback visual de senhas iguais
                if (confirma.isNotEmpty()) {
                    val senhasBatem = senha == confirma
                    val matchColor = if (senhasBatem) Color(0xFF69F0AE) else Color(0xFFFF5252)
                    Text(
                        text = if (senhasBatem) "Senhas conferem" else "Senhas não conferem",
                        color = matchColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (vm.canUseBiometric(activity)) {
                BiometricSwitch(checked = vm.useBiometric.value) { checked ->
                    if (checked) vm.promptBiometric(
                        activity,
                        {},
                        { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() })
                    else vm.useBiometric.value = false
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            Button(
                onClick = {
                    if (validateForm(context, nomeCompleto, usuario, senha, confirma)) {
                        vm.nomeCompletoState.value = nomeCompleto.trim()
                        vm.usuarioState.value = usuario.trim()
                        vm.passState.value = senha
                        vm.confirmState.value = confirma
                        if (!fotoUri.isNullOrBlank()) vm.userPhotoState.value = fotoUri!!.trim()
                        vm.saveCredentials(onSuccess = { onFinished() })
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("CRIAR COFRE", color = DeepSpaceBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PasswordStrengthIndicator(strengthUi: PasswordStrengthUi) {
    val animatedColor by animateColorAsState(targetValue = strengthUi.color, animationSpec = tween(500), label = "color")
    val animatedProgress by animateFloatAsState(targetValue = strengthUi.progress, animationSpec = tween(500), label = "progress")

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = strengthUi.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = animatedColor)
            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = animatedColor, modifier = Modifier.size(14.dp))
        }
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = animatedColor,
            trackColor = TextWhite.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun AvatarPicker(fotoUri: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(CardGlass)
            .border(2.dp, NeonCyan.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!fotoUri.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(fotoUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(60.dp), tint = NeonCyan.copy(0.7f))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(36.dp)
                .background(DeepSpaceBlue, CircleShape)
                .border(1.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CameraAlt, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BiometricSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardGlass)
            .border(1.dp, TextWhite.copy(0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Default.Fingerprint, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text("Desbloqueio Biométrico", fontSize = 14.sp, color = TextWhite, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepSpaceBlue,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = TextWhite.copy(0.5f),
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = TextWhite.copy(0.3f)
            )
        )
    }
}

@Composable
private fun PremiumRegistrationInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    isVisible: Boolean = false,
    onVisibilityChange: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, color = TextWhite.copy(0.5f)) },
        leadingIcon = { Icon(icon, null, tint = NeonCyan.copy(0.7f)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onVisibilityChange) {
                    Icon(if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = TextWhite.copy(0.4f))
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            cursorColor = NeonCyan,
            focusedContainerColor = TextWhite.copy(0.05f),
            unfocusedContainerColor = TextWhite.copy(0.02f)
        )
    )
}

private fun validateForm(
    context: android.content.Context,
    nome: String,
    user: String,
    pass: String,
    conf: String
): Boolean {
    return when {
        nome.trim().isBlank() -> { Toast.makeText(context, "Nome obrigatório", Toast.LENGTH_SHORT).show(); false }
        user.trim().isBlank() -> { Toast.makeText(context, "Usuário obrigatório", Toast.LENGTH_SHORT).show(); false }
        pass.isBlank() -> { Toast.makeText(context, "Senha obrigatória", Toast.LENGTH_SHORT).show(); false }
        pass != conf -> { Toast.makeText(context, "As senhas não conferem", Toast.LENGTH_SHORT).show(); false }
        else -> true
    }
}