package com.meudinheiro.funcoes

import com.meudinheiro.domain.Bcb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.TimeZone

/** Falha de rede com mensagem curta em português (exibida na tela). */
class BcbErro(mensagem: String) : Exception(mensagem)

/**
 * R100 — a ÚNICA porta de rede do Banco Central. Só tem `get(url)`: sem corpo, sem cabeçalhos, sem cookies, sem credenciais.
 * Fácil de substituir por um fake nos testes.
 */
interface BcbHttp {
    /** Corpo da resposta (texto). Lança [BcbErro] com mensagem curta em qualquer falha (HTTP != 2xx, sem rede, tempo esgotado). */
    suspend fun get(url: String): String
}

/**
 * Implementação real com `HttpURLConnection`: GET público, sem cookies (o app não instala `CookieHandler`), sem cabeçalhos
 * próprios, sem redirecionamento automático, timeout de 8 s, resposta limitada a 1 MB, sempre em `Dispatchers.IO` e
 * desconectando se a corrotina for cancelada.
 */
class HttpBcb : BcbHttp {
    override suspend fun get(url: String): String = coroutineScope {
        if (!Bcb.urlPermitida(url)) throw BcbErro("endereço não permitido")
        val conn = URL(url).openConnection() as HttpURLConnection
        val tarefa = async(Dispatchers.IO) { ler(conn) }
        try {
            tarefa.await()
        } catch (e: CancellationException) {
            conn.disconnect() // desbloqueia a leitura em andamento
            throw e
        }
    }

    private fun ler(conn: HttpURLConnection): String {
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = Bcb.TIMEOUT_MS.toInt()
            conn.readTimeout = Bcb.TIMEOUT_MS.toInt()
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            conn.doOutput = false
            val code = conn.responseCode
            if (code !in 200..299) throw BcbErro("HTTP $code")
            val buf = ByteArrayOutputStream()
            conn.inputStream.use { ins ->
                val tmp = ByteArray(8192)
                while (buf.size() < MAX_BYTES) {
                    val n = ins.read(tmp, 0, minOf(tmp.size, MAX_BYTES - buf.size()))
                    if (n < 0) break
                    buf.write(tmp, 0, n)
                }
            }
            return String(buf.toByteArray(), Charsets.UTF_8)
        } catch (e: BcbErro) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw BcbErro("tempo esgotado")
        } catch (e: UnknownHostException) {
            throw BcbErro("sem conexão")
        } catch (e: IOException) {
            throw BcbErro("falha de rede")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}

/**
 * R100 — orquestra as consultas (mesma lógica de `buscarIndicesBcb`/`buscarCotacoes`/`buscarHistorico` do portal). Cada série e
 * cada moeda são isoladas: uma que falha vira [Bcb.Falha] e as outras seguem (resultado parcial). Nenhum dado do usuário entra
 * nas URLs (só código da série, moeda e datas). [relogio] e [fuso] são injetáveis para os testes.
 */
class BcbClient(
    private val http: BcbHttp,
    private val relogio: () -> Long = { System.currentTimeMillis() },
    private val fuso: () -> TimeZone = { TimeZone.getDefault() },
    private val timeoutMs: Long = Bcb.TIMEOUT_MS
) {
    /** GET com a URL validada (https + hosts do BCB) e prazo de [timeoutMs] por requisição. */
    private suspend fun baixar(url: String): String {
        if (!Bcb.urlPermitida(url)) throw BcbErro("endereço não permitido")
        return withTimeoutOrNull(timeoutMs) { http.get(url) } ?: throw BcbErro("tempo esgotado")
    }

    private fun msg(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: "falha de rede"

    private sealed class ResSerie {
        class Ok(val r: Bcb.Resultado) : ResSerie()
        class Erro(val f: Bcb.Falha) : ResSerie()
    }

    /** Consulta as 13 séries em paralelo, cada uma isolada. Nunca lança (exceto cancelamento). */
    suspend fun buscarIndices(): Bcb.SnapshotIndices {
        val agora = relogio()
        val hoje = Bcb.hojeIso(agora, fuso())
        val res = coroutineScope {
            Bcb.SERIES.map { def ->
                async {
                    try {
                        ResSerie.Ok(Bcb.interpretarSerie(def, baixar(Bcb.urlSgs(def.codigo, Bcb.ultimosPedidos(def))), hoje))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ResSerie.Erro(Bcb.Falha("${def.nome} (SGS ${def.codigo})", msg(e)))
                    }
                }
            }.awaitAll()
        }
        val itens = LinkedHashMap<String, Bcb.IndiceBcb>()
        var meta: Bcb.SelicMetaBcb? = null
        var sal: Bcb.SalarioBcb? = null
        val falhas = ArrayList<Bcb.Falha>()
        for (r in res) when (r) {
            is ResSerie.Erro -> falhas += r.f
            is ResSerie.Ok -> when (val x = r.r) {
                is Bcb.Resultado.Indice -> itens[x.chave] = x.valor
                is Bcb.Resultado.Meta -> meta = x.valor
                is Bcb.Resultado.Salario -> sal = x.valor
            }
        }
        return Bcb.SnapshotIndices(agora, itens, meta, sal, falhas)
    }

    private sealed class ResMoeda {
        class Ok(val c: Bcb.CotacaoMoeda) : ResMoeda()
        class Erro(val f: Bcb.Falha) : ResMoeda()
    }

    /** Cotações PTAX das 10 moedas (uma consulta por moeda, isoladas). Nunca lança (exceto cancelamento). */
    suspend fun buscarCotacoes(): Bcb.SnapshotCambio {
        val agora = relogio()
        val tz = fuso()
        val hoje = Bcb.hojeIso(agora, tz)
        val inicio = agora - Bcb.JANELA_COTACAO_DIAS * Bcb.DIA_MS
        val res = coroutineScope {
            Bcb.MOEDAS.map { m ->
                async {
                    try {
                        val boletins = Bcb.parseBoletins(baixar(Bcb.urlPtax(m.codigo, inicio, agora, tz)), hoje)
                        val c = Bcb.calcularCotacao(m.codigo, boletins) ?: throw BcbErro("sem cotação no período")
                        ResMoeda.Ok(c)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ResMoeda.Erro(Bcb.Falha("${m.nome} (${m.codigo})", msg(e)))
                    }
                }
            }.awaitAll()
        }
        return Bcb.SnapshotCambio(
            agora,
            res.filterIsInstance<ResMoeda.Ok>().map { it.c },
            res.filterIsInstance<ResMoeda.Erro>().map { it.f }
        )
    }

    /** Histórico (fechamentos) de uma moeda nos últimos ~45 dias corridos, do mais antigo ao mais recente, até 30 pontos. Lança [BcbErro]. */
    suspend fun buscarHistorico(moeda: String): List<Bcb.PontoHistorico> {
        if (Bcb.MOEDAS.none { it.codigo == moeda }) throw BcbErro("moeda inválida")
        val agora = relogio()
        val tz = fuso()
        val inicio = agora - Bcb.JANELA_HISTORICO_DIAS * Bcb.DIA_MS
        return Bcb.historicoDe(Bcb.parseBoletins(baixar(Bcb.urlPtax(moeda, inicio, agora, tz)), Bcb.hojeIso(agora, tz)))
    }
}
