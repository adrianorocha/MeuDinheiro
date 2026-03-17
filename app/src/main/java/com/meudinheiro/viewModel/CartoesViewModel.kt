package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CartoesViewModel(private val repository: MainRepository) : ViewModel() {

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
}