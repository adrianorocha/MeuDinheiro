package com.meudinheiro.funcoes

import com.google.gson.Gson
import com.meudinheiro.domain.BackupRetencao
import com.meudinheiro.domain.MetaBackup
import com.meudinheiro.domain.TipoBackup
import com.meudinheiro.repository.BackupJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * R66 — pasta de backups (privada) com um `.json` (mesmo formato do backup manual) e um `.meta.json` por backup.
 * Não mexe no BackupDto/BackupJson: os metadados ficam em arquivo próprio ao lado.
 */
class BackupArmazenamento(private val pasta: File, private val fuso: TimeZone = TimeZone.getDefault()) {
    private val gson = Gson()

    private fun prefixo(tipo: TipoBackup) = when (tipo) {
        TipoBackup.DIARIO, TipoBackup.SEMANAL -> "auto"
        TipoBackup.MANUAL -> "manual"
        TipoBackup.SEGURANCA -> "seguranca"
    }

    /** Grava o backup [json] e seus metadados. Retorna os metadados gravados. */
    fun salvar(json: String, tipo: TipoBackup, agora: Long): MetaBackup {
        pasta.mkdirs()
        val carimbo = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).apply { timeZone = fuso }.format(Date(agora))
        var nome = "${prefixo(tipo)}_$carimbo.json"
        var n = 2
        while (File(pasta, nome).exists()) nome = "${prefixo(tipo)}_${carimbo}_${n++}.json"
        val dto = BackupJson.fromJson(json)
        val arquivo = File(pasta, nome)
        arquivo.writeText(json, Charsets.UTF_8)
        val meta = MetaBackup(nome, agora, BackupJson.VERSAO_ATUAL, BackupRetencao.contagens(dto), arquivo.length(), tipo)
        File(pasta, "$nome.meta.json").writeText(gson.toJson(meta), Charsets.UTF_8)
        return meta
    }

    /** Backups conhecidos (com `.meta.json` e `.json` presentes), do mais novo ao mais antigo. */
    fun listar(): List<MetaBackup> =
        pasta.listFiles { f -> f.isFile && f.name.endsWith(".json.meta.json") }.orEmpty().mapNotNull { f ->
            runCatching { gson.fromJson(f.readText(Charsets.UTF_8), MetaBackup::class.java) }.getOrNull()
                ?.takeIf { @Suppress("SENSELESS_COMPARISON") (it.arquivo != null && it.arquivo.isNotBlank() && it.tipo != null && File(pasta, it.arquivo).isFile) }
        }.sortedByDescending { it.criadoEm }

    fun arquivo(meta: MetaBackup) = File(pasta, meta.arquivo)

    fun ler(meta: MetaBackup): String = arquivo(meta).readText(Charsets.UTF_8)

    fun excluir(meta: MetaBackup) {
        File(pasta, meta.arquivo).delete()
        File(pasta, meta.arquivo + ".meta.json").delete()
    }

    /** Aplica a retenção (7 diários + 4 semanais; preserva manuais) e devolve o que foi apagado. */
    fun aplicarRetencao(agora: Long): List<MetaBackup> =
        BackupRetencao.retencao(listar(), agora).apagar.onEach(::excluir)

    companion object {
        /**
         * Restaura [json] só depois de guardar o estado ATUAL como backup de SEGURANCA. Se a cópia de segurança falhar
         * (ou o arquivo for inválido) nada é restaurado. [restaurar] é o `restaurarBackupCompleto` existente.
         */
        suspend fun restaurarComSeguranca(
            armazenamento: BackupArmazenamento,
            json: String,
            gerarAtual: suspend () -> String,
            restaurar: suspend (String) -> Unit,
            agora: Long
        ): MetaBackup {
            BackupJson.fromJson(json) // inválido: falha antes de qualquer alteração
            val seguranca = armazenamento.salvar(gerarAtual(), TipoBackup.SEGURANCA, agora)
            restaurar(json)
            return seguranca
        }
    }
}
