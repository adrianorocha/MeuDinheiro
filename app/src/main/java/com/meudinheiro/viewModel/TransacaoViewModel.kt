package com.meudinheiro.viewModel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.dao.TransacaoDao
import com.meudinheiro.data.Transacao
import com.meudinheiro.data.TransacaoModel
import com.meudinheiro.funcoes.DateUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

class TransacaoViewModel(private val dao: TransacaoDao) : ViewModel() {

    // Transforma a Entity do Banco no Model da UI automaticamente
    val ultimasTransacoes = dao.getUltimasTransacoes().map { lista ->
        lista.map { entity ->
            TransacaoModel(
                id = entity.id,
                descricao = entity.descricao,
                valor = entity.valor,
                bancoNome = entity.bancoNome,
                dataHora = DateUtils.formatarData(Date(entity.timestamp)), // Função de utilitário
                categoriaNome = entity.categoriaNome,
                categoriaCor = Color(android.graphics.Color.parseColor(entity.categoriaCorHex))
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun adicionarGasto(
        descricao: String,
        valor: Double,
        banco: String,
        categoria: String,
        corHex: String
    ) {
        viewModelScope.launch {
            dao.inserir(
                Transacao(
                    descricao = descricao,
                    valor = -valor, // Garante que seja negativo se for gasto
                    bancoNome = banco,
                    categoriaNome = categoria,
                    categoriaCorHex = corHex
                )
            )
        }
    }
}