package com.meudinheiro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meudinheiro.dao.CategoriaDao
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.DespesaDao
import com.meudinheiro.dao.DespesaFixaDao
import com.meudinheiro.dao.InvestimentoDao
import com.meudinheiro.dao.MetaDao
import com.meudinheiro.dao.OrcamentoDao

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE despesas ADD COLUMN pago INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [Despesa::class, ContaSaldo::class, DespesaFixa::class,
    Categoria::class, Orcamento::class, Meta::class, Investimento::class], version = 2)
@TypeConverters(Converters::class)

abstract class AppDatabase : RoomDatabase() {
    abstract fun despesaDao(): DespesaDao
    abstract fun contaSaldoDao(): ContaSaldoDao
    abstract fun despesaFixaDao(): DespesaFixaDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun orcamentoDao(): OrcamentoDao
    abstract fun metaDao(): MetaDao
    abstract fun investimentoDao(): InvestimentoDao


    companion object{
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meudinheiro-db"
                )
                    .fallbackToDestructiveMigration()
                    //.addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }

        fun getDatabase(context: Context): AppDatabase {
            // Se a instância já existe, retorna ela
            return INSTANCE ?: synchronized(this) {
                // Se não existe, cria uma nova
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meu_dinheiro_db" // Nome do arquivo do banco
                )
                    .fallbackToDestructiveMigration() // Opcional: Recria o banco se mudar versão
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}