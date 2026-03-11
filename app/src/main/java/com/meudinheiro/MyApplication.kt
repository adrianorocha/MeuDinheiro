package com.meudinheiro

import android.app.Application
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.repository.MainRepository

class MyApplication : Application() {
    // 1. Instância do Banco
    val database by lazy { AppDatabase.getDatabase(this) }

    // 2. O REPOSITÓRIO (Garanta que esta linha existe e NÃO é private)
    val repository by lazy { MainRepository(database.contaSaldoDao()) }
}