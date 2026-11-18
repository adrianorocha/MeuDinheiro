package com.meudinheiro.funcoes

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import com.meudinheiro.MyApplication
import com.meudinheiro.storage.InfoSync
import com.meudinheiro.storage.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Checagens reais da abertura do app (tela de boot): cada linha mostrada ao usuário vem de uma verificação feita de verdade
 * (memória, banco de dados, contas/cartões, bloqueio do aparelho, sensores, vibração, nuvem). Nada aqui altera dados.
 * Falha em uma checagem nunca trava a abertura: vira "[FALHA]" e segue.
 */
object DiagnosticoInicial {

    /** Uma linha do terminal. [aviso] = algo merece atenção (linha em laranja). */
    data class Linha(val texto: String, val aviso: Boolean = false)

    /** Tamanho dos rótulos com pontos de preenchimento (colunas). */
    private const val LARGURA_ROTULO = 24

    /** Tempo máximo esperando o estado da nuvem assentar (a abertura não depende da rede). */
    private const val ESPERA_NUVEM_MS = 2_500L
    private const val TEMPO_MAXIMO_CHECAGEM_MS = 4_000L

    /** "ROTULO........ [RESULTADO]" (sem rótulo vazio; rótulo maior que a largura só ganha um espaço). */
    fun formatar(rotulo: String, resultado: String): String {
        val pontos = if (rotulo.length >= LARGURA_ROTULO) "" else ".".repeat(LARGURA_ROTULO - rotulo.length)
        return "$rotulo$pontos [$resultado]"
    }

    /** Memória livre em GB com uma casa e vírgula ("1,8 GB LIVRES"); [baixa] = o sistema avisou pouca memória. */
    fun memoria(disponivelBytes: Long, baixa: Boolean): Pair<String, Boolean> {
        val gb = disponivelBytes.coerceAtLeast(0) / 1_000_000_000.0
        val texto = String.format(Locale.ROOT, "%.1f", gb).replace('.', ',') + " GB LIVRES"
        return (if (baixa) "BAIXA · $texto" else texto) to baixa
    }

    /** Estado da nuvem a partir do que o [com.meudinheiro.storage.StorageManager] já sabe. Retorna (texto, aviso). */
    fun nuvem(info: InfoSync): Pair<String, Boolean> {
        if (!info.nuvem || info.status == SyncStatus.Desligado) return "LOCAL" to false
        return when (info.status) {
            SyncStatus.AguardandoLogin -> "SEM LOGIN" to true
            is SyncStatus.Erro -> "ERRO" to true
            SyncStatus.Conectando -> (if (info.rede) "CONECTANDO" else "SEM REDE") to !info.rede
            is SyncStatus.Sincronizado -> when {
                !info.rede -> "SEM REDE" to true
                info.quarentena > 0 -> "${info.quarentena} EM QUARENTENA" to true
                info.pendentes > 0 -> "${info.pendentes} A ENVIAR" to false
                else -> "SINCRONIZADO" to false
            }
            SyncStatus.Desligado -> "LOCAL" to false
        }
    }

    /** Como o usuário entra: biometria, senha ou ainda sem cadastro. */
    fun acesso(temCadastro: Boolean, biometria: Boolean): Pair<String, Boolean> = when {
        !temCadastro -> "SEM CADASTRO" to false
        biometria -> "BIOMETRIA" to false
        else -> "SENHA" to false
    }

    fun plural(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

    /** Fecha a sequência: total de avisos nas checagens. */
    fun resumo(avisos: Int): Pair<String, Boolean> =
        if (avisos == 0) "SISTEMA PRONTO ⚡" to false else "PRONTO COM ${plural(avisos, "AVISO", "AVISOS")}" to true

    private fun vibrador(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun versao(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /**
     * Roda as checagens em ordem e entrega cada linha assim que fica pronta ([aoLinha]). Devolve o número de avisos.
     * Cada checagem tem tempo máximo; erro ou estouro vira "FALHA" (aviso).
     */
    suspend fun executar(context: Context, userPrefs: UserPreferences, aoLinha: suspend (Linha) -> Unit): Int {
        val app = context.applicationContext as MyApplication
        var avisos = 0

        suspend fun etapa(rotulo: String, bloco: suspend () -> Pair<String, Boolean>) {
            val (res, aviso) = withTimeoutOrNull(TEMPO_MAXIMO_CHECAGEM_MS) { runCatching { bloco() }.getOrNull() } ?: ("FALHA" to true)
            if (aviso) avisos++
            aoLinha(Linha(formatar(rotulo, res), aviso))
        }

        aoLinha(Linha("MEU DINHEIRO v${versao(context)} [ANDROID ${Build.VERSION.RELEASE}]"))

        etapa("MEMÓRIA") {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            memoria(mi.availMem, mi.lowMemory)
        }
        etapa("BANCO DE DADOS") {
            withContext(Dispatchers.IO) {
                val ok = app.database.openHelper.readableDatabase.query("PRAGMA quick_check").use { it.moveToFirst() && it.getString(0) == "ok" }
                (if (ok) "ÍNTEGRO" else "CORROMPIDO") to !ok
            }
        }
        etapa("LANÇAMENTOS") { plural(app.repository.todasDespesasFlow.first().size, "REGISTRO", "REGISTROS") to false }
        etapa("CONTAS") { app.repository.getTodasContas().first().size.toString() to false }
        etapa("CARTÕES") { app.repository.cartoesFlow().first().size.toString() to false }
        etapa("BLOQUEIO DO APARELHO") {
            val seguro = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
            (if (seguro) "ATIVO" else "SEM BLOQUEIO") to !seguro
        }
        etapa("ACESSO AO APP") {
            val temCadastro = userPrefs.userNameFlow.first().isNotBlank() && userPrefs.userPassFlow.first().isNotEmpty()
            acesso(temCadastro, userPrefs.biometriaEnabledFlow.first())
        }
        etapa("SENSOR DE MOVIMENTO") {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) "ATIVO" to false else "INDISPONÍVEL" to true
        }
        etapa("VIBRAÇÃO") { if (vibrador(context)?.hasVibrator() == true) "PRONTA" to false else "INDISPONÍVEL" to false }
        etapa("NUVEM") {
            val sm = app.storageManager
            // Dá um instante para o estado da nuvem assentar; modo local responde na hora.
            withTimeoutOrNull(ESPERA_NUVEM_MS) {
                while (sm.info.value.let { it.nuvem && it.status == SyncStatus.Conectando && it.rede }) delay(100)
            }
            nuvem(sm.info.value)
        }

        val (texto, aviso) = resumo(avisos)
        aoLinha(Linha(texto, aviso))
        return avisos
    }
}
