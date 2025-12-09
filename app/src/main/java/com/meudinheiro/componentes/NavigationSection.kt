package com.meudinheiro.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meudinheiro.R

@Composable
@Preview(showBackground = true)

fun NavigationSection(
    onItemSelected: (Int) -> Unit,
    modifier: Modifier

) {
    NavigationBar(
        containerColor = colorResource(R.color.white),
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        NavigationBarItem(
            selected = false,
            onClick = {onItemSelected(0) },
            icon = {
                Icon(painterResource(R.drawable.wallet), contentDescription = "Dinheiro")
            },
            label={Text(text="Carteira")}
        )
        NavigationBarItem(
            selected = false,
            onClick = {onItemSelected(0) },
            icon = {
                Icon(painterResource(R.drawable.bank), contentDescription = "Conta")
            },
            label={Text(text="Conta")}
        )

    }
}
