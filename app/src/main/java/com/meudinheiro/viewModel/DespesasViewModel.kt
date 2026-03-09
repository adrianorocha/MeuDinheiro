package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {

    // 1. Estados de Data (Mês/Ano)
    private val calendar = Calendar.getInstance()

    private val _mesSelecionado = MutableStateFlow(calendar.get(Calendar.MONTH))
    val mesSelecionado = _mesSelecionado.asStateFlow()

    private val _anoSelecionado = MutableStateFlow(calendar.get(Calendar.YEAR))
    val anoSelecionado = _anoSelecionado.asStateFlow()

    // 2. Estado de Conta (Onde estava o erro)
    // Agora temos apenas UMA fonte de verdade para a conta selecionada
    private val _contaFiltro = MutableStateFlow<String?>(null)

    // 3. O Fluxo Unificado e Filtrado
    // Combina: Banco de Dados + Mês + Ano + Conta Selecionada
/*    val despesasFiltradas = combine(
        repository.obterDespesas(), // Flow do Room
        _mesSelecionado,
        _anoSelecionado,
        _contaFiltro
    ) { lista, mes, ano, contaId ->

        // Normalização do filtro (remove espaços extras)
        val filtroContaLimpo = contaId?.trim()

        lista.filter { despesa ->
            val cal = Calendar.getInstance()
            cal.time = Date(despesa.data)

            val mesmoMes = cal.get(Calendar.MONTH) == mes
            val mesmoAno = cal.get(Calendar.YEAR) == ano

            // Lógica de Comparação Segura
            val mesmaConta = if (filtroContaLimpo.isNullOrBlank()) {
                // Se não tiver conta selecionada, retorna TRUE (mostra tudo) ou FALSE (não mostra nada)
                // Para a MainScreen funcionar bem, geralmente deixamos true ou garantimos a seleção na UI.
                true
            } else {
                // Compara ignorando maiúsculas/minúsculas e espaços
                // Ex: "Nubank" bate com "nubank "
                despesa.conta.trim().equals(filtroContaLimpo, ignoreCase = true)
            }

            mesmoMes && mesmoAno && mesmaConta
        }.sortedByDescending { it.data } // Ordena: Mais recentes primeiro

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )*/

    private val _contaSelecionada = MutableStateFlow("")

    // 2. A função que a MainScreen vai chamar quando o usuário trocar de conta no carrossel
    fun setContaSelecionada(idConta: String) {
        _contaSelecionada.value = idConta
    }
    // --- AÇÕES ---

    // Esta função agora atualiza a variável CORRETA (_contaFiltro)
    fun setContaSelecionada(contaId: String?) {
        _contaFiltro.value = contaId
    }

    fun mesAnterior() {
        if (_mesSelecionado.value == 0) {
            _mesSelecionado.value = 11
            _anoSelecionado.value -= 1
        } else {
            _mesSelecionado.value -= 1
        }
    }

    fun proximoMes() {
        if (_mesSelecionado.value == 11) {
            _mesSelecionado.value = 0
            _anoSelecionado.value += 1
        } else {
            _mesSelecionado.value += 1
        }
    }

    fun removerDespesaComRestituicao(id: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.excluirDespesaComRestituicao(id)
            // Não precisa recarregar nada manualmente, o Flow (obterDespesas) avisa o combine automaticamente
        }
    }

    fun getTotalPorMesEAno(mes: Int, ano: Int): Flow<Double> {
        return repository.getTotalDespesasPorPeriodo(mes, ano)
    }

    fun getDespesaMesAnterior(mesAtual: Int, anoAtual: Int): Flow<Double> {
        // Lógica para retroceder o mês
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, anoAtual)
            set(java.util.Calendar.MONTH, mesAtual - 1)
            add(java.util.Calendar.MONTH, -1)
        }

        val mesAnterior = calendar.get(java.util.Calendar.MONTH) + 1
        val anoAnterior = calendar.get(java.util.Calendar.YEAR)

        // O erro do 'it' acontece se você não usar as chaves {} corretamente no map
        return repository.getTotalDespesasPorPeriodo(mesAnterior, anoAnterior)
            .map { valor -> valor ?: 0.0 } // Use 'valor ->' em vez de 'it' para ser mais claro
    }

    fun setMes(mes: Int) {
        _mesSelecionado.value = mes
    }

    // 1. O estado do filtro dentro do VM
    private val _filtroAtivo = MutableStateFlow(0) // 0: Este Mês, 1: Mês Passado, 2: Total

    // 2. A função que a MainScreen vai chamar
    fun setFiltro(novoFiltro: Int) {
        _filtroAtivo.value = novoFiltro
    }

    // 3. A MÁGICA: A lista de despesas agora "observa" o filtro e os meses
    @OptIn(ExperimentalCoroutinesApi::class)
    val despesasFiltradas: StateFlow<List<DespesasDomain>> = combine(
        _filtroAtivo,
        _mesSelecionado,
        _anoSelecionado,
        _contaSelecionada
    ) { filtro, mes, ano, conta ->
        when (filtro) {
            0 -> repository.getDespesasPorMes(mes, ano, conta)
            1 -> repository.getDespesasMesAnterior(mes, ano, conta)
            else -> repository.getTodasDespesas(conta)
        }
    }.flatMapLatest { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<DespesasDomain>() // <--- Dica: já force a tipagem aqui também!
        )
}
