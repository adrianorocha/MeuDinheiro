package com.meudinheiro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.DespesaDao

@Database(entities = [Despesa::class, ContaSaldo::class], version = 1)
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
                    "financas-db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}