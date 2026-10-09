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
import com.meudinheiro.data.Meta
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.ResumoDto
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.domain.Financas
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Totais do histórico completo (receitas/despesas REAIS — transferências, aportes e pagamentos de
 * fatura ficam de fora; ver R5). `dadosPorConta[conta] = receitas to despesas`.
 */
data class DashboardFinanceiroState(
    val receitaGlobal: Double = 0.0,
    val despesaGlobal: Double = 0.0,
    /** Parte de [despesaGlobal] já paga e parte ainda a pagar (pendentes, parcelas futuras, cartão em aberto). */
    val despesaPagaGlobal: Double = 0.0,
    val despesaPendenteGlobal: Double = 0.0,
    val dadosPorConta: Map<String, Pair<Double, Double>> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class ContaSaldoViewModel(
    application: Application,
    private val repository: MainRepository
) : AndroidViewModel(application) {

    private val _uiEvent = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiEvent = _uiEvent.asSharedFlow()

    private fun avisarErro(titulo: String, e: Throwable) {
        val msg = if (e is RegraFinanceiraException) e.message else "Algo deu errado. Tente novamente."
        if (e !is RegraFinanceiraException) Log.e("ContaSaldoVM", titulo, e)
        _uiEvent.tryEmit("$titulo | $msg | Erro")
    }

    // ==========================================
    // 1. ESTADOS GLOBAIS DA UI
    // ==========================================
    val bancos = mutableStateOf<List<BancoDomain>>(emptyList())

    /** Reativo: recalcula sozinho a cada mudança no extrato (não precisa mais "carregar" manualmente). */
    val dashboardState: StateFlow<DashboardFinanceiroState> = repository.todasDespesasFlow
        .map { lista ->
            val global = Financas.kpisPeriodo(lista, null, null)
            val porConta = lista.groupBy { it.conta }.mapValues { (_, itens) ->
                Financas.kpisPeriodo(itens, null, null).let { it.receitasRealizadas to it.despesasTotal }
            }
            DashboardFinanceiroState(
                receitaGlobal = global.receitasRealizadas, despesaGlobal = global.despesasTotal,
                despesaPagaGlobal = global.despesasPagas, despesaPendenteGlobal = global.despesasPendentes,
                dadosPorConta = porConta
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardFinanceiroState())

    /** Patrimônio líquido (R14): contas + investimentos + metas − faturas abertas. */
    val patrimonioLiquido: StateFlow<Double> = repository.patrimonioLiquidoFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Previsão do mês (R15), com o saldo real das contas. */
    val previsao: StateFlow<Financas.Previsao?> = repository.previsaoFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Total de contas a pagar até o fim do mês (compat. com telas antigas). */
    val contasAVencer: StateFlow<Double> = previsao
        .map { it?.contasAPagar ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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
        bancos.value = repository.bancos
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.processarRecorrencias() }
                .onFailure { Log.e("ContaSaldoVM", "Falha ao processar recorrências", it) }
            runCatching { repository.recalcularTudo() }
            runCatching { repository.atualizarSnapshotPatrimonial() }
            runCatching { repository.purgarLixeira() } // R31: apaga de vez o que passou de 30 dias
        }
        observarWidget()
        viewModelScope.launch {
            // Sinal vindo de receivers (ex.: "Pagar agora" na notificação): o Room já reemite os Flows.
            repository.atualizacaoSinal.collect { carregarRecorrencias() }
        }
    }

    // ==========================================
    // 3. FLUXOS REATIVOS
    // ==========================================
    val agendamentosAtivos = repository.obterAgendamentosAtivos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agendamentosFiltrados = combine(
        _contaSelecionadaId.asFlow(),
        repository.obterAgendamentosAtivos()
    ) { contaId: String?, agendamentos: List<TransferenciaAgendada> ->
        if (contaId.isNullOrEmpty()) emptyList()
        else agendamentos.filter { it.contaOrigem.trim() == contaId.trim() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPoupado = repository.getTotalPoupado()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Entradas/saídas do período escolhido, reativo a mudanças no extrato (antes só recalculava ao trocar o filtro). */
    val resumoFinanceiro: StateFlow<ResumoDto> = snapshotFlow { filtroAtual }
        .flatMapLatest { filtro ->
            val (inicio, fim) = obterIntervalo(filtro)
            repository.kpisFlow(inicio, fim)
        }
        .map { ResumoDto(it.receitasRealizadas, it.despesasTotal) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResumoDto())

    private val _recorrencias = MutableStateFlow<List<DespesaFixa>>(emptyList())
    val recorrencias = _recorrencias.asStateFlow()

    // ==========================================
    // 4. CARREGAMENTO (compat.)
    // ==========================================
    @Deprecated("Os totais agora são reativos (dashboardState).")
    fun carregarSaldosGlobais() = Unit

    @Deprecated("Os totais agora são reativos (resumoFinanceiro).")
    fun carregarResumoFinanceiro(mes: Int? = null, ano: Int? = null) = Unit

    // ==========================================
    // 5. LANÇAMENTOS (CRUD)
    // ==========================================
    fun adicionarDespesa(despesa: Despesa, modalidade: Financas.Modalidade? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.registrarLancamento(despesa, modalidade)
            } catch (e: Exception) {
                avisarErro("Lançamento", e)
            }
        }
    }

    fun adicionarDespesaParcelada(despesa: Despesa, numeroParcelas: Int, dataSelecionada: Long, modalidade: Financas.Modalidade? = null, parcelaAtual: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.registrarParcelado(despesa.copy(data = Date(dataSelecionada)), numeroParcelas, modalidade, parcelaAtual)
            } catch (e: Exception) {
                avisarErro("Compra parcelada", e)
            }
        }
    }

    fun removerDespesa(item: DespesasDomain) = excluirLancamento(item.id.toLong(), false)

    /** R11/R31 — exclui o lançamento; com [grupoParcelas], todas as parcelas do parcelamento. */
    fun excluirLancamento(id: Long, grupoParcelas: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val n = repository.excluirLancamento(id, grupoParcelas)
                if (n > 0) _uiEvent.tryEmit(
                    "Exclusão | " + (if (n > 1) "$n lançamentos excluídos" else "Lançamento excluído") + ". Ficam 30 dias na Lixeira. | Sucesso"
                )
            } catch (e: Exception) {
                avisarErro("Exclusão", e)
            }
        }
    }

    fun alternarStatusDespesa(item: DespesasDomain) {
        if (item.cartaoId != null && item.cartaoId != 0) {
            _uiEvent.tryEmit("Cartão | Compras do cartão são quitadas pelo pagamento da fatura (aba Cartões). | Erro")
            return
        }
        alternarPago(item.id.toLong(), !item.pago)
    }

    /** R12 — marca como pago/pendente (só lançamentos de conta; compra de cartão é paga pela fatura). */
    fun alternarPago(id: Long, pago: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val d = repository.obterDespesaPorId(id) ?: throw RegraFinanceiraException("Lançamento não encontrado.")
                if (d.cartaoId != null && d.cartaoId != 0) throw RegraFinanceiraException(com.meudinheiro.domain.LancamentoAcoes.MSG_CARTAO_FATURA)
                repository.alternarPago(id, pago)
                _uiEvent.tryEmit("Pagamento | " + (if (pago) "Marcado como pago." else "Marcado como pendente.") + " | Sucesso")
            } catch (e: Exception) {
                avisarErro("Pagamento", e)
            }
        }
    }

    /** R23 — duplica o lançamento para hoje (em aberto). */
    fun duplicarDespesa(item: DespesasDomain) = duplicarLancamento(item.id.toLong())

    fun duplicarLancamento(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.duplicarLancamento(id)
                _uiEvent.tryEmit("Duplicar | Lançamento duplicado para hoje (em aberto). | Sucesso")
            } catch (e: Exception) {
                avisarErro("Duplicar", e)
            }
        }
    }

    /** R23 — cria [n] repetições futuras (em aberto). */
    fun repetirDespesa(item: DespesasDomain, n: Int, intervalo: Int, unidade: com.meudinheiro.domain.Analises.UnidadeRepeticao) =
        repetirLancamento(item.id.toLong(), n, intervalo, unidade)

    fun repetirLancamento(id: Long, n: Int, intervalo: Int, unidade: com.meudinheiro.domain.Analises.UnidadeRepeticao) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val criados = repository.repetirLancamento(id, n, intervalo, unidade)
                _uiEvent.tryEmit("Repetir | $criados lançamentos criados em aberto. | Sucesso")
            } catch (e: Exception) {
                avisarErro("Repetir", e)
            }
        }
    }

    /** R11/R42 — grava a edição completa de um lançamento (conta ou cartão). */
    fun editarLancamento(despesa: Despesa, modalidade: Financas.Modalidade? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.atualizarLancamento(despesa, modalidade)
                _uiEvent.tryEmit("Edição | Lançamento atualizado. | Sucesso")
            } catch (e: Exception) {
                avisarErro("Edição", e)
            }
        }
    }

    suspend fun obterLancamento(id: Long): Despesa? = repository.obterDespesaPorId(id)

    suspend fun antecipaveisDoGrupo(id: Long): List<Despesa> = repository.antecipaveisDoGrupo(id)

    /** R40 — antecipa o pagamento dos lançamentos [ids] (ordem de consumo), com [desconto] opcional. */
    fun anteciparPagamento(ids: List<Long>, valorPago: Double, desconto: Double, data: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = repository.anteciparPagamento(ids, valorPago, desconto, data)
                val pago = com.meudinheiro.funcoes.formatarMoedaBR(valorPago, false)
                _uiEvent.tryEmit(
                    "Antecipação | " + when {
                        r.economia > 0 -> "Pago $pago e abatido ${com.meudinheiro.funcoes.formatarMoedaBR(r.economia, false)} de desconto."
                        r.restante > 0 -> "Pago $pago adiantado. Restam ${com.meudinheiro.funcoes.formatarMoedaBR(r.restante, false)} em aberto."
                        else -> "Pago $pago adiantado."
                    } + " | Sucesso"
                )
            } catch (e: Exception) {
                avisarErro("Antecipação", e)
            }
        }
    }

    /** R42 - iguala o saldo da conta ao do banco (lançamento AJUSTE). */
    fun ajustarSaldoConta(conta: String, saldoReal: Double, observacao: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ajuste = repository.ajustarSaldoConta(conta, saldoReal, observacao)
                _uiEvent.tryEmit(
                    if (ajuste == null) "Saldo já confere com o banco: nenhum ajuste necessário."
                    else "Saldo ajustado: " + (if (ajuste.tipo == com.meudinheiro.data.TipoDespesa.CREDITO) "+" else "-") +
                        com.meudinheiro.funcoes.formatarMoedaBR(ajuste.valor, false) + ". Exclua o lançamento para desfazer."
                )
            } catch (e: Exception) {
                avisarErro("Ajuste de saldo", e)
            }
        }
    }

    fun salvarDespesaRecorrente(despesaBase: Despesa, diaVencimento: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.salvarDespesaFixa(
                    DespesaFixa(
                        descricao = despesaBase.descricao,
                        valor = despesaBase.valor,
                        conta = despesaBase.conta,
                        categoria = despesaBase.categoria,
                        pic = despesaBase.pic,
                        tipo = despesaBase.tipo,
                        diaVencimento = diaVencimento,
                        ultimaDataLancamento = null,
                        cartaoId = despesaBase.cartaoId
                    )
                )
                carregarRecorrencias()
            } catch (e: Exception) {
                avisarErro("Despesa fixa", e)
            }
        }
    }

    /** Nomes para exibir a origem de cada recorrência (conta pelo número, cartão pelo id). */
    val origensRecorrencia: StateFlow<Pair<Map<String, String>, Map<Int, String>>> = combine(
        repository.getTodasContas(), repository.cartoesFlow()
    ) { contas, cartoes ->
        contas.associate { it.conta to it.banco } to
            cartoes.associate { it.id to (it.nome + " ••" + it.finalCartao + if (it.cartaoPrincipalId != null) " (virtual)" else "") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap<String, String>() to emptyMap())

    val contasParaRecorrencia: StateFlow<List<ContaSaldo>> = repository.getTodasContas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cartoesParaRecorrencia: StateFlow<List<com.meudinheiro.data.Cartao>> = repository.cartoesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Troca a origem do pagamento da regra: [cartaoId] (cartão) ou [conta] (débito em conta). */
    fun alterarOrigemRecorrencia(id: Int, conta: String?, cartaoId: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.alterarOrigemRecorrencia(id, conta, cartaoId)
                carregarRecorrencias()
            } catch (e: Exception) {
                avisarErro("Recorrência", e)
            }
        }
    }

    fun carregarRecorrencias() {
        viewModelScope.launch(Dispatchers.IO) { _recorrencias.value = repository.obterTodasRecorrencias() }
    }

    /** R47 — pausa a recorrência; [ate] (ms) é a retomada automática opcional. */
    fun pausarRecorrencia(id: Int, ate: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.pausarRecorrencia(id, ate)
                carregarRecorrencias()
            } catch (e: Exception) {
                avisarErro("Recorrência", e)
            }
        }
    }

    fun retomarRecorrencia(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.retomarRecorrencia(id)
                carregarRecorrencias()
            } catch (e: Exception) {
                avisarErro("Recorrência", e)
            }
        }
    }

    fun cancelarRecorrencia(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirRecorrencia(id)
            carregarRecorrencias()
        }
    }

    // ==========================================
    // 6. CONTAS E TRANSFERÊNCIAS
    // ==========================================
    fun selecionarConta(contaId: String) {
        _contaSelecionadaId.postValue(contaId)
    }

    fun adicionarContaSaldo(contaSaldo: ContaSaldo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.salvarConta(contaSaldo)
            } catch (e: Exception) {
                avisarErro("Nova conta", e)
            }
        }
    }

    fun removerContaSaldo(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.excluirConta(id)
            } catch (e: Exception) {
                avisarErro("Excluir conta", e)
            }
        }
    }

    fun obterReceitaPorConta(conta: String): Double = dashboardState.value.dadosPorConta[conta]?.first ?: 0.0
    fun obterDespesaPorConta(conta: String): Double = dashboardState.value.dadosPorConta[conta]?.second ?: 0.0

    fun alterarFiltro(novoFiltro: FiltroPeriodo) {
        filtroAtual = novoFiltro
    }

    fun transferirValor(contaOrigem: String, contaDestino: String, valor: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.transferirEntreContas(contaOrigem, contaDestino, valor)
                _uiEvent.emit("Sucesso | Transferência realizada com sucesso! | Sucesso")
            } catch (e: Exception) {
                avisarErro("Transferência", e)
            }
        }
    }

    fun agendarTransferencia(origem: String, destino: String, valor: Double, data: Long, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (origem.trim() == destino.trim()) throw RegraFinanceiraException("As contas de origem e destino são iguais.")
                val idGerado = repository.inserirAgendamento(
                    TransferenciaAgendada(
                        contaOrigem = origem, contaDestino = destino, valor = valor,
                        dataAgendada = data, executada = false
                    )
                )
                val delay = (data - System.currentTimeMillis()).coerceAtLeast(0)
                val tarefa = OneTimeWorkRequestBuilder<TransferenciaWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .addTag("transferencia_$idGerado")
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
                WorkManager.getInstance(context).enqueue(tarefa)
                _uiEvent.emit("Agendamento VIP | Sua transferência foi programada com sucesso. | Sucesso")
            } catch (e: Exception) {
                avisarErro("Agendamento", e)
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
                avisarErro("Cancelamento", e)
            }
        }
    }

    // ==========================================
    // 7. WIDGET (saldo real das contas, atualizado a cada mudança)
    // ==========================================
    private fun observarWidget() {
        // O widget lê o banco ao desenhar; aqui só pedimos o redesenho quando algo que ele mostra muda.
        val prefs = UserPreferences(getApplication())
        combine(
            repository.getTodasContas(),
            repository.getTodasMetas(),
            repository.todasDespesasFlow,
            prefs.privateModeFlow
        ) { contas, metas, despesas, privado -> listOf(contas, metas, despesas, privado) }
            .debounce(500)
            .onEach { runCatching { SaldoWidget().updateAll(getApplication<Application>()) } }
            .launchIn(viewModelScope)
    }

    // ==========================================
    // 8. PATRIMÔNIO
    // ==========================================
    val historicoPatrimonial: StateFlow<List<PatrimonioHistorico>> = repository
        .obterHistoricoPatrimonial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun realizarSnapshotPatrimonialAutomatico() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.atualizarSnapshotPatrimonial() }
                .onFailure { Log.e("PATRIMONIO", "Erro ao atualizar snapshot: ${it.message}") }
        }
    }

    @Deprecated("A previsão agora é reativa (previsao).")
    fun calcularPrevisaoDoMes() = Unit
}
