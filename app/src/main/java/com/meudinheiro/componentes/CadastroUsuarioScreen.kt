package com.meudinheiro.componentes

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
            color = Color(0xFFE53935)
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

    // score máximo: 6
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
            color = Color(0xFFFFB300)
        )
        else -> PasswordStrengthUi(
            strength = PasswordStrength.STRONG,
            progress = progress.coerceAtLeast(0.75f),
            label = "Forte",
            color = Color(0xFF43A047)
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

    // campos da tela
    var nomeCompleto by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirma by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val savedUri by homeVm.userPhoto.collectAsState(initial = "")
    val initialPhotoUri: String? = savedUri.takeIf { it.isNotBlank() }
    var fotoUri by remember { mutableStateOf(initialPhotoUri) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            // copia para um arquivo interno do app (permanente)
            val dir = File(context.filesDir, "profile")
            if (!dir.exists()) dir.mkdirs()

            val destFile = File(dir, "profile_${UUID.randomUUID()}.jpg")

            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Não foi possível abrir a imagem." }
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // salva o PATH do arquivo interno
            fotoUri = destFile.absolutePath
            homeVm.updateUserPhoto(destFile.absolutePath)

            Log.d("CadastroUsuario", "Foto salva em: ${destFile.absolutePath}")

        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao salvar foto: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CadastroUsuario", "Erro ao salvar foto", e)
        }
    }
    LaunchedEffect(Unit) {
        vm.loadInitial()
        nomeCompleto = vm.nomeCompletoState.value
        usuario = vm.usuarioState.value
        // não pré-preencher senha na UI por segurança
        senha = ""
        confirma = ""
    }

    val strengthUi = remember(senha) { evaluatePasswordStrength(senha) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Cadastro de Usuário", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .clickable { pickLauncher.launch(arrayOf("image/*"))},
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
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nomeCompleto,
            onValueChange = { nomeCompleto = it },
            label = { Text("Nome completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Nome de usuário") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Mini barra de força + texto
        LinearProgressIndicator(
            progress = { strengthUi.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = strengthUi.color,
            trackColor = Color(0xFFE0E0E0)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Força da senha: ${strengthUi.label}",
            fontSize = 12.sp,
            color = strengthUi.color,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirma,
            onValueChange = { confirma = it },
            label = { Text("Confirme a senha") },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Switch biometria
        if (vm.canUseBiometric(activity)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ativar biometria", fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                when {
                    nomeCompleto.trim().isBlank() ->
                        Toast.makeText(activity, "Nome completo obrigatório", Toast.LENGTH_SHORT).show()

                    usuario.trim().isBlank() ->
                        Toast.makeText(activity, "Nome de usuário obrigatório", Toast.LENGTH_SHORT).show()

                    senha.isBlank() ->
                        Toast.makeText(activity, "Senha obrigatória", Toast.LENGTH_SHORT).show()

                    senha != confirma ->
                        Toast.makeText(activity, "Senhas diferentes", Toast.LENGTH_SHORT).show()

                    else -> {
                        // grava credenciais no DataStore (nome completo, usuário e senha)
                        vm.nomeCompletoState.value = nomeCompleto.trim()
                        vm.usuarioState.value = usuario.trim()
                        vm.passState.value = senha
                        vm.confirmState.value = confirma
                        if (!fotoUri.isNullOrBlank()) {
                            homeVm.updateUserPhoto(fotoUri!!) // Salva a URI da foto
                        }

                        vm.saveCredentials(
                            onSuccess = {
                                onFinished()
                            }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar")
        }
    }
}
