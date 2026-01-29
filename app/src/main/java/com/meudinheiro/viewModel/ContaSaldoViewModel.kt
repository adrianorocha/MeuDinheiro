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
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

data class DashboardFinanceiroState(
    val receitaGlobal: Double = 0.0,
    val despesaGlobal: Double = 0.0,
    // Mapa: Chave = NomeDaConta -> Valor = Par(Receita, Despesa)
    val dadosPorConta: Map<String, Pair<Double, Double>> = emptyMap()
)
class ContaSaldoViewModel(private val repository: MainRepository) : ViewModel() {

    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())

    private val _dashboardState = MutableStateFlow(DashboardFinanceiroState())
    val dashboardState = _dashboardState.asStateFlow()

    private var _saldo = mutableStateOf(0.0)
    val saldo: State<Double> get() = _saldo

    private var _descricao = mutableStateOf("")

    private val _data = MutableLiveData<Long?>(null)
    val data: LiveData<Long?> = _data

    init {
        bancos.value = repository.bancos
        carregarResumoFinanceiro()
    }

    val contaSaldo: LiveData<List<ContaSaldoDomain>> = repository.obterContaSaldo().asLiveData(
        viewModelScope.coroutineContext
    )

    private val _contaSelecionadaId = MutableLiveData<String?>(null)
    val contaSelecionadaId: LiveData<String?> = _contaSelecionadaId

    fun carregarResumoFinanceiro() {
        viewModelScope.launch(Dispatchers.IO) {
            // Define o período: Mês atual
            val (inicio, fim) = repository.getDatesCurrentMonth()

            val listaResumo = repository.obterResumoFinanceiro(inicio, fim)

            var recGlobal = 0.0
            var despGlobal = 0.0
            val mapaContas = mutableMapOf<String, Pair<Double, Double>>()

            // Consolida os dados em memória (extremamente rápido)
            listaResumo.forEach { dto ->
                // Soma nos globais
                if (dto.tipo == TipoDespesa.CREDITO) recGlobal += dto.valorTotal
                else despGlobal += dto.valorTotal

                // Soma por conta
                val (recAtual, despAtual) = mapaContas.getOrDefault(dto.conta, 0.0 to 0.0)
                if (dto.tipo == TipoDespesa.CREDITO) {
                    mapaContas[dto.conta] = (recAtual + dto.valorTotal) to despAtual
                } else {
                    mapaContas[dto.conta] = recAtual to (despAtual + dto.valorTotal)
                }
            }

            _dashboardState.value = DashboardFinanceiroState(
                receitaGlobal = recGlobal,
                despesaGlobal = despGlobal,
                dadosPorConta = mapaContas
            )
        }
    }

    fun obterReceitaPorConta(conta: String): Double {
        return _dashboardState.value.dadosPorConta[conta]?.first ?: 0.0
    }

    fun obterDespesaPorConta(conta: String): Double {
        return _dashboardState.value.dadosPorConta[conta]?.second ?: 0.0
    }

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
                TipoDespesa.DEBITO -> saldoAtual - 0
//                TipoDespesa.DEBITO -> saldoAtual - despesa.valor
                TipoDespesa.CREDITO -> saldoAtual + despesa.valor
            }

            // 3) Atualiza saldo somente dessa conta
            repository.atualizarSaldo(despesa.conta, novoSaldo)
            carregarResumoFinanceiro()
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
                    TipoDespesa.DEBITO -> saldoTemp - 0
//                    TipoDespesa.DEBITO -> saldoTemp - valorParcela
                    TipoDespesa.CREDITO -> saldoTemp + valorParcela
                }
            }

            repository.atualizarSaldo(despesa.conta, saldoTemp)
            carregarResumoFinanceiro()
        }
    }

    fun alternarStatusDespesa(item: DespesasDomain) {
        viewModelScope.launch(Dispatchers.IO) {
            val novoStatus = !item.pago // Inverte o status atual

            // 1. Atualiza o status usando o ID que vem do Domain
            repository.atualizarStatusPago(item.id.toLong(), novoStatus)

            // 2. Busca o saldo atual da conta
            val saldoAtual = repository.obterSaldoPorConta(item.conta)

            // 3. Lógica de SOMA ou SUBTRAÇÃO
            val novoSaldo = if (novoStatus) {
                // Se virou "Pago", tira do saldo
                saldoAtual - item.valor
            } else {
                // Se desmarcou (estorno), devolve ao saldo
                saldoAtual + item.valor
            }

            // 4. Atualiza o saldo no banco
            repository.atualizarSaldo(item.conta, novoSaldo)

            // 5. Atualiza a UI
            carregarResumoFinanceiro()
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

}
