package com.meudinheiro.componentes

import android.net.Uri
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

// --- LÓGICA DE SENHA ---
// Estrutura de dados para a UI da força da senha
private data class PasswordStrengthUi(
    val label: String,
    val color: Color,
    val progress: Float // 0.0 a 1.0
)

// Função para avaliar a força da senha e retornar o estado da UI
private fun evaluatePasswordStrength(pass: String): PasswordStrengthUi {
    if (pass.isBlank()) return PasswordStrengthUi("", Color.Transparent, 0f)

    var score = 0
    if (pass.length >= 6) score++
    if (pass.length >= 10) score++
    if (pass.any { it.isDigit() }) score++
    if (pass.any { !it.isLetterOrDigit() }) score++
    if (pass.any { it.isUpperCase() } && pass.any { it.isLowerCase() }) score++

    // Cores baseadas na imagem de exemplo
    val weakColor = Color(0xFFE53935) // Vermelho
    val mediumColor = Color(0xFFFFB300) // Amarelo/Laranja
    val strongColor = Color(0xFF43A047) // Verde

    return when (score) {
        0, 1 -> PasswordStrengthUi("Fraca", weakColor, 0.33f)
        2, 3 -> PasswordStrengthUi("Média", mediumColor, 0.66f)
        4, 5 -> PasswordStrengthUi("Forte", strongColor, 1.0f)
        5,6 -> PasswordStrengthUi("Muito forte", strongColor, 1.0f)
        6,7 -> PasswordStrengthUi("Extremamente forte", strongColor, 1.0f)
        7,8 -> PasswordStrengthUi("Muito muito forte", strongColor, 1.0f)
        8,9 -> PasswordStrengthUi("Muito muito muito forte", strongColor, 1.0f)
        9,10 -> PasswordStrengthUi("Muito muito muito muito forte", strongColor, 1.0f)
        else -> PasswordStrengthUi("", Color.Transparent, 0f) // Deveria ser inalcançável
    }
}

@Composable
fun CadastroUsuarioScreen(
    userPrefs: UserPreferences,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    // ... (Restante do código de inicialização e estados permanece o mesmo)
    val context = LocalContext.current
    val activity = remember {
        (context as? FragmentActivity)
            ?: throw IllegalStateException("Composable must be hosted in a FragmentActivity")
    }

    val homeVm: HomeViewModel = viewModel(factory = HomeViewModelFactory(userPrefs))
    val vm: AuthViewModel = viewModel(factory = AuthViewModelFactory(userPrefs))

    // Estados
    var nomeCompleto by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirma by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Carregamento inicial
    LaunchedEffect(Unit) {
        vm.loadInitial()
        nomeCompleto = vm.nomeCompletoState.value
        usuario = vm.usuarioState.value
        senha = ""
        confirma = ""
    }

    // Foto
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

    // --- CÁLCULO DE FORÇA ---
    val strengthUi = remember(senha) { evaluatePasswordStrength(senha) }
    val confirmStrengthUi = remember(confirma) { evaluatePasswordStrength(confirma) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
                )
            )
    ) {
        // --- CONTEÚDO SCROLLÁVEL ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ... (Espaçamento e Título)
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                "Criar Conta",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE0E1DD)
            )
            Spacer(modifier = Modifier.height(24.dp))

            AvatarPicker(fotoUri = fotoUri) { pickLauncher.launch(arrayOf("image/*")) }
            Spacer(modifier = Modifier.height(32.dp))

            // ... (Outros campos de input)
            PremiumRegistrationInput(
                value = nomeCompleto, onValueChange = { nomeCompleto = it },
                label = "Nome completo", icon = Icons.Default.Badge
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

            // Senha
            PremiumRegistrationInput(
                value = senha, onValueChange = { senha = it },
                label = "Senha", icon = Icons.Default.Lock,
                isPassword = true, isVisible = isPasswordVisible,
                onVisibilityChange = { isPasswordVisible = !isPasswordVisible }
            )
            // NOVO INDICADOR DE FORÇA DE SENHA
            PasswordStrengthIndicator(strengthUi)
            Spacer(modifier = Modifier.height(16.dp))

            // Confirmação
            PremiumRegistrationInput(
                value = confirma, onValueChange = { confirma = it },
                label = "Confirme a senha", icon = Icons.Default.Lock,
                isPassword = true, isVisible = isPasswordVisible,
                onVisibilityChange = { isPasswordVisible = !isPasswordVisible }
            )
            // NOVO INDICADOR DE FORÇA DE SENHA PARA CONFIRMAÇÃO
            PasswordStrengthIndicator(confirmStrengthUi)

            Spacer(modifier = Modifier.height(24.dp))

            // ... (Restante do código: Biometria, Botão Salvar)
            if (vm.canUseBiometric(activity)) {
                BiometricSwitch(checked = vm.useBiometric.value) { checked ->
                    if (checked) vm.promptBiometric(
                        activity,
                        {},
                        { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() })
                    else vm.useBiometric.value = false
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Button(
                onClick = {
                    if (validateForm(activity, nomeCompleto, usuario, senha, confirma)) {
                        vm.nomeCompletoState.value = nomeCompleto.trim()
                        vm.usuarioState.value = usuario.trim()
                        vm.passState.value = senha
                        vm.confirmState.value = confirma
                        if (!fotoUri.isNullOrBlank()) vm.userPhotoState.value = fotoUri!!.trim()
                        vm.saveCredentials(onSuccess = { onFinished() })
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0D1B2A)
                ),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Text("SALVAR ALTERAÇÕES", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // --- BOTÃO VOLTAR (Fixo no topo esquerdo) ---
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Voltar",
                tint = Color(0xFFE0E1DD),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// --- NOVO COMPONENTE DE FORÇA DA SENHA (Estilo da Imagem) ---
@Composable
private fun PasswordStrengthIndicator(strengthUi: PasswordStrengthUi) {
    // Animação suave para a cor e o progresso
    val animatedColor by animateColorAsState(
        targetValue = strengthUi.color,
        animationSpec = tween(300),
        label = "color"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = strengthUi.progress,
        animationSpec = tween(300),
        label = "progress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Barra de progresso
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp) // Altura da barra
                .clip(RoundedCornerShape(2.dp)), // Bordas arredondadas
            color = animatedColor,
            trackColor = Color(0xFFE0E1DD).copy(alpha = 0.2f) // Cor de fundo da barra
        )

        // Rótulo e ícone do cadeado
        if (strengthUi.label.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strengthUi.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor
                )
                Icon(
                    imageVector = Icons.Default.Lock, // Usei o ícone de cadeado padrão
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ... (Os outros componentes auxiliares AvatarPicker, BiometricSwitch, PremiumRegistrationInput e a função validateForm permanecem exatamente os mesmos do código anterior e devem ser incluídos aqui para o funcionamento completo)
// (Inclua aqui os outros componentes AvatarPicker, BiometricSwitch, PremiumRegistrationInput, validateForm do código anterior)
@Composable
private fun AvatarPicker(fotoUri: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(110.dp)
            .clip(CircleShape)
            .border(2.dp, Color(0xFFE0E1DD).copy(alpha = 0.3f), CircleShape)
            .background(Color.Black.copy(alpha = 0.2f))
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
            Icon(
                Icons.Default.Person,
                null,
                modifier = Modifier.size(50.dp),
                tint = Color(0xFFE0E1DD).copy(alpha = 0.7f)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(28.dp)
                .background(Color(0xFF0D1B2A), CircleShape)
                .border(1.dp, Color(0xFFE0E1DD), CircleShape), contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                null,
                tint = Color(0xFFE0E1DD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BiometricSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            color = Color(0xFFE0E1DD),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0D1B2A),
                checkedTrackColor = Color(0xFFE0E1DD),
                uncheckedThumbColor = Color(0xFFE0E1DD),
                uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
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
        value = value, onValueChange = onValueChange, label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = Color(0xFFE0E1DD).copy(alpha = 0.7f)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onVisibilityChange) {
                    Icon(
                        if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint = Color(0xFFE0E1DD).copy(alpha = 0.7f)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFE0E1DD),
            unfocusedBorderColor = Color(0xFFE0E1DD).copy(alpha = 0.3f),
            focusedLabelColor = Color(0xFFE0E1DD),
            unfocusedLabelColor = Color(0xFFE0E1DD).copy(alpha = 0.7f),
            cursorColor = Color(0xFFE0E1DD),
            focusedTextColor = Color(0xFFE0E1DD),
            unfocusedTextColor = Color(0xFFE0E1DD),
            focusedLeadingIconColor = Color(0xFFE0E1DD),
            unfocusedLeadingIconColor = Color(0xFFE0E1DD).copy(alpha = 0.7f)
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
        nome.trim().isBlank() -> {
            Toast.makeText(context, "Nome obrigatório", Toast.LENGTH_SHORT).show(); false
        }

        user.trim().isBlank() -> {
            Toast.makeText(context, "Usuário obrigatório", Toast.LENGTH_SHORT).show(); false
        }

        pass.isBlank() -> {
            Toast.makeText(context, "Senha obrigatória", Toast.LENGTH_SHORT).show(); false
        }

        pass != conf -> {
            Toast.makeText(context, "Senhas não conferem", Toast.LENGTH_SHORT).show(); false
        }

        else -> true
    }
}