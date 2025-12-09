package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.repository.MainRepository

class ContaSaldoViewModelFactory(
    private val repository: MainRepository
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (modelClass.isAssignableFrom(ContaSaldoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            ContaSaldoViewModel(repository) as T
        } else throw IllegalArgumentException("Unknown VM")
}