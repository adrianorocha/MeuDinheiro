package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class DespesasViewModel(private val repository: MainRepository) : ViewModel() {

    // 1. Estados de Data (Mês/Ano)
    private val calendar = Calendar.getInstance()

    private val _mesSelecionado = MutableStateFlow(calendar.get(Calendar.MONTH))
    val mesSelecionado = _mesSelecionado.asStateFlow()

    private val _anoSelecionado = MutableStateFlow(calendar.get(Calendar.YEAR))
    val anoSelecionado = _anoSelecionado.asStateFlow()

    // 2. Estado de Conta
    private val _contaSelecionada = MutableStateFlow("")

    // 1. O estado do filtro dentro do VM
    private val _filtroAtivo = MutableStateFlow(0) // 0: Este Mês, 1: Mês Passado, 2: Total

    // 3. A MÁGICA: A lista de despesas agora "observa" o filtro e os meses
// 3. A MÁGICA: A lista de despesas agora "observa" o filtro e os meses
// 3. A MÁGICA: Filtro absoluto em memória (À prova de falhas do Room)
    @OptIn(ExperimentalCoroutinesApi::class)
    val despesasFiltradas: StateFlow<List<DespesasDomain>> = combine(
        repository.obterDespesas(), // <--- Usa a sua função que traz TODA a base
        _filtroAtivo,
        _mesSelecionado,
        _anoSelecionado,
        _contaSelecionada
    ) { listaCompleta, filtro, mesDaTela, anoDaTela, contaId ->

        val filtroContaLimpo = contaId.trim()

        listaCompleta.filter { despesa ->
            // Extrai o mês e o ano REAIS direto da data da despesa
            val cal = Calendar.getInstance()
            cal.time = java.util.Date(despesa.data) // Usa a data exata do lançamento

            val despesaMes = cal.get(Calendar.MONTH)
            val despesaAno = cal.get(Calendar.YEAR)

            // Regra 1: Bate com a conta selecionada?
            val mesmaConta = if (filtroContaLimpo.isEmpty()) {
                true // Se não tem conta selecionada, mostra todas
            } else {
                despesa.conta.trim().equals(filtroContaLimpo, ignoreCase = true)
            }

            // Regra 2: Bate com o período selecionado?
            val mesmoPeriodo = when (filtro) {
                0 -> {
                    // ESTE MÊS
                    despesaMes == mesDaTela && despesaAno == anoDaTela
                }

                1 -> {
                    // MÊS PASSADO
                    val mesPassado = if (mesDaTela == 0) 11 else mesDaTela - 1
                    val anoPassado = if (mesDaTela == 0) anoDaTela - 1 else anoDaTela
                    despesaMes == mesPassado && despesaAno == anoPassado
                }

                else -> {
                    // TOTAL
                    true
                }
            }

            // Só passa no filtro quem acertar a conta E o período
            mesmaConta && mesmoPeriodo

        }.sortedByDescending { it.data } // Mantém os mais recentes no topo

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )    // --- AÇÕES ---

    // A função que a MainScreen vai chamar quando o usuário trocar de conta no carrossel
    // Ajustada para String? para não dar conflito (clash) na compilação JVM
    fun setContaSelecionada(idConta: String?) {
        _contaSelecionada.value = idConta?.trim().orEmpty()
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
        viewModelScope.launch(Dispatchers.IO) {
            repository.excluirDespesaComRestituicao(id)
            // Não precisa recarregar nada manualmente, o Flow (obterDespesas) avisa o combine automaticamente
        }
    }

    fun getTotalPorMesEAno(mes: Int, ano: Int): Flow<Double> {
        return repository.getTotalDespesasPorPeriodo(mes, ano)
    }

    fun getDespesaMesAnterior(mesAtual: Int, anoAtual: Int): Flow<Double> {
        // Lógica para retroceder o mês
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, anoAtual)
            set(Calendar.MONTH, mesAtual - 1)
            add(Calendar.MONTH, -1)
        }

        val mesAnterior = calendar.get(Calendar.MONTH) + 1
        val anoAnterior = calendar.get(Calendar.YEAR)

        // O erro do 'it' acontece se você não usar as chaves {} corretamente no map
        return repository.getTotalDespesasPorPeriodo(mesAnterior, anoAnterior)
            .map { valor -> valor ?: 0.0 } // Use 'valor ->' em vez de 'it' para ser mais claro
    }

    fun setMes(mes: Int) {
        _mesSelecionado.value = mes
    }

    // A função que a MainScreen vai chamar
    fun setFiltro(novoFiltro: Int) {
        _filtroAtivo.value = novoFiltro
    }

    fun setDataAtual(mes: Int, ano: Int) {
        _mesSelecionado.value = mes
        _anoSelecionado.value = ano
    }
}