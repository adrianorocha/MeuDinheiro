package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.DespesasDomain
import kotlinx.coroutines.flow.Flow
import com.meudinheiro.data.TransferenciaAgendada

@Dao
interface ContaSaldoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo)

    @Query("SELECT * FROM contasaldo ORDER BY banco DESC")
    fun obterContaSaldo(): Flow<List<ContaSaldoDomain>>

    @Query("SELECT saldo FROM contasaldo WHERE conta = :conta LIMIT 1")
    suspend fun obterSaldoPorConta(conta: String): Double?

    @Query("DELETE FROM contasaldo WHERE id = :id")
    suspend fun excluirConta(id: Int)

    @Query("UPDATE contasaldo SET saldo = :novoSaldo WHERE conta = :conta")
    suspend fun atualizarSaldo(conta: String, novoSaldo: Double)

    @Query("SELECT * FROM contasaldo")
    suspend fun obterTodasStatic(): List<ContaSaldo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirLista(contas: List<ContaSaldo>)

    @Query("UPDATE contasaldo SET saldo = saldo - :valor WHERE conta = :contaId")
    suspend fun subtrairSaldo(contaId: String, valor: Double)

    @Query("SELECT * FROM contasaldo")
    fun getTodasContas(): Flow<List<ContaSaldo>>

    @Query("DELETE FROM contasaldo")
    suspend fun limparTudo()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(contas: List<ContaSaldo>)

    @Transaction // ESSENCIAL: Ou faz tudo, ou não faz nada!
    suspend fun transferir(origem: String, destino: String, valor: Double): Boolean {
        val linhasDebito = debitar(origem, valor)
        val linhasCredito = creditar(destino, valor)

        // A última linha DEVE ser a lógica que resulta em true/false
        return (linhasDebito > 0 && linhasCredito > 0)
    }
    @Query("UPDATE contasaldo SET saldo = COALESCE(saldo, 0.0) - :valor WHERE TRIM(conta) = TRIM(:contaId)")
    suspend fun debitar(contaId: String, valor: Double): Int
    @Query("UPDATE contasaldo SET saldo = COALESCE(saldo, 0.0) + :valor WHERE TRIM(conta) = TRIM(:contaId)")
    suspend fun creditar(contaId: String, valor: Double): Int
    // Retorna a lista reativa de agendamentos futuros
    @Query("SELECT * FROM transferencias_agendadas WHERE executada = 0 ORDER BY dataAgendada ASC")
    fun obterAgendamentosPendentesFlow(): Flow<List<TransferenciaAgendada>>

    // Deleta um agendamento caso o usuário cancele
    @Query("DELETE FROM transferencias_agendadas WHERE id = :id")
    suspend fun excluirAgendamento(id: Int)

    // Insere o agendamento no banco
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirAgendamento(agendamento: TransferenciaAgendada): Long

    @Query("SELECT * FROM transferencias_agendadas WHERE executada = 0 AND dataAgendada <= :hoje")
    suspend fun obterAgendamentosPendentesSync(hoje: Long): List<TransferenciaAgendada>

    // Aproveite e garanta que você tem esta aqui também para o Worker marcar como feito:
    @Query("UPDATE transferencias_agendadas SET executada = 1 WHERE id = :id")
    suspend fun marcarAgendamentoComoExecutado(id: Int)

    @Transaction
    suspend fun limparTodasAsTabelas() {
        apagarAgendamentos()
        apagarContas()
        apagarCategorias()
        apagarDespesas()
        // Adicione outras tabelas se houver
    }

    @Query("DELETE FROM transferencias_agendadas")
    suspend fun apagarAgendamentos()

    @Query("DELETE FROM contasaldo") // Use o nome exato da sua tabela de contas
    suspend fun apagarContas()

    @Query("DELETE FROM categorias")
    suspend fun apagarCategorias()

    @Query("DELETE FROM despesas")
    suspend fun apagarDespesas()

    @Query("SELECT * FROM transferencias_agendadas WHERE executada = 0 ORDER BY dataAgendada ASC")
    fun obterAgendamentosAtivos(): Flow<List<TransferenciaAgendada>>

    @Query("SELECT SUM(valor) FROM transferencias_agendadas WHERE executada = 0") // Adapte o nome da tabela/coluna se necessário
    suspend fun somarContasPendentes(): Double?

}
