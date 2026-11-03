package com.meudinheiro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Último estado conhecido (hash do conteúdo) de cada documento já sincronizado com o Firestore. */
@Entity(tableName = "sync_meta", primaryKeys = ["colecao", "docId"])
data class SyncMeta(
    val colecao: String,
    val docId: String,
    val hash: String
)

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta")
    suspend fun todos(): List<SyncMeta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(metas: List<SyncMeta>)

    @Query("DELETE FROM sync_meta WHERE colecao = :colecao AND docId = :docId")
    suspend fun remover(colecao: String, docId: String)

    @Query("DELETE FROM sync_meta")
    suspend fun limpar()
}
