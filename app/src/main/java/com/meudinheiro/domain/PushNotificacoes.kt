package com.meudinheiro.domain

import java.text.NumberFormat
import java.util.Locale

/**
 * R65 — regras PURAS das notificações push (FCM): preferências por tipo, decisão de registrar o token, montagem do documento
 * `users/<uid>/dispositivos/<id>` e textos. Sem Android/Firebase: testado na JVM.
 */
enum class TipoPush(val codigo: String) {
    VENCIMENTOS("VENCIMENTOS"),
    ORCAMENTO("ORCAMENTO"),
    RESUMO_SEMANAL("RESUMO_SEMANAL"),
    CONVITE("CONVITE");

    companion object {
        fun deCodigo(c: String?): TipoPush? = entries.firstOrNull { it.codigo == c }
    }
}

/** Preferências por tipo (todas ligadas por padrão; o interruptor GERAL do push é que nasce desligado). */
data class PushPrefs(
    val vencimentos: Boolean = true,
    val orcamento: Boolean = true,
    val resumoSemanal: Boolean = true,
    val convites: Boolean = true
) {
    fun permite(tipo: TipoPush): Boolean = when (tipo) {
        TipoPush.VENCIMENTOS -> vencimentos
        TipoPush.ORCAMENTO -> orcamento
        TipoPush.RESUMO_SEMANAL -> resumoSemanal
        TipoPush.CONVITE -> convites
    }

    fun com(tipo: TipoPush, ligado: Boolean): PushPrefs = when (tipo) {
        TipoPush.VENCIMENTOS -> copy(vencimentos = ligado)
        TipoPush.ORCAMENTO -> copy(orcamento = ligado)
        TipoPush.RESUMO_SEMANAL -> copy(resumoSemanal = ligado)
        TipoPush.CONVITE -> copy(convites = ligado)
    }

    /** Campo `prefs` do documento do dispositivo (chaves lidas pela Cloud Function). */
    fun toDoc(): Map<String, Any> = mapOf(
        "vencimentos" to vencimentos, "orcamento" to orcamento, "resumoSemanal" to resumoSemanal, "convites" to convites
    )

    companion object {
        /** Tolerante: ausente/valor de outro tipo = ligado (padrão). */
        fun deDoc(d: Map<*, *>?): PushPrefs = PushPrefs(
            vencimentos = d?.get("vencimentos") as? Boolean ?: true,
            orcamento = d?.get("orcamento") as? Boolean ?: true,
            resumoSemanal = d?.get("resumoSemanal") as? Boolean ?: true,
            convites = d?.get("convites") as? Boolean ?: true
        )
    }
}

object PushRegistro {
    const val PLATAFORMA = "android"

    /** Opt-in: só registra com usuário logado E nuvem ativa E interruptor ligado. Qualquer outra combinação = não registra (e remove). */
    fun deveRegistrar(logado: Boolean, nuvemAtiva: Boolean, interruptorLigado: Boolean): Boolean =
        logado && nuvemAtiva && interruptorLigado

    private val ID_VALIDO = Regex("^[A-Za-z0-9_-]{8,64}$")

    /**
     * ID estável do aparelho (nome do documento). Reaproveita o já guardado se for válido; senão gera um novo
     * (`and-<uuid sem hífens>`). O ID nunca depende do token (que muda) nem de dados pessoais.
     */
    fun idEstavel(salvo: String?, gerarUuid: () -> String): String {
        val s = salvo?.trim().orEmpty()
        if (ID_VALIDO.matches(s)) return s
        return "and-" + gerarUuid().replace("-", "").take(32)
    }

    /**
     * Documento do dispositivo (sem `atualizadoEm`, que é o serverTimestamp do Firestore, acrescentado por quem grava).
     * Dados mínimos: token, plataforma, modelo, versão do app, prefs e a raiz de dados em uso (a Function confere o vínculo).
     */
    fun montarDoc(token: String, modelo: String?, appVersao: String, prefs: PushPrefs, raizUid: String?, uid: String): Map<String, Any> {
        require(token.isNotBlank()) { "Token vazio" }
        val base = mutableMapOf<String, Any>(
            "token" to token,
            "plataforma" to PLATAFORMA,
            "appVersao" to appVersao.take(40),
            "prefs" to prefs.toDoc()
        )
        modelo?.trim()?.takeIf { it.isNotEmpty() }?.let { base["modelo"] = it.take(60) }
        raizUid?.takeIf { it.isNotBlank() && it != uid }?.let { base["raizUid"] = it }
        return base
    }
}

/** Texto de uma notificação push já pronta para o sistema. */
data class TextoPush(val tipo: TipoPush, val titulo: String, val corpo: String, val publico: String, val atrasado: Boolean = false, val estourado: Boolean = false)

object PushTextos {
    /** Tela bloqueada: genérico, NUNCA com valores nem nomes. */
    fun publico(tipo: TipoPush): String = when (tipo) {
        TipoPush.VENCIMENTOS -> "Você tem contas a pagar"
        TipoPush.ORCAMENTO -> "Aviso do seu orçamento"
        TipoPush.RESUMO_SEMANAL -> "Seu resumo da semana está pronto"
        TipoPush.CONVITE -> "Você tem um convite"
    }

    private fun reais(centavos: Long): String {
        val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        return "R$ " + nf.format(centavos / 100.0)
    }

    /**
     * Monta o texto a partir do `data` do FCM. O servidor manda só contagens/categoria (e, no resumo, centavos); os valores em
     * reais são formatados AQUI, só na versão privada. Dado inválido/desconhecido = null (a notificação é ignorada).
     */
    fun montar(data: Map<String, String>): TextoPush? {
        val tipo = TipoPush.deCodigo(data["tipo"]) ?: return null
        val titulo = data["titulo"]?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: return null
        val corpoServidor = data["corpo"]?.trim()?.take(200).orEmpty()
        return when (tipo) {
            TipoPush.RESUMO_SEMANAL -> {
                val entradas = data["entradasCentavos"]?.toLongOrNull()
                val saidas = data["saidasCentavos"]?.toLongOrNull()
                val top = data["topCategoria"]?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
                val corpo = if (entradas != null && saidas != null) {
                    "Entradas ${reais(entradas)} · Saídas ${reais(saidas)}" + (top?.let { " · Maior gasto: $it" } ?: "")
                } else corpoServidor
                TextoPush(tipo, titulo, corpo, publico(tipo))
            }
            TipoPush.VENCIMENTOS -> TextoPush(tipo, titulo, corpoServidor, publico(tipo), atrasado = (data["atrasadas"]?.toIntOrNull() ?: 0) > 0)
            TipoPush.ORCAMENTO -> TextoPush(tipo, titulo, corpoServidor, publico(tipo), estourado = data["limiar"] == "100")
            TipoPush.CONVITE -> TextoPush(tipo, titulo, corpoServidor, publico(tipo))
        }
    }
}
