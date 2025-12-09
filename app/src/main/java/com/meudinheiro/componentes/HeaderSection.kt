package com.meudinheiro.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R

@Preview(showBackground = true)
@Composable
fun HeaderSection(){
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp,top = 48.dp,end = 16.dp, bottom = 16.dp)
    ) {
        Column{
            Text(
                text = "Meu Dinheiro",
                fontSize = 19.sp,
                color = Color.Blue,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Olá, Adriano dos Santos Rocha.",
                fontSize = 14.sp,
                color = Color.Blue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 2.dp)
            )
        }
        Image(
            painter = painterResource(id = R.drawable.user),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 120.dp, top = 6.dp)
                .size(50.dp)
        )
    }
}