package com.meudinheiro.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.repository.MainRepository

class CartoesViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Verificamos se a classe pedida é o nosso ViewModel de Cartões
        if (modelClass.isAssignableFrom(CartoesViewModel::class.java)) {
            // Criamos o repositório passando o contexto necessário
            val repository = MainRepository(context.applicationContext)

            @Suppress("UNCHECKED_CAST")
            return CartoesViewModel(repository) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida: ${modelClass.name}")
    }
}