package com.meudinheiro.storage

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class Membro(val uid: String, val email: String)
data class Convite(val donoUid: String, val donoEmail: String)

/** Falha de compartilhamento com mensagem pronta para o usuário. */
class CompartilhamentoException(message: String) : Exception(message)

/**
 * R32 — conta compartilhada. Os dados continuam em `users/{donoUid}/…`; o dono libera outra pessoa criando
 * `users/{donoUid}/membros/{membroUid}` e um convite em `perfis/{emailDoMembro}/convites/{donoUid}`.
 * `perfis/{email}` (uid de quem já entrou no app/portal) é a forma de achar o uid sem listar usuários.
 */
class Compartilhamento(
    private val auth: CloudAuth,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun email(e: String) = e.trim().lowercase()

    private fun uidLogado() = auth.uidAtual ?: throw CompartilhamentoException("Entre na sua conta da nuvem primeiro.")
    private fun emailLogado() = auth.emailAtual?.let(::email) ?: throw CompartilhamentoException("Sua conta não tem e-mail.")

    /** Publica "este e-mail = este uid" para que possam te convidar. Chamado ao logar. */
    suspend fun registrarPerfil() {
        val uid = auth.uidAtual ?: return
        val mail = auth.emailAtual?.let(::email) ?: return
        firestore.collection("perfis").document(mail).set(mapOf("uid" to uid, "email" to mail)).await()
    }

    suspend fun convidar(emailMembro: String) {
        val dono = uidLogado()
        val donoEmail = emailLogado()
        val alvo = email(emailMembro)
        if (alvo.isEmpty() || !alvo.contains('@')) throw CompartilhamentoException("Informe um e-mail válido.")
        if (alvo == donoEmail) throw CompartilhamentoException("Esse é o seu próprio e-mail.")

        val perfil = firestore.collection("perfis").document(alvo).get().await()
        val uidMembro = perfil.getString("uid")
            ?: throw CompartilhamentoException("Essa pessoa ainda não entrou no Meu Dinheiro. Peça para ela entrar no app ou no portal com esse e-mail e tente de novo.")

        val agora = System.currentTimeMillis()
        val batch = firestore.batch()
        batch.set(
            firestore.collection("users").document(dono).collection("membros").document(uidMembro),
            mapOf("uid" to uidMembro, "email" to alvo, "criadoEm" to agora)
        )
        batch.set(
            firestore.collection("perfis").document(alvo).collection("convites").document(dono),
            mapOf("donoUid" to dono, "donoEmail" to donoEmail, "criadoEm" to agora)
        )
        batch.commit().await()
    }

    suspend fun membros(): List<Membro> =
        firestore.collection("users").document(uidLogado()).collection("membros").get().await().documents
            .mapNotNull { d -> d.getString("uid")?.let { Membro(it, d.getString("email").orEmpty()) } }

    suspend fun removerMembro(membro: Membro) {
        val batch = firestore.batch()
        batch.delete(firestore.collection("users").document(uidLogado()).collection("membros").document(membro.uid))
        if (membro.email.isNotBlank()) {
            batch.delete(firestore.collection("perfis").document(email(membro.email)).collection("convites").document(uidLogado()))
        }
        batch.commit().await()
    }

    suspend fun convitesRecebidos(): List<Convite> =
        firestore.collection("perfis").document(emailLogado()).collection("convites").get().await().documents
            .mapNotNull { d -> d.getString("donoUid")?.let { Convite(it, d.getString("donoEmail").orEmpty()) } }
}
