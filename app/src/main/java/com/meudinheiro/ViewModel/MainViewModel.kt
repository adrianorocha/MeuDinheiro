package com.meudinheiro.ViewModel

import androidx.lifecycle.ViewModel
import com.meudinheiro.Repository.MainRepository

class MainViewModel (val repository: MainRepository) : ViewModel() {
    constructor() : this(MainRepository())

    fun loadOrcamento() = repository.orcamento
    fun loadDespesas() = repository.items

}