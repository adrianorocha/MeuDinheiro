package com.meudinheiro.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.meudinheiro.data.Anexo
import com.meudinheiro.domain.Anexos
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * R60 — arquivos dos anexos de lançamentos (app).
 *
 *  - Sem nuvem (modo LOCAL) ou sem login: o arquivo fica em `filesDir/anexos/<despesaId>/<id>.<ext>` e o `caminho` leva
 *    o prefixo `local:`. **Quando a nuvem for ligada depois NÃO há envio retroativo automático**: o anexo "local:" mostra
 *    "Enviar para a nuvem" (ação manual).
 *  - Com nuvem: o anexo é salvo primeiro no aparelho (o lançamento nunca depende da rede) e enviado ao Firebase Storage em
 *    `usuarios/<uid>/anexos/<despesaId>/<id>.<ext>` (uid = dono dos dados em uso, R32). Falha de envio = continua "local:"
 *    e aparece "Tentar de novo".
 *  - Imagens são reduzidas antes (lado maior ≤ 1600 px, JPEG qualidade 82).
 */
class AnexosGerenciador private constructor(context: Context) {

    private val ctx = context.applicationContext
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Arquivo já validado/reduzido e pronto para anexar (ainda em `cache/anexos_prep/`). */
    data class Preparado(val id: String, val nome: String, val tipo: String, val tamanho: Long, val arquivo: File)

    private val _progresso = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** id do anexo → 0..100 enquanto envia. */
    val progresso: StateFlow<Map<String, Int>> = _progresso.asStateFlow()

    private val _falhas = MutableStateFlow<Set<String>>(emptySet())

    /** ids cujo último envio falhou ("Tentar de novo"). */
    val falhas: StateFlow<Set<String>> = _falhas.asStateFlow()

    // ------------------------------------------------------------------ nuvem

    /** uid cujo caminho recebe os arquivos (dono em uso, R32), ou `null` sem nuvem/sem login. */
    suspend fun uidDaNuvem(): String? {
        return runCatching {
            val prefs = UserPreferences(ctx)
            if (prefs.storageModeFlow.first() != StorageMode.FIREBASE) return@runCatching null
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@runCatching null
            prefs.cloudRootUidFlow.first().ifBlank { uid }
        }.getOrNull()
    }

    suspend fun nuvemAtiva(): Boolean = uidDaNuvem() != null

    // ------------------------------------------------------------------ preparar (reduz/valida)

    /** Lê [uri], valida (tipo/tamanho/quantidade) e, se for imagem, reduz. Falha traz a mensagem pt-BR para o usuário. */
    suspend fun preparar(uri: Uri, quantidadeAtual: Int): Result<Preparado> = withContext(Dispatchers.IO) {
        runCatching {
            Anexos.validarNovo("image/jpeg", 1, quantidadeAtual)?.let { throw IllegalArgumentException(it) }
            val resolver = ctx.contentResolver
            var nome: String? = null
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) nome = c.getString(0)
                }
            }
            val tipoBruto = (resolver.getType(uri) ?: "").lowercase().substringBefore(';').trim()
            val tipo = when {
                tipoBruto == "image/jpg" || tipoBruto == "image/pjpeg" -> "image/jpeg"
                tipoBruto.isNotEmpty() && tipoBruto != "application/octet-stream" -> tipoBruto
                else -> tipoPorExtensao(nome ?: uri.lastPathSegment.orEmpty())
            }
            Anexos.validarNovo(tipo, 1, quantidadeAtual)?.let { throw IllegalArgumentException(it) }
            val id = UUID.randomUUID().toString()
            val pasta = File(ctx.cacheDir, "anexos_prep").apply { mkdirs() }
            val nomeOriginal = nome
            if (Anexos.ehImagem(tipo)) {
                val saida = File(pasta, "$id.jpg")
                reduzirImagem(uri, saida)
                val base = (nomeOriginal ?: "foto").substringBeforeLast('.')
                val tam = saida.length()
                Anexos.validarNovo("image/jpeg", tam, quantidadeAtual)?.let { saida.delete(); throw IllegalArgumentException(it) }
                Preparado(id, Anexos.nomeSeguro("$base.jpg"), "image/jpeg", tam, saida)
            } else {
                val saida = File(pasta, "$id.pdf")
                var total = 0L
                val entrada = resolver.openInputStream(uri) ?: throw IllegalArgumentException("Não foi possível ler o arquivo.")
                entrada.use { ins ->
                    FileOutputStream(saida).use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > Anexos.MAX_BYTES) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                Anexos.validarNovo(tipo, total, quantidadeAtual)?.let { saida.delete(); throw IllegalArgumentException(it) }
                Preparado(id, Anexos.nomeSeguro(nomeOriginal ?: "documento.pdf"), tipo, total, saida)
            }
        }.recoverCatching { e ->
            throw IllegalArgumentException(
                if (e is IllegalArgumentException) e.message else "Não foi possível ler o arquivo escolhido."
            )
        }
    }

    private fun tipoPorExtensao(nome: String): String = when (nome.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        else -> ""
    }

    private fun reduzirImagem(uri: Uri, saida: File) {
        val resolver = ctx.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) throw IllegalArgumentException("Imagem inválida ou corrompida.")
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = Anexos.amostragem(limites.outWidth, limites.outHeight)
        }
        val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
            ?: throw IllegalArgumentException("Imagem inválida ou corrompida.")
        val rotacao = runCatching {
            resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val (lw, lh) = Anexos.ladoReduzido(original.width, original.height)
        val m = Matrix().apply {
            if (lw != original.width) postScale(lw.toFloat() / original.width, lh.toFloat() / original.height)
            if (rotacao != 0f) postRotate(rotacao)
        }
        val ajustada = Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, true)
        // JPEG não tem transparência: PNG com alfa vai sobre fundo branco.
        val opaca = Bitmap.createBitmap(ajustada.width, ajustada.height, Bitmap.Config.ARGB_8888)
        Canvas(opaca).apply { drawColor(Color.WHITE); drawBitmap(ajustada, 0f, 0f, null) }
        FileOutputStream(saida).use { opaca.compress(Bitmap.CompressFormat.JPEG, Anexos.QUALIDADE_JPEG, it) }
        if (ajustada !== original) original.recycle()
        ajustada.recycle()
        opaca.recycle()
    }

    fun descartarPreparado(p: Preparado) {
        runCatching { p.arquivo.delete() }
    }

    // ------------------------------------------------------------------ gravar / enviar

    private fun arquivoLocal(despesaId: Long, id: String, tipo: String): File {
        val ext = Anexos.extensao(tipo) ?: "bin"
        return File(ctx.filesDir, "anexos/$despesaId/$id.$ext")
    }

    private fun arquivoDoCaminhoLocal(caminho: String): File = File(ctx.filesDir, caminho.removePrefix(Anexos.PREFIXO_LOCAL))

    /** Move o arquivo preparado para o armazenamento interno e devolve o anexo "local:" (sempre funciona, mesmo offline). */
    suspend fun guardarLocal(despesaId: Long, p: Preparado): Anexo = withContext(Dispatchers.IO) {
        val destino = arquivoLocal(despesaId, p.id, p.tipo).also { it.parentFile?.mkdirs() }
        if (!p.arquivo.renameTo(destino)) {
            p.arquivo.copyTo(destino, overwrite = true)
            p.arquivo.delete()
        }
        Anexo(
            id = p.id, nome = p.nome, tipo = p.tipo, tamanho = p.tamanho,
            caminho = Anexos.caminhoLocal(despesaId, p.id, p.tipo)!!, criadoEm = System.currentTimeMillis()
        )
    }

    /**
     * Envia o arquivo "local:" ao Storage. Sucesso → devolve o anexo com o caminho da nuvem (o arquivo local é apagado).
     * Falha (sem rede, Storage desativado, regra negando…) → `null`, o anexo segue "local:" e o id entra em [falhas].
     */
    suspend fun enviarParaNuvem(despesaId: Long, anexo: Anexo): Anexo? = withContext(Dispatchers.IO) {
        if (!Anexos.ehLocal(anexo.caminho)) return@withContext anexo
        val uid = uidDaNuvem() ?: return@withContext null
        val arquivo = arquivoDoCaminhoLocal(anexo.caminho)
        val destino = Anexos.caminhoStorage(uid, despesaId, anexo.id, anexo.tipo)
        if (destino == null || !arquivo.exists()) {
            _falhas.value = _falhas.value + anexo.id
            return@withContext null
        }
        _falhas.value = _falhas.value - anexo.id
        _progresso.value = _progresso.value + (anexo.id to 0)
        try {
            val ref = FirebaseStorage.getInstance().reference.child(destino)
            val tarefa = ref.putFile(Uri.fromFile(arquivo), StorageMetadata.Builder().setContentType(anexo.tipo).build())
            tarefa.addOnProgressListener { s ->
                val pct = if (s.totalByteCount > 0) (s.bytesTransferred * 100 / s.totalByteCount).toInt() else 0
                _progresso.value = _progresso.value + (anexo.id to pct.coerceIn(0, 100))
            }
            tarefa.await()
            runCatching { arquivo.delete() }
            anexo.copy(caminho = destino)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar anexo ${anexo.id}", e)
            _falhas.value = _falhas.value + anexo.id
            null
        } finally {
            _progresso.value = _progresso.value - anexo.id
        }
    }

    /** Dispara o envio em segundo plano (sobrevive ao fechamento do diálogo) e chama [aoEnviar] com o anexo atualizado. */
    fun enviarEmSegundoPlano(despesaId: Long, anexo: Anexo, aoEnviar: suspend (antigo: Anexo, novo: Anexo) -> Boolean) {
        escopo.launch {
            val novo = enviarParaNuvem(despesaId, anexo) ?: return@launch
            if (!aoEnviar(anexo, novo)) {
                // O lançamento sumiu enquanto enviava: não deixa arquivo órfão no Storage.
                runCatching { FirebaseStorage.getInstance().reference.child(novo.caminho).delete().await() }
            }
        }
    }

    // ------------------------------------------------------------------ excluir

    /** Melhor esforço: apaga o arquivo (Storage ou local). Nunca lança. */
    suspend fun excluir(anexo: Anexo) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (Anexos.ehLocal(anexo.caminho)) {
                    arquivoDoCaminhoLocal(anexo.caminho).delete()
                } else {
                    try {
                        FirebaseStorage.getInstance().reference.child(anexo.caminho).delete().await()
                    } catch (e: StorageException) {
                        if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
                    }
                }
                File(ctx.cacheDir, "anexos/${anexo.id}.${Anexos.extensao(anexo.tipo) ?: "bin"}").delete()
            }.onFailure { Log.w(TAG, "Não foi possível apagar o anexo ${anexo.id}", it) }
        }
    }

    suspend fun excluirTodos(anexos: List<Anexo>) = anexos.forEach { excluir(it) }

    // ------------------------------------------------------------------ abrir

    /** Arquivo pronto para exibir/abrir: o local ou o baixado para `cache/anexos/` (reaproveita se já baixado). `null` se indisponível. */
    suspend fun arquivoParaVer(anexo: Anexo, aoProgredir: (Int) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        runCatching {
            if (Anexos.ehLocal(anexo.caminho)) {
                return@runCatching arquivoDoCaminhoLocal(anexo.caminho).takeIf { it.exists() }
            }
            val cache = File(ctx.cacheDir, "anexos/${anexo.id}.${Anexos.extensao(anexo.tipo) ?: "bin"}")
            if (cache.exists() && cache.length() > 0) return@runCatching cache
            cache.parentFile?.mkdirs()
            val tarefa = FirebaseStorage.getInstance().reference.child(anexo.caminho).getFile(cache)
            tarefa.addOnProgressListener { s ->
                if (s.totalByteCount > 0) aoProgredir((s.bytesTransferred * 100 / s.totalByteCount).toInt())
            }
            tarefa.await()
            cache
        }.onFailure { Log.w(TAG, "Não foi possível obter o anexo ${anexo.id}", it) }.getOrNull()
    }

    companion object {
        private const val TAG = "AnexosGerenciador"

        @Volatile
        private var instancia: AnexosGerenciador? = null

        fun get(context: Context): AnexosGerenciador =
            instancia ?: synchronized(this) { instancia ?: AnexosGerenciador(context).also { instancia = it } }
    }
}
