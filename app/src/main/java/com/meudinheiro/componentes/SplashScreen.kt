package com.meudinheiro.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Aqui você pode usar um logo ou uma animação
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Exemplo de logo ou animação
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp) // ajuste o tamanho conforme necessário
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Meu Dinheiro", fontSize = 24.sp)
        }
    }

    // Adiciona uma espera de 2 segundos e depois navega
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout() // Chama o callback para ir para a tela de login
    }
}
