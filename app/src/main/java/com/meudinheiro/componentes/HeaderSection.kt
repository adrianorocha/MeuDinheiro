package com.meudinheiro.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meudinheiro.R
import java.io.File

@Preview(showBackground = true)
@Composable
fun HeaderSection(
    nome: String,
    fotoUri: String?,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Meu Dinheiro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Blue)
            Text("Olá, $nome.", fontSize = 14.sp, color = Color.Blue)
        }

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            if (!fotoUri.isNullOrBlank()) {
                val model = if (fotoUri.startsWith("/")) File(fotoUri) else fotoUri

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.user),
                    error = painterResource(id = R.drawable.user),
                    fallback = painterResource(id = R.drawable.user)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.user),
                    contentDescription = "Avatar padrão",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
