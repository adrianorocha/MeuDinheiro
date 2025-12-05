package com.meudinheiro.ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.meudinheiro.Repository.MainRepository

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {
    val despesas = repository.getDespesas().asLiveData()
}

