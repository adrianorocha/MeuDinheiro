package com.meudinheiro.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.DAO.DespesasDomain
import com.meudinheiro.Data.Despesa
import com.meudinheiro.Repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {
//    private val _despesas = MutableLiveData<List<Despesa>>(emptyList())

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