package com.meudinheiro

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.meudinheiro.storage.EnvioImediato
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
        // R85: ao sair do app, empurra na hora o que ainda não foi para a nuvem (antes de o processo ser congelado).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = EnvioImediato.atividadeIniciada()
            override fun onActivityStopped(activity: Activity) {
                val foiParaSegundoPlano = EnvioImediato.atividadeParada()
                if (foiParaSegundoPlano && !activity.isChangingConfigurations) {
                    EnvioImediato.aoIrParaSegundoPlano(storageManager, this@MyApplication)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
