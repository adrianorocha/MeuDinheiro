package com.meudinheiro.storage

/** Onde os dados são gravados. Escolhido pelo usuário em Configurações → Armazenamento de dados. */
enum class StorageMode(val rotulo: String, val descricao: String) {
    LOCAL(
        "Somente neste celular",
        "Os dados ficam apenas no armazenamento do aparelho. Use Backup para guardar cópias."
    ),
    FIREBASE(
        "Nuvem (Firebase)",
        "Os dados são gravados no Firebase e sincronizados com o portal web e outros aparelhos. Continua funcionando offline."
    );

    companion object {
        fun deNome(nome: String?): StorageMode = entries.firstOrNull { it.name == nome } ?: LOCAL
    }
}

/** Estado exibido ao usuário. */
sealed interface SyncStatus {
    /** Modo local: nada é enviado. */
    data object Desligado : SyncStatus

    /** Modo nuvem escolhido, mas sem login. */
    data object AguardandoLogin : SyncStatus

    data object Conectando : SyncStatus
    data class Sincronizado(val em: Long) : SyncStatus
    data class Erro(val mensagem: String) : SyncStatus
}
