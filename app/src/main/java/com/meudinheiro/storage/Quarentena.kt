package com.meudinheiro.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** R87 — um registro local que a nuvem recusou. O dado local NUNCA é apagado; ele só deixa de bloquear a fila. */
data class RegistroQuarentena(
    val colecao: String,
    val id: String,
    val descricao: String,
    val motivo: String,
    /** Quando foi isolado pela primeira vez (epoch ms). */
    val desde: Long,
    /** Quantas vezes o envio foi recusado (1 = isolado agora; cresce a cada "tentar de novo" que falha). */
    val tentativas: Int,
    /** Hash local no momento da recusa: se o registro for editado, uma nova tentativa é liberada. */
    val hash: String,
    /** "Ignorado" pelo usuário: continua parado (não reenvia), mas não conta no aviso do status. */
    val ignorado: Boolean = false
) {
    val chave: String get() = "$colecao/$id"
}

/** Onde a quarentena é guardada entre execuções (SharedPreferences no app; fake nos testes). */
interface PersistenciaQuarentena {
    fun ler(): String?
    fun gravar(texto: String)
}

/**
 * R87 — registro de quarentena, persistente e thread-safe. Guarda em texto simples (sem Room: nenhuma migração, nenhum
 * risco para os dados do usuário). Regras que impedem loop/consumo extra:
 *  - um registro em quarentena NÃO é reenviado automaticamente ([bloqueia]);
 *  - só volta à fila por ação do usuário ([liberarTodos]), por atualização do app ([liberarSeNovaVersao], uma vez por
 *    versão) ou porque o registro foi editado ([liberarSeEditado]);
 *  - cada nova recusa aumenta [RegistroQuarentena.tentativas]; sucesso remove ([remover]).
 */
class Quarentena(private val persistencia: PersistenciaQuarentena) {

    private val trava = Any()
    private val registros = LinkedHashMap<String, RegistroQuarentena>()
    private var versao: String = ""

    /** Em memória: liberados para uma nova tentativa nesta execução do app. */
    private val liberados = HashSet<String>()

    private val _lista = MutableStateFlow<List<RegistroQuarentena>>(emptyList())

    /** Todos os registros (inclusive ignorados), mais antigos primeiro. */
    val lista: StateFlow<List<RegistroQuarentena>> = _lista.asStateFlow()

    init {
        carregar()
    }

    /** Quantos precisam da atenção do usuário (não conta os ignorados). */
    val pendentesDeAtencao: Int get() = _lista.value.count { !it.ignorado }

    /** O envio deste registro deve ser pulado? */
    fun bloqueia(colecao: String, id: String): Boolean = synchronized(trava) {
        val chave = "$colecao/$id"
        chave in registros && chave !in liberados
    }

    fun contem(colecao: String, id: String): Boolean = synchronized(trava) { "$colecao/$id" in registros }

    /** Chaves (`colecao/id`) em quarentena, para consulta rápida na reconciliação. */
    fun chaves(): Set<String> = synchronized(trava) { registros.keys.toSet() }

    fun hashDe(colecao: String, id: String): String? = synchronized(trava) { registros["$colecao/$id"]?.hash }

    /** Registra (ou atualiza) uma recusa. Reenvio que falha de novo soma tentativa e preserva `desde` e `ignorado`. */
    fun registrar(item: ItemEnvio, motivo: String, agora: Long) = registrar(
        item.colecao, item.id, IsolamentoLote.descrever(item), motivo, item.hash, agora
    )

    fun registrar(colecao: String, id: String, descricao: String, motivo: String, hash: String, agora: Long) {
        synchronized(trava) {
            val chave = "$colecao/$id"
            val antigo = registros[chave]
            registros[chave] = RegistroQuarentena(
                colecao = colecao, id = id, descricao = descricao, motivo = motivo,
                desde = antigo?.desde ?: agora,
                tentativas = (antigo?.tentativas ?: 0) + 1,
                hash = hash,
                ignorado = antigo?.ignorado ?: false
            )
            liberados.remove(chave)
            salvar()
        }
    }

    /** Enviado com sucesso (ou deixou de existir): sai da quarentena. */
    fun remover(colecao: String, id: String) {
        synchronized(trava) {
            val chave = "$colecao/$id"
            if (registros.remove(chave) != null) {
                liberados.remove(chave)
                salvar()
            }
        }
    }

    /** "Tentar de novo": libera todos (inclusive ignorados) para o próximo envio. Os dados continuam no lugar. */
    fun liberarTodos(): Int = synchronized(trava) {
        if (registros.isEmpty()) return 0
        registros.replaceAll { _, r -> r.copy(ignorado = false) }
        liberados += registros.keys
        salvar()
        registros.size
    }

    /** "Ignorar/limpar": os registros param de aparecer no status; seguem sem ser reenviados. Nada é apagado. */
    fun ignorarTodos() {
        synchronized(trava) {
            if (registros.isEmpty()) return
            registros.replaceAll { _, r -> r.copy(ignorado = true) }
            liberados.clear()
            salvar()
        }
    }

    /**
     * Atualização do app: libera UMA vez por versão (a nova versão pode ter corrigido o motivo). Se a nova tentativa
     * falhar, volta para a quarentena sem ciclo, pois a versão já foi marcada.
     * @return true se liberou algo.
     */
    fun liberarSeNovaVersao(versaoAtual: String): Boolean = synchronized(trava) {
        if (versao == versaoAtual) return false
        val tinha = versao.isNotEmpty() && registros.isNotEmpty()
        versao = versaoAtual
        if (tinha) {
            registros.replaceAll { _, r -> r.copy(ignorado = false) }
            liberados += registros.keys
        }
        salvar()
        tinha
    }

    /** O registro foi editado desde a recusa (hash diferente): vale uma nova tentativa. */
    fun liberarSeEditado(colecao: String, id: String, hashAtual: String): Boolean = synchronized(trava) {
        val chave = "$colecao/$id"
        val r = registros[chave] ?: return false
        if (r.hash == hashAtual || chave in liberados) return false
        registros[chave] = r.copy(ignorado = false)
        liberados += chave
        salvar()
        true
    }

    /** Troca/saída de conta: a quarentena de outro usuário não vale para este. */
    fun limpar() {
        synchronized(trava) {
            registros.clear(); liberados.clear()
            salvar()
        }
    }

    // ------------------------------------------------------------------ persistência

    private fun salvar() {
        _lista.value = registros.values.sortedBy { it.desde }
        runCatching { persistencia.gravar(codificar(versao, registros.values)) }
    }

    private fun carregar() {
        val texto = runCatching { persistencia.ler() }.getOrNull().orEmpty()
        val (v, lidos) = decodificar(texto)
        versao = v
        lidos.forEach { registros[it.chave] = it }
        _lista.value = registros.values.sortedBy { it.desde }
    }

    companion object {
        private const val CAMPO = '\u001F'
        private const val LINHA = '\u001E'
        private const val CABECALHO = "Q1"

        private fun limpo(s: String) = s.replace(CAMPO, ' ').replace(LINHA, ' ').replace('\n', ' ')

        fun codificar(versao: String, itens: Collection<RegistroQuarentena>): String =
            (listOf("$CABECALHO$CAMPO${limpo(versao)}") + itens.map {
                listOf(
                    limpo(it.colecao), limpo(it.id), limpo(it.descricao), limpo(it.motivo),
                    it.desde.toString(), it.tentativas.toString(), limpo(it.hash), if (it.ignorado) "1" else "0"
                ).joinToString(CAMPO.toString())
            }).joinToString(LINHA.toString())

        /** Tolerante: texto vazio/corrompido vira lista vazia (nunca lança). */
        fun decodificar(texto: String): Pair<String, List<RegistroQuarentena>> {
            if (texto.isBlank()) return "" to emptyList()
            val linhas = texto.split(LINHA)
            val cab = linhas.first().split(CAMPO)
            if (cab.firstOrNull() != CABECALHO) return "" to emptyList()
            val versao = cab.getOrElse(1) { "" }
            val itens = linhas.drop(1).mapNotNull { l ->
                val p = l.split(CAMPO)
                if (p.size < 8) return@mapNotNull null
                val desde = p[4].toLongOrNull() ?: return@mapNotNull null
                val tent = p[5].toIntOrNull() ?: return@mapNotNull null
                RegistroQuarentena(p[0], p[1], p[2], p[3], desde, tent, p[6], p[7] == "1")
            }
            return versao to itens
        }
    }
}
