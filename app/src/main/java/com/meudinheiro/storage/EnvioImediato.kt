package com.meudinheiro.storage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meudinheiro.MyApplication
import com.meudinheiro.R

/** R85 — regras puras do envio imediato (sem I/O, testáveis). */
object PoliticaEnvio {
    /** Só vale agendar o envio em segundo plano quando há algo a enviar e o app não está na tela. */
    fun deveAgendar(temPendencias: Boolean, emPrimeiroPlano: Boolean): Boolean = temPendencias && !emPrimeiroPlano

    /** Recuo entre tentativas de reabrir a sessão: 2 s × tentativa, no máximo 60 s. */
    fun recuoMs(tentativa: Int): Long = (2_000L * tentativa.coerceAtLeast(1)).coerceAtMost(60_000L)

    /** Uma sessão que viveu mais que isto era saudável: a falha seguinte começa o recuo do zero. */
    const val SESSAO_ESTAVEL_MS = 30_000L
}

/** Acompanha se o app está visível e agenda o [EnviarSincronizacaoWorker]. */
object EnvioImediato {
    private const val NOME_TRABALHO = "enviar_sincronizacao"

    @Volatile
    private var iniciadas = 0

    val emPrimeiroPlano: Boolean get() = iniciadas > 0

    fun atividadeIniciada() {
        iniciadas++
    }

    /** @return true quando foi a última atividade visível (app foi para segundo plano). */
    fun atividadeParada(): Boolean {
        iniciadas = (iniciadas - 1).coerceAtLeast(0)
        return iniciadas == 0
    }

    /**
     * Agenda um envio expedido (trabalho único; KEEP: se já houver um rodando, ele cobre este pedido).
     * Exige rede; sem rede o WorkManager espera e dispara ao reconectar.
     */
    fun agendar(context: Context) {
        val req = OneTimeWorkRequestBuilder<EnviarSincronizacaoWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NOME_TRABALHO, ExistingWorkPolicy.KEEP, req)
        }.onFailure { Log.w("EnvioImediato", "Não foi possível agendar o envio", it) }
    }

    /** O app foi para segundo plano: se há algo a enviar, garante o envio antes de o processo ser congelado. */
    fun aoIrParaSegundoPlano(storage: StorageManager, context: Context) {
        if (PoliticaEnvio.deveAgendar(storage.temPendencias, emPrimeiroPlano)) agendar(context)
    }

    /** A sessão acabou de enviar escritas: se o app não está na tela (serviço/receiver/worker), envia já. */
    fun aoEnviar(context: Context) {
        if (PoliticaEnvio.deveAgendar(temPendencias = true, emPrimeiroPlano = emPrimeiroPlano)) agendar(context)
    }
}

/**
 * R85 — empurra as escritas pendentes para o Firestore e espera a confirmação do servidor, para que o portal veja
 * o lançamento na hora mesmo com o app em segundo plano. Não lê nada novo: só aguarda as escritas já feitas.
 */
class EnviarSincronizacaoWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val storage = (applicationContext as? MyApplication)?.storageManager ?: return Result.success()
        if (!storage.temPendencias) return Result.success()
        val ok = storage.enviarPendentes(TEMPO_MAX_MS)
        return if (ok || !storage.temPendencias) Result.success() else Result.retry()
    }

    /** Necessário para trabalho expedido no Android < 12 (vira serviço em primeiro plano discreto). */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CANAL, "Sincronização", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Envio rápido dos lançamentos para a nuvem"
                }
            )
        }
        val notif = NotificationCompat.Builder(applicationContext, CANAL)
            .setSmallIcon(R.drawable.ic_stat_dinheiro)
            .setContentTitle("Enviando lançamentos para a nuvem")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_NOTIF, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_NOTIF, notif)
        }
    }

    private companion object {
        const val CANAL = "sincronizacao_envio"
        const val ID_NOTIF = 8501
        const val TEMPO_MAX_MS = 25_000L
    }
}
