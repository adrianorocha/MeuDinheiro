package com.meudinheiro.repository

import android.content.Context
import androidx.room.withTransaction
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.BancoDomain
import com.meudinheiro.data.CategoriaDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import kotlinx.coroutines.flow.Flow

class MainRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    // DAOs
    private val despesaDao = db.despesaDao()
    private val contaSaldoDao = db.contaSaldoDao()

    /* ======================= DESPESAS ======================= */

    suspend fun inserirDespesa(despesa: Despesa) {
        despesaDao.inserirDespesa(despesa)
    }

    fun obterDespesas(): Flow<List<DespesasDomain>> {
        return despesaDao.obterDespesas()
    }

    suspend fun obterDespesaPorId(id: Int): Despesa? {
        return despesaDao.obterDespesaPorId(id)
    }

    suspend fun obterDespesasPorConta(contaId: String): List<DespesasDomain> {
        return despesaDao.obterDespesasPorConta(contaId)
    }

    suspend fun excluirDespesa(id: Int) {
        despesaDao.excluirDespesa(id)
    }

    /**
     * Exclui a despesa e ajusta o saldo da conta devolvendo/removendo o valor.
     * Tudo feito em transação para não deixar dados inconsistentes.
     */
    suspend fun excluirDespesaComRestituicao(id: Int) {
        db.withTransaction {
            // 1. Buscar a despesa
            val despesa = despesaDao.obterDespesaPorId(id) ?: return@withTransaction

            // 2. Obter saldo atual (tratando possível null do DAO)
            val saldoAtual = contaSaldoDao.obterSaldoPorConta(despesa.conta) ?: 0.0

            // 3. Calcular novo saldo
            val novoSaldo = when (despesa.tipo) {
                TipoDespesa.DEBITO -> saldoAtual + despesa.valor   // devolve o débito
                TipoDespesa.CREDITO -> saldoAtual - despesa.valor  // remove o crédito
                else -> saldoAtual                                  // fallback se tiver outro tipo
            }

            // 4. Atualizar saldo da conta
            contaSaldoDao.atualizarSaldo(despesa.conta, novoSaldo)

            // 5. Excluir a despesa
            despesaDao.excluirDespesa(id)
        }
    }

    /* ======================= CATEGORIAS / BANCOS (IN-MEMORY) ======================= */

    // Listas imutáveis — você não precisa alterá-las em tempo de execução
    val categorias: List<CategoriaDomain> = listOf(
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

    val bancos: List<BancoDomain> = listOf(
        BancoDomain(id = 1, nome = "Banco do Brasil"),
        BancoDomain(id = 2, nome = "Bradesco"),
        BancoDomain(id = 3, nome = "Santander"),
        BancoDomain(id = 4, nome = "Caixa Econômica"),
        BancoDomain(id = 5, nome = "Itaú"),
        BancoDomain(id = 6, nome = "HSBC"),
        BancoDomain(id = 7, nome = "Nubank"),
        BancoDomain(id = 8, nome = "C6"),
        BancoDomain(id = 9, nome = "MercadoPago"),
        BancoDomain(id = 10, nome = "Sicoob"),
        BancoDomain(id = 11, nome = "Banco Original"),
        BancoDomain(id = 12, nome = "Banco Pan"),
        BancoDomain(id = 13, nome = "Banco do Nordeste"),
        BancoDomain(id = 14, nome = "Banco Inter"),
        BancoDomain(id = 15, nome = "Banco Itaú BBA"),
        BancoDomain(id = 16, nome = "Banco BMG")
    )

    fun getPicCategoria(titulo: String): String {
        val categoria = categorias.find { it.title == titulo }
        return categoria?.pic ?: "default_pic"
    }

    /* ======================= SALDO DE CONTAS ======================= */

    fun obterContaSaldo(): Flow<List<ContaSaldoDomain>> {
        return contaSaldoDao.obterContaSaldo()
    }

    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo) {
        contaSaldoDao.inserirContaSaldo(contaSaldo)
    }

    /**
     * Wrapper para garantir que, se não existir saldo para a conta, volte 0.0
     */
    suspend fun obterSaldoPorConta(conta: String): Double {
        return contaSaldoDao.obterSaldoPorConta(conta) ?: 0.0
    }

    suspend fun excluirConta(id: Int) {
        contaSaldoDao.excluirConta(id)
    }

    suspend fun atualizarSaldo(conta: String, novoSaldo: Double) {
        contaSaldoDao.atualizarSaldo(conta, novoSaldo)
    }
}
