package com.meudinheiro.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.meudinheiro.data.BackupDto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Natureza
import java.lang.reflect.Type
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serialização do backup (versão 2): datas em epoch ms (independente de idioma/fuso), ids preservados.
 * Também lê backups v1, onde `Date` foi gravado como texto dependente de localidade.
 */
object BackupJson {

    const val VERSAO_ATUAL = 2

    private object DateAdapter : JsonSerializer<Date>, JsonDeserializer<Date> {
        override fun serialize(src: Date, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            JsonPrimitive(src.time)

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Date {
            val prim = json.asJsonPrimitive
            if (prim.isNumber) return Date(prim.asLong)
            val texto = prim.asString.trim()
            texto.toLongOrNull()?.let { return Date(it) }
            return parseLegado(texto) ?: Date()
        }

        private fun parseLegado(texto: String): Date? {
            val formatos = listOf<DateFormat>(
                DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.US),
                DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault()),
                DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale("pt", "BR")),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )
            // O Gson antigo usava espaço estreito (U+202F) antes de AM/PM em JDKs recentes.
            val normalizado = texto.replace(' ', ' ')
            return formatos.firstNotNullOfOrNull { f -> runCatching { f.parse(normalizado) }.getOrNull() }
        }
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Date::class.java, DateAdapter)
        .create()

    fun toJson(backup: BackupDto): String = gson.toJson(backup.copy(versaoBackup = VERSAO_ATUAL))

    fun fromJson(json: String): BackupDto {
        val dto = try {
            gson.fromJson(json, BackupDto::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Formato de JSON inválido ou incompatível.", e)
        } ?: throw IllegalArgumentException("Arquivo de backup vazio.")
        return sanitizar(dto)
    }

    /** Gson ignora valores-padrão do Kotlin (campos ausentes ficam nulos): normaliza o que for legado. */
    private fun sanitizar(dto: BackupDto): BackupDto = dto.copy(
        despesas = dto.despesas?.map(::sanitizarDespesa)
    )

    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
    private fun sanitizarDespesa(d: Despesa): Despesa {
        val tipo: TipoDespesa = (d.tipo as TipoDespesa?) ?: TipoDespesa.DEBITO
        val descricao = (d.descricao as String?).orEmpty()
        val natureza = (d.natureza as String?)
            ?.takeIf { it in Natureza.TODAS }
            ?: classificarLegado(descricao, tipo)
        return d.copy(
            descricao = descricao,
            tipo = tipo,
            categoria = (d.categoria as String?) ?: "Geral",
            conta = (d.conta as String?) ?: "",
            pic = (d.pic as String?) ?: "default_pic",
            cartaoId = d.cartaoId?.takeIf { it != 0 },
            moedaOriginal = (d.moedaOriginal as String?) ?: "BRL",
            cotacaoNaData = if (d.cotacaoNaData > 0) d.cotacaoNaData else 1.0,
            natureza = natureza
        )
    }

    /** Mesma heurística da migração 1→2 para lançamentos internos gravados antes do campo `natureza`. */
    fun classificarLegado(descricao: String, tipo: TipoDespesa): String = when {
        descricao == "Saldo Inicial" && tipo == TipoDespesa.CREDITO -> Natureza.SALDO_INICIAL
        descricao.startsWith("Pagamento Fatura:") -> Natureza.PAGAMENTO_FATURA
        descricao.startsWith("Aporte: ") && tipo == TipoDespesa.DEBITO -> Natureza.APORTE_META
        descricao.startsWith("Estorno: Meta ") && tipo == TipoDespesa.CREDITO -> Natureza.RESGATE_META
        else -> Natureza.NORMAL
    }
}
