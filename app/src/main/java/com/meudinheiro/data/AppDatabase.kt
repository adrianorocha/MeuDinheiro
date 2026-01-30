package com.meudinheiro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.DespesaDao

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE despesas ADD COLUMN pago INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [Despesa::class, ContaSaldo::class], version = 2)
@TypeConverters(Converters::class)

abstract class AppDatabase : RoomDatabase() {
    abstract fun despesaDao(): DespesaDao
    abstract fun contaSaldoDao(): ContaSaldoDao

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
    }
}