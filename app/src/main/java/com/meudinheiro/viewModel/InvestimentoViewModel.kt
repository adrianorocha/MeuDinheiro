package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.dao.InvestimentoDao
import com.meudinheiro.data.Investimento
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InvestimentoViewModel(private val dao: InvestimentoDao) : ViewModel() {

    // 1. Lista de todos os seus ativos
    val investimentos = dao.getTodosInvestimentos().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 2. O total do patrimônio (O SQLite devolve nulo se a tabela estiver vazia, tratamos aqui)
    val patrimonioTotal = dao.getPatrimonioTotal().map { it ?: 0.0 }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // 3. Soma todos os lucros/prejuízos da lista (O valor em R$ que vai ficar verde ou vermelho)
    val rendimentoTotal = investimentos.map { lista ->
        lista.sumOf { it.rendimentoReal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 4. Calcula a porcentagem geral de crescimento da sua carteira inteira
    val porcentagemTotal = investimentos.map { lista ->
        val totalInvestido = lista.sumOf { it.valorInvestido }
        val totalAtual = lista.sumOf { it.valorAtual }
        if (totalInvestido > 0) ((totalAtual - totalInvestido) / totalInvestido) * 100 else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- FUNÇÕES DE AÇÃO ---

    fun salvarInvestimento(nome: String, tipo: String, valorInvestido: Double, valorAtual: Double) {
        viewModelScope.launch {
            val novoAtivo = Investimento(
                nome = nome,
                tipo = tipo,
                valorInvestido = valorInvestido,
                valorAtual = valorAtual
            )
            dao.inserir(novoAtivo)
        }
    }

    // Usado quando o Bitcoin sobe ou as cotas do MXRF11 rendem!
    fun atualizarValorAtivo(investimento: Investimento, novoValorAtual: Double) {
        viewModelScope.launch {
            dao.atualizar(investimento.copy(valorAtual = novoValorAtual))
        }
    }

    fun excluirInvestimento(investimento: Investimento) {
        viewModelScope.launch {
            dao.deletar(investimento)
        }
    }

    val distribuicaoPorTipo = investimentos.map { lista ->
        val total = lista.sumOf { it.valorAtual }

        // Se não tiver nada investido, retorna lista vazia para não dar divisão por zero
        if (total <= 0.0) return@map emptyList<Pair<String, Double>>()

        // Agrupa por tipo (Cripto, Ações, etc) e calcula o % de cada um
        lista.groupBy { it.tipo }
            .map { (tipo, ativos) ->
                val totalDoTipo = ativos.sumOf { it.valorAtual }
                val percentual = (totalDoTipo / total) * 100
                tipo to percentual
            }
            .sortedByDescending { it.second } // O maior grupo sempre aparece primeiro na barra
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}