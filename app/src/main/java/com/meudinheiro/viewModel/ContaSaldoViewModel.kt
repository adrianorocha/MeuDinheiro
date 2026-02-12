package com.meudinheiro.viewModel

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.flatMapLatest
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
    application: Application, // <-- Recebe o application aqui
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

    /**
     * Busca TODAS as despesas do banco, sem filtro de mês.
     * Atualiza o Header e os Cards de Conta com o saldo real.
     */
    fun carregarSaldosGlobais() {
        viewModelScope.launch(Dispatchers.IO) {
            // Certifique-se que seu Repository chama o DAO: SELECT ... GROUP BY conta, tipo
            val listaResumoGlobal = repository.obterResumoGlobalPorConta()

            var recAcumulada = 0.0
            var despAcumulada = 0.0
            val mapaContas = mutableMapOf<String, Pair<Double, Double>>()

            listaResumoGlobal.forEach { dto ->
                // Tratamento seguro de nulos
                val valor = dto.valorTotal // Se for DTO com Double (não nulo), ok. Se for Double?, use ?: 0.0

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

            // Atualiza o StateFlow de forma atômica
            _dashboardState.update {
                DashboardFinanceiroState(
                    receitaGlobal = recAcumulada,
                    despesaGlobal = despAcumulada,
                    dadosPorConta = mapaContas
                )
            }
        }
    }

    // Mantido para compatibilidade, mas apenas chama o Global
    // Se no futuro quiser ver "Apenas Mês", altere aqui.
    fun carregarResumoFinanceiro(mes: Int? = null, ano: Int? = null) {
        carregarSaldosGlobais()
    }

    // --- AÇÕES DE DESPESAS ---

    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.inserirDespesa(despesa)
            repository.recalcularSaldoTotal(despesa.conta)
            carregarSaldosGlobais() // Atualiza UI
        }
    }

    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val valorTotal = despesa.valor
            val formatador = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
            val textoTotal = formatador.format(valorTotal)

            // Lógica de Centavos
            val valorParcelaBase = floor((valorTotal / numeroParcelas) * 100) / 100.0
            val totalBase = (valorParcelaBase * 100).roundToInt() * numeroParcelas
            val totalReal = (valorTotal * 100).roundToInt()
            val diferenca = (totalReal - totalBase) / 100.0

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dataSelecionada

            for (i in 1..numeroParcelas) {
                // Última parcela absorve a diferença de centavos
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
            carregarSaldosGlobais() // Atualiza UI
        }
    }

    fun removerDespesa(despesas: DespesasDomain) {
        viewModelScope.launch(Dispatchers.IO) {
            val despesa = repository.obterDespesaPorId(despesas.id)
            //if (despesa != null) {
                repository.excluirDespesa(despesas.id)
                repository.recalcularSaldoTotal(despesas.conta)
                carregarSaldosGlobais() // Atualiza UI
            //}
        }
    }

    fun alternarStatusDespesa(item: DespesasDomain) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.atualizarStatusPago(item.id.toLong(), !item.pago)
            repository.recalcularSaldoTotal(item.conta)
            carregarSaldosGlobais() // Atualiza UI
        }
    }

    // --- GETTERS E AUXILIARES DE CONTA ---

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
            repository.excluirConta(id) // O Repository deve lidar com a exclusão em cascata se necessário
        }
    }

    // Getters rápidos para UI (opcional, já que temos o dashboardState exposto)
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
                ultimaDataLancamento = null // Nunca lançada
            )

            // Salva e já tenta lançar a deste mês se o dia já chegou
            repository.salvarDespesaFixa(novaFixa)

            // Atualiza a UI
            carregarSaldosGlobais()
        }
    }

    // Estado para a lista de assinaturas/fixas
    private val _recorrencias = MutableStateFlow<List<DespesaFixa>>(emptyList())
    val recorrencias = _recorrencias.asStateFlow()

    // Carrega a lista (Chame isso quando abrir a tela de gerenciamento)
    fun carregarRecorrencias() {
        viewModelScope.launch(Dispatchers.IO) {
            val lista = repository.obterTodasRecorrencias() // Certifique-se que seu Repo tem essa função chamando o DAO
            _recorrencias.value = lista
        }
    }

    // Cancela a assinatura (Exclui da tabela despesas_fixas)
    fun cancelarRecorrencia(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirRecorrencia(id) // Certifique-se que seu Repo tem essa função
            carregarRecorrencias() // Atualiza a lista
        }
    }

    val totalPoupado = repository.getTotalPoupado()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    var filtroAtual by mutableStateOf(FiltroPeriodo.ESTE_MES)
        private set

    // Este Flow vai reagir sempre que o filtro mudar
    val resumoFinanceiro = snapshotFlow { filtroAtual }
        .mapLatest { filtro ->
            val intervalo = obterIntervalo(filtro)
            val (inicio, fim) = obterIntervalo(filtro)
            inicio?.let { i ->
                fim?.let { f ->
                    repository.obterResumoPorPeriodo(java.util.Date(i), java.util.Date(f))
                }
            } ?: repository.obterResumoGlobal()
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

        // Notifica o Glance para redesenhar o widget
        viewModelScope.launch {
            SaldoWidget().updateAll(context)
        }
    }

    private fun carregarDadosIniciaisESincronizarWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resumo = repository.obterResumoGlobal()
                val saldoTotal = (resumo.entradas- resumo.saidas) ?: 0.0

                // 2. Busca a meta mais relevante (ex: a que vence primeiro ou a mais próxima de 100%)
                val metaDestaque = repository.obterTodasAsMetasSync().firstOrNull()

                // 3. Dispara a atualização para o "mundo externo" (SharedPreferences + Glance)
                atualizarInformacoesWidget(
                    context = getApplication(), // Necessário ser AndroidViewModel para ter o contexto
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