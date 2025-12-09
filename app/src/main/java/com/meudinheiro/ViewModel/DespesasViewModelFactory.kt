package com.meudinheiro.ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.Repository.MainRepository

class DespesasViewModelFactory(
    private val repository: MainRepository
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (modelClass.isAssignableFrom(DespesasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            DespesasViewModel(repository) as T
        } else throw IllegalArgumentException("Unknown VM")
}