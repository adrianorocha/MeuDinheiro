package com.meudinheiro.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.CategoriaCompra
import com.meudinheiro.data.Compra
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.domain.Financas
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class CartoesViewModel(private val repository: MainRepository) : ViewModel() {

    private val _uiEvent = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiEvent = _uiEvent.asSharedFlow()

    private fun avisarErro(titulo: String, e: Throwable) {
        val msg = if (e is RegraFinanceiraException) e.message else "Algo deu errado. Tente novamente."
        if (e !is RegraFinanceiraException) Log.e("CartoesVM", titulo, e)
        _uiEvent.tryEmit("$titulo | $msg | Erro")
    }

    private val cartaoEmFoco = MutableStateFlow<Int?>(null)

    /** Compras do cartão em foco. Trocar de cartão cancela a coleta anterior (antes vazava coletores). */
    val despesasDoCartao: StateFlow<List<Despesa>> = cartaoEmFoco
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.getDespesasDoGrupoDe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cartoes: StateFlow<List<CartaoComConta>> = repository.getTodosOsCartoes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contasDisponiveis: StateFlow<List<ContaSaldo>> = repository.getTodasContas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun salvarCartao(cartao: Cartao) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.salvarCartao(cartao)
            } catch (e: Exception) {
                avisarErro("Cartão", e)
            }
        }
    }

    fun removerCartao(cartao: CartaoComConta) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.buscarCartaoPorId(cartao.id)?.let { repository.excluirCartao(it) }
            } catch (e: Exception) {
                avisarErro("Excluir cartão", e)
            }
        }
    }

    fun buscarDespesasPorCartao(cartaoId: Int) {
        cartaoEmFoco.value = cartaoId
    }

    fun Despesa.paraCompra(): Compra = Compra(
        id = this.id.toInt(),
        estabelecimento = this.descricao,
        valor = this.valor,
        data = SimpleDateFormat("dd MMM, HH:mm", Locale("pt", "BR")).format(this.data),
        categoria = converterStringParaCategoria(this.categoria)
    )

    fun converterStringParaCategoria(nome: String): CategoriaCompra = when (nome.uppercase()) {
        "ALIMENTAÇÃO" -> CategoriaCompra.ALIMENTACAO
        "TRANSPORTE" -> CategoriaCompra.TRANSPORTE
        "SAÚDE" -> CategoriaCompra.SAUDE
        else -> CategoriaCompra.OUTROS
    }

    private var pagandoId: Int? = null

    /**
     * R7 — paga a fatura do mês de [dataReferencia] (qualquer data dentro do mês de fechamento).
     * O valor é sempre o pendente real da fatura, calculado no repositório (nunca o que a tela exibia).
     */
    fun pagarFatura(cartao: CartaoComConta, dataReferencia: Date) {
        if (pagandoId == cartao.id) return
        pagandoId = cartao.id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ref = Financas.FaturaRef(Financas.mesDe(dataReferencia.time), Financas.anoDe(dataReferencia.time))
                val pago = repository.pagarFatura(cartao.id, ref)
                _uiEvent.tryEmit("Fatura | Fatura paga: R$ %.2f debitados de ${cartao.nomeConta}. | Sucesso".format(pago))
            } catch (e: Exception) {
                avisarErro("Fatura", e)
            } finally {
                pagandoId = null
            }
        }
    }
}
