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
import kotlin.math.floor
import kotlin.math.roundToInt

data class DashboardFinanceiroState(
    val receitaGlobal: Double = 0.0,
    val despesaGlobal: Double = 0.0,
    val dadosPorConta: Map<String, Pair<Double, Double>> = emptyMap()
)

class ContaSaldoViewModel(private val repository: MainRepository) : ViewModel() {

    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())

    private val _dashboardState = MutableStateFlow(DashboardFinanceiroState())
    val dashboardState = _dashboardState.asStateFlow()

    private var _saldo = mutableStateOf(0.0)
    val saldo: State<Double> get() = _saldo

    // Carrega dados iniciais
    init {
        bancos.value = repository.bancos
        carregarResumoFinanceiro()
    }

    val contaSaldo: LiveData<List<ContaSaldoDomain>> = repository.obterContaSaldo().asLiveData(
        viewModelScope.coroutineContext
    )

    private val _contaSelecionadaId = MutableLiveData<String?>(null)
    val contaSelecionadaId: LiveData<String?> = _contaSelecionadaId

    // --- CARREGAMENTO DE DADOS ---

    fun carregarResumoFinanceiro() {
        viewModelScope.launch(Dispatchers.IO) {
            val (inicio, fim) = repository.getDatesCurrentMonth()
            val listaResumo = repository.obterResumoFinanceiro(inicio, fim)

            var recGlobal = 0.0
            var despGlobal = 0.0
            val mapaContas = mutableMapOf<String, Pair<Double, Double>>()

            listaResumo.forEach { dto ->
                if (dto.tipo == TipoDespesa.CREDITO) recGlobal += dto.valorTotal
                else despGlobal += dto.valorTotal

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

    // --- AÇÕES DE DESPESAS (COM LÓGICA SEGURA) ---

    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Insere
            repository.inserirDespesa(despesa)

            // 2. Recalcula o saldo total da conta consultando todo o histórico (Seguro)
            repository.recalcularSaldoTotal(despesa.conta)

            // 3. Atualiza UI
            carregarResumoFinanceiro()
        }
    }

    /**
     * Lógica Corrigida de Parcelamento
     */
    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val valorTotal = despesa.valor

            // Formata o valor total para exibir na descrição (Ex: "Total: R$ 1.000,00")
            val formatador = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
            val textoTotal = formatador.format(valorTotal)

            // 1. Lógica dos Centavos: Calcula base e diferença
            // Ex: 100 / 3 = 33.33 (base). Sobra 0.01 (diferença).
            val valorParcelaBase = floor((valorTotal / numeroParcelas) * 100) / 100.0
            val totalBase = (valorParcelaBase * 100).roundToInt() * numeroParcelas
            val totalReal = (valorTotal * 100).roundToInt()
            val diferenca = (totalReal - totalBase) / 100.0

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dataSelecionada

            for (i in 1..numeroParcelas) {
                // 2. A última parcela absorve a diferença
                val valorFinal = if (i == numeroParcelas) {
                    valorParcelaBase + diferenca
                } else {
                    valorParcelaBase
                }

                val novaDescricao = "${despesa.descricao} ($i/$numeroParcelas) • Total: $textoTotal"

                val despesaParcelada = despesa.copy(
                    id = 0, // Importante: Zera ID para criar novo registro
                    descricao = novaDescricao,
                    valor = valorFinal,
                    data = calendar.time
                )

                repository.inserirDespesa(despesaParcelada)

                // Avança 1 mês
                calendar.add(Calendar.MONTH, 1)
            }

            // 3. Atualização Segura: Recalcula saldo total baseado no banco
            repository.recalcularSaldoTotal(despesa.conta)
            carregarResumoFinanceiro()
        }
    }

    fun removerDespesa(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val despesa = repository.obterDespesaPorId(id)
            if (despesa != null) {
                repository.excluirDespesa(id)
                repository.recalcularSaldoTotal(despesa.conta)
                carregarResumoFinanceiro()
            }
        }
    }

    fun alternarStatusDespesa(item: DespesasDomain) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Atualiza status
            repository.atualizarStatusPago(item.id.toLong(), !item.pago)

            // 2. Recalcula saldo total da conta
            repository.recalcularSaldoTotal(item.conta)

            // 3. Atualiza UI
            carregarResumoFinanceiro()
        }
    }

    // --- GETTERS E AUXILIARES ---

    fun obterReceitaPorConta(conta: String): Double {
        return _dashboardState.value.dadosPorConta[conta]?.first ?: 0.0
    }

    fun obterDespesaPorConta(conta: String): Double {
        return _dashboardState.value.dadosPorConta[conta]?.second ?: 0.0
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
}