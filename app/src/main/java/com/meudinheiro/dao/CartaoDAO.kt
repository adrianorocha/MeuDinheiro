package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.CartaoComConta
import kotlinx.coroutines.flow.Flow

@Dao
interface CartaoDao {

    @Upsert
    suspend fun inserirCartao(cartao: Cartao)

    @Delete
    suspend fun deletarCartao(cartao: Cartao)

    @Query(
        """
        SELECT c.id, c.nome as nomeCartao, c.finalCartao, c.tipo, c.limiteDisponivel, c.limiteTotal,
               c.diaFechamento, c.diaVencimento, c.contaId,
               b.banco as nomeConta, b.conta as numeroConta, c.cartaoPrincipalId
        FROM cartoes c
        INNER JOIN contasaldo b ON c.contaId = b.id
        """
    )
    fun getCartoesComConta(): Flow<List<CartaoComConta>>

    @Query("SELECT * FROM cartoes")
    suspend fun obterTodasStatic(): List<Cartao>

    @Query("SELECT * FROM cartoes")
    fun obterTodosFlow(): Flow<List<Cartao>>

    @Query("SELECT * FROM cartoes WHERE id = :id LIMIT 1")
    suspend fun getCartaoPorId(id: Int): Cartao?

    @Query("SELECT * FROM cartoes WHERE id = :principalId OR cartaoPrincipalId = :principalId")
    suspend fun obterGrupo(principalId: Int): List<Cartao>

    @Query("SELECT * FROM cartoes WHERE contaId = :contaId")
    suspend fun obterPorConta(contaId: Int): List<Cartao>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(cartao: List<Cartao>)

    /** O limite é derivado do extrato (R4); este método apenas grava o valor recalculado. */
    @Query("UPDATE cartoes SET limiteDisponivel = :limite WHERE id = :id")
    suspend fun atualizarLimite(id: Int, limite: Double)

    @Query("UPDATE cartoes SET contaId = :contaId, limiteTotal = :limiteTotal, diaFechamento = :fecha, diaVencimento = :vence, tipo = :tipo WHERE cartaoPrincipalId = :principalId")
    suspend fun propagarParaVirtuais(principalId: Int, contaId: Int, limiteTotal: Double, fecha: Int, vence: Int, tipo: String)

    @Query("DELETE FROM cartoes")
    suspend fun limparTudo()

    @Query("DELETE FROM cartoes WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Int>)
}
