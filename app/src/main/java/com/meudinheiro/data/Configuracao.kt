package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * R57 — linha única (`chave = "geral"`) com as configurações sincronizadas: [json] = documento
 * `configuracoes/geral` do Firestore (painel 50-30-20 e período do limite diário) e [atualizadoEm] (epoch ms)
 * da última alteração do usuário (0 = nunca alterada). O DataStore continua sendo a fonte imediata da UI.
 */
@Entity(tableName = "configuracoes")
data class Configuracao(
    @PrimaryKey val chave: String = CHAVE_GERAL,
    val json: String = "{}",
    val atualizadoEm: Long = 0L
) {
    companion object {
        const val CHAVE_GERAL = "geral"
    }
}

@Dao
interface ConfiguracaoDao {
    @Query("SELECT * FROM configuracoes WHERE chave = 'geral' LIMIT 1")
    suspend fun obterGeral(): Configuracao?

    @Query("SELECT * FROM configuracoes WHERE chave = 'geral' LIMIT 1")
    fun observarGeral(): Flow<Configuracao?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(configuracao: Configuracao)

    @Query("DELETE FROM configuracoes")
    suspend fun limpar()
}
