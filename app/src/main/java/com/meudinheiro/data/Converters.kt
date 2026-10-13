package com.meudinheiro.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromTipo(t: TipoDespesa?): String? = t?.name

    @TypeConverter
    fun toTipo(s: String?): TipoDespesa {
        if (s.isNullOrBlank()) return TipoDespesa.DEBITO
        return runCatching { TipoDespesa.valueOf(s) }.getOrDefault(TipoDespesa.DEBITO)
    }

    /** R56 — tags: lista de textos ⇄ array JSON (`["viagem","hotel"]`), igual ao array nativo do Firestore. */
    @TypeConverter
    fun fromTags(tags: List<String>?): String = if (tags.isNullOrEmpty()) "[]" else GSON.toJson(tags)

    @TypeConverter
    fun toTags(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { GSON.fromJson(json, Array<String>::class.java)?.filterNotNull() }.getOrNull().orEmpty()
    }

    private companion object {
        val GSON = Gson()
    }
}
