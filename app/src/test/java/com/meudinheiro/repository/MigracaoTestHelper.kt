package com.meudinheiro.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import com.meudinheiro.data.AppDatabase
import org.json.JSONObject
import java.io.File

/**
 * Padrão para testar migrações do Room SEM instrumentação (Robolectric):
 *  1. [criarBancoNaVersao] monta um arquivo SQLite com o schema EXATO da versão anterior, lido de
 *     `app/schemas/com.meudinheiro.data.AppDatabase/<versao>.json` (gerado pelo próprio Room a cada build) —
 *     tabelas, índices e `room_master_table` (identity hash). Os dados "antigos" são inseridos no callback.
 *  2. [abrirComRoom] abre o arquivo com o Room real e as migrações informadas; o Room executa a migração e VALIDA o
 *     resultado contra as entidades atuais (se o SQL da migração divergir do schema, a abertura lança exceção).
 *
 * Para uma migração N → N+1: `criarBancoNaVersao(ctx, "x.db", N) { db -> db.execSQL("INSERT ...") }`, depois
 * `abrirComRoom(ctx, "x.db", MIGRATION_N_N1)` e conferir as linhas pelos DAOs. Veja `Migracao8Para9Test`.
 */
object MigracaoTestHelper {

    private const val PASTA_SCHEMAS = "schemas/com.meudinheiro.data.AppDatabase"

    private fun arquivoSchema(versao: Int): File =
        listOf(File("$PASTA_SCHEMAS/$versao.json"), File("app/$PASTA_SCHEMAS/$versao.json")).firstOrNull { it.exists() }
            ?: error("Schema $versao.json não encontrado (rode o build para o Room exportá-lo em app/schemas).")

    fun criarBancoNaVersao(ctx: Context, nome: String, versao: Int, aoCriar: (SQLiteDatabase) -> Unit = {}) {
        val destino = ctx.getDatabasePath(nome)
        destino.parentFile?.mkdirs()
        destino.delete()
        val banco = JSONObject(arquivoSchema(versao).readText()).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(destino, null)
        try {
            val entidades = banco.getJSONArray("entities")
            for (i in 0 until entidades.length()) {
                val e = entidades.getJSONObject(i)
                val tabela = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", tabela))
                val indices = e.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tabela))
                }
            }
            val setup = banco.optJSONArray("setupQueries")
            for (i in 0 until (setup?.length() ?: 0)) db.execSQL(setup!!.getString(i))
            aoCriar(db)
            db.version = versao
        } finally {
            db.close()
        }
    }

    fun abrirComRoom(ctx: Context, nome: String, vararg migracoes: Migration): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, nome)
            .addMigrations(*migracoes)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase } // força abrir (migrar + validar) agora
}
