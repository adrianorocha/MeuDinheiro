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

class ContaSaldoViewModel(private val repository: MainRepository) : ViewModel(){

    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())
    private var _saldo = mutableStateOf(0.0)
    private var _descricao = mutableStateOf("")
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
        selecionarConta(despesa.conta)
        _saldo.value = contaSaldo.value?.find { it.conta == despesa.conta }?.saldo ?: 0.0

        when (despesa.tipo) {
            TipoDespesa.DEBITO -> {
                _saldo.value -= despesa.valor // Subtrai do saldo
            }
            TipoDespesa.CREDITO -> {
                _saldo.value += despesa.valor // Soma ao saldo
            }
        }
        //Atualiza saldo
        atualizarSaldo(despesa.conta, _saldo.value)    }


    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {

        val calendar = Calendar.getInstance().apply {
            timeInMillis = dataSelecionada // Data inicial recebida
        }
        selecionarConta(despesa.conta)
        _saldo.value = contaSaldo.value?.find { it.conta == despesa.conta }?.saldo ?: 0.0
        _descricao.value = despesa.descricao

        // Calcular o valor da parcela
        val valorParcela = despesa.valor / numeroParcelas

        for (i in 1..numeroParcelas) {
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