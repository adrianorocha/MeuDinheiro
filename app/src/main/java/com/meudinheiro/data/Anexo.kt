package com.meudinheiro.data

import com.google.gson.annotations.SerializedName

/**
 * R60 — anexo (foto/comprovante) de um lançamento. Só metadados: o arquivo fica no Firebase Storage
 * (`caminho` = `usuarios/<uid>/anexos/<despesaId>/<id>.<ext>`) ou, sem nuvem, no armazenamento interno do app
 * (`caminho` = `local:anexos/<despesaId>/<id>.<ext>`). `@SerializedName` mantém os nomes estáveis no Gson (Room/backup).
 */
data class Anexo(
    @SerializedName("id") val id: String,
    @SerializedName("nome") val nome: String,
    /** MIME: image/jpeg, image/png ou application/pdf. */
    @SerializedName("tipo") val tipo: String,
    /** Bytes. */
    @SerializedName("tamanho") val tamanho: Long,
    @SerializedName("caminho") val caminho: String,
    /** epoch ms. */
    @SerializedName("criadoEm") val criadoEm: Long
)
