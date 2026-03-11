package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Meta
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MetaViewModel(private val repository: MainRepository) : ViewModel() {
    val metas = repository.getTodasMetas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val contas = repository.getTodasContas().asLiveData()

    fun salvarMeta(nome: String, objetivo: Double) {
        viewModelScope.launch(Dispatchers.IO) { // Adicionado Dispatcher explícito
            repository.salvarMeta(Meta(nome = nome, valorObjetivo = objetivo, valorGuardado = 0.0))
        }
    }

    fun realizarAporteReal(meta: Meta, contaId: String, valor: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.realizarAporte(meta, contaId, valor)
        }
    }

    fun excluirMeta(meta: Meta, contaId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirMetaComRestituicao(meta, contaId)
        }
    }

    fun editarMeta(meta: Meta) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarMeta(meta)
        }
    }

}