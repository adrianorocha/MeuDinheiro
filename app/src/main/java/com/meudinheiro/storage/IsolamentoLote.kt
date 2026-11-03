package com.meudinheiro.storage

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException

/**
 * R86/R87 — um registro a enviar (gravar ou excluir) ao Firestore. [doc] nulo = exclusão.
 * [hash] é o hash do conteúdo local no momento do envio (vazio para exclusões).
 */
class ItemEnvio(val colecao: String, val id: String, val doc: Doc?, val hash: String) {
    val chave: String get() = "$colecao/$id"
    val exclusao: Boolean get() = doc == null
}

/**
 * R87 — isolamento de registros recusados (sem I/O próprio; o envio é injetado, então é testável com fakes).
 *
 * Um lote atômico falha inteiro quando UM registro é recusado (regras/validação). Ao falhar por motivo NÃO
 * transitório, o lote é dividido ao meio (bisseção) até achar o(s) registro(s) culpado(s); o resto é enviado
 * normalmente. Erros transitórios (rede/servidor) e sistêmicos (regras não publicadas, banco inexistente) não são
 * culpa de um registro e continuam com o recuo da sessão.
 */
object IsolamentoLote {

    enum class Classe {
        /** Rede/servidor: tentar de novo depois (recuo da sessão). */
        TRANSITORIO,

        /** O registro (ou o conteúdo dele) foi recusado: isolar. */
        RECUSADO,

        /** Problema que afeta qualquer registro (ex.: banco não configurado): não adianta isolar. */
        SISTEMICO
    }

    /** Acima disto, num único lote, a recusa deixa de ser "um registro ruim" e vira problema geral (ex.: permissão). */
    const val LIMITE_RECUSADOS = 20

    /** Nome do código do Firestore (PERMISSION_DENIED…) ou null. Isolado para os testes não carregarem o SDK. */
    fun codigoFirestore(e: Throwable): String? = (e as? FirebaseFirestoreException)?.code?.name

    fun classificar(e: Throwable, codigoDe: (Throwable) -> String? = ::codigoFirestore): Classe =
        when (codigoDe(e)) {
            "PERMISSION_DENIED", "INVALID_ARGUMENT", "OUT_OF_RANGE", "NOT_FOUND", "ALREADY_EXISTS", "DATA_LOSS" -> Classe.RECUSADO
            "FAILED_PRECONDITION", "UNAUTHENTICATED", "UNIMPLEMENTED" -> Classe.SISTEMICO
            // Valor que o SDK não consegue serializar (lançado ao montar o lote): culpa do registro.
            null -> if (e is IllegalArgumentException) Classe.RECUSADO else Classe.TRANSITORIO
            else -> Classe.TRANSITORIO // UNAVAILABLE, DEADLINE_EXCEEDED, ABORTED, RESOURCE_EXHAUSTED, CANCELLED…
        }

    class Recusado(val item: ItemEnvio, val erro: Throwable)

    class Resultado(
        /** Registros isolados (nunca vazio com [sistemico]: nesse caso a lista é descartada). */
        val recusados: List<Recusado>,
        /** Erro transitório/sistêmico que interrompeu o processo (null = terminou). */
        val falha: Throwable?,
        val sistemico: Boolean,
        /** Quantidade de commits tentados (para teste/diagnóstico). */
        val tentativas: Int
    ) {
        val ok: Boolean get() = falha == null
    }

    /** Envia [lote] atomicamente; lança em caso de falha (a classificação é feita por [classificar]). */
    fun interface Enviador {
        suspend fun enviar(lote: List<ItemEnvio>)
    }

    /**
     * Tenta enviar [lote]; se um subconjunto for recusado, isola-o por bisseção e envia o resto.
     * Cada registro bom é enviado exatamente uma vez com sucesso; o custo extra só existe quando há falha.
     */
    suspend fun processar(
        lote: List<ItemEnvio>,
        enviador: Enviador,
        limite: Int = LIMITE_RECUSADOS,
        codigoDe: (Throwable) -> String? = ::codigoFirestore
    ): Resultado {
        val recusados = mutableListOf<Recusado>()
        val pilha = ArrayDeque<List<ItemEnvio>>()
        if (lote.isNotEmpty()) pilha.addLast(lote)
        var tentativas = 0
        while (pilha.isNotEmpty()) {
            val atual = pilha.removeFirst()
            tentativas++
            try {
                enviador.enviar(atual)
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (classificar(e, codigoDe)) {
                    Classe.TRANSITORIO -> return Resultado(recusados, e, sistemico = false, tentativas = tentativas)
                    Classe.SISTEMICO -> return Resultado(emptyList(), e, sistemico = true, tentativas = tentativas)
                    Classe.RECUSADO -> {
                        if (atual.size == 1) {
                            recusados += Recusado(atual[0], e)
                            if (recusados.size > limite) {
                                // Recusa em massa: provavelmente permissão/regras, não registros ruins. Não põe tudo em quarentena.
                                return Resultado(emptyList(), e, sistemico = true, tentativas = tentativas)
                            }
                        } else {
                            val meio = atual.size / 2
                            // Primeira metade antes da segunda (ArrayDeque: a ordem de envio é preservada).
                            pilha.addFirst(atual.subList(meio, atual.size))
                            pilha.addFirst(atual.subList(0, meio))
                        }
                    }
                }
            }
        }
        return Resultado(recusados, null, sistemico = false, tentativas = tentativas)
    }

    /** Motivo curto e legível para mostrar ao usuário. */
    fun motivo(e: Throwable, codigoDe: (Throwable) -> String? = ::codigoFirestore): String = when (codigoDe(e)) {
        "PERMISSION_DENIED" -> "Recusado pelas regras de segurança da nuvem"
        "INVALID_ARGUMENT" -> "Dados inválidos para a nuvem"
        "OUT_OF_RANGE" -> "Valor fora do limite aceito pela nuvem"
        null -> if (e is IllegalArgumentException) "Dado que a nuvem não aceita" else (e.message ?: "Recusado")
        else -> e.message ?: "Recusado pela nuvem"
    }.take(160)

    /** Descrição curta de um registro (para a lista de quarentena). */
    fun descrever(item: ItemEnvio): String {
        val doc = item.doc ?: return "exclusão de ${item.id}"
        val base = listOf("descricao", "nome", "titulo", "title", "banco", "categoria")
            .firstNotNullOfOrNull { k -> doc[k]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
            ?: "id ${item.id}"
        val valor = (doc["valor"] as? Number)?.toDouble()
        val sufixo = if (valor != null) " · R$ " + "%.2f".format(java.util.Locale("pt", "BR"), valor) else ""
        return (base.take(60) + sufixo)
    }
}
