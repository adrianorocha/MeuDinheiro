package com.meudinheiro

import android.app.Application
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.repository.MainRepository

class MyApplication : Application() {

    // O erro estava aqui: O getDatabase pede um Contexto (this), não o DAO!
    val database by lazy { AppDatabase.getDatabase(this) }

    // O repositório sim, esse recebe o DAO que o banco fabricou
    val repository by lazy { MainRepository(database.contaSaldoDao()) }
}