package com.meudinheiro.storage

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.meudinheiro.domain.ConviteCodigo
import com.meudinheiro.domain.Papel
import com.meudinheiro.domain.Permissoes
import kotlinx.coroutines.tasks.await

/** Membro de uma conta compartilhada. [papel] ausente no documento (R32) = EDITOR. */
data class Membro(val uid: String, val email: String, val papel: Papel = Papel.EDITOR, val entrouEm: Long = 0L, val nome: String = "")

/** Convite por e-mail (R32): a pessoa já tem acesso e escolhe "usar os dados de <dono>". */
data class Convite(val donoUid: String, val donoEmail: String)

/** Convite por código (R67), lido de `convites/<codigo>`. */
data class ConviteCodigoInfo(
    val codigo: String, val donoUid: String, val papel: Papel, val criadoEm: Long, val expiraEm: Long,
    val usado: Boolean, val donoNome: String, val donoEmail: String
) {
    fun estado(agora: Long = System.currentTimeMillis()) = ConviteCodigo.estado(usado, expiraEm, agora)
}

/** Falha de compartilhamento com mensagem pronta para o usuário. */
class CompartilhamentoException(message: String) : Exception(message)

/**
 * R32/R67 — conta compartilhada. Os dados continuam em `users/{donoUid}/…`.
 * - R32: o dono libera uma pessoa por e-mail criando `users/{dono}/membros/{uid}` e `perfis/{email}/convites/{dono}`.
 * - R67: convite por CÓDIGO (`convites/<codigo>`, 8 caracteres, uso único, 7 dias, papel fixado no convite). Quem entra grava,
 *   no MESMO batch, o próprio `membros/<uid>` (papel = o do convite) e `convites/<codigo>.usado = true`; as regras conferem
 *   as duas escritas com `getAfter` (ver `portal/firestore.rules`). Papéis: LEITOR, EDITOR, ADMIN (dono = ADMIN implícito).
 */
class Compartilhamento(
    private val auth: CloudAuth,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun email(e: String) = e.trim().lowercase()

    private fun uidLogado() = auth.uidAtual ?: throw CompartilhamentoException("Entre na sua conta da nuvem primeiro.")
    private fun emailLogado() = auth.emailAtual?.let(::email) ?: throw CompartilhamentoException("Sua conta não tem e-mail.")
    private fun membrosDe(dono: String) = firestore.collection("users").document(dono).collection("membros")

    /** Publica "este e-mail = este uid" para que possam te convidar. Chamado ao logar. */
    suspend fun registrarPerfil() {
        val uid = auth.uidAtual ?: return
        val mail = auth.emailAtual?.let(::email) ?: return
        firestore.collection("perfis").document(mail).set(mapOf("uid" to uid, "email" to mail)).await()
    }

    /** Meu papel na conta de [donoUid]: DONO se for a minha; senão o do meu documento de membro (sem documento = LEITOR, por segurança). */
    suspend fun meuPapel(donoUid: String): Papel {
        val uid = uidLogado()
        if (donoUid == uid) return Papel.DONO
        val doc = membrosDe(donoUid).document(uid).get().await()
        return if (doc.exists()) Papel.doDocumento(doc.getString("papel")) else Papel.LEITOR
    }

    /** Convite por e-mail (R32), agora com [papel] (padrão EDITOR). */
    suspend fun convidar(emailMembro: String, donoUid: String = uidLogado(), papel: Papel = Papel.EDITOR) {
        val donoEmail = emailLogado()
        val alvo = email(emailMembro)
        if (alvo.isEmpty() || !alvo.contains('@')) throw CompartilhamentoException("Informe um e-mail válido.")
        if (alvo == donoEmail) throw CompartilhamentoException("Esse é o seu próprio e-mail.")
        if (papel !in Permissoes.papeisConcediveis(meuPapel(donoUid))) throw CompartilhamentoException("Você não pode conceder esse papel.")

        val perfil = firestore.collection("perfis").document(alvo).get().await()
        val uidMembro = perfil.getString("uid")
            ?: throw CompartilhamentoException("Essa pessoa ainda não entrou no Meu Dinheiro. Peça para ela entrar no app ou no portal com esse e-mail e tente de novo.")
        if (uidMembro == donoUid) throw CompartilhamentoException("Essa pessoa já é a dona desta conta.")

        val agora = System.currentTimeMillis()
        val batch = firestore.batch()
        batch.set(
            membrosDe(donoUid).document(uidMembro),
            mapOf("uid" to uidMembro, "email" to alvo, "criadoEm" to agora, "entrouEm" to agora, "papel" to papel.name)
        )
        if (donoUid == uidLogado()) {
            batch.set(
                firestore.collection("perfis").document(alvo).collection("convites").document(donoUid),
                mapOf("donoUid" to donoUid, "donoEmail" to donoEmail, "criadoEm" to agora)
            )
        }
        batch.commit().await()
    }

    suspend fun membros(donoUid: String = uidLogado()): List<Membro> =
        membrosDe(donoUid).get().await().documents
            .mapNotNull { d ->
                d.getString("uid")?.let {
                    Membro(
                        uid = it, email = d.getString("email").orEmpty(), papel = Papel.doDocumento(d.getString("papel")),
                        entrouEm = d.getLong("entrouEm") ?: d.getLong("criadoEm") ?: 0L, nome = d.getString("nome").orEmpty()
                    )
                }
            }
            .sortedBy { it.entrouEm }

    /** Altera o papel de um membro (ADMIN só mexe em LEITOR/EDITOR; só o dono mexe em ADMIN). */
    suspend fun alterarPapel(donoUid: String, membro: Membro, novo: Papel) {
        val meu = meuPapel(donoUid)
        if (!Permissoes.podeGerenciarMembro(meu, membro.papel) || novo !in Permissoes.papeisConcediveis(meu))
            throw CompartilhamentoException("Você não pode alterar esse papel.")
        membrosDe(donoUid).document(membro.uid).update("papel", novo.name).await()
    }

    suspend fun removerMembro(membro: Membro, donoUid: String = uidLogado()) {
        val batch = firestore.batch()
        batch.delete(membrosDe(donoUid).document(membro.uid))
        if (donoUid == uidLogado() && membro.email.isNotBlank()) {
            batch.delete(firestore.collection("perfis").document(email(membro.email)).collection("convites").document(donoUid))
        }
        batch.commit().await()
    }

    /** O membro sai da conta compartilhada (apaga o próprio documento). O dono não sai. */
    suspend fun sair(donoUid: String) {
        val uid = uidLogado()
        if (donoUid == uid) throw CompartilhamentoException("Você é o dono desta conta; não é possível sair dela.")
        membrosDe(donoUid).document(uid).delete().await()
    }

    suspend fun convitesRecebidos(): List<Convite> =
        firestore.collection("perfis").document(emailLogado()).collection("convites").get().await().documents
            .mapNotNull { d -> d.getString("donoUid")?.let { Convite(it, d.getString("donoEmail").orEmpty()) } }

    // ------------------------------------------------------------------ R67: convite por código

    /** Gera um convite (uso único, 7 dias) com o [papel] pré-definido e devolve o código. */
    suspend fun gerarConvite(donoUid: String, papel: Papel, donoNome: String = ""): ConviteCodigoInfo {
        val uid = uidLogado()
        if (papel !in Permissoes.papeisConcediveis(meuPapel(donoUid))) throw CompartilhamentoException("Você não pode gerar convite com esse papel.")
        val agora = System.currentTimeMillis()
        var ultimo: Exception? = null
        repeat(3) { // colisão de código é improvável; criar sobre documento existente é negado pelas regras
            val codigo = ConviteCodigo.gerar()
            val doc = ConviteCodigo.documento(donoUid, papel, uid, agora).toMutableMap()
            auth.emailAtual?.let { doc["donoEmail"] = it.lowercase() }
            donoNome.trim().take(80).takeIf { it.isNotEmpty() }?.let { doc["donoNome"] = it } // só para exibição
            try {
                firestore.collection("convites").document(codigo).set(doc).await()
                return ConviteCodigoInfo(codigo, donoUid, papel, agora, agora + ConviteCodigo.VALIDADE_MS, false, donoNome, auth.emailAtual.orEmpty())
            } catch (e: Exception) {
                ultimo = e
            }
        }
        throw CompartilhamentoException("Não foi possível gerar o convite (${ultimo?.message ?: "erro"}). Verifique se as regras do Firestore foram publicadas.")
    }

    /** Convites pendentes (não usados e não expirados) da conta; depende da regra de `list` para dono/ADMIN. */
    suspend fun convitesPendentes(donoUid: String): List<ConviteCodigoInfo> {
        val agora = System.currentTimeMillis()
        return firestore.collection("convites").whereEqualTo("donoUid", donoUid).get().await().documents
            .mapNotNull { lerConvite(it.id, it) }
            .filter { it.estado(agora) == ConviteCodigo.Estado.VALIDO }
            .sortedByDescending { it.criadoEm }
    }

    suspend fun revogarConvite(codigo: String) {
        firestore.collection("convites").document(codigo).delete().await()
    }

    /** Lê o convite para mostrar de quem é a conta ANTES de confirmar. Lança com mensagem pronta (inválido/expirado/usado). */
    suspend fun consultarConvite(digitado: String): ConviteCodigoInfo {
        val codigo = ConviteCodigo.normalizar(digitado)
        val invalido = ConviteCodigo.mensagem(ConviteCodigo.Estado.INVALIDO)!!
        if (!ConviteCodigo.valido(codigo)) throw CompartilhamentoException(invalido)
        val doc = try {
            firestore.collection("convites").document(codigo).get().await()
        } catch (e: Exception) {
            throw CompartilhamentoException("Não foi possível consultar o convite. Verifique a conexão e tente de novo.")
        }
        val info = (if (doc.exists()) lerConvite(codigo, doc) else null) ?: throw CompartilhamentoException(invalido)
        ConviteCodigo.mensagem(info.estado())?.let { throw CompartilhamentoException(it) }
        if (info.donoUid == uidLogado()) throw CompartilhamentoException("Este convite é da sua própria conta.")
        return info
    }

    /** Entra na conta: UM batch com o meu documento de membro (papel do convite) e o convite marcado como usado. */
    suspend fun aceitarConvite(info: ConviteCodigoInfo, nome: String) {
        val uid = uidLogado()
        if (info.donoUid == uid) throw CompartilhamentoException("Este convite é da sua própria conta.")
        val agora = System.currentTimeMillis()
        val membro = mutableMapOf<String, Any>(
            "uid" to uid, "papel" to info.papel.name, "entrouEm" to agora, "criadoEm" to agora,
            "nome" to nome.trim().take(80).ifEmpty { auth.emailAtual?.substringBefore('@').orEmpty() }, "convite" to info.codigo
        )
        auth.emailAtual?.let { membro["email"] = it.lowercase() }
        val batch = firestore.batch()
        batch.set(membrosDe(info.donoUid).document(uid), membro)
        batch.update(firestore.collection("convites").document(info.codigo), mapOf("usado" to true, "usadoPor" to uid, "usadoEm" to agora))
        try {
            batch.commit().await()
        } catch (e: Exception) {
            throw CompartilhamentoException("Não foi possível entrar na conta. O convite pode ter sido usado ou ter expirado; peça um código novo.")
        }
    }

    private fun lerConvite(codigo: String, d: DocumentSnapshot): ConviteCodigoInfo? {
        val dono = d.getString("donoUid") ?: return null
        return ConviteCodigoInfo(
            codigo = codigo, donoUid = dono, papel = Papel.doDocumento(d.getString("papel")).takeIf { it != Papel.DONO } ?: Papel.LEITOR,
            criadoEm = d.getLong("criadoEm") ?: 0L, expiraEm = d.getLong("expiraEm") ?: 0L, usado = d.getBoolean("usado") ?: true,
            donoNome = d.getString("donoNome").orEmpty(), donoEmail = d.getString("donoEmail").orEmpty()
        )
    }
}
