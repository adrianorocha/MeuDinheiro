package com.meudinheiro.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {
    private val _despesas = MutableLiveData<List<DespesasDomain>>()

    val despesa : LiveData<List<DespesasDomain>> = repository.obterDespesas().asLiveData(
        viewModelScope.coroutineContext
    )
    val despesasLiveData: LiveData<List<DespesasDomain>> = repository.obterDespesas().asLiveData()

    fun carregarDespesas() {
        viewModelScope.launch {
            _despesas.postValue(repository.obterDespesas() as List<DespesasDomain>?)
        }
    }
    fun carregarDespesasPorConta(contaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val despesasFiltradas = repository.obterDespesasPorConta(contaId)
            _despesas.postValue(despesasFiltradas)
        }
    }
    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.inserirDespesa(despesa)
        }
    }

    fun removerDespesa(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirDespesa(id)
        }
    }
}