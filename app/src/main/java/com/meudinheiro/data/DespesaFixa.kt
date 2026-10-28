package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "despesas_fixas")
data class DespesaFixa(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val descricao: String,
    val valor: Double,
    val conta: String,
    val categoria: String,
    val pic: String,
    val tipo: TipoDespesa,
    val diaVencimento: Int, // Dia do mês (1 a 31) que deve ser lançada
    val ultimaDataLancamento: Date? = null, // Para saber se já lançamos neste mês
    /** Origem do pagamento: preenchido = cada ocorrência é uma compra neste cartão (físico ou virtual); nulo = débito na [conta]. */
    val cartaoId: Int? = null,
    /** R47 — recorrência pausada (dados antigos = não pausada). */
    val pausada: Boolean = false,
    /** R47 — retomada automática quando `agora >= pausadaAte`; nulo = só retoma manualmente. */
    val pausadaAte: Date? = null
)