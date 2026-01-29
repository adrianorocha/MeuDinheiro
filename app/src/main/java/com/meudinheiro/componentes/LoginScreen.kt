package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.viewModel.AuthViewModel
import com.meudinheiro.viewModel.AuthViewModelFactory

// Cores "Premium" para o tema bancário (Azul Profundo e Dourado/Branco)
private val PremiumAccent = Color(0xFF415A77)
@Composable
fun LoginScreen(
    userPrefs: UserPreferences,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
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
    val canBiometric = biometricEnabled && hasUser && authVm.canUseBiometric(activity)

    // Fundo com Gradiente Elegante
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

            // --- LOGO E CABEÇALHO ---
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = PremiumAccent.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.1f)
                )
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

            // --- CAMPOS DE ENTRADA (Card "Flutuante" ou direto no fundo) ---
            // Optei por direto no fundo para visual mais limpo (Clean UI)

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

            Spacer(modifier = Modifier.height(32.dp))

            // --- BOTÃO PRINCIPAL ---
            Button(
                onClick = {
                    when {
                        !hasUser -> {
                            Toast.makeText(
                                context,
                                "Nenhum usuário cadastrado. Faça o cadastro primeiro.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        username.isBlank() || password.isBlank() -> {
                            Toast.makeText(context, "Preencha todos os campos!", Toast.LENGTH_SHORT)
                                .show()
                        }

                        username.trim() != savedUser.trim() || password != savedPass -> {
                            Toast.makeText(
                                context,
                                "Usuário ou senha inválidos.",
                                Toast.LENGTH_SHORT
                            ).show()
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
                Text(
                    text = "ENTRAR",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // --- BIOMETRIA ---
            if (canBiometric) {
                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = {
                        authVm.promptBiometric(
                            activity = activity,
                            onAuthenticated = { onLoginSuccess() },
                            onError = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextWhite)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Usar biometria", fontSize = 16.sp)
                }
            }
        }

        // --- RODAPÉ ---
        Text(
            text = "Meu Dinheiro App v1.0",
            color = TextWhite.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

// Componente auxiliar para padronizar os Inputs Premium
@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    isVisible: Boolean = false,
    onVisibilityChange: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        },
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