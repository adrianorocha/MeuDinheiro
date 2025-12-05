package com.meudinheiro.Components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        Column(
            modifier = Modifier
                .fillMaxSize()

        ) {
            HeaderSection()
            CardSection { onCardClick }
            ActionButtonRow()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                items( despesas) { item -> DespesasItem(item = item) }
            }
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
        DespesasDomain("drink", "Bar", 178.11,"01/12/2025"),
        DespesasDomain("health", "Remedios", 150.11,"01/12/2025"),
        DespesasDomain("cinema", "Filmes", 50.00,"01/12/2025"),
        DespesasDomain("shopping", "Compras", 400.00,"01/12/2025"),
        DespesasDomain("fuel", "Combustível", 250.00,"01/12/2025")
    )
    MainScreen(despesas = despesas)
}