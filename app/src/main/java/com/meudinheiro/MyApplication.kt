package com.meudinheiro

import android.app.Application
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.storage.StorageManager

class MyApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    val repository by lazy { MainRepository(this) }

    /** Orquestra o armazenamento escolhido pelo usuário (local ou nuvem/Firebase). */
    val storageManager by lazy { StorageManager(this) }

    override fun onCreate() {
        super.onCreate()
        storageManager.iniciar()
    }
}
