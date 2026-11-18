package com.meudinheiro.importacao

import android.content.Context
import android.net.Uri
import com.meudinheiro.domain.ImportacaoExtrato
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * R63 — extratos recebidos (compartilhamento ou pasta monitorada) aguardando revisão. Ficam numa pasta PRIVADA do app
 * (`filesDir/extratos_pendentes`), com nome `<hash>__<nome-sanitizado>`: o nome original nunca vira caminho.
 * Guardar aqui não cria lançamento nenhum; só a confirmação do usuário na tela de revisão cria.
 */
object ExtratosPendentes {

    data class Pendente(val id: String, val nome: String, val tamanho: Long)

    sealed interface Resultado {
        data class Salvo(val nome: String, val novo: Boolean) : Resultado
        data class Recusado(val nome: String, val motivo: String) : Resultado
    }

    private fun pasta(ctx: Context) = File(ctx.filesDir, "extratos_pendentes").apply { mkdirs() }

    /** Lê no máximo [ImportacaoExtrato.MAX_BYTES]+1 bytes: se passar do limite, recusa sem carregar o resto. */
    internal fun lerLimitado(input: InputStream): ByteArray? {
        val limite = ImportacaoExtrato.MAX_BYTES.toInt()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limite) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Valida (nome, MIME, tamanho, conteúdo) e guarda os bytes já lidos. */
    fun guardar(ctx: Context, nomeBruto: String?, mime: String?, bytes: ByteArray): Resultado {
        val nome = ImportacaoExtrato.sanitizarNome(nomeBruto)
        val v = ImportacaoExtrato.validarArquivo(nome, bytes.size.toLong(), mime).takeIf { !it.ok } ?: ImportacaoExtrato.conteudoValido(nome, bytes)
        if (!v.ok) return Resultado.Recusado(nome, v.motivo ?: "Arquivo recusado.")
        val destino = File(pasta(ctx), "${sha256(bytes).take(24)}__$nome")
        if (destino.exists()) return Resultado.Salvo(nome, novo = false)
        destino.writeBytes(bytes)
        return Resultado.Salvo(nome, novo = true)
    }

    /** Copia o conteúdo de [uri] (somente `content://`; nunca `file://` nem caminho informado por terceiros). */
    fun guardarDeUri(ctx: Context, uri: Uri, mimeInformado: String?): Resultado {
        val nome = nomeDe(ctx, uri)
        if (uri.scheme != "content") return Resultado.Recusado(ImportacaoExtrato.sanitizarNome(nome), "Origem não suportada.")
        return try {
            val mime = ctx.contentResolver.getType(uri) ?: mimeInformado?.takeIf { !it.contains('*') }
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { lerLimitado(it) }
                ?: return Resultado.Recusado(ImportacaoExtrato.sanitizarNome(nome), "Arquivo grande demais ou ilegível (máx. 5 MB).")
            guardar(ctx, nome, mime, bytes)
        } catch (e: Exception) {
            Resultado.Recusado(ImportacaoExtrato.sanitizarNome(nome), "Não foi possível ler o arquivo.")
        }
    }

    private fun nomeDe(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment
    } catch (_: Exception) {
        uri.lastPathSegment
    }

    fun listar(ctx: Context): List<Pendente> =
        (pasta(ctx).listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.contains("__") }
            .sortedBy { it.lastModified() }
            .map { Pendente(it.name, it.name.substringAfter("__"), it.length()) }

    fun contar(ctx: Context): Int = listar(ctx).size

    fun ler(ctx: Context, id: String): ByteArray? {
        if (id.contains('/') || id.contains('\\') || id.contains("..")) return null
        val f = File(pasta(ctx), id)
        return if (f.isFile) runCatching { f.readBytes() }.getOrNull() else null
    }

    fun remover(ctx: Context, id: String) {
        if (id.contains('/') || id.contains('\\') || id.contains("..")) return
        File(pasta(ctx), id).delete()
    }
}
