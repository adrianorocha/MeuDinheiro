package com.meudinheiro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.CartaoComConta
import kotlinx.coroutines.flow.Flow

@Dao
interface CartaoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirCartao(cartao: Cartao)

    @Delete
    suspend fun deletarCartao(cartao: Cartao)

    // A MÁGICA DO VÍNCULO: Busca os cartões e já traz o nome da Conta Corrente atrelada
    @Query("""
        SELECT c.id, c.nome as nomeCartao, c.finalCartao, c.tipo, c.limiteDisponivel,c.limiteTotal, 
               c.diaFechamento, c.diaVencimento, c.contaId, 
               b.banco as nomeConta, b.conta as numeroConta 
        FROM cartoes c 
        INNER JOIN contasaldo b ON c.contaId = b.id
    """)
    fun getCartoesComConta(): Flow<List<CartaoComConta>>

    @Query("SELECT * FROM cartoes")
    suspend fun obterTodasStatic(): List<Cartao>


    // Busca um cartão específico para quando formos abater o limite na hora da compra
    @Query("SELECT * FROM cartoes WHERE id = :id LIMIT 1")
    suspend fun getCartaoPorId(id: Int): Cartao?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(cartao: List<Cartao>)

    @Query("DELETE FROM cartoes")
    suspend fun limparTudo()

    @Query("UPDATE cartoes SET limiteDisponivel = limiteDisponivel - :valor WHERE id = :id")
    suspend fun abaterLimite(id: Int, valor: Double)

    @Query("UPDATE cartoes SET limiteDisponivel = limiteDisponivel + :valor WHERE id = :id")
    suspend fun estornarLimite(id: Int, valor: Double)
}