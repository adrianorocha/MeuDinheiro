package com.meudinheiro.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meudinheiro.domain.BackupRetencao
import com.meudinheiro.domain.MetaBackup
import com.meudinheiro.domain.TipoBackup
import com.meudinheiro.funcoes.BackupArmazenamento
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * R66 — backup automático DIÁRIO em armazenamento privado (`files/backups/auto/`), ligado por padrão (como no R33).
 * Retenção: 7 diários + 4 semanais (o de domingo vira semanal); manuais nunca são apagados (ver [BackupRetencao]).
 *
 * Cópia na nuvem (Firebase Storage `usuarios/<uid>/backups/`): AINDA NÃO HABILITADA. A regra atual de portal/storage.rules
 * só aceita image/jpeg, image/png e application/pdf até 5 MB, então um `application/json` seria negado.
 * TODO: quando a regra liberar `backups/` com application/json (limite maior), enviar o arquivo logo após [executar].
 */
object BackupAutomatico {
    private const val NOME_TRABALHO = "BackupAutomaticoDiario"
    private const val NOME_TRABALHO_LEGADO = "BackupAutomaticoSemanal" // R33 (semanal, 4 cópias)
    const val NUVEM_HABILITADA = false

    fun pasta(context: Context): File = File(File(context.filesDir, "backups"), "auto").apply { mkdirs() }

    fun armazenamento(context: Context) = BackupArmazenamento(pasta(context))

    fun listar(context: Context): List<MetaBackup> = armazenamento(context).listar()

    /** Gera o backup agora (mesmo JSON do backup manual), grava com metadados e aplica a retenção. */
    suspend fun executar(context: Context, tipo: TipoBackup? = null, agora: Long = System.currentTimeMillis()): MetaBackup {
        val json = MainRepository(context.applicationContext).gerarBackup()
        val arm = armazenamento(context)
        val meta = arm.salvar(json, tipo ?: BackupRetencao.tipoAutomatico(agora), agora)
        runCatching { arm.aplicarRetencao(agora) }
        return meta
    }

    /** Liga ou desliga o agendamento conforme a preferência do usuário. */
    fun agendar(context: Context, ativo: Boolean) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(NOME_TRABALHO_LEGADO)
        if (!ativo) {
            wm.cancelUniqueWork(NOME_TRABALHO)
            return
        }
        val pedido = PeriodicWorkRequestBuilder<BackupAutomaticoWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
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
        Log.e("BackupAutomatico", "Falha no backup automático", e)
        Result.retry()
    }
}
