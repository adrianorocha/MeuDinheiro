package com.meudinheiro.ViewModel

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import com.meudinheiro.Repository.MainRepository
import androidx.lifecycle.asLiveData

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MainRepository(application)

    fun loadOrcamento() = repository.orcamento
    fun loadDespesas() = repository.items

}