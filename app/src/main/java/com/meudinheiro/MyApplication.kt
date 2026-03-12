package com.meudinheiro

import android.app.Application
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.repository.MainRepository

class MyApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }

    // O repositório sim, esse recebe o DAO que o banco fabricou
    val repository by lazy { MainRepository(this, database.contaSaldoDao()) }
}