package com.meudinheiro.viewModel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.meudinheiro.componentes.FiltroPeriodo
import com.meudinheiro.componentes.SaldoWidget
import com.meudinheiro.componentes.obterIntervalo
import com.meudinheiro.data.BancoDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.ResumoDto
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.floor
import kotlin.math.roundToInt

// Estado da UI: Contém os totais acumulados (Histórico Completo)
data class DashboardFinanceiroState(
    val receitaGlobal: Double = 0.0,
    val despesaGlobal: Double = 0.0,
    val dadosPorConta: Map<String, Pair<Double, Double>> = emptyMap()
)

class ContaSaldoViewModel(
    application: Application,
    private val repository: MainRepository
) : AndroidViewModel(application) {

    // Lista de bancos para Spinners/Dialogs
    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())

    // Estado principal reativo (Flow)
    private val _dashboardState = MutableStateFlow(DashboardFinanceiroState())
    val dashboardState = _dashboardState.asStateFlow()

    // Propriedade computada para o Card de Resumo Geral (Patrimônio Líquido)
    val saldoPatrimonial: Double
        get() {
            val estado = _dashboardState.value
            return estado.dadosPorConta.values.sumOf { (receita, despesa) -> receita - despesa }
        }

    // Seleção de conta (para navegação ou detalhes)
    private val _contaSelecionadaId = MutableLiveData<String?>(null)
    val contaSelecionadaId: LiveData<String?> = _contaSelecionadaId

    // LiveData direto do Room para a lista de Contas
    val contaSaldo: LiveData<List<ContaSaldoDomain>> = repository.obterContaSaldo().asLiveData(
        viewModelScope.coroutineContext
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.processarRecorrencias() // Checa assinaturas ao abrir
            carregarSaldosGlobais()
        }

        bancos.value = repository.bancos

        carregarDadosIniciaisESincronizarWidget()
    }

    // --- CARREGAMENTO DE DADOS (GLOBAL / ACUMULADO) ---

    fun carregarSaldosGlobais() {
        viewModelScope.launch(Dispatchers.IO) {
            val listaResumoGlobal = repository.obterResumoGlobalPorConta()

            var recAcumulada = 0.0
            var despAcumulada = 0.0
            val mapaContas = mutableMapOf<String, Pair<Double, Double>>()

            listaResumoGlobal.forEach { dto ->
                val valor = dto.valorTotal

                // 1. Soma para o Header (Total Geral Acumulado)
                if (dto.tipo == TipoDespesa.CREDITO) {
                    recAcumulada += valor
                } else {
                    despAcumulada += valor
                }

                // 2. Soma para os Cards (Total por Conta Acumulado)
                val (recConta, despConta) = mapaContas.getOrDefault(dto.conta, 0.0 to 0.0)
                if (dto.tipo == TipoDespesa.CREDITO) {
                    mapaContas[dto.conta] = (recConta + valor) to despConta
                } else {
                    mapaContas[dto.conta] = recConta to (despConta + valor)
                }
            }

            _dashboardState.update {
                DashboardFinanceiroState(
                    receitaGlobal = recAcumulada,
                    despesaGlobal = despAcumulada,
                    dadosPorConta = mapaContas
                )
            }
        }
    }

    fun carregarResumoFinanceiro(mes: Int? = null, ano: Int? = null) {
        carregarSaldosGlobais()
    }

    // --- AÇÕES DE DESPESAS ---

    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.inserirDespesa(despesa)
            repository.recalcularSaldoTotal(despesa.conta)
            carregarSaldosGlobais()
        }
    }

    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val valorTotal = despesa.valor
            val formatador = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
            val textoTotal = formatador.format(valorTotal)

            val valorParcelaBase = floor((valorTotal / numeroParcelas) * 100) / 100.0
            val totalBase = (valorParcelaBase * 100).roundToInt() * numeroParcelas
            val totalReal = (valorTotal * 100).roundToInt()
            val diferenca = (totalReal - totalBase) / 100.0

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dataSelecionada

            for (i in 1..numeroParcelas) {
                val valorFinal = if (i == numeroParcelas) valorParcelaBase + diferenca else valorParcelaBase
                val novaDescricao = "${despesa.descricao} ($i/$numeroParcelas) • Total: $textoTotal"

                val novaDespesa = despesa.copy(
                    id = 0,
                    descricao = novaDescricao,
                    valor = valorFinal,
                    data = calendar.time
                )

                repository.inserirDespesa(novaDespesa)
                calendar.add(Calendar.MONTH, 1)
            }

            repository.recalcularSaldoTotal(despesa.conta)
            carregarSaldosGlobais()
        }
    }

    fun removerDespesa(item: DespesasDomain) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirDespesa(item.id)
            repository.recalcularSaldoTotal(item.conta)
            carregarSaldosGlobais()
        }
    }

    fun alternarStatusDespesa(item: DespesasDomain) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.atualizarStatusPago(item.id.toLong(), !item.pago)
            repository.recalcularSaldoTotal(item.conta)
            carregarSaldosGlobais()
        }
    }

    // --- GETTERS E AUXILIARES DE CONTA ---

    fun selecionarConta(contaId: String) {
        _contaSelecionadaId.postValue(contaId) // postValue é mais seguro se chamado de threads IO por acidente
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

    fun obterReceitaPorConta(conta: String): Double {
        return _dashboardState.value.dadosPorConta[conta]?.first ?: 0.0
    }

    fun obterDespesaPorConta(conta: String): Double {
        return _dashboardState.value.dadosPorConta[conta]?.second ?: 0.0
    }

    fun salvarDespesaRecorrente(despesaBase: Despesa, diaVencimento: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val novaFixa = DespesaFixa(
                descricao = despesaBase.descricao,
                valor = despesaBase.valor,
                conta = despesaBase.conta,
                categoria = despesaBase.categoria,
                pic = despesaBase.pic,
                tipo = despesaBase.tipo,
                diaVencimento = diaVencimento,
                ultimaDataLancamento = null
            )
            repository.salvarDespesaFixa(novaFixa)
            carregarSaldosGlobais()
        }
    }

    private val _recorrencias = MutableStateFlow<List<DespesaFixa>>(emptyList())
    val recorrencias = _recorrencias.asStateFlow()

    fun carregarRecorrencias() {
        viewModelScope.launch(Dispatchers.IO) {
            val lista = repository.obterTodasRecorrencias()
            _recorrencias.value = lista
        }
    }

    fun cancelarRecorrencia(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirRecorrencia(id)
            carregarRecorrencias()
        }
    }

    val totalPoupado = repository.getTotalPoupado()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    var filtroAtual by mutableStateOf(FiltroPeriodo.ESTE_MES)
        private set

    // REVISADO: Substituído o let aninhado por um if/else seguro e limpo
    val resumoFinanceiro = snapshotFlow { filtroAtual }
        .mapLatest { filtro ->
            val (inicio, fim) = obterIntervalo(filtro)
            if (inicio != null && fim != null) {
                repository.obterResumoPorPeriodo(Date(inicio), Date(fim))
            } else {
                repository.obterResumoGlobal()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ResumoDto()
        )

    fun alterarFiltro(novoFiltro: FiltroPeriodo) {
        filtroAtual = novoFiltro
    }

    fun atualizarInformacoesWidget(context: Context, saldo: Double, metaNome: String, metaId: String) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("saldo_atual", saldo.toFloat())
            putString("nome_meta", metaNome)
            putString("id_meta", metaId)
            apply()
        }

        viewModelScope.launch {
            SaldoWidget().updateAll(context)
        }
    }

    private fun carregarDadosIniciaisESincronizarWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resumo = repository.obterResumoGlobal()
                // REVISADO: Removido o elvis operator redundante (assumindo primitivos no DTO)
                val saldoTotal = resumo.entradas - resumo.saidas

                val metaDestaque = repository.obterTodasAsMetasSync().firstOrNull()

                atualizarInformacoesWidget(
                    context = getApplication(),
                    saldo = saldoTotal,
                    metaNome = metaDestaque?.nome ?: "Nenhuma meta ativa",
                    metaId = metaDestaque?.id?.toString() ?: ""
                )
            } catch (e: Exception) {
                Log.e("WidgetSync", "Erro ao sincronizar dados: ${e.message}")
            }
        }
    }
}