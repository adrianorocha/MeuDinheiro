package com.meudinheiro.storage

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Falha de autenticação com mensagem pronta para o usuário. */
class AuthException(message: String) : Exception(message)

/** Login na nuvem (Firebase Auth, e‑mail/senha — o mesmo usado no portal web). */
class CloudAuth(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val uidAtual: String? get() = auth.currentUser?.uid
    val emailAtual: String? get() = auth.currentUser?.email

    /** Emite o uid do usuário logado (ou null) a cada mudança de sessão. */
    val uidFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    suspend fun entrar(email: String, senha: String) = executar {
        auth.signInWithEmailAndPassword(email.trim(), senha).await()
    }

    suspend fun criarConta(email: String, senha: String) = executar {
        auth.createUserWithEmailAndPassword(email.trim(), senha).await()
    }

    suspend fun recuperarSenha(email: String) = executar { auth.sendPasswordResetEmail(email.trim()).await() }

    fun sair() = auth.signOut()

    private suspend fun executar(bloco: suspend () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            throw AuthException(traduzir(e))
        }
    }

    private fun traduzir(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "A senha precisa ter pelo menos 6 caracteres."
        is FirebaseAuthUserCollisionException -> "Já existe uma conta com este e-mail. Use Entrar."
        is FirebaseAuthInvalidUserException -> "Usuário não encontrado ou desativado."
        is FirebaseAuthInvalidCredentialsException -> "E-mail ou senha inválidos."
        is FirebaseNetworkException -> "Sem conexão com a internet."
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED", "ERROR_ADMIN_RESTRICTED_OPERATION" ->
                "O login por e-mail/senha não está habilitado no console do Firebase (Authentication → Sign-in method)."
            "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Aguarde alguns minutos."
            else -> e.message ?: "Não foi possível autenticar."
        }
        else -> e.message ?: "Não foi possível autenticar."
    }
}
