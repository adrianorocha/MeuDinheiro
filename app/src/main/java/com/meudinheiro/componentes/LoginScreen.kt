package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.viewModel.AuthViewModel
import com.meudinheiro.viewModel.AuthViewModelFactory

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
            ?: throw IllegalStateException("LoginScreen precisa estar hospedado em uma FragmentActivity (ex: AppCompatActivity).")
    }

    val authVm: AuthViewModel = viewModel(factory = AuthViewModelFactory(userPrefs))

    // credenciais salvas (cadastro)
    val savedUser by userPrefs.userLoginFlow.collectAsState(initial = "")
    val savedPass by userPrefs.userPassFlow.collectAsState(initial = "")

    // se você tiver isso no prefs:
    val biometricEnabled by userPrefs.biometricEnabledFlow.collectAsState(initial = false)

    val hasUser = savedUser.isNotBlank() && savedPass.isNotBlank()
    val canBiometric = biometricEnabled && hasUser && authVm.canUseBiometric(activity)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECEFF1))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Login", fontSize = 32.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuário") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFB0BEC5),
                        focusedLabelColor = Color(0xFF2196F3),
                        unfocusedLabelColor = Color(0xFF90A4AE),
                        cursorColor = Color(0xFF2196F3)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFB0BEC5),
                        focusedLabelColor = Color(0xFF2196F3),
                        unfocusedLabelColor = Color(0xFF90A4AE),
                        cursorColor = Color(0xFF2196F3)
                    ),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                                Toast.makeText(context, "Preencha todos os campos!", Toast.LENGTH_SHORT).show()
                            }
                            username.trim() != savedUser.trim() || password != savedPass -> {
                                Toast.makeText(context, "Usuário ou senha inválidos.", Toast.LENGTH_SHORT).show()
                            }
                            else -> onLoginSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        contentColor = Color.White
                    )
                ) {
                    Text("Entrar")
                }

                if (canBiometric) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            authVm.promptBiometric(
                                activity = activity,
                                onAuthenticated = { onLoginSuccess() },
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Entrar com biometria")
                    }
                }
            }
        }
    }
}
