package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.dao.InvestimentoDao

class InvestimentoViewModelFactory(private val dao: InvestimentoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InvestimentoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InvestimentoViewModel(dao) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida")
    }
}