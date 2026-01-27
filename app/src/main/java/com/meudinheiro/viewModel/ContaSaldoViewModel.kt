package com.meudinheiro.viewModel

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
import androidx.compose.runtime.State
import java.util.Calendar
import java.util.Date

class ContaSaldoViewModel(private val repository: MainRepository) : ViewModel(){

    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())
    private var _saldo = mutableStateOf(0.0)
    private var _descricao = mutableStateOf("")

    private val _data = MutableLiveData<Long?>(null)
    val data: LiveData<Long?> = _data


    val saldo: State<Double> get() = _saldo

    init {
        // Carregue os bancos do repositório
        bancos.value = repository.bancos
    }
    val contaSaldo : LiveData<List<ContaSaldoDomain>> = repository.obterContaSaldo().asLiveData(
        viewModelScope.coroutineContext
    )
    private val _contaSelecionadaId = MutableLiveData<String?>(null)
    val contaSelecionadaId: LiveData<String?> = _contaSelecionadaId

    // Função para adicionar despesas e atualizar o saldo
    fun adicionarDespesa(despesa: Despesa) {
        _contaSelecionadaId.value = despesa.conta

        viewModelScope.launch(Dispatchers.IO) {
            // 1) Insere a despesa na conta informada
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

    /*fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {

        val calendar = Calendar.getInstance().apply {
            timeInMillis = dataSelecionada // Data inicial recebida
        }
        selecionarConta(despesa.conta)
        _saldo.value = contaSaldo.value?.find { it.conta == despesa.conta }?.saldo ?: 0.0
        _descricao.value = despesa.descricao
        _data.value = dataSelecionada
        // Calcular o valor da parcela
        val valorParcela = despesa.valor / numeroParcelas

        for (i in 1..numeroParcelas) {
            calendar.add(Calendar.DAY_OF_MONTH, 30 * (i - 1)) // Adiciona 30 dias para cada parcela
            _data.value = calendar.time.time

            // Ajustar o saldo e persistir a nova despesa com suas informações
            _descricao.value = "${despesa.descricao} - $i de ${numeroParcelas}"

            when (despesa.tipo) {
                TipoDespesa.DEBITO -> {
                    _saldo.value -= valorParcela
                }
                TipoDespesa.CREDITO -> {
                    _saldo.value += valorParcela
                }
            }

            // Aqui você pode persistir ou processar cada parcela
            viewModelScope.launch(Dispatchers.IO) {
                repository.inserirDespesa(despesa)
            }
        }

        // Atualizar o saldo no banco
        atualizarSaldo(despesa.conta, _saldo.value)
    }*/

    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {
        _contaSelecionadaId.value = despesa.conta

        viewModelScope.launch(Dispatchers.IO) {
            val saldoAtual = repository.obterSaldoPorConta(despesa.conta)
            var saldoTemp = saldoAtual

            val valorParcela = despesa.valor / numeroParcelas

            for (i in 1..numeroParcelas) {
                val dataParcelaCal = Calendar.getInstance().apply {
                    timeInMillis = dataSelecionada
                    add(Calendar.DAY_OF_MONTH, 30 * (i - 1))
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
        viewModelScope.launch( Dispatchers.IO){
            repository.atualizarSaldo(conta, novoSaldo)
        }

    }
}