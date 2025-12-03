package com.meudinheiro.Components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Meu Dinheiro",
                fontSize = 14.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
        /*Image(
            painter = painterResource(id = 0),
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
        )*/
    }
}