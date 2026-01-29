package com.meudinheiro.componentes

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

// --- LOGICA DE SENHA (Mantida) ---
private enum class PasswordStrength { WEAK, MEDIUM, STRONG }

private data class PasswordStrengthUi(
    val strength: PasswordStrength,
    val progress: Float,        // 0..1
    val label: String,
    val color: Color
)

private fun evaluatePasswordStrength(pass: String): PasswordStrengthUi {
    if (pass.isBlank()) {
        return PasswordStrengthUi(
            strength = PasswordStrength.WEAK,
            progress = 0f,
            label = "Fraca",
            color = Color(0xFFE53935) // Vermelho vivo
        )
    }

    var score = 0
    val len = pass.length

    if (len >= 8) score++
    if (len >= 12) score++
    if (pass.any { it.isLowerCase() }) score++
    if (pass.any { it.isUpperCase() }) score++
    if (pass.any { it.isDigit() }) score++
    if (pass.any { !it.isLetterOrDigit() }) score++

    val progress = (score / 6f).coerceIn(0f, 1f)

    return when {
        score <= 2 -> PasswordStrengthUi(
            strength = PasswordStrength.WEAK,
            progress = progress.coerceAtMost(0.4f),
            label = "Fraca",
            color = Color(0xFFE53935)
        )
        score <= 4 -> PasswordStrengthUi(
            strength = PasswordStrength.MEDIUM,
            progress = progress.coerceIn(0.4f, 0.75f),
            label = "Média",
            color = Color(0xFFFFB300) // Âmbar
        )
        else -> PasswordStrengthUi(
            strength = PasswordStrength.STRONG,
            progress = progress.coerceAtLeast(0.75f),
            label = "Forte",
            color = Color(0xFF43A047) // Verde
        )
    }
}

@Composable
fun CadastroUsuarioScreen(
    userPrefs: UserPreferences,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember {
        (context as? FragmentActivity)
            ?: throw IllegalStateException("Composable must be hosted in a FragmentActivity")
    }

    val homeVm: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val vm: AuthViewModel = viewModel(factory = AuthViewModelFactory(userPrefs))

    // Estados dos campos
    var nomeCompleto by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirma by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Foto
    val savedUri by homeVm.userPhoto.collectAsState(initial = "")
    val initialPhotoUri: String? = savedUri.takeIf { it.isNotBlank() }
    var fotoUri by remember { mutableStateOf(initialPhotoUri) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val dir = File(context.filesDir, "profile")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "profile_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Não foi possível abrir a imagem." }
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            fotoUri = destFile.absolutePath
            homeVm.updateUserPhoto(destFile.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao salvar foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        vm.loadInitial()
        nomeCompleto = vm.nomeCompletoState.value
        usuario = vm.usuarioState.value
        senha = ""
        confirma = ""
    }

    val strengthUi = remember(senha) { evaluatePasswordStrength(senha) }
    val scrollState = rememberScrollState() // Para rolar em telas pequenas

    // --- LAYOUT PRINCIPAL ---
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
                .padding(24.dp)
                .verticalScroll(scrollState), // Habilita rolagem
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Criar Conta",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- AVATAR ---
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(2.dp, TextWhite.copy(alpha = 0.3f), CircleShape)
                    .background(Color.Black.copy(alpha = 0.2f))
                    .clickable { pickLauncher.launch(arrayOf("image/*")) },
                contentAlignment = Alignment.Center
            ) {
                if (!fotoUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(fotoUri),
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar padrão",
                        modifier = Modifier.size(50.dp),
                        tint = TextWhite.copy(alpha = 0.7f)
                    )
                }

                // Ícone de câmera pequeno para indicar edição
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(PremiumDarkBlue, CircleShape)
                        .border(1.dp, TextWhite, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- CAMPOS ---
            PremiumRegistrationInput(
                value = nomeCompleto,
                onValueChange = { nomeCompleto = it },
                label = "Nome completo",
                icon = Icons.Default.Badge
            )

            Spacer(modifier = Modifier.height(16.dp))

            PremiumRegistrationInput(
                value = usuario,
                onValueChange = { usuario = it },
                label = "Usuário",
                icon = Icons.Default.AccountCircle,
                keyboardType = KeyboardType.Ascii
            )

            Spacer(modifier = Modifier.height(16.dp))

            PremiumRegistrationInput(
                value = senha,
                onValueChange = { senha = it },
                label = "Senha",
                icon = Icons.Default.Lock,
                isPassword = true,
                isVisible = isPasswordVisible,
                onVisibilityChange = { isPasswordVisible = !isPasswordVisible }
            )

            // Indicador de força
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { strengthUi.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = strengthUi.color,
                trackColor = TextWhite.copy(alpha = 0.2f)
            )
            Text(
                text = "Força: ${strengthUi.label}",
                fontSize = 12.sp,
                color = strengthUi.color,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            PremiumRegistrationInput(
                value = confirma,
                onValueChange = { confirma = it },
                label = "Confirme a senha",
                icon = Icons.Default.Lock,
                isPassword = true,
                isVisible = isPasswordVisible, // Usa o mesmo toggle da senha principal
                onVisibilityChange = { isPasswordVisible = !isPasswordVisible }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- SWITCH BIOMETRIA ---
            if (vm.canUseBiometric(activity)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Habilitar Biometria",
                        fontSize = 14.sp,
                        color = TextWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = vm.useBiometric.value,
                        onCheckedChange = { checked ->
                            if (checked) {
                                vm.promptBiometric(
                                    activity,
                                    onAuthenticated = { /* ok */ },
                                    onError = { errorMsg ->
                                        Toast.makeText(activity, errorMsg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                vm.useBiometric.value = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PremiumDarkBlue,
                            checkedTrackColor = TextWhite,
                            uncheckedThumbColor = TextWhite,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- BOTÃO SALVAR ---
            Button(
                onClick = {
                    when {
                        nomeCompleto.trim().isBlank() -> Toast.makeText(activity, "Nome completo obrigatório", Toast.LENGTH_SHORT).show()
                        usuario.trim().isBlank() -> Toast.makeText(activity, "Nome de usuário obrigatório", Toast.LENGTH_SHORT).show()
                        senha.isBlank() -> Toast.makeText(activity, "Senha obrigatória", Toast.LENGTH_SHORT).show()
                        senha != confirma -> Toast.makeText(activity, "Senhas diferentes", Toast.LENGTH_SHORT).show()
                        else -> {
                            vm.nomeCompletoState.value = nomeCompleto.trim()
                            vm.usuarioState.value = usuario.trim()
                            vm.passState.value = senha
                            vm.confirmState.value = confirma
                            if (!fotoUri.isNullOrBlank()) {
                                vm.userPhotoState.value = fotoUri!!.trim()
                            }
                            vm.saveCredentials(onSuccess = { onFinished() })
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = PremiumDarkBlue
                ),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Text("SALVAR ALTERAÇÕES", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // Espaço extra para o scroll não cortar o botão
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Helper visual para os Inputs ficarem iguais ao login
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
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = TextWhite.copy(alpha = 0.7f)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onVisibilityChange) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Alternar senha",
                        tint = TextWhite.copy(alpha = 0.7f)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if(isPassword) KeyboardType.Password else keyboardType),
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