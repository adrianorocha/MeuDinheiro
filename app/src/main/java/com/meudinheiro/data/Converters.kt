package com.meudinheiro.data

import androidx.room.TypeConverter
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
}