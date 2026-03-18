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
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class CartoesViewModel(private val repository: MainRepository) : ViewModel() {

    private val _despesasDoCartao = MutableStateFlow<List<Despesa>>(emptyList())
    val despesasDoCartao = _despesasDoCartao.asStateFlow()
    // 1. LISTA DE CARTÕES (Flow Reativo)
    // O stateIn converte o Flow do banco em um StateFlow para o Compose
    val cartoes: StateFlow<List<CartaoComConta>> = repository.getTodosOsCartoes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 2. LISTA DE CONTAS (Para o Dropdown de vinculação)
    // Supondo que você já tenha essa função getContas() no seu repository
    val contasDisponiveis: StateFlow<List<ContaSaldo>> = repository.getTodasContas()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 3. FUNÇÃO PARA SALVAR
    fun salvarCartao(cartao: Cartao) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.salvarCartao(cartao)
                // Aqui você poderia disparar um evento para mostrar a PremiumSnackbar de sucesso
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 4. FUNÇÃO PARA DELETAR
    fun removerCartao(cartao: CartaoComConta) {
        viewModelScope.launch(Dispatchers.IO) {
            // Convertemos o CartaoComConta de volta para Cartao para o Room entender
            val cartaoParaDeletar = repository.buscarCartaoPorId(cartao.id)
            cartaoParaDeletar?.let {
                repository.excluirCartao(it)
            }
        }
    }
    fun abaterLimite(id: Int, valor: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Chama o repository para fazer a alteração no banco
                repository.abaterLimiteCartao(id, valor)

                // O carrossel vai atualizar sozinho porque o StateFlow
                // está observando as mudanças na tabela de cartões!
            } catch (e: Exception) {
                Log.e("VM_ERROR", "Erro ao abater limite: ${e.message}")
            }
        }
    }

    fun buscarDespesasPorCartao(cartaoId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getDespesasPorCartao(cartaoId)
                .collect { lista ->
                    _despesasDoCartao.value = lista
                }
        }
    }

    fun Despesa.paraCompra(): Compra {
        return Compra(
            id = this.id.toInt(),
            estabelecimento = this.descricao, // Descrição vira Estabelecimento
            valor = this.valor,
            data = SimpleDateFormat("dd MMM, HH:mm", Locale("pt", "BR")).format(this.data),
            categoria = converterStringParaCategoria(this.categoria) // Converte String para o Objeto Categoria
        )
    }

    // Função auxiliar para definir ícone/cor da categoria
    fun converterStringParaCategoria(nome: String): CategoriaCompra {
        return when (nome.uppercase()) {
            "ALIMENTAÇÃO" -> CategoriaCompra.ALIMENTACAO
            "TRANSPORTE" -> CategoriaCompra.TRANSPORTE
            "SAÚDE" -> CategoriaCompra.SAUDE
            else -> CategoriaCompra.OUTROS
        }
    }

    fun pagarFatura(cartao: CartaoComConta, valor: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Tira o dinheiro da conta bancária vinculada ao cartão
                repository.subtrairSaldo(cartao.contaId, valor)

                // 2. Devolve o limite para o cartão
                repository.estornarLimiteCartao(cartao.id, valor)

                // 3. (Opcional) Você pode criar uma transação de "Pagamento de Fatura"
                // no histórico geral de despesas para registro futuro.

                Log.d("PAGAMENTO", "Fatura de R$ $valor paga com sucesso!")
            } catch (e: Exception) {
                Log.e("VM_ERROR", "Falha ao pagar fatura: ${e.message}")
            }
        }
    }
}