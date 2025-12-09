package com.meudinheiro.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.dao.DespesasDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {

    val despesa : LiveData<List<DespesasDomain>> = repository.obterDespesas().asLiveData(
        viewModelScope.coroutineContext
    )

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