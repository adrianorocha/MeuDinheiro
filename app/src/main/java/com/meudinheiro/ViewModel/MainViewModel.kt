package com.meudinheiro.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.meudinheiro.Repository.MainRepository

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MainRepository(application)

    fun loadOrcamento() = repository.orcamento
    fun loadDespesas() = repository.items

}