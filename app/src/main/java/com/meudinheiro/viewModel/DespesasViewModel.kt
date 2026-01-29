package com.meudinheiro.viewModel

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {

    private val contaSelecionadaFlow = MutableStateFlow("")

    fun setContaSelecionada(contaId: String) {
        contaSelecionadaFlow.value = contaId.trim()
    }

    val despesasLiveData: LiveData<List<DespesasDomain>> =
        contaSelecionadaFlow
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { contaId ->
                repository.obterDespesasPorContaFlow(contaId)
            }
            .asLiveData(viewModelScope.coroutineContext)

    fun removerDespesaComRestituicao(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirDespesaComRestituicao(id)
            // Flow atualiza sozinho
        }
    }
}
