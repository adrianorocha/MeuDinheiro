package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

class OrcamentoViewModel(private val repository: MainRepository) : ViewModel() {

    val listaOrcamentos = repository.obterOrcamentosFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orcamentosComProgresso = combine(
        repository.obterOrcamentosFlow(),
        despesasViewModel.despesasFiltradas // Lista de despesas do mês atual
    ) { orcamentos, despesa ->
        orcamentos.map { orc ->
            val gastoAtual = despesa
                .filter { it.categoria == orc.categoria && it.tipo == TipoDespesa.DEBITO }
                .sumOf { it.valor }

            // Criamos um objeto temporário para a UI
            OrcamentoProgresso(
                categoria = orc.categoria,
                limite = orc.valorLimite,
                gastoAtual = gastoAtual,
                porcentagem = if (orc.valorLimite > 0) (gastoAtual / orc.valorLimite).toFloat() else 0f
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun salvarNovoTeto(categoria: String, valor: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarOrcamento(categoria, valor)
        }
    }
}
