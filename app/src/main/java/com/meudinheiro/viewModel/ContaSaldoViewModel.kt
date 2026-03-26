package com.meudinheiro.viewModel

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.meudinheiro.componentes.FiltroPeriodo
import com.meudinheiro.componentes.SaldoWidget
import com.meudinheiro.componentes.obterIntervalo
import com.meudinheiro.data.BancoDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.ResumoDto
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.worker.TransferenciaWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.roundToInt

// Estado da UI: Contém os totais acumulados (Histórico Completo)
data class DashboardFinanceiroState(
    val receitaGlobal: Double = 0.0,
    val despesaGlobal: Double = 0.0,
    val dadosPorConta: Map<String, Pair<Double, Double>> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ContaSaldoViewModel(
    application: Application,
    private val repository: MainRepository
) : AndroidViewModel(application) {

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()
    // ==========================================
    // 1. ESTADOS GLOBAIS DA UI
    // ==========================================
    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())

    private val _dashboardState = MutableStateFlow(DashboardFinanceiroState())
    val dashboardState = _dashboardState.asStateFlow()

    // Propriedade computada para o Card de Resumo Geral
    val saldoPatrimonial: Double
        get() {
            val estado = _dashboardState.value
            return estado.dadosPorConta.values.sumOf { (receita, despesa) -> receita - despesa }
        }
    private val _contasAVencer = MutableStateFlow(0.0)
    val contasAVencer: StateFlow<Double> = _contasAVencer.asStateFlow()


    // Seleção de conta (LiveData para manter compatibilidade com sua MainScreen)
    private val _contaSelecionadaId = MutableLiveData<String?>(null)
    val contaSelecionadaId: LiveData<String?> = _contaSelecionadaId

    val contaSaldo: LiveData<List<ContaSaldoDomain>> = repository.obterContaSaldo().asLiveData(
        viewModelScope.coroutineContext
    )

    var filtroAtual by mutableStateOf(FiltroPeriodo.ESTE_MES)
        private set

    // ==========================================
    // 2. INICIALIZAÇÃO
    // ==========================================
    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.processarRecorrencias()
            carregarSaldosGlobais()
        }
        bancos.value = repository.bancos
        carregarDadosIniciaisESincronizarWidget()
        realizarSnapshotPatrimonialAutomatico()
        calcularPrevisaoDoMes()
        viewModelScope.launch {
            repository.atualizacaoSinal.collect {
                // Quando o sinal chegar, ele roda a sua função de carregar dados de novo
                carregarSaldosGlobais()
            }
        }

    }

    // ==========================================
    // 3. FLUXOS REATIVOS (FLOWS) DE DADOS
    // ==========================================
    val agendamentosAtivos = repository.obterAgendamentosAtivos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // CORREÇÃO: Transformamos o LiveData em Flow para usar no combine com segurança
    val agendamentosFiltrados = combine(
        _contaSelecionadaId.asFlow(),
        repository.obterAgendamentosAtivos()
    ) { contaId: String?, agendamentos: List<TransferenciaAgendada> ->
        if (contaId.isNullOrEmpty()) {
            emptyList()
        } else {
            // Usamos trim() para evitar que um espaço invisível quebre a comparação
            agendamentos.filter { it.contaOrigem.trim() == contaId.trim() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalPoupado = repository.getTotalPoupado()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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

    private val _recorrencias = MutableStateFlow<List<DespesaFixa>>(emptyList())
    val recorrencias = _recorrencias.asStateFlow()

    // ==========================================
    // 4. CARREGAMENTO E CÁLCULOS
    // ==========================================
    fun carregarSaldosGlobais() {
        viewModelScope.launch(Dispatchers.IO) {
            val listaResumoGlobal = repository.obterResumoGlobalPorConta()
            var recAcumulada = 0.0
            var despAcumulada = 0.0
            val mapaContas = mutableMapOf<String, Pair<Double, Double>>()

            listaResumoGlobal.forEach { dto ->
                val valor = dto.valorTotal

                if (dto.tipo == TipoDespesa.CREDITO) recAcumulada += valor
                else despAcumulada += valor

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

    // ==========================================
    // 5. TRANSAÇÕES E DESPESAS (CRUD)
    // ==========================================
    fun adicionarDespesa(despesa: Despesa) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.inserirDespesa(despesa)
            despesa.cartaoId?.let { id ->
                // Aqui chamamos o repositório de cartões
                repository.registrarCompraNoCartao(id, despesa.valor)
            }
            repository.recalcularSaldoTotal(despesa.conta)
            carregarSaldosGlobais()
        }
    }

    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val valorTotal = despesa.valor
            val formatador =
                java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
            val textoTotal = formatador.format(valorTotal)

            val valorParcelaBase = floor((valorTotal / numeroParcelas) * 100) / 100.0
            val totalBase = (valorParcelaBase * 100).roundToInt() * numeroParcelas
            val totalReal = (valorTotal * 100).roundToInt()
            val diferenca = (totalReal - totalBase) / 100.0

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dataSelecionada

            for (i in 1..numeroParcelas) {
                val valorFinal =
                    if (i == numeroParcelas) valorParcelaBase + diferenca else valorParcelaBase
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

    fun carregarRecorrencias() {
        viewModelScope.launch(Dispatchers.IO) {
            _recorrencias.value = repository.obterTodasRecorrencias()
        }
    }

    fun cancelarRecorrencia(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirRecorrencia(id)
            carregarRecorrencias()
        }
    }

    // ==========================================
    // 6. GESTÃO DE CONTAS E TRANSFERÊNCIAS
    // ==========================================
    fun selecionarConta(contaId: String) {
        _contaSelecionadaId.postValue(contaId)
    }

    fun adicionarContaSaldo(contaSaldo: ContaSaldo) {
        viewModelScope.launch(Dispatchers.IO) { repository.inserirContaSaldo(contaSaldo) }
    }

    fun removerContaSaldo(id: Int) {
        viewModelScope.launch(Dispatchers.IO) { repository.excluirConta(id) }
    }

    fun obterReceitaPorConta(conta: String): Double =
        _dashboardState.value.dadosPorConta[conta]?.first ?: 0.0

    fun obterDespesaPorConta(conta: String): Double =
        _dashboardState.value.dadosPorConta[conta]?.second ?: 0.0

    fun alterarFiltro(novoFiltro: FiltroPeriodo) {
        filtroAtual = novoFiltro
    }

    fun transferirValor(
        contaOrigem: String,
        contaDestino: String,
        valor: Double
    ) {
        if (contaOrigem == contaDestino) {
            viewModelScope.launch {
                _uiEvent.emit("Atenção | As contas de origem e destino são iguais. | Erro")
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.transferirEntreContas(contaOrigem, contaDestino, valor)
                carregarSaldosGlobais()
                _uiEvent.emit("Sucesso | Transferência realizada com sucesso! | Sucesso")
            } catch (e: Exception) {
                _uiEvent.emit("Erro na Transferência | ${e.message} | Erro")
                Log.e("Transferencia", "Erro: ${e.message}")
            }
        }
    }
    fun agendarTransferencia(
        origem: String,
        destino: String,
        valor: Double,
        data: Long,
        context: Context // Mantemos o context aqui apenas para o WorkManager
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val agendamento = TransferenciaAgendada(
                    contaOrigem = origem,
                    contaDestino = destino,
                    valor = valor,
                    dataAgendada = data,
                    executada = false
                )

                val idGerado = repository.inserirAgendamento(agendamento)
                val delay = (data - System.currentTimeMillis()).coerceAtLeast(0)

                val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

                val tarefa = OneTimeWorkRequestBuilder<TransferenciaWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .addTag("transferencia_$idGerado")
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueue(tarefa)

                _uiEvent.emit("Agendamento VIP | Sua transferência foi programada com sucesso. | Sucesso")
            } catch (e: Exception) {
                Log.e("AgendamentoWorker", "Erro ao agendar: ${e.message}")
                _uiEvent.emit("Falha no Agendamento | Não conseguimos programar esta operação. | Erro")
            }
        }
    }

    fun cancelarAgendamento(id: Int, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.excluirAgendamento(id)
                WorkManager.getInstance(context).cancelAllWorkByTag("transferencia_$id")
                _uiEvent.emit("Agendamento Cancelado | A operação foi removida do calendário. | Sucesso")
            } catch (e: Exception) {
                Log.e("ErroCancelamento", "Falha ao remover agendamento: ${e.message}")
                _uiEvent.emit("Erro | Não foi possível cancelar o agendamento. | Erro")
            }
        }
    }
    // ==========================================
    // 7. WIDGETS
    // ==========================================
    private fun atualizarInformacoesWidget(
        context: Context,
        saldo: Double,
        metas: List<com.meudinheiro.data.Meta> // 🚀 Agora passamos a LISTA de metas
    ) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putFloat("saldo_atual", saldo.toFloat())

        // 🚀 SALVANDO MÚLTIPLAS METAS (Padrão Novo)
        val metasParaWidget = metas.take(3)
        editor.putInt("quantidade_metas", metasParaWidget.size)

        metasParaWidget.forEachIndexed { index, meta ->
            editor.putString("meta_${index}_nome", meta.nome)
            editor.putString("meta_${index}_id", meta.id.toString())
            editor.putFloat("meta_${index}_valor_meta", (meta.valorObjetivo ?: 1.0).toFloat())
            editor.putFloat("meta_${index}_valor_alcancado", (meta.valorGuardado ?: 0.0).toFloat())
        }

        editor.apply()

        // Manda o widget se redesenhar
        viewModelScope.launch { SaldoWidget().updateAll(context) }
    }

    // E atualize a chamada dentro da função de sincronização:
    private fun carregarDadosIniciaisESincronizarWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resumo = repository.obterResumoGlobal()
                val saldoTotal = resumo.entradas - resumo.saidas
                val todasAsMetas = repository.obterTodasAsMetasSync() // Pega todas

                atualizarInformacoesWidget(
                    context = getApplication(),
                    saldo = saldoTotal,
                    metas = todasAsMetas // Passa a lista completa
                )
            } catch (e: Exception) {
                Log.e("WidgetSync", "Erro: ${e.message}")
            }
        }
    }    fun carregarResumoFinanceiro(mes: Int? = null, ano: Int? = null) {
        carregarSaldosGlobais()
    }

    // Dentro do seu ContaSaldoViewModel
    val historicoPatrimonial: StateFlow<List<PatrimonioHistorico>> = repository
        .obterHistoricoPatrimonial() // Isso precisa retornar Flow<List<...>>
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun realizarSnapshotPatrimonialAutomatico() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. SEGURANÇA MÁXIMA: Verificar se o repositório está pronto
                // Se as contas ainda não carregaram, não fazemos nada
                val contasAtuais = repository.obterTodasStatic() ?: emptyList()
                if (contasAtuais.isEmpty()) {
                    Log.d("PATRIMONIO", "Cofre vazio ou ainda carregando. Snapshot cancelado.")
                    return@launch
                }

                val totalPatrimonio = contasAtuais.sumOf { it.saldo }

                // 2. DATA SEGURA: Formatação robusta do mês
                val sdf = SimpleDateFormat("MMM", Locale("pt", "BR"))
                val mesAtual = sdf.format(Date()).uppercase().replace(".", "").trim()

                // 3. CONSULTA DIRETA AO BANCO (Evita o NullPointerException do StateFlow)
                // Em vez de usar historicoPatrimonial.value (que pode ser null ou estar vazio no init),
                // perguntamos ao banco se JÁ EXISTE um registro para este mês.
                val jaExisteNoBanco = repository.verificarSnapshotMes(mesAtual)

                // 4. LÓGICA DE DECISÃO: "E se não tiver valor?"
                // Agora salvamos mesmo que o valor seja 0, desde que não exista o registro.
                // Só não salvamos se o patrimônio for negativo (opcional) ou se já existir.
                if (!jaExisteNoBanco) {
                    val novoSnapshot = PatrimonioHistorico(
                        dataMillis = System.currentTimeMillis(),
                        valorTotal = totalPatrimonio,
                        mesReferencia = mesAtual
                    )

                    repository.salvarSnapshotPatrimonial(novoSnapshot)
                    Log.d(
                        "PATRIMONIO",
                        "✅ Snapshot VIP de $mesAtual registrado: R$ $totalPatrimonio"
                    )
                } else {
                    Log.d("PATRIMONIO", "Ronda concluída: Snapshot de $mesAtual já está no cofre.")
                }

            } catch (e: Exception) {
                // Protege o app de fechar se o banco de dados estiver ocupado
                Log.e("PATRIMONIO", "Erro no motor de snapshot: ${e.message}")
            }
        }
    }

    // 2. A Função que calcula a previsão
    fun calcularPrevisaoDoMes() {
        viewModelScope.launch(Dispatchers.IO) {
            val pendentes = repository.obterTotalPendentes()
            _contasAVencer.value = pendentes
        }
    }


}