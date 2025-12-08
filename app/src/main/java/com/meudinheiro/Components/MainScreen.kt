package com.meudinheiro.Components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.DAO.DespesasDomain
import com.meudinheiro.Repository.MainRepository
import com.meudinheiro.ViewModel.DespesasViewModel
import kotlin.collections.emptyList

@Composable
fun MainScreen(
    onCardClick: () -> Unit = {},
    despesas : List<DespesasDomain>
) {
    val context = LocalContext.current
    val repository =  remember {MainRepository(context)} //Carrega as Informações do Repository
    val viewModel = DespesasViewModel(repository)

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
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ){
                Text(
                    text = "Despesas Recentes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                items( despesas) { item -> DespesasItem(item = item, onRemover = { id -> viewModel.removerDespesa(id)} ) }
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
    val context = LocalContext.current
    val repository =  remember {MainRepository(context)}
    val viewModel = DespesasViewModel(repository)
    val despesas by viewModel.despesa.collectAsState().
    MainScreen(despesas = despesas)
}