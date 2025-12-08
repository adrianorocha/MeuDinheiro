package com.meudinheiro.ViewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.Data.Despesa
import com.meudinheiro.Repository.MainRepository
import kotlinx.coroutines.launch

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {
    private val _despesas = MutableLiveData<List<Despesa>>(emptyList())

    val despesa = repository.obterDespesas()

    fun carregarDespesas() {
        viewModelScope.launch {
            _despesas.value = repository.obterDespesas() as List<Despesa>?
        }
    }

    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch {
            repository.inserirDespesa(despesa)
            //carregarDespesas()
        }
    }

    fun removerDespesa(id: Int) {
        viewModelScope.launch {
            repository.excluirDespesa(id)
            //carregarDespesas()
        }
    }
}

