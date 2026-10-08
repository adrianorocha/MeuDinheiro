package com.meudinheiro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meudinheiro.dao.CartaoDao
import com.meudinheiro.dao.CategoriaDao
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.DespesaDao
import com.meudinheiro.dao.DespesaFixaDao
import com.meudinheiro.dao.InvestimentoDao
import com.meudinheiro.dao.MetaDao
import com.meudinheiro.dao.OrcamentoDao
import com.meudinheiro.dao.PatrimonioDao
import com.meudinheiro.dao.TransacaoDao

/**
 * 1 → 2
 *  - despesas: `natureza` e `grupoId`, índices, `cartaoId = 0` passa a NULL, `mes/ano` recalculados a
 *    partir da data (antes ficavam 0/0 ou na data de digitação) e lançamentos internos legados classificados;
 *  - nova tabela `sync_meta` (sincronização com o Firestore).
 * Os dados do usuário são preservados (nada de migração destrutiva).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE despesas ADD COLUMN natureza TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL("ALTER TABLE despesas ADD COLUMN grupoId TEXT")

        db.execSQL("UPDATE despesas SET cartaoId = NULL WHERE cartaoId = 0")
        db.execSQL(
            """
            UPDATE despesas SET
              mes = CAST(strftime('%m', data / 1000, 'unixepoch', 'localtime') AS INTEGER),
              ano = CAST(strftime('%Y', data / 1000, 'unixepoch', 'localtime') AS INTEGER)
            """.trimIndent()
        )

        db.execSQL("UPDATE despesas SET natureza = 'SALDO_INICIAL' WHERE descricao = 'Saldo Inicial' AND tipo = 'CREDITO'")
        db.execSQL("UPDATE despesas SET natureza = 'PAGAMENTO_FATURA' WHERE descricao LIKE 'Pagamento Fatura:%'")
        db.execSQL("UPDATE despesas SET natureza = 'APORTE_META' WHERE descricao LIKE 'Aporte: %' AND tipo = 'DEBITO'")
        db.execSQL("UPDATE despesas SET natureza = 'RESGATE_META' WHERE descricao LIKE 'Estorno: Meta %' AND tipo = 'CREDITO'")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_despesas_conta ON despesas (conta)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_despesas_cartaoId ON despesas (cartaoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_despesas_data ON despesas (data)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_meta (
              colecao TEXT NOT NULL,
              docId TEXT NOT NULL,
              hash TEXT NOT NULL,
              PRIMARY KEY (colecao, docId)
            )
            """.trimIndent()
        )
    }
}

/** 2 → 3: cartões virtuais (limite compartilhado com o cartão físico). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cartoes ADD COLUMN cartaoPrincipalId INTEGER")
    }
}

/** 3 → 4: meta com prazo, autor do lançamento (conta compartilhada) e lixeira de 30 dias. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE metas ADD COLUMN dataAlvo INTEGER")
        db.execSQL("ALTER TABLE despesas ADD COLUMN autor TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lixeira (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              tipo TEXT NOT NULL,
              descricao TEXT NOT NULL,
              valor REAL NOT NULL,
              excluidoEm INTEGER NOT NULL,
              payload TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** 4 → 5: despesas fixas podem ser pagas em um cartão (físico ou virtual). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE despesas_fixas ADD COLUMN cartaoId INTEGER")
    }
}

/** 5 → 6: campos de conciliação bancária (feita no portal) preservados no app. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE despesas ADD COLUMN fitid TEXT")
        db.execSQL("ALTER TABLE despesas ADD COLUMN conciliadoEm INTEGER")
    }
}

@Database(
    entities = [
        Despesa::class, ContaSaldo::class, DespesaFixa::class, Categoria::class, Orcamento::class,
        Meta::class, Investimento::class, Transacao::class, TransferenciaAgendada::class,
        PatrimonioHistorico::class, Cartao::class, SyncMeta::class, Lixeira::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun despesaDao(): DespesaDao
    abstract fun contaSaldoDao(): ContaSaldoDao
    abstract fun despesaFixaDao(): DespesaFixaDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun orcamentoDao(): OrcamentoDao
    abstract fun metaDao(): MetaDao
    abstract fun investimentoDao(): InvestimentoDao
    abstract fun transacaoDao(): TransacaoDao
    abstract fun patrimonioDao(): PatrimonioDao
    abstract fun cartaoDao(): CartaoDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun lixeiraDao(): LixeiraDao

    companion object {
        // Nome do arquivo que já era (de fato) usado pelo app: `getDatabase` sempre rodava primeiro.
        private const val DB_NAME = "meu_dinheiro_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // Só em downgrade (APK antigo sobre banco novo). Upgrades NUNCA apagam dados.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }

        /** Mantido por compatibilidade com chamadas existentes. */
        fun getDatabase(context: Context): AppDatabase = getInstance(context)
    }
}
