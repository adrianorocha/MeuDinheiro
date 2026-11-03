package com.meudinheiro.viewModel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.DespesaDao
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.TransacaoModel
import com.meudinheiro.funcoes.DateUtils
import com.meudinheiro.funcoes.obterCorDaCategoria
import com.meudinheiro.domain.Financas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Date

/**
 * "Transações recentes" agora vem do EXTRATO real (antes lia a tabela `transacoes`, que nenhuma tela
 * alimentava, e a lista ficava sempre vazia). Saídas aparecem negativas, entradas positivas.
 */
class TransacaoViewModel(despesaDao: DespesaDao, contaDao: ContaSaldoDao) : ViewModel() {

    private val contaSelecionada = MutableStateFlow("")

    /** Conta do carrossel: a lista mostra só os movimentos dela. Vazio = todas as contas. */
    fun selecionarConta(conta: String?) {
        contaSelecionada.value = conta?.trim().orEmpty()
    }

    val ultimasTransacoes = combine(despesaDao.obterTodasFlow(), contaDao.getTodasContas(), contaSelecionada) { despesas, contas, conta ->
        val bancoPorConta = contas.associate { it.conta to it.banco }
        Financas.ultimasDaConta(despesas, conta, System.currentTimeMillis())
            .map { d ->
                TransacaoModel(
                    id = d.id,
                    descricao = d.descricao,
                    valor = if (d.tipo == TipoDespesa.CREDITO) d.valor else -d.valor,
                    bancoNome = bancoPorConta[d.conta].orEmpty(),
                    dataHora = DateUtils.formatarData(Date(d.data.time)),
                    categoriaNome = d.categoria,
                    categoriaCor = obterCorDaCategoria(d.categoria)
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList<TransacaoModel>()
    )
}
