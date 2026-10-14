package com.meudinheiro.captura

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meudinheiro.AtalhosCaptura
import com.meudinheiro.componentes.LancamentoRapidoActivity
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.NotificacaoBancaria
import com.meudinheiro.domain.NotificacaoBancaria.Estado
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada
import com.meudinheiro.notif.Aviso
import com.meudinheiro.notif.BotaoAviso
import com.meudinheiro.notif.Notificacoes
import com.meudinheiro.notif.TipoAviso

/** R64 — avisa "Lançar R$ X em Y?" com Confirmar / Ignorar. Nada é lançado sem a confirmação do usuário. */
object NotificadorSugestoes {
    const val ACAO_IGNORAR = "com.meudinheiro.action.SUGESTAO_IGNORAR"
    private const val EXTRA_ID = "sugestao_id"
    private const val IMUTAVEL = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Id estável (faixa própria, para não colidir com os outros avisos) a partir do id da sugestão. */
    fun idNotificacao(sugestaoId: String): Int = 0x64000000 or (sugestaoId.hashCode() and 0x00FFFFFF)

    /** Intent que abre o formulário de lançamento JÁ PREENCHIDO (sem lançar sozinho; a biometria é exigida ao salvar). */
    fun intentConfirmar(ctx: Context, g: SugestaoGuardada): Intent =
        Intent(ctx, LancamentoRapidoActivity::class.java).apply {
            putExtra(LancamentoRapidoActivity.EXTRA_TIPO, (if (g.entrada) TipoDespesa.CREDITO else TipoDespesa.DEBITO).name)
            putExtra(LancamentoRapidoActivity.EXTRA_VALOR, "%d.%02d".format(g.valorCentavos / 100, g.valorCentavos % 100))
            putExtra(LancamentoRapidoActivity.EXTRA_DESCRICAO, NotificacaoBancaria.tituloLancamento(g))
            putExtra(LancamentoRapidoActivity.EXTRA_CATEGORIA, "")
            putExtra(LancamentoRapidoActivity.EXTRA_CONTA, g.conta.orEmpty())
            putExtra(LancamentoRapidoActivity.EXTRA_CARTAO, g.cartaoId ?: -1)
            putExtra(LancamentoRapidoActivity.EXTRA_REVISAR, true)
            putExtra(LancamentoRapidoActivity.EXTRA_DEBITO, g.tipo == NotificacaoBancaria.Tipo.COMPRA_DEBITO)
            putExtra(LancamentoRapidoActivity.EXTRA_SUGESTAO_ID, g.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun mostrar(ctx: Context, g: SugestaoGuardada) {
        val app = ctx.applicationContext
        val nid = idNotificacao(g.id)
        val confirmar = PendingIntent.getActivity(app, nid, intentConfirmar(app, g), IMUTAVEL)
        val ignorar = PendingIntent.getBroadcast(
            app, nid,
            Intent(app, SugestaoAcaoReceiver::class.java).setAction(ACAO_IGNORAR).putExtra(EXTRA_ID, g.id),
            IMUTAVEL
        )
        Notificacoes.mostrar(
            app,
            Aviso(
                tipo = TipoAviso.SUGESTAO,
                titulo = "Lançar ${FormatoCentavos.brl(g.valorCentavos)}?",
                resumo = NotificacaoBancaria.pergunta(g),
                id = nid,
                linhas = listOf(NotificacaoBancaria.pergunta(g) + " (" + g.tipo.rotulo + ")"),
                textoPublico = "Você tem uma sugestão de lançamento",
                acao = AtalhosCaptura.ACAO_SUGESTOES,
                botoes = listOf(BotaoAviso("Confirmar", confirmar), BotaoAviso("Ignorar", ignorar))
            )
        )
    }

    fun cancelar(ctx: Context, sugestaoId: String) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(Notificacoes.TAG, idNotificacao(sugestaoId))
    }

    fun cancelarTodas(ctx: Context, ids: Collection<String>) = ids.forEach { cancelar(ctx, it) }

    class SugestaoAcaoReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACAO_IGNORAR) return
            val id = intent.getStringExtra(EXTRA_ID) ?: return
            SugestoesStore.marcar(context, id, Estado.IGNORADA)
            cancelar(context, id)
        }
    }
}
