package com.meudinheiro.Data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Despesa::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun despesaDao(): DespesaDao
}