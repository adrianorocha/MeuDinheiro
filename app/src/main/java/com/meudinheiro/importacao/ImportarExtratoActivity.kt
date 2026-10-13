package com.meudinheiro.importacao

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.meudinheiro.AtalhosApp
import com.meudinheiro.MainActivity
import com.meudinheiro.domain.ImportacaoExtrato

/**
 * R63 — porta de entrada de extratos compartilhados (ACTION_SEND / SEND_MULTIPLE / VIEW). Sem tela própria:
 * valida cada arquivo, guarda numa pasta privada e abre o app na revisão. É EXPORTADA, então tudo que chega
 * é tratado como não confiável: só `content://`, tamanho/tipo/extensão/conteúdo validados e nada cria lançamento.
 */
class ImportarExtratoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val salvos = processar(intent)
        if (salvos.recusados.isNotEmpty() && salvos.ok == 0) {
            Toast.makeText(this, salvos.recusados.first(), Toast.LENGTH_LONG).show()
        } else if (salvos.ok > 0) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = AtalhosApp.ACAO_IMPORTAR_EXTRATOS
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            if (salvos.recusados.isNotEmpty()) Toast.makeText(this, "${salvos.recusados.size} arquivo(s) recusado(s).", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private class Contagem(val ok: Int, val recusados: List<String>)

    private fun processar(i: Intent?): Contagem {
        val uris = urisDe(i).take(ImportacaoExtrato.MAX_ARQUIVOS_POR_ENVIO)
        if (uris.isEmpty()) return Contagem(0, listOf("Nenhum arquivo recebido."))
        var ok = 0
        val recusados = mutableListOf<String>()
        for (u in uris) {
            when (val r = ExtratosPendentes.guardarDeUri(this, u, i?.type)) {
                is ExtratosPendentes.Resultado.Salvo -> ok++
                is ExtratosPendentes.Resultado.Recusado -> recusados.add("${r.nome}: ${r.motivo}")
            }
        }
        return Contagem(ok, recusados)
    }

    @Suppress("DEPRECATION")
    private fun urisDe(i: Intent?): List<Uri> {
        if (i == null) return emptyList()
        val lista = linkedSetOf<Uri>()
        when (i.action) {
            Intent.ACTION_VIEW -> i.data?.let { lista.add(it) }
            Intent.ACTION_SEND -> uriExtra(i)?.let { lista.add(it) }
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))?.let { lista.addAll(it) }
        }
        val clip = i.clipData
        if (lista.isEmpty() && clip != null) for (k in 0 until clip.itemCount) clip.getItemAt(k).uri?.let { lista.add(it) }
        return lista.toList()
    }

    @Suppress("DEPRECATION")
    private fun uriExtra(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
}
