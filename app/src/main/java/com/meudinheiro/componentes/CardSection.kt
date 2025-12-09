package com.meudinheiro.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R
import com.meudinheiro.data.ContaSaldo

@Composable
@Preview(showBackground = true)
fun CardSection(
    onClick: () -> Unit ={}
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .height(230.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable{onClick()}
    ) {
        Image(
            painter = painterResource(id = R.drawable.card_tecno),
            contentDescription = null, 
            modifier = Modifier
                .matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            text = "Nubank",
            color = Color.Yellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, bottom = 16.dp)
        )

        Image(
            painter = painterResource(id = R.drawable.sim_chip_2),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top=25.dp,start = 315.dp)
        )
        Text(
            text = "Agência : 00001 - C/C : 00000000000",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(start = 12.dp, bottom = 18.dp, end = 8.dp)
        )

        Text(
            text = "Saldo R$ 1.000,00",
            color = Color.Yellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 18.dp, end = 8.dp)
        )
    }
}