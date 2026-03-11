package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.dao.TransacaoDao

class TransacaoViewModelFactory(private val dao: TransacaoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransacaoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransacaoViewModel(dao) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida")
    }
}