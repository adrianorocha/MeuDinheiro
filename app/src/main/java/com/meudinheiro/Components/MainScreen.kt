package com.meudinheiro.Components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meudinheiro.DAO.DespesasDomain

@Composable
fun MainScreen(
    onCardClick: () -> Unit = {},
    despesas : List<DespesasDomain>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.White)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 70.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item{HeaderSection()}
            item{CardSection { onCardClick }}
            item { ActionButtonRow() }
            //item{DespesasScreen(despesas)}
            items( despesas) { item -> DespesasItem(item = item) }
        }
        NavigationSection(
            onItemSelected = { 0 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
@Preview(showBackground = true)

fun MainScreenPreview() {
    val despesas = listOf(
        DespesasDomain("lunch", "Lanche", 25.35,"01/12/2025"),
        DespesasDomain("transport", "Condução", 150.72,"01/12/2025"),
        DespesasDomain("restaurant", "Almoço", 40.11,"01/12/2025"),
        DespesasDomain("drink", "Bar", 50.11,"01/12/2025"),
        DespesasDomain("health", "Remedios", 150.11,"01/12/2025")

    )
    MainScreen(despesas = despesas)
}