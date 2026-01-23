package com.meudinheiro.componentes

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.*
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

@Composable
fun CadastroUsuarioScreen(
    userPrefs: UserPreferences,
    onFinished: ()->Unit
) {
    val ctx = LocalContext.current
    // tenta converter para FragmentActivity
    val activity = remember {
        (ctx as? FragmentActivity)
            ?: throw IllegalStateException("Composable must be hosted in a FragmentActivity")
    }
    val homeVm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(userPrefs)
    )

    val vm: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(userPrefs)
    )

    var nome by remember { mutableStateOf(homeVm.userName.value) }
    var senha by remember { mutableStateOf("") }
    var confirma by remember { mutableStateOf("") }

    // 1) coleta a string salva (sempre não-nula, mas pode ser "")
    val savedUri by homeVm.userPhoto.collectAsState(initial = "")
    // 2) decide se tem foto inicial ou não
    val initialPhotoUri: String? = savedUri.takeIf { it.isNotBlank() }

    // 3) fotoUri agora é String? bem tipado
    var fotoUri by remember { mutableStateOf(initialPhotoUri) }

    // launcher para buscar imagem
    val pickLauncher = rememberLauncherForActivityResult(
        contract = GetContent()
    ) { uri: Uri? ->
        fotoUri = uri?.toString()
    }

    LaunchedEffect(Unit) { vm.loadInitial()}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Cadastro de Usuário", fontSize = 24.sp)

        Spacer(Modifier.height(16.dp))

        // avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .clickable { pickLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (fotoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(fotoUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // ícone padrão
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirma,
            onValueChange = { confirma = it },
            label = { Text("Confirme a senha") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(16.dp))

        // Switch para biometria
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
                                onError = { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
                            )
                        } else {
                            vm.useBiometric.value = false
                        }
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    nome.isBlank() ->
                        Toast.makeText(activity, "Nome obrigatório", Toast.LENGTH_SHORT).show()
                    senha.isBlank() ->
                        Toast.makeText(activity, "Senha obrigatória", Toast.LENGTH_SHORT).show()
                    senha != confirma ->
                        Toast.makeText(activity, "Senhas diferentes", Toast.LENGTH_SHORT).show()
                    else -> {
                        // grava nome e foto
                        homeVm.updateUserName(nome.trim())
                        fotoUri?.let { homeVm.updateUserPhoto(it) }
                        onFinished()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar")
        }
    }
}
