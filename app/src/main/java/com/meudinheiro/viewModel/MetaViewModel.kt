package com.meudinheiro.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Meta
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MetaViewModel(private val repository: MainRepository) : ViewModel() {
    val metas = repository.getTodasMetas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val contas = repository.getTodasContas().asLiveData()

    private val _uiEvent = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiEvent = _uiEvent.asSharedFlow()

    private fun executar(titulo: String, bloco: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bloco()
            } catch (e: Exception) {
                val msg = if (e is RegraFinanceiraException) e.message else "Algo deu errado. Tente novamente."
                if (e !is RegraFinanceiraException) Log.e("MetaVM", titulo, e)
                _uiEvent.tryEmit("$titulo | $msg | Erro")
            }
        }
    }

    fun salvarMeta(nome: String, objetivo: Double) = executar("Meta") {
        repository.salvarMeta(Meta(nome = nome, valorObjetivo = objetivo, valorGuardado = 0.0))
    }

    /** Tira o valor da conta [contaId] e guarda na meta (R10). */
    fun realizarAporteReal(meta: Meta, contaId: String, valor: Double) = executar("Aporte") {
        repository.realizarAporte(meta, contaId, valor)
    }

    fun excluirMeta(meta: Meta, contaId: String?) = executar("Excluir meta") {
        repository.excluirMetaComRestituicao(meta, contaId)
    }

    fun editarMeta(meta: Meta) = executar("Meta") { repository.salvarMeta(meta) }

    /** Depósito rápido: sai da conta [contaId] (antes o dinheiro "aparecia" na meta sem sair de lugar nenhum). */
    fun depositarNaMeta(id: Long, contaId: String, valor: Double) = executar("Depósito") {
        repository.depositarNaMeta(id.toInt(), contaId, valor)
    }
}
