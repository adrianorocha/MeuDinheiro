package com.meudinheiro.Components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.LightGray
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.R

@Composable
@Preview(showBackground = true)
fun ActionButtonRow() {
    val exibirFormulario = remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ActionButton(
            R.drawable.deposit,
             "Depositar",
            onClick = { /* Ação ao clicar no botão Adicionar */ }
        )
        ActionButton(
             R.drawable.add,
             "Adicionar",
            onClick = { exibirFormulario.value = true}
        )

        ActionButton(
             R.drawable.sim_chip,
             "Configurações",
            onClick = { /* Ação ao clicar no botão Configurações */ }
        )
    }
    if(exibirFormulario.value) {
        /* Adicionar Despesas */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Novas Despesas",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            var descricao by remember { mutableStateOf("") }
            var valor by remember { mutableStateOf("") }
            var data by remember { mutableStateOf("") }
            TextField(
                value = descricao,
                onValueChange = {},
                label = { Text("Descrição") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            TextField(
                value = valor,
                onValueChange = {},
                label = { Text("Valor") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            TextField(
                value = data,
                onValueChange = {},
                label = { Text("Data") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Text("Adicionar")
            }
            Button(
                onClick = { },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Text("Cancelar")
            }
        }
    }

}
@Composable
fun RowScope.ActionButton(icon: Int, text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(78.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color = LightGray)
            .clickable{ onClick() }
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,

        ) {
        Image(painter = painterResource(id = icon),
            contentDescription = text
        )
        Text(
            text = text,
            color = colorResource(R.color.blue_dark),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)

        )
    }
}