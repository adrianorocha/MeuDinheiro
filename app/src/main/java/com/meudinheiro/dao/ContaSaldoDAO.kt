package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.TransferenciaAgendada
import kotlinx.coroutines.flow.Flow

@Dao
interface ContaSaldoDao {

    // @Upsert (e não REPLACE): REPLACE apaga a linha antes de reinserir e o ON DELETE CASCADE
    // dos cartões vinculados destruiria todos os cartões da conta a cada edição.
    @Upsert
    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo): Long

    @Query("SELECT * FROM contasaldo ORDER BY banco DESC")
    fun obterContaSaldo(): Flow<List<ContaSaldoDomain>>

    @Query("SELECT * FROM contasaldo")
    fun getTodasContas(): Flow<List<ContaSaldo>>

    @Query("SELECT * FROM contasaldo")
    suspend fun obterTodasStatic(): List<ContaSaldo>

    @Query("SELECT * FROM contasaldo WHERE id = :id LIMIT 1")
    suspend fun obterPorId(id: Int): ContaSaldo?

    @Query("SELECT * FROM contasaldo WHERE TRIM(conta) = TRIM(:conta) LIMIT 1")
    suspend fun obterPorNumero(conta: String): ContaSaldo?

    @Query("SELECT saldo FROM contasaldo WHERE conta = :conta LIMIT 1")
    suspend fun obterSaldoPorConta(conta: String): Double?

    /** Cache do saldo derivado do extrato (R3). Nunca usar para "mexer" no saldo diretamente. */
    @Query("UPDATE contasaldo SET saldo = :novoSaldo WHERE conta = :conta")
    suspend fun atualizarSaldo(conta: String, novoSaldo: Double)

    @Query("DELETE FROM contasaldo WHERE id = :id")
    suspend fun excluirConta(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(contas: List<ContaSaldo>)

    @Query("DELETE FROM contasaldo")
    suspend fun limparTudo()

    // ------------------------------------------------------ transferências agendadas

    @Query("SELECT * FROM transferencias_agendadas WHERE executada = 0 ORDER BY dataAgendada ASC")
    fun obterAgendamentosAtivos(): Flow<List<TransferenciaAgendada>>

    @Query("SELECT * FROM transferencias_agendadas")
    suspend fun obterAgendamentosStatic(): List<TransferenciaAgendada>

    @Query("SELECT * FROM transferencias_agendadas WHERE id = :id LIMIT 1")
    suspend fun obterAgendamento(id: Int): TransferenciaAgendada?

    @Query("DELETE FROM transferencias_agendadas WHERE id = :id")
    suspend fun excluirAgendamento(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirAgendamento(agendamento: TransferenciaAgendada): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirAgendamentos(agendamentos: List<TransferenciaAgendada>)

    @Query("SELECT * FROM transferencias_agendadas WHERE executada = 0 AND dataAgendada <= :hoje")
    suspend fun obterAgendamentosPendentesSync(hoje: Long): List<TransferenciaAgendada>

    @Query("UPDATE transferencias_agendadas SET executada = 1 WHERE id = :id")
    suspend fun marcarAgendamentoComoExecutado(id: Int)

    @Query("DELETE FROM transferencias_agendadas WHERE contaOrigem = :conta OR contaDestino = :conta")
    suspend fun excluirAgendamentosDaConta(conta: String)

    @Query("DELETE FROM transferencias_agendadas")
    suspend fun apagarAgendamentos()

    // ----------------------------------------------------------- limpeza total

    @Query("DELETE FROM cartoes") suspend fun apagarCartoes()
    @Query("DELETE FROM despesas") suspend fun apagarDespesas()
    @Query("DELETE FROM despesas_fixas") suspend fun apagarDespesasFixas()
    @Query("DELETE FROM categorias") suspend fun apagarCategorias()
    @Query("DELETE FROM metas") suspend fun apagarMetas()
    @Query("DELETE FROM orcamentos") suspend fun apagarOrcamentos()
    @Query("DELETE FROM investimentos") suspend fun apagarInvestimentos()
    @Query("DELETE FROM patrimonio_historico") suspend fun apagarPatrimonioHistorico()
    @Query("DELETE FROM transacoes") suspend fun apagarTransacoes()
    @Query("DELETE FROM contasaldo") suspend fun apagarContas()

    // ---- sincronização (Firestore) ----
    @Upsert
    suspend fun upsertContas(contas: List<ContaSaldo>)

    @Query("DELETE FROM contasaldo WHERE id IN (:ids)")
    suspend fun excluirContasPorIds(ids: List<Int>)

    @Upsert
    suspend fun upsertAgendamentos(agendamentos: List<TransferenciaAgendada>)

    @Query("DELETE FROM transferencias_agendadas WHERE id IN (:ids)")
    suspend fun excluirAgendamentosPorIds(ids: List<Int>)
}
