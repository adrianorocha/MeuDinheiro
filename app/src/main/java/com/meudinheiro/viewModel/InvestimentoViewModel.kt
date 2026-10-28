package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
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
        Dinheiro.somar(lista.map { it.rendimentoReal })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 4. Calcula a porcentagem geral de crescimento da sua carteira inteira
    val porcentagemTotal = investimentos.map { lista ->
        Financas.rentabilidadePercentual(
            Dinheiro.somar(lista.map { it.valorInvestido }),
            Dinheiro.somar(lista.map { it.valorAtual })
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- FUNÇÕES DE AÇÃO ---

    fun salvarInvestimento(nome: String, tipo: String, valorInvestido: Double, valorAtual: Double) {
        if (nome.isBlank() || valorInvestido < 0 || valorAtual < 0) return
        viewModelScope.launch {
            dao.inserir(
                Investimento(
                    nome = nome.trim(),
                    tipo = tipo,
                    valorInvestido = Dinheiro.arredondar(valorInvestido),
                    valorAtual = Dinheiro.arredondar(valorAtual)
                )
            )
        }
    }

    // Usado quando o Bitcoin sobe ou as cotas do MXRF11 rendem!
    fun atualizarValorAtivo(investimento: Investimento, novoValorAtual: Double) {
        viewModelScope.launch {
            dao.atualizar(investimento.copy(valorAtual = Dinheiro.arredondar(novoValorAtual.coerceAtLeast(0.0))))
        }
    }

    fun excluirInvestimento(investimento: Investimento) {
        viewModelScope.launch {
            dao.deletar(investimento)
        }
    }

    val distribuicaoPorTipo = investimentos.map { lista ->
        val total = Dinheiro.somar(lista.map { it.valorAtual })

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