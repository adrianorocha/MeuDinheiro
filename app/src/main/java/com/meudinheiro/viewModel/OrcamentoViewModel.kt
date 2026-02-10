package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.map

class OrcamentoViewModel(private val repository: MainRepository) : ViewModel() {

    val listaOrcamentos = repository.obterOrcamentosFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orcamentosComProgresso: StateFlow<List<OrcamentoProgresso>> = combine<List<Orcamento>, List<Despesa>, List<OrcamentoProgresso>>(
        repository.obterOrcamentosFlow(),
        despesasViewModel.despesasFiltradas,
    ) { orcamentos, despesas ->

        // Lógica de cruzamento dos dados
        orcamentos.map { orc ->
            // Filtra despesas daquela categoria e soma
            val gastoAtual = despesas
                .filter { it.categoria == orc.categoria && it.tipo == "DEBITO" } // Ajuste "DEBITO" conforme seu Enum/String
                .sumOf { it.valor }

            // Calcula porcentagem (evita divisão por zero)
            val porcentagem = if (orc.valorLimite > 0) (gastoAtual / orc.valorLimite).toFloat() else 0f

            OrcamentoProgresso(
                categoria = orc.categoria,
                limite = orc.valorLimite,
                gastoAtual = gastoAtual,
                porcentagem = porcentagem
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    fun salvarNovoTeto(categoria: String, valor: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarOrcamento(categoria, valor)
        }
    }
}
