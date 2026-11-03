package com.meudinheiro.domain

import com.meudinheiro.data.BackupDto
import java.util.Calendar
import java.util.TimeZone

/** R66 — tipo do backup guardado no aparelho. */
enum class TipoBackup(val rotulo: String) {
    DIARIO("Diário"), SEMANAL("Semanal"), MANUAL("Manual"), SEGURANCA("Segurança (antes de restaurar)")
}

/** R66 — metadados de um backup (arquivo `.meta.json` ao lado do `.json`; o backup em si não muda de formato). */
data class MetaBackup(
    val arquivo: String,
    val criadoEm: Long,
    val versaoSchema: Int,
    val contagens: Map<String, Int>,
    val tamanho: Long,
    val tipo: TipoBackup
)

data class ResultadoRetencao(val manter: List<MetaBackup>, val apagar: List<MetaBackup>)

/** R66 — regras puras de retenção e de contagem por coleção. */
object BackupRetencao {
    const val DIARIOS_MANTIDOS = 7
    const val SEMANAIS_MANTIDOS = 4
    const val DIAS_SEGURANCA = 30
    private const val DIA_MS = 86_400_000L

    /** O backup automático de domingo vira SEMANAL; os demais dias são DIARIO. */
    fun tipoAutomatico(agora: Long, fuso: TimeZone = TimeZone.getDefault()): TipoBackup {
        val c = Calendar.getInstance(fuso).apply { timeInMillis = agora }
        return if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) TipoBackup.SEMANAL else TipoBackup.DIARIO
    }

    /**
     * Decide o que manter e o que apagar: os 7 DIARIO e os 4 SEMANAL mais recentes ficam; o excedente sai.
     * MANUAL nunca é apagado automaticamente; SEGURANCA só depois de 30 dias.
     */
    fun retencao(lista: List<MetaBackup>, agora: Long): ResultadoRetencao {
        val apagar = mutableListOf<MetaBackup>()
        fun excedente(tipo: TipoBackup, max: Int) {
            lista.filter { it.tipo == tipo }.sortedByDescending { it.criadoEm }.drop(max).let(apagar::addAll)
        }
        excedente(TipoBackup.DIARIO, DIARIOS_MANTIDOS)
        excedente(TipoBackup.SEMANAL, SEMANAIS_MANTIDOS)
        lista.filter { it.tipo == TipoBackup.SEGURANCA && agora - it.criadoEm > DIAS_SEGURANCA * DIA_MS }.let(apagar::addAll)
        val fora = apagar.toSet()
        return ResultadoRetencao(manter = lista.filter { it !in fora }, apagar = apagar)
    }

    /** Contagens por coleção, na ordem exibida ao usuário. */
    fun contagens(b: BackupDto): Map<String, Int> = linkedMapOf(
        "lancamentos" to (b.despesas?.size ?: 0),
        "contas" to (b.contas?.size ?: 0),
        "cartoes" to (b.cartoes?.size ?: 0),
        "categorias" to (b.categorias?.size ?: 0),
        "orcamentos" to (b.orcamentos?.size ?: 0),
        "metas" to (b.metas?.size ?: 0),
        "investimentos" to (b.investimentos?.size ?: 0),
        "recorrencias" to (b.despesasFixas?.size ?: 0),
        "dividas" to (b.dividas?.size ?: 0),
        "pessoas" to (b.pessoas?.size ?: 0),
        "acertos" to (b.acertos?.size ?: 0),
        "projetos" to (b.projetos?.size ?: 0),
        "regras" to (b.regrasCategoria?.size ?: 0),
        "lixeira" to (b.lixeira?.size ?: 0)
    )

    private val ROTULOS = mapOf(
        "lancamentos" to "Lançamentos", "contas" to "Contas", "cartoes" to "Cartões", "categorias" to "Categorias",
        "orcamentos" to "Orçamentos", "metas" to "Metas", "investimentos" to "Investimentos", "recorrencias" to "Recorrências",
        "dividas" to "Dívidas", "pessoas" to "Pessoas", "acertos" to "Acertos", "projetos" to "Projetos", "regras" to "Regras",
        "lixeira" to "Lixeira"
    )

    fun rotulo(chave: String) = ROTULOS[chave] ?: chave

    /** Resumo curto, ex.: "120 lançamentos · 3 contas" (omite zeros, no máximo [max] itens). */
    fun resumo(contagens: Map<String, Int>, max: Int = 4): String =
        contagens.filter { it.value > 0 }.entries.take(max).joinToString(" · ") { "${it.value} ${rotulo(it.key).lowercase()}" }
            .ifBlank { "sem dados" }

    /** Linhas "coleção: n" para a confirmação de restauração. */
    fun linhas(contagens: Map<String, Int>): List<String> = contagens.map { "${rotulo(it.key)}: ${it.value}" }
}
