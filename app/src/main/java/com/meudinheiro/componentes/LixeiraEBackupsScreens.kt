package com.meudinheiro.componentes

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.funcoes.RelatorioExport
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonRed
import com.meudinheiro.worker.BackupAutomatico
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val TextoClaro = Color(0xFFE0E1DD)
private val CardFundo = Color(0xFF1B263B)

/** R31 — lançamentos excluídos ficam 30 dias aqui; dá para restaurar (com saldo/limite recalculados). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LixeiraScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val itens by repository.lixeiraFlow().collectAsState(initial = emptyList())
    var confirmarEsvaziar by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Lixeira", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                actions = { if (itens.isNotEmpty()) TextButton(onClick = { confirmarEsvaziar = true }) { Text("Esvaziar", color = NeonRed) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Itens excluídos ficam aqui por 30 dias e depois são apagados de vez.", color = Color.White.copy(0.6f), fontSize = 12.sp) }
            if (itens.isEmpty()) item { Text("A lixeira está vazia.", color = Color.White.copy(0.8f), modifier = Modifier.padding(top = 24.dp)) }
            items(itens, key = { it.id }) { item ->
                val diasRestantes = (30 - (System.currentTimeMillis() - item.excluidoEm) / 86_400_000L).coerceAtLeast(0)
                Row(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.descricao, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text(
                            formatarMoedaBR(item.valor, isPrivate) + " · excluído " +
                                DateUtils.getRelativeTimeSpanString(item.excluidoEm, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) +
                                " · some em ${diasRestantes}d",
                            color = Color.White.copy(0.5f), fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { repository.restaurarDaLixeira(item.id) }
                                Toast.makeText(context, "Lançamento restaurado.", Toast.LENGTH_SHORT).show()
                            } catch (e: RegraFinanceiraException) {
                                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Restaurar", color = NeonCyan) }
                    TextButton(onClick = { scope.launch(Dispatchers.IO) { repository.excluirDaLixeira(item.id) } }) { Text("Apagar", color = NeonRed) }
                }
            }
        }
    }

    if (confirmarEsvaziar) {
        AlertDialog(
            onDismissRequest = { confirmarEsvaziar = false }, containerColor = DeepSpaceBlue,
            title = { Text("Esvaziar a lixeira?", color = Color.White) },
            text = { Text("Os ${itens.size} itens serão apagados definitivamente.", color = Color.White.copy(0.8f)) },
            confirmButton = {
                Button(
                    onClick = { confirmarEsvaziar = false; scope.launch(Dispatchers.IO) { repository.esvaziarLixeira() } },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DeepSpaceBlue)
                ) { Text("Esvaziar") }
            },
            dismissButton = { TextButton(onClick = { confirmarEsvaziar = false }) { Text("Cancelar", color = Color.White.copy(0.6f)) } }
        )
    }
}

// R66: a tela de backups (antes R33, semanal) agora vive em BackupHistoricoScreen.kt.
