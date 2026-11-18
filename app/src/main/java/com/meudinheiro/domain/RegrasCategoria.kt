package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.RegraCategoria

/**
 * R55 — Regras de categorização automática. Funções puras; espelham `portal/src/lib/finance/regras.ts`
 * (mesmos cenários em `RegrasCategoriaTest` e `regras.test.ts`). Texto comparado sem acento, sem diferenciar caixa.
 */
object Regras {
    const val CONTEM = "CONTEM"
    const val COMECA_COM = "COMECA_COM"
    const val IGUAL = "IGUAL"
    val TIPOS_MATCH = listOf(CONTEM, COMECA_COM, IGUAL)
    const val MIN_VEZES_APRENDIZADO = 2

    fun rotuloMatch(t: String) = when (t) {
        COMECA_COM -> "começa com"
        IGUAL -> "é igual a"
        else -> "contém"
    }

    private fun peso(t: String) = when (t) {
        IGUAL -> 2
        COMECA_COM -> 1
        else -> 0
    }

    data class Entrada(val descricao: String, val conta: String? = null, val tipo: String? = null)

    private fun casa(padraoNorm: String, tipoMatch: String, descNorm: String): Boolean {
        if (padraoNorm.isEmpty()) return false
        return when (tipoMatch) {
            CONTEM -> descNorm.contains(padraoNorm)
            COMECA_COM -> descNorm.startsWith(padraoNorm)
            IGUAL -> descNorm == padraoNorm
            else -> false
        }
    }

    /** A regra se aplica? (ativa, categoria e padrão não vazios, casamento, restrições de conta e tipo.) */
    fun casa(r: RegraCategoria, e: Entrada): Boolean {
        if (!r.ativa || r.categoria.isBlank()) return false
        if (!casa(Texto.normalizar(r.padrao), r.tipoMatch, Texto.normalizar(e.descricao))) return false
        val conta = r.conta?.trim()
        if (!conta.isNullOrEmpty() && conta != (e.conta ?: "").trim()) return false
        if (!r.tipo.isNullOrEmpty() && r.tipo != e.tipo) return false
        return true
    }

    private fun restricoes(r: RegraCategoria) = (if (!r.conta.isNullOrBlank()) 1 else 0) + (if (!r.tipo.isNullOrEmpty()) 1 else 0)

    private fun vence(a: RegraCategoria, b: RegraCategoria): Boolean {
        if (a.prioridade != b.prioridade) return a.prioridade > b.prioridade
        val la = Texto.normalizar(a.padrao).length
        val lb = Texto.normalizar(b.padrao).length
        if (la != lb) return la > lb
        if (peso(a.tipoMatch) != peso(b.tipoMatch)) return peso(a.tipoMatch) > peso(b.tipoMatch)
        if (restricoes(a) != restricoes(b)) return restricoes(a) > restricoes(b)
        return a.id < b.id
    }

    /**
     * Regra vencedora: maior prioridade; empate = padrão normalizado mais longo; depois tipo de casamento mais específico
     * (IGUAL > COMECA_COM > CONTEM); depois restrição de conta/tipo; por fim a de menor id (mais antiga).
     */
    fun aplicar(regras: List<RegraCategoria>, e: Entrada): RegraCategoria? {
        var melhor: RegraCategoria? = null
        for (r in regras) {
            if (!casa(r, e)) continue
            if (melhor == null || vence(r, melhor)) melhor = r
        }
        return melhor
    }

    fun categoria(regras: List<RegraCategoria>, e: Entrada): String? = aplicar(regras, e)?.categoria?.trim()

    /** Mensagem de erro ou `null` se a regra é válida. */
    fun validar(padrao: String, categoria: String, tipoMatch: String): String? = when {
        Texto.normalizar(padrao).isEmpty() -> "Informe o texto da regra."
        padrao.trim().length > 80 -> "O texto da regra deve ter até 80 caracteres."
        categoria.isBlank() -> "Informe a categoria."
        tipoMatch !in TIPOS_MATCH -> "Tipo de comparação inválido."
        else -> null
    }

    // ------------------------------------------------ aplicar às existentes

    data class Mudanca(val id: Long, val descricao: String, val de: String, val para: String, val regraId: Int)
    data class Anterior(val id: Long, val categoria: String)

    /** Prévia: lançamentos NORMAL cuja categoria mudaria. Não altera nada. */
    fun previa(despesas: List<Despesa>, regras: List<RegraCategoria>): List<Mudanca> = despesas.mapNotNull { d ->
        if (d.natureza != Natureza.NORMAL) return@mapNotNull null
        val r = aplicar(regras, Entrada(d.descricao, d.conta, d.tipo.name)) ?: return@mapNotNull null
        val para = r.categoria.trim()
        if (Texto.normalizar(para) == Texto.normalizar(d.categoria)) null else Mudanca(d.id, d.descricao, d.categoria, para, r.id)
    }

    /** Aplica as mudanças de uma prévia confirmada; devolve as novas despesas e o necessário para desfazer. */
    fun aplicarMudancas(despesas: List<Despesa>, mudancas: List<Mudanca>): Pair<List<Despesa>, List<Anterior>> {
        val por = mudancas.associateBy { it.id }
        val desfazer = ArrayList<Anterior>()
        val novas = despesas.map { d ->
            val m = por[d.id]
            if (m == null || d.natureza != Natureza.NORMAL) d else {
                desfazer += Anterior(d.id, d.categoria)
                d.copy(categoria = m.para)
            }
        }
        return novas to desfazer
    }

    fun desfazer(despesas: List<Despesa>, anteriores: List<Anterior>): List<Despesa> {
        val por = anteriores.associate { it.id to it.categoria }
        return despesas.map { d -> por[d.id]?.let { d.copy(categoria = it) } ?: d }
    }

    // ------------------------------------------------ aprendizado leve

    data class Sugestao(val padrao: String, val categoria: String, val vezes: Int)

    /**
     * Depois de o usuário trocar a categoria de um lançamento: se já há 2+ lançamentos NORMAL com a mesma descrição
     * normalizada e esta MESMA categoria (contando o editado) e nenhuma regra ativa já dá essa categoria para a
     * descrição, sugere "Criar regra". Nunca cria sozinho.
     */
    fun sugerirAprendida(despesas: List<Despesa>, regras: List<RegraCategoria>, editado: Despesa): Sugestao? {
        val padrao = Texto.normalizar(editado.descricao)
        val categoria = editado.categoria.trim()
        if (padrao.isEmpty() || categoria.isEmpty()) return null
        val vezes = despesas.count {
            it.natureza == Natureza.NORMAL && Texto.normalizar(it.descricao) == padrao &&
                Texto.normalizar(it.categoria) == Texto.normalizar(categoria)
        }
        if (vezes < MIN_VEZES_APRENDIZADO) return null
        val atual = aplicar(regras, Entrada(editado.descricao, editado.conta, editado.tipo.name))
        if (atual != null && Texto.normalizar(atual.categoria) == Texto.normalizar(categoria)) return null
        if (regras.any { Texto.normalizar(it.padrao) == padrao && Texto.normalizar(it.categoria) == Texto.normalizar(categoria) }) return null
        return Sugestao(padrao, categoria, vezes)
    }

    fun novaRegraAprendida(s: Sugestao, agora: Long) =
        RegraCategoria(padrao = s.padrao, tipoMatch = IGUAL, categoria = s.categoria, ativa = true, prioridade = 0, criadaEm = agora)
}
