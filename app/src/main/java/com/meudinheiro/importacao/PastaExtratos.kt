package com.meudinheiro.importacao

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meudinheiro.AtalhosApp
import com.meudinheiro.domain.ImportacaoExtrato
import com.meudinheiro.notif.Aviso
import com.meudinheiro.notif.Notificacoes
import com.meudinheiro.notif.TipoAviso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * R63 — pasta monitorada (opcional, desligada por padrão). O usuário escolhe a pasta pelo seletor do sistema
 * (`ACTION_OPEN_DOCUMENT_TREE`, permissão persistível só de leitura; nenhuma permissão ampla de armazenamento).
 * As preferências são locais (SharedPreferences) e NÃO fazem parte do backup/sincronização.
 */
object PastaExtratos {

    private const val PREFS = "importacao_extratos"
    private const val K_ATIVO = "ativo"
    private const val K_URI = "pasta_uri"
    private const val K_VISTOS = "vistos"
    private const val TRABALHO = "ImportacaoExtratosPasta"
    private const val MAX_VISTOS = 1000

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ativo(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ATIVO, false) && pasta(ctx) != null
    fun pasta(ctx: Context): Uri? = prefs(ctx).getString(K_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /** Nome legível da pasta escolhida (último trecho do id do documento). */
    fun nomePasta(ctx: Context): String? = pasta(ctx)?.let { runCatching { DocumentsContract.getTreeDocumentId(it).substringAfterLast(':').substringAfterLast('/') }.getOrNull() }

    private fun vistos(ctx: Context): LinkedHashSet<String> =
        LinkedHashSet(prefs(ctx).getString(K_VISTOS, "").orEmpty().split('\n').filter { it.isNotBlank() })

    private fun gravarVistos(ctx: Context, v: Collection<String>) {
        prefs(ctx).edit().putString(K_VISTOS, v.toList().takeLast(MAX_VISTOS).joinToString("\n")).apply()
    }

    fun ligar(ctx: Context, ligado: Boolean) {
        prefs(ctx).edit().putBoolean(K_ATIVO, ligado && pasta(ctx) != null).apply()
        sincronizarAgendamento(ctx)
    }

    /** Guarda a pasta escolhida e marca o que já existe nela como "visto": só arquivos posteriores geram aviso. */
    suspend fun definirPasta(ctx: Context, tree: Uri) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        runCatching { app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        limparPermissaoAntiga(app, mantendo = tree)
        prefs(app).edit().putString(K_URI, tree.toString()).putString(K_VISTOS, "").apply()
        gravarVistos(app, listar(app, tree).map { ImportacaoExtrato.carimbo(it.info) })
        sincronizarAgendamento(app)
    }

    fun limparPasta(ctx: Context) {
        val app = ctx.applicationContext
        limparPermissaoAntiga(app, mantendo = null)
        prefs(app).edit().remove(K_URI).remove(K_VISTOS).putBoolean(K_ATIVO, false).apply()
        sincronizarAgendamento(app)
    }

    private fun limparPermissaoAntiga(ctx: Context, mantendo: Uri?) {
        pasta(ctx)?.takeIf { it != mantendo }?.let { velha ->
            runCatching { ctx.contentResolver.releasePersistableUriPermission(velha, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    /** Liga ou desliga o trabalho periódico (6 h, bateria não baixa) conforme a configuração. */
    fun sincronizarAgendamento(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (ativo(ctx)) {
            val req = PeriodicWorkRequestBuilder<VerificadorPastaExtratosWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork(TRABALHO)
    }

    class ItemPasta(val info: ImportacaoExtrato.ArquivoPasta, val uri: Uri)

    /** Lista os arquivos do nível da pasta (sem recursão) via DocumentsContract. */
    fun listar(ctx: Context, tree: Uri): List<ItemPasta> {
        val raiz = DocumentsContract.getTreeDocumentId(tree)
        val filhos = DocumentsContract.buildChildDocumentsUriUsingTree(tree, raiz)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val saida = mutableListOf<ItemPasta>()
        ctx.contentResolver.query(filhos, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val nome = c.getString(1) ?: continue
                saida.add(
                    ItemPasta(
                        ImportacaoExtrato.ArquivoPasta(nome, if (c.isNull(2)) -1 else c.getLong(2), if (c.isNull(3)) 0 else c.getLong(3)),
                        DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    )
                )
            }
        }
        return saida
    }

    /**
     * Uma verificação: copia para a área privada os arquivos novos (sem criar lançamento algum) e devolve quantos
     * extratos novos ficaram aguardando revisão.
     */
    suspend fun verificar(ctx: Context): Int = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val tree = pasta(app) ?: return@withContext 0
        val permitido = app.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        if (!permitido) { prefs(app).edit().putBoolean(K_ATIVO, false).apply(); sincronizarAgendamento(app); return@withContext 0 }
        val itens = listar(app, tree)
        val vistos = vistos(app)
        val novos = ImportacaoExtrato.arquivosNovos(itens.map { it.info }, vistos).take(ImportacaoExtrato.MAX_ARQUIVOS_POR_ENVIO)
        var guardados = 0
        for (n in novos) {
            val item = itens.first { it.info == n }
            val r = ExtratosPendentes.guardarDeUri(app, item.uri, null)
            if (r is ExtratosPendentes.Resultado.Salvo && r.novo) guardados++
            vistos.add(ImportacaoExtrato.carimbo(n)) // recusado também é marcado, para não retentar a cada 6 h
        }
        gravarVistos(app, vistos)
        guardados
    }

    fun avisar(ctx: Context, n: Int) {
        if (n <= 0) return
        Notificacoes.mostrar(
            ctx,
            Aviso(
                tipo = TipoAviso.EXTRATO,
                titulo = if (n == 1) "1 extrato novo para conciliar" else "$n extratos novos para conciliar",
                resumo = "Toque para revisar. Nada é lançado sem a sua confirmação.",
                id = 63001,
                textoPublico = "Você tem extratos para conciliar",
                acao = AtalhosApp.ACAO_IMPORTAR_EXTRATOS
            )
        )
    }
}

class VerificadorPastaExtratosWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!PastaExtratos.ativo(applicationContext)) return Result.success()
        return try {
            PastaExtratos.avisar(applicationContext, PastaExtratos.verificar(applicationContext))
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
