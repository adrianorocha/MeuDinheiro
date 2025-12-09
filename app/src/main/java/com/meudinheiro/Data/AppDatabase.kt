package com.meudinheiro.Data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Despesa::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun despesaDao(): DespesaDao
    companion object{
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "financas-db"
                )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}