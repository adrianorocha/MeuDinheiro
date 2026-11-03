package com.meudinheiro.captura

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.NotificacaoBancaria
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * R64 — captura de notificações do banco (opt-in, desligada por padrão).
 *
 * Privacidade: a PRIMEIRA coisa feita em [onNotificationPosted] é consultar só o `packageName`. Pacote fora da lista
 * permitida (ou captura desligada) = retorno imediato, SEM tocar em `notification.extras`. O texto bruto nunca é
 * gravado nem registrado em log; só os campos extraídos (valor, tipo, estabelecimento/nome, final do cartão, horário,
 * pacote). Nenhuma chamada de rede. Nunca lança: só cria a sugestão e avisa.
 */
class CapturaNotificacoesService : NotificationListenerService() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() { conectado = true }
    override fun onListenerDisconnected() { conectado = false }

    override fun onDestroy() {
        conectado = false
        escopo.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pacote = sbn?.packageName ?: return
        val ctx = applicationContext
        if (!CapturaConfig.ativo(ctx)) return
        if (!NotificacaoBancaria.pacoteAceito(pacote, CapturaConfig.permitidos(ctx))) {
            CapturaConfig.registrarVisto(ctx, pacote) // só o nome do pacote; o conteúdo NÃO é lido
            return
        }
        // A partir daqui o app é um dos permitidos: já se pode ler os campos de texto.
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val texto = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val chave = NotificacaoBancaria.chaveNotificacao(pacote, sbn.id, titulo, texto)
        val horario = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
        escopo.launch { runCatching { processar(ctx, pacote, chave, titulo, texto, horario) } }
    }

    private suspend fun processar(ctx: Context, pacote: String, chave: String, titulo: String?, texto: String?, horario: Long) {
        if (!vistas.registrar(chave, System.currentTimeMillis())) return // atualização da mesma notificação
        val s = NotificacaoBancaria.interpretar(pacote, titulo, texto, horario) ?: return
        val agora = System.currentTimeMillis()

        // 1) mesma sugestão (valor + descrição + tipo) em 10 minutos
        if (NotificacaoBancaria.duplicadaRecente(s, horario, SugestoesStore.todas(ctx, agora))) return

        // 2) destino (cartão pelo final / conta pelo banco) e 3) já existe lançamento igual a ±1 dia?
        var destino: NotificacaoBancaria.Destino? = null
        val repo = runCatching { MainRepository(ctx) }.getOrNull()
        if (repo != null) {
            val ja = runCatching {
                val contas = repo.obterTodasStatic()
                val cartoes = repo.cartoesFlow().first()
                val numeroDaConta = contas.associate { it.id to it.conta }
                destino = NotificacaoBancaria.resolverDestino(
                    s, pacote,
                    cartoes.map { NotificacaoBancaria.CartaoRef(it.id, it.finalCartao, numeroDaConta[it.contaId]) },
                    contas.map { NotificacaoBancaria.ContaRef(it.conta, it.banco) }
                )
                val recentes = repo.todasDespesasFlow.first().map {
                    NotificacaoBancaria.LancamentoRecente(
                        Dinheiro.centavos(it.valor), it.tipo == TipoDespesa.CREDITO, it.data.time, it.conta, it.cartaoId
                    )
                }
                NotificacaoBancaria.jaLancado(s, horario, destino, recentes)
            }.getOrDefault(false)
            if (ja) return
        }

        val g = SugestaoGuardada(
            id = UUID.randomUUID().toString(), tipo = s.tipo, valorCentavos = s.valorCentavos, descricao = s.descricao,
            finalCartao = s.finalCartao, confianca = s.confianca, entrada = s.entrada, pacote = pacote,
            horarioMs = horario, criadaEm = agora, cartaoId = destino?.cartaoId, conta = destino?.conta, e2e = s.e2e
        )
        SugestoesStore.adicionar(ctx, g, agora)
        NotificadorSugestoes.mostrar(ctx, g)
    }

    companion object {
        private val vistas = NotificacaoBancaria.ChavesVistas()

        @Volatile
        var conectado: Boolean = false
            private set

        /** O usuário concedeu "acesso às notificações" ao app nas configurações do sistema? */
        fun acessoConcedido(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        /** Pede ao sistema para religar o serviço (útil logo depois de conceder o acesso). */
        fun religar(ctx: Context) {
            runCatching { requestRebind(ComponentName(ctx, CapturaNotificacoesService::class.java)) }
        }
    }
}
