package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.repository.MainRepository

class OrcamentoViewModelFactory(
    private val repository: MainRepository
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (modelClass.isAssignableFrom(OrcamentoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            OrcamentoViewModel(repository) as T
        } else throw IllegalArgumentException("Unknown VM")
}