package com.meudinheiro.viewModel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.BancoDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContaSaldoViewModel(private val repository: MainRepository) : ViewModel(){

    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())

    init {
        // Carregue os bancos do repositório
        bancos.value = repository.bancos
    }
    val contaSaldo : LiveData<List<ContaSaldoDomain>> = repository.obterContaSaldo().asLiveData(
        viewModelScope.coroutineContext
    )
    private val _contaSelecionadaId = MutableLiveData<String?>(null)
    val contaSelecionadaId: LiveData<String?> = _contaSelecionadaId

    fun selecionarConta(contaId: String) {
        _contaSelecionadaId.value = contaId
    }

    fun adicionarContaSaldo(contaSaldo: ContaSaldo) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.inserirContaSaldo(contaSaldo)
        }
    }

    fun removerContaSaldo(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirConta(id)
        }
    }

    fun atualizarSaldo(conta: String, novoSaldo: Double) {
        viewModelScope.launch( Dispatchers.IO){
            repository.atualizarSaldo(conta, novoSaldo)
        }

    }
}