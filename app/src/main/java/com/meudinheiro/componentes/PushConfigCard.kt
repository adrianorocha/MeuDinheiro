package com.meudinheiro.componentes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.meudinheiro.MyApplication
import com.meudinheiro.domain.PushPrefs
import com.meudinheiro.domain.TipoPush
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.push.PushDispositivo
import com.meudinheiro.storage.StorageMode
import kotlinx.coroutines.launch

/**
 * R65 — Configurações > "Notificações push": interruptor geral (opt-in, padrão desligado) e um interruptor por tipo.
 * O estado fica local (DataStore) e é espelhado no campo `prefs` do documento do dispositivo (StorageManager).
 * Os avisos locais (Ronda Financeira etc.) seguem independentes: valem mesmo sem push/nuvem/Blaze.
 */
@Composable
internal fun PushConfigCard(userPrefs: UserPreferences, onMensagem: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ligado by userPrefs.pushEnabledFlow.collectAsState(initial = false)
    val prefs by userPrefs.pushPrefsFlow.collectAsState(initial = PushPrefs())
    val modo by (context.applicationContext as MyApplication).storageManager.modo.collectAsState(initial = StorageMode.LOCAL)
    val aviso by PushDispositivo.aviso.collectAsState()

    fun temPermissao() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        scope.launch {
            if (concedida) userPrefs.savePushEnabled(true)
            else onMensagem("Push | Permita as notificações do app para receber avisos. | Erro")
        }
    }

    val nuvem = modo == StorageMode.FIREBASE
    ConfiguracaoSwitchItem(
        icone = Icons.Default.NotificationsActive,
        titulo = "Receber notificações push",
        descricao = when {
            !nuvem -> "Exige a nuvem (Firebase) ligada e login. Sem isso, só os avisos locais."
            ligado -> "Ativo neste aparelho (valores nunca aparecem na tela bloqueada)"
            else -> "Desligado"
        },
        checked = ligado,
        onCheckedChange = { v ->
            scope.launch {
                if (!v) userPrefs.savePushEnabled(false)
                else if (!temPermissao()) permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
                else userPrefs.savePushEnabled(true)
            }
        }
    )
    if (aviso != null && ligado) {
        Text(aviso.orEmpty(), color = Color(0xFFFFB74D), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
    }
    if (ligado) {
        Column {
            val itens = listOf(
                Triple(TipoPush.VENCIMENTOS, "Vencimentos", "Contas a pagar amanhã ou em atraso (todo dia, 08:00)"),
                Triple(TipoPush.ORCAMENTO, "Orçamento estourado", "Quando uma categoria chega a 80% ou 100% do limite"),
                Triple(TipoPush.RESUMO_SEMANAL, "Resumo semanal", "Entradas, saídas e maior gasto (domingo, 18:00)"),
                Triple(TipoPush.CONVITE, "Convites", "Convite para compartilhar uma conta")
            )
            itens.forEach { (tipo, titulo, desc) ->
                HorizontalDivider(color = TextWhite.copy(0.05f), modifier = Modifier.padding(vertical = 4.dp))
                ConfiguracaoSwitchItem(
                    icone = Icons.Default.Notifications,
                    titulo = titulo,
                    descricao = desc,
                    checked = prefs.permite(tipo),
                    onCheckedChange = { v -> scope.launch { userPrefs.savePushPref(tipo, v) } },
                    padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
