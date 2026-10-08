package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.DespesaDao

class TransacaoViewModelFactory(
    private val despesaDao: DespesaDao,
    private val contaDao: ContaSaldoDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransacaoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransacaoViewModel(despesaDao, contaDao) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida")
    }
}
