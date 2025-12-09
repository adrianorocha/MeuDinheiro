package com.meudinheiro.repository

import android.content.Context
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.CategoriaDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import kotlinx.coroutines.flow.Flow

class MainRepository(val context: Context) {
    private val db = AppDatabase.getInstance(context)

    //Despesas
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
    //Saldo Conta
    private val contaSaldoDao = db.contaSaldoDao()

    fun obterContaSaldo(): Flow<List<ContaSaldoDomain>> {
        return contaSaldoDao.obterContaSaldo()
    }

    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo) {
        contaSaldoDao.inserirContaSaldo(contaSaldo)
    }

    suspend fun excluirConta(id: Int): ContaSaldo? {
        return contaSaldoDao.excluirConta(id)
    }
}