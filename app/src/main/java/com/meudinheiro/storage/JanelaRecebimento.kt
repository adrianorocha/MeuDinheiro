package com.meudinheiro.storage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meudinheiro.MyApplication
import com.meudinheiro.R
import com.meudinheiro.domain.SinalSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * R88 — "janela de recebimento": mantém o processo vivo o bastante para a SyncSession (já criada pelo
 * `MyApplication`) receber do Firestore o que o portal mudou. Não lê nada extra: só observa [StorageManager.info].
 */
object JanelaRecebimento {
    private const val NOME_TRABALHO = "receber_sincronizacao"
    private const val PREFS = "sinal_sync"
    private const val CHAVE_ULTIMO = "ultimo_ms"
    const val ENTRADA_SINAL = "sinal_ms"

    /** Anti-rajada persistente: registra e devolve true se este sinal deve ser processado. */
    @Synchronized
    fun registrarSeNovo(context: Context, agoraMs: Long): Boolean {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!SinalSync.passouIntervalo(sp.getLong(CHAVE_ULTIMO, 0L), agoraMs)) return false
        sp.edit().putLong(CHAVE_ULTIMO, agoraMs).apply()
        return true
    }

    /** Agenda a janela (único, expedido, exige rede; KEEP: sinais repetidos colapsam na janela em andamento). */
    fun agendar(context: Context, sinalMs: Long) {
        val req = OneTimeWorkRequestBuilder<ReceberSincronizacaoWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putLong(ENTRADA_SINAL, sinalMs).build())
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NOME_TRABALHO, ExistingWorkPolicy.KEEP, req)
        }.onFailure { Log.w("JanelaRecebimento", "Não foi possível agendar a janela", it) }
    }
}

class ReceberSincronizacaoWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val storage = (applicationContext as? MyApplication)?.storageManager ?: return Result.success()
        val sinal = inputData.getLong(JanelaRecebimento.ENTRADA_SINAL, System.currentTimeMillis())
        val recebeu = withTimeoutOrNull(SinalSync.JANELA_MAX_MS) {
            storage.info.first { i ->
                val st = i.status
                i.nuvem && st is SyncStatus.Sincronizado && SinalSync.recebeu(st.em, sinal)
            }
            true
        } ?: false
        // Folga para a reconciliação terminar de gravar no banco local antes de o processo ficar elegível a ser congelado.
        if (recebeu) delay(SinalSync.FOLGA_MS)
        Log.i("JanelaRecebimento", if (recebeu) "Mudanças do portal recebidas" else "Janela encerrada pelo teto")
        return Result.success()
    }

    /** Necessário para trabalho expedido no Android < 12 (serviço em primeiro plano discreto). */
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
            .setContentTitle("Atualizando seus dados")
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
        // Mesmo canal do envio imediato (R85), para não criar canal novo.
        const val CANAL = "sincronizacao_envio"
        const val ID_NOTIF = 8802
    }
}
