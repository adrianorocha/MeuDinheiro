package com.meudinheiro.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {

    private val contaSelecionadaFlow = MutableStateFlow("")

    fun setContaSelecionada(contaId: String) {
        contaSelecionadaFlow.value = contaId.trim()
    }

    fun marcarComoPaga(id: Int, pago: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.marcarDespesaComoPaga(id, pago)
        }
    }
    val despesasLiveData: LiveData<List<DespesasDomain>> =
        contaSelecionadaFlow
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { contaId ->
                repository.obterDespesasPorContaFlow(contaId)
            }
            .asLiveData(viewModelScope.coroutineContext)

    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.inserirDespesa(despesa)
            // NÃO chama carregar; o Flow atualiza sozinho
        }
    }

    fun removerDespesa(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirDespesa(id)
            // Flow atualiza sozinho
        }
    }

    fun removerDespesaComRestituicao(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirDespesaComRestituicao(id)
            // Flow atualiza sozinho
        }
    }
}
