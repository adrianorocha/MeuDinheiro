package com.meudinheiro.viewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.repository.MainRepository

class ContaSaldoViewModelFactory(
    private val application: Application, // Adicionamos a aplicação aqui
    private val repository: MainRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContaSaldoViewModel::class.java)) {
            // Agora passamos os DOIS parâmetros na ordem correta
            return ContaSaldoViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida")
    }
}