package com.meudinheiro.funcoes

import android.content.Context
import com.meudinheiro.data.BackupDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * R66 — "Exportar tudo": ZIP com um CSV por coleção (UTF-8 com BOM, separador `;`, vírgula decimal, datas dd/mm/aaaa).
 * Usa só `java.util.zip`. A parte de formatação é pura (testada); [exportar] só grava e devolve o arquivo.
 */
object ExportarTudo {
    private const val BOM = "﻿"

    /** Escapa um campo CSV: aspas se tiver `;`, aspas, quebra de linha ou espaço nas pontas; aspas viram duplas. */
    fun campo(v: Any?, fuso: TimeZone = TimeZone.getDefault()): String {
        val t = when (v) {
            null -> ""
            is Double -> String.format(Locale.US, "%.2f", v).replace('.', ',')
            is Float -> String.format(Locale.US, "%.2f", v.toDouble()).replace('.', ',')
            is Boolean -> if (v) "sim" else "não"
            is Date -> data(v.time, fuso)
            else -> v.toString()
        }
        val precisaAspas = t.any { it == ';' || it == '"' || it == '\n' || it == '\r' } || t != t.trim()
        return if (precisaAspas) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    fun data(ms: Long?, fuso: TimeZone = TimeZone.getDefault()): String =
        if (ms == null || ms <= 0L) "" else SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = fuso }.format(Date(ms))

    /** CSV completo (com BOM, linhas separadas por CRLF). */
    fun csv(cabecalho: List<String>, linhas: List<List<Any?>>, fuso: TimeZone = TimeZone.getDefault()): String {
        val sb = StringBuilder(BOM)
        sb.append(cabecalho.joinToString(";") { campo(it, fuso) }).append("\r\n")
        linhas.forEach { l -> sb.append(l.joinToString(";") { campo(it, fuso) }).append("\r\n") }
        return sb.toString()
    }

    /** Um CSV por coleção do backup, indexado pelo nome do arquivo (`despesas.csv`…). Coleções vazias também saem (só o cabeçalho). */
    fun csvs(b: BackupDto, fuso: TimeZone = TimeZone.getDefault()): Map<String, String> {
        val m = linkedMapOf<String, String>()
        fun t(nome: String, cab: List<String>, linhas: List<List<Any?>>) { m["$nome.csv"] = csv(cab, linhas, fuso) }
        t("despesas", listOf("id", "data", "descricao", "valor", "tipo", "categoria", "conta", "cartao_id", "pago", "natureza", "tags", "autor", "grupo_id", "moeda", "valor_original"),
            b.despesas.orEmpty().sortedBy { it.data }.map { listOf(it.id, it.data, it.descricao, it.valor, it.tipo.name, it.categoria, it.conta, it.cartaoId, it.pago, it.natureza, it.tags.joinToString(","), it.autor, it.grupoId, it.moedaOriginal, it.valorOriginal) })
        t("contas", listOf("id", "banco", "agencia", "conta", "titular", "saldo"),
            b.contas.orEmpty().map { listOf(it.id, it.banco, it.agencia, it.conta, it.titular, it.saldo) })
        t("cartoes", listOf("id", "nome", "final", "tipo", "limite_total", "limite_disponivel", "dia_fechamento", "dia_vencimento", "conta_id", "cartao_principal_id", "limite_proprio", "fechamento_melhor_dia"),
            b.cartoes.orEmpty().map { listOf(it.id, it.nome, it.finalCartao, it.tipo, it.limiteTotal, it.limiteDisponivel, it.diaFechamento, it.diaVencimento, it.contaId, it.cartaoPrincipalId, it.limiteProprio, it.fechamentoMelhorDia) })
        t("categorias", listOf("id", "nome", "icone"), b.categorias.orEmpty().map { listOf(it.id, it.nome, it.pic) })
        t("orcamentos", listOf("id", "categoria", "limite"), b.orcamentos.orEmpty().map { listOf(it.id, it.categoria, it.valorLimite) })
        t("metas", listOf("id", "nome", "objetivo", "guardado", "data_alvo"),
            b.metas.orEmpty().map { listOf(it.id, it.nome, it.valorObjetivo, it.valorGuardado, data(it.dataAlvo, fuso)) })
        t("investimentos", listOf("id", "nome", "tipo", "investido", "atual"),
            b.investimentos.orEmpty().map { listOf(it.id, it.nome, it.tipo, it.valorInvestido, it.valorAtual) })
        t("recorrencias", listOf("id", "descricao", "valor", "tipo", "conta", "categoria", "dia_vencimento", "cartao_id", "ultimo_lancamento", "pausada", "pausada_ate"),
            b.despesasFixas.orEmpty().map { listOf(it.id, it.descricao, it.valor, it.tipo.name, it.conta, it.categoria, it.diaVencimento, it.cartaoId, it.ultimaDataLancamento, it.pausada, it.pausadaAte) })
        t("dividas", listOf("id", "nome", "tipo", "credor", "saldo_devedor", "valor_original", "juros_mensal_pct", "parcela_minima", "parcelas_restantes", "dia_vencimento", "conta", "ativa"),
            b.dividas.orEmpty().map { listOf(it.id, it.nome, it.tipo, it.credor, it.saldoDevedor, it.valorOriginal, it.taxaJurosMensal, it.parcelaMinima, it.parcelasRestantes, it.diaVencimento, it.conta, it.ativa) })
        t("pessoas", listOf("id", "nome", "contato"), b.pessoas.orEmpty().map { listOf(it.id, it.nome, it.contato) })
        t("acertos", listOf("id", "pessoa_id", "tipo", "valor", "descricao", "data", "despesa_id", "quitado", "quitado_em", "grupo_rateio"),
            b.acertos.orEmpty().map { listOf(it.id, it.pessoaId, it.tipo, it.valor, it.descricao, data(it.data, fuso), it.despesaId, it.quitado, data(it.quitadoEm, fuso), it.grupoRateio) })
        t("projetos", listOf("id", "nome", "tag", "orcamento", "inicio", "fim", "ativo"),
            b.projetos.orEmpty().map { listOf(it.id, it.nome, it.tag, it.orcamento, data(it.inicio, fuso), data(it.fim, fuso), it.ativo) })
        t("regras", listOf("id", "padrao", "tipo_match", "categoria", "conta", "tipo", "ativa", "prioridade"),
            b.regrasCategoria.orEmpty().map { listOf(it.id, it.padrao, it.tipoMatch, it.categoria, it.conta, it.tipo, it.ativa, it.prioridade) })
        t("transferencias_agendadas", listOf("id", "data", "origem", "destino", "valor", "executada"),
            b.transferenciasAgendadas.orEmpty().map { listOf(it.id, data(it.dataAgendada, fuso), it.contaOrigem, it.contaDestino, it.valor, it.executada) })
        t("patrimonio", listOf("id", "data", "valor_total", "mes"),
            b.patrimonio.orEmpty().map { listOf(it.id, data(it.dataMillis, fuso), it.valorTotal, it.mesReferencia) })
        t("lixeira", listOf("id", "descricao", "valor", "excluido_em"),
            b.lixeira.orEmpty().map { listOf(it.id, it.descricao, it.valor, data(it.excluidoEm, fuso)) })
        return m
    }

    /** Monta o ZIP (deflate) com os arquivos [conteudos] (nome -> texto UTF-8). */
    fun zip(conteudos: Map<String, String>): ByteArray {
        val saida = ByteArrayOutputStream()
        ZipOutputStream(saida).use { z ->
            conteudos.forEach { (nome, texto) ->
                z.putNextEntry(ZipEntry(nome))
                z.write(texto.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return saida.toByteArray()
    }

    fun zipDoBackup(b: BackupDto, fuso: TimeZone = TimeZone.getDefault()): ByteArray = zip(csvs(b, fuso))

    /** Grava o ZIP em `cache/extratos/` (pasta já exposta pelo FileProvider) para compartilhar. */
    fun exportar(context: Context, b: BackupDto, agora: Long = System.currentTimeMillis()): File {
        val pasta = File(context.cacheDir, "extratos").apply { mkdirs() }
        val nome = "meu_dinheiro_tudo_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(agora)) + ".zip"
        return File(pasta, nome).also { it.writeBytes(zipDoBackup(b)) }
    }
}
