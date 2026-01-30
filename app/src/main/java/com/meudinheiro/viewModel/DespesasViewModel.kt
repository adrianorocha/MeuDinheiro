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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {
    private val _mesSelecionado = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    private val _anoSelecionado = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))

    // Filtro de conta opcional (se quiser filtrar por conta específica também)
    private val _contaFiltro = MutableStateFlow<String?>(null)

    // Expomos para a UI saber o que mostrar no cabeçalho
    val mesSelecionado: StateFlow<Int> = _mesSelecionado
    val anoSelecionado: StateFlow<Int> = _anoSelecionado

    // 2. A Lista Mágica Filtrada
    // Combina: (Banco de Dados) + (Mês) + (Ano) + (Conta)
    val despesasFiltradas = combine(
        repository.obterDespesas(), // Flow do Room
        _mesSelecionado,
        _anoSelecionado,
        _contaFiltro
    ) { lista, mes, ano, contaId ->
        lista.filter { despesa ->
            val cal = Calendar.getInstance()
            cal.time = despesa.data

            val mesmoMes = cal.get(Calendar.MONTH) == mes
            val mesmoAno = cal.get(Calendar.YEAR) == ano
            val mesmaConta = if (contaId.isNullOrBlank()) true else despesa.conta == contaId

            mesmoMes && mesmoAno && mesmaConta
        }.sortedByDescending { it.data } // Mais recentes no topo
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Controles de Navegação
    fun mesAnterior() {
        if (_mesSelecionado.value == 0) {
            _mesSelecionado.value = 11
            _anoSelecionado.value -= 1
        } else {
            _mesSelecionado.value -= 1
        }
    }

    fun proximoMes() {
        if (_mesSelecionado.value == 11) {
            _mesSelecionado.value = 0
            _anoSelecionado.value += 1
        } else {
            _mesSelecionado.value += 1
        }
    }
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
