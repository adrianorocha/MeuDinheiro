package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.OrcamentoProgresso
import com.meudinheiro.domain.Financas
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class OrcamentoViewModel(private val repository: MainRepository) : ViewModel() {

    private val orcamentosFlow = repository.obterOrcamentosFlow()

    /**
     * R13 — gasto do mês corrente por categoria (despesas NORMAIS, inclusive compras no cartão, menos
     * estornos). O mês é resolvido a cada emissão, então a tela vira sozinha na virada do mês.
     */
    val orcamentosComProgresso: StateFlow<List<OrcamentoProgresso>> = combine(
        orcamentosFlow,
        repository.todasDespesasFlow
    ) { listaOrcamentos, despesas ->
        val agora = System.currentTimeMillis()
        val mes = Financas.mesDe(agora)
        val ano = Financas.anoDe(agora)
        val inicio = Financas.inicioDoMes(mes, ano)
        val fim = Financas.fimDoMes(mes, ano)

        listaOrcamentos.map { orcamento ->
            val p = Financas.progressoOrcamento(orcamento.categoria, orcamento.valorLimite, despesas, inicio, fim)
            OrcamentoProgresso(
                categoria = orcamento.categoria,
                limite = p.limite,
                gastoAtual = p.gasto,
                porcentagem = p.percentual.toFloat()
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