package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meudinheiro.data.Categoria
import com.meudinheiro.data.CategoriaDomain
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriaViewModel(private val repository: MainRepository) : ViewModel() {

    // Observa as categorias do banco. O 'stateIn' converte o Flow para um Estado que a UI entende.
    val categorias: StateFlow<List<CategoriaDomain>> = repository.obterCategoriasCustom()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun adicionarCategoria(nome: String, icone: String) {
        viewModelScope.launch {
            // Cria a entidade para o banco
            val novaCategoria = Categoria(nome = nome, pic = icone)
            repository.salvarCategoria(novaCategoria)
        }
    }

    fun excluirCategoria(categoria: CategoriaDomain) {
        viewModelScope.launch {
            // Como seu CategoriaDomain só tem 'title' e não ID, deletamos pelo nome
            repository.excluirCategoriaPorNome(categoria.title)
        }
    }
}