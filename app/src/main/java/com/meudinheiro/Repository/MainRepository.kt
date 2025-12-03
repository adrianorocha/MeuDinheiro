package com.meudinheiro.Repository

import com.meudinheiro.DAO.DespesasDomain
import com.meudinheiro.DAO.OrcamentoDomain

class MainRepository {
    val items=mutableListOf(
        DespesasDomain(pic = "ic_food", title = "Alimentação", value = 250.00, date = "10/06/2024"),
        DespesasDomain(pic = "ic_transport", title = "Transporte", value = 120.00, date = "11/06/2024"),
        DespesasDomain(pic = "ic_shopping", title = "Compras", value = 300.00, date = "12/06/2024"),
        DespesasDomain(pic = "ic_entertainment", title = "Entretenimento", value = 150.00, date = "13/06/2024"),
        DespesasDomain(pic = "ic_health", title = "Saúde", value = 200.00, date = "14/06/2024")
    )

    val orcamento=mutableListOf(
        OrcamentoDomain(title = "Emprestimo", value = 1500.00, date = "01/06/2024"),
        OrcamentoDomain(title = "Salário", value = 3000.00, date = "05/06/2024"),
        OrcamentoDomain(title = "Financiamento", value = 800.00, date = "10/06/2024")
    )
}