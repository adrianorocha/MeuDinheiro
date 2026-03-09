package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.map

class OrcamentoViewModel(private val repository: MainRepository) : ViewModel() {

    // 1. Pegamos o mês atual para filtrar as despesas do orçamento
    private val calendar = Calendar.getInstance()
    private val mesAtual = calendar.get(Calendar.MONTH) // 0 a 11
    private val anoAtual = calendar.get(Calendar.YEAR)

    // 2. Fluxo de Orçamentos (Vem do Banco)
    private val orcamentosFlow = repository.obterOrcamentosFlow()

    // 3. Fluxo de Despesas (Vem do Banco e filtramos o mês atual aqui)
    private val despesasDoMesFlow = repository.todasDespesasFlow
        .map { lista ->
            lista.filter { despesa ->
                val calDespesa = Calendar.getInstance()
                calDespesa.time = despesa.data

                calDespesa.get(Calendar.MONTH) == mesAtual &&
                        calDespesa.get(Calendar.YEAR) == anoAtual &&
                        despesa.tipo == TipoDespesa.DEBITO
            }
        }

    // 4. COMBINE: Cruza os dois fluxos
    val orcamentosComProgresso: StateFlow<List<OrcamentoProgresso>> = combine(
        orcamentosFlow,
        despesasDoMesFlow
    ) { listaOrcamentos, listaDespesas ->

        listaOrcamentos.map { orcamento ->
            // Para cada orçamento, somamos as despesas daquela categoria
            val gastoTotal = listaDespesas
                .filter { it.categoria == orcamento.categoria }
                .sumOf { it.valor }

            val porcentagem = if (orcamento.valorLimite > 0) {
                (gastoTotal / orcamento.valorLimite).toFloat()
            } else 0f

            OrcamentoProgresso(
                categoria = orcamento.categoria,
                limite = orcamento.valorLimite,
                gastoAtual = gastoTotal,
                porcentagem = porcentagem
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    fun salvarOrcamento(categoria: String, valor: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            // Chama a função do repositório para salvar no banco de dados (Room)
            repository.salvarOrcamento(categoria, valor)
        }
    }

    fun excluirOrcamento(categoria: String) {
        viewModelScope.launch {
            try {
                repository.excluirOrcamento(categoria)
            } catch (e: Exception) {
                e.printStackTrace()
                // Se quiser, pode adicionar um Log aqui caso falhe
            }
        }
    }
    fun atualizarOrcamento(categoria: String, novoValor: Double) {
        viewModelScope.launch {
            try {
                repository.atualizarOrcamento(categoria, novoValor)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _filtroAtivo = MutableStateFlow(0)

    fun setFiltro(novoFiltro: Int) {
        _filtroAtivo.value = novoFiltro
    }

    // O progresso dos orçamentos reage ao filtro
    /*@OptIn(ExperimentalCoroutinesApi::class)
    val orcamentosComProgresso = _filtroAtivo.flatMapLatest { filtro ->
        // Aqui o repositório deve calcular o somatório das despesas
        // por categoria dentro do período do filtro
        repository.getOrcamentosComProgresso(filtro)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())*/
}