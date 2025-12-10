package com.meudinheiro.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory

@Composable
@Preview(showBackground = true)
fun Carteira(
    viewModelFactory: ContaSaldoViewModelFactory
){
    var conta: ContaSaldoDomain

    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .height(230.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable{}
    ) {
    }
}