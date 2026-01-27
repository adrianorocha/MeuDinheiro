package com.meudinheiro.viewModel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.BancoDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class ContaSaldoViewModel(private val repository: MainRepository) : ViewModel() {

    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())

    private var _saldo = mutableStateOf(0.0)
    val saldo: State<Double> get() = _saldo

    private var _descricao = mutableStateOf("")

    private val _data = MutableLiveData<Long?>(null)
    val data: LiveData<Long?> = _data

    init {
        bancos.value = repository.bancos
    }

    val contaSaldo: LiveData<List<ContaSaldoDomain>> = repository.obterContaSaldo().asLiveData(
        viewModelScope.coroutineContext
    )

    private val _contaSelecionadaId = MutableLiveData<String?>(null)
    val contaSelecionadaId: LiveData<String?> = _contaSelecionadaId

    /**
     * Adiciona uma despesa na conta informada, sem alterar a seleção atual.
     */
    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1) Insere a despesa
            repository.inserirDespesa(despesa)

            // 2) Calcula novo saldo da conta informada
            val saldoAtual = repository.obterSaldoPorConta(despesa.conta)
            val novoSaldo = when (despesa.tipo) {
                TipoDespesa.DEBITO -> saldoAtual - despesa.valor
                TipoDespesa.CREDITO -> saldoAtual + despesa.valor
            }

            // 3) Atualiza saldo somente dessa conta
            repository.atualizarSaldo(despesa.conta, novoSaldo)
        }
    }

    /**
     * Adiciona despesas parceladas na conta informada, sem alterar a seleção atual.
     */
    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val saldoAtual = repository.obterSaldoPorConta(despesa.conta)
            var saldoTemp = saldoAtual

            val valorParcela = despesa.valor / numeroParcelas

            for (i in 1..numeroParcelas) {
                val dataParcelaCal = Calendar.getInstance().apply {
                    timeInMillis = dataSelecionada
                    add(Calendar.MONTH, i - 1) // Melhor usar MONTH ao invés de 30 dias fixos
                }

                val despesaParcelada = despesa.copy(
                    descricao = "${despesa.descricao} - $i de $numeroParcelas",
                    valor = valorParcela,
                    data = Date(dataParcelaCal.timeInMillis)
                )

                repository.inserirDespesa(despesaParcelada)

                saldoTemp = when (despesa.tipo) {
                    TipoDespesa.DEBITO -> saldoTemp - valorParcela
                    TipoDespesa.CREDITO -> saldoTemp + valorParcela
                }
            }

            repository.atualizarSaldo(despesa.conta, saldoTemp)
        }
    }

    /**
     * Define a conta selecionada (chamada pelo CardSection ao clicar em um cartão).
     */
    fun selecionarConta(contaId: String) {
        _contaSelecionadaId.value = contaId
    }

    fun adicionarContaSaldo(contaSaldo: ContaSaldo) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.inserirContaSaldo(contaSaldo)
        }
    }

    fun removerContaSaldo(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirConta(id)
        }
    }

    fun atualizarSaldo(conta: String, novoSaldo: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.atualizarSaldo(conta, novoSaldo)
        }
    }
}
