package com.meudinheiro.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** R33 — backup semanal em armazenamento privado do app (`files/backups/`), mantendo os 4 mais recentes. */
object BackupAutomatico {
    private const val NOME_TRABALHO = "BackupAutomaticoSemanal"
    private const val MANTER = 4

    fun pasta(context: Context): File = File(context.filesDir, "backups").apply { mkdirs() }

    fun listar(context: Context): List<File> =
        pasta(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedByDescending { it.lastModified() }

    /** Gera o backup agora e apaga os mais antigos. Retorna o arquivo criado. */
    suspend fun executar(context: Context): File {
        val json = MainRepository(context.applicationContext).gerarBackup()
        val nome = "backup_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
        val arquivo = File(pasta(context), nome)
        arquivo.writeText(json, Charsets.UTF_8)
        listar(context).drop(MANTER).forEach { it.delete() }
        return arquivo
    }

    /** Liga ou desliga o agendamento conforme a preferência do usuário. */
    fun agendar(context: Context, ativo: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!ativo) {
            wm.cancelUniqueWork(NOME_TRABALHO)
            return
        }
        val pedido = PeriodicWorkRequestBuilder<BackupAutomaticoWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(NOME_TRABALHO, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    /** Lê a preferência e agenda (chamado ao abrir o app). */
    suspend fun sincronizarAgendamento(context: Context) {
        agendar(context, UserPreferences(context).autoBackupEnabledFlow.first())
    }
}

class BackupAutomaticoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        BackupAutomatico.executar(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Log.e("BackupAutomatico", "Falha no backup semanal", e)
        Result.retry()
    }
}
