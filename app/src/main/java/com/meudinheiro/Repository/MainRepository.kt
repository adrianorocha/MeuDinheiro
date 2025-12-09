package com.meudinheiro.Repository

import android.content.Context
import com.meudinheiro.DAO.CategoriaDomain
import com.meudinheiro.DAO.DespesasDomain
import com.meudinheiro.DAO.OrcamentoDomain
import com.meudinheiro.Data.AppDatabase
import com.meudinheiro.Data.Despesa
import kotlinx.coroutines.flow.Flow

class MainRepository(val context: Context) {
    private val db = AppDatabase.getInstance(context)

    private val despesaDao = db.despesaDao()

    suspend fun inserirDespesa(despesa: Despesa) {
        despesaDao.inserirDespesa(despesa)
    }

    fun obterDespesas(): Flow<List<DespesasDomain>> {
        return despesaDao.obterDespesas()
    }

    suspend fun excluirDespesa(id: Int) {
        despesaDao.excluirDespesa(id)
    }

    fun getPicCategoria(titulo: String): String {
        val categoria = categorias.find { it.title == titulo }
        return categoria?.pic ?: "default_pic"
    }

    val categorias=mutableListOf(
        CategoriaDomain(pic = "fuel", title = "Combustível"),
        CategoriaDomain(pic = "restaurant", title = "Alimentação"),
        CategoriaDomain(pic = "transport", title = "Transporte"),
        CategoriaDomain(pic = "shopping", title = "Compras"),
        CategoriaDomain(pic = "cinema", title = "Cinema"),
        CategoriaDomain(pic = "health", title = "Saúde"),
        CategoriaDomain(pic = "education", title = "Educação"),
        CategoriaDomain(pic = "salary", title = "Salário"),
        CategoriaDomain(pic = "repair_car", title = "Oficina"),
        CategoriaDomain(pic = "supermarket", title = "Supermercado"),
        CategoriaDomain(pic = "gym", title = "Academia"),
        CategoriaDomain(pic = "games", title = "Jogos"),
        CategoriaDomain(pic = "drink", title = "Bebidas"),
        CategoriaDomain(pic = "lunch", title = "Lanche")
    )
    val items=mutableListOf(
        DespesasDomain(id = 1, pic = "ic_food", descricao = "Alimentação", valor = 250.00, data = "10/06/2024"),
        DespesasDomain(id = 2, pic = "ic_transport", descricao = "Transporte", valor = 120.00, data = "11/06/2024"),
        DespesasDomain(id = 3 , pic = "ic_shopping", descricao = "Compras", valor = 300.00, data = "12/06/2024"),
        DespesasDomain(id = 4, pic = "ic_entertainment", descricao = "Entretenimento", valor = 150.00, data = "13/06/2024"),
        DespesasDomain(id = 5, pic = "ic_health", descricao = "Saúde", valor = 200.00, data = "14/06/2024")
    )

    val orcamento=mutableListOf(
        OrcamentoDomain(title = "Emprestimo", value = 1500.00, date = "01/06/2024"),
        OrcamentoDomain(title = "Salário", value = 3000.00, date = "05/06/2024"),
        OrcamentoDomain(title = "Financiamento", value = 800.00, date = "10/06/2024")
    )

}