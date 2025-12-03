package com.meudinheiro.Components

import android.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.DAO.DespesasDomain

@Composable
@Preview(showBackground = true)
fun DespesasItem(item: DespesasDomain){
    Row(
        modifier = Modifier
            .padding(vertical=4.dp)
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color.LightGray,
                shape = RoundedCornerShape(12.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    )
    {
        val context = LocalContext.current
        val resId = context.resources.getIdentifier(
            item.pic, "drawable", context.packageName
        )
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(55.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorResource(R.color.darker_gray))
                .padding(12.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = item.date,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        Text(
            text = "R$ ${item.value}",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

@Composable
@Preview(showBackground = true)
fun PreviewDespesasItem(){
    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text(text = "")}
            )
        },
        content = {innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
            ) {
                // Conteúdo principal aqui
            }
        }
    ) {

    }
    val despesa = DespesasDomain(
        title = "Aluguel",
        value = 1200.0,
        date = "2024-06-01",
        pic = "restaurant"

    )
    DespesasItem(item = despesa)
}