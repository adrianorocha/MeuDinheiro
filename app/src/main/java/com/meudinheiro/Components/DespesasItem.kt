package com.meudinheiro.Components

import android.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun DespesasItem(
    item: DespesasDomain,
    onRemover : (Int) -> Unit
){
    var showDialog by remember { mutableStateOf(false) }

    if(showDialog){
        AlertDialog(
            onDismissRequest = { showDialog = false},
            title = { Text(text = "Remover Despesa")},
            text = { Text(text = "Deseja remover esta despesa?")},
            confirmButton = {
                Button(
                    onClick = {
                        onRemover(item.id)
                        showDialog = false
                    }
                ) {
                    Text(text = "Sim")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog = false}
                ) {
                    Text(text = "Não")
                }
            }
        )
    }
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
            ).combinedClickable(
                onClick = {
                },
                onLongClick = {showDialog = true}
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
                text = item.descricao,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = item.data,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        Text(
            text = "R$ ${item.valor}",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

/*@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespesasScreen(
    despesas: List<DespesasDomain>,
    onRemoverDespesa: (DespesasDomain) -> Unit) {
    val context = LocalContext.current
    val repository =  remember {MainRepository(context)} //Carrega as Informações do Repository
    val viewModel = DespesasViewModel(repository)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Despesas") }
            )
        },
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(despesas) { item ->
                    DespesasItem(item = item, onRemover = { id -> viewModel.removerDespesa(id)})
                }
            }
        }
    )
}

*@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun PreviewDespesasItem(){
    val scrollState = rememberScrollState()
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
                .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val despesa = DespesasDomain(
                    descricao = "Aluguel",
                    valor = 1200.0,
                    data = "01/12/2025",
                    pic = "restaurant"
                )
                DespesasItem(item = item, onRemover = { id -> viewModel.removerDespesa(id)})
            }
        }
    )
}*/