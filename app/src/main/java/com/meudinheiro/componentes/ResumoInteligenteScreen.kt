package com.meudinheiro.componentes

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.ResumoInteligente
import com.meudinheiro.domain.ResumoInteligente.Tom
import com.meudinheiro.domain.ResumoInteligente.TipoPeriodo
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import java.util.Calendar

private val TextoClaro = Color(0xFFE0E1DD)
private val CardFundo = Color(0xFF1B263B)

/** R53 — Resumo inteligente do mês/semana: insights comparando com o período anterior (sem campos novos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumoInteligenteScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val agora = remember { System.currentTimeMillis() }
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val orcamentos by repository.obterOrcamentosFlow().collectAsState(initial = emptyList())
    var fixas by remember { mutableStateOf<List<DespesaFixa>>(emptyList()) }
    LaunchedEffect(Unit) { fixas = repository.obterTodasRecorrencias() }
    var tipo by remember { mutableStateOf(TipoPeriodo.MES) }
    var ref by remember { mutableStateOf(agora) }

    val resumo = remember(despesas, fixas, orcamentos, tipo, ref) {
        ResumoInteligente.gerar(despesas, fixas, orcamentos, tipo, ref, agora)
    }
    val texto = remember(resumo) { ResumoInteligente.texto(resumo) }
    fun mascarar(t: String) = if (isPrivate) t.replace(Regex("-?R\\$ [\\d.]+,\\d{2}"), "••••••") else t

    fun andar(sentido: Int) {
        ref = if (tipo == TipoPeriodo.MES) Financas.somarMeses(ref, sentido)
        else Calendar.getInstance().apply { timeInMillis = ref; add(Calendar.DAY_OF_YEAR, 7 * sentido) }.timeInMillis
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Resumo inteligente", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(TipoPeriodo.MES to "Mês", TipoPeriodo.SEMANA to "Semana").forEach { (t, rotulo) ->
                        val sel = t == tipo
                        TextButton(onClick = { tipo = t; ref = agora }) {
                            Text((if (sel) "● " else "○ ") + rotulo, color = if (sel) NeonCyan else Color.White.copy(0.6f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { andar(-1) }) { Text("‹ Anterior", color = NeonCyan, fontSize = 12.sp) }
                    Text(
                        resumo.periodo.rotulo, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { andar(1) }) { Text("Próximo ›", color = NeonCyan, fontSize = 12.sp) }
                }
                if (resumo.periodo.emAndamento) {
                    Text(
                        "Em andamento: dia ${resumo.periodo.diasDecorridos} de ${resumo.periodo.diasTotal}", color = Color.White.copy(0.6f),
                        fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(texto))
                        Toast.makeText(context, "Resumo copiado", Toast.LENGTH_SHORT).show()
                    }) { Text("Copiar", color = NeonCyan) }
                    TextButton(onClick = {
                        val envio = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Resumo de ${resumo.periodo.rotulo}")
                            putExtra(Intent.EXTRA_TEXT, texto)
                        }
                        context.startActivity(Intent.createChooser(envio, "Compartilhar resumo"))
                    }) { Text("Compartilhar", color = NeonCyan) }
                }
            }

            if (resumo.vazio || resumo.insights.isEmpty()) {
                item {
                    Text(
                        "Não há lançamentos comuns neste período nem no anterior. Escolha outro período ou registre seus gastos.",
                        color = Color.White.copy(0.7f), fontSize = 13.sp
                    )
                }
            } else {
                items(resumo.insights, key = { it.id }) { i ->
                    val (cor, selo) = when (i.tom) {
                        Tom.POSITIVO -> NeonGreen to "Bom sinal"
                        Tom.NEGATIVO -> NeonRed to "Atenção"
                        Tom.ALERTA -> NeonOrange to "Alerta"
                        Tom.NEUTRO -> Color.White.copy(0.6f) to "Informação"
                    }
                    Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(i.titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            // texto além da cor: o selo diz o tom por extenso
                            Text(selo, color = cor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(mascarar(i.texto), color = Color.White.copy(0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            item {
                Text(
                    "Não entram transferências, aportes/resgates de metas, pagamentos de fatura nem ajustes. Em período em andamento a comparação é feita com o mesmo ponto do período anterior.",
                    color = Color.White.copy(0.45f), fontSize = 11.sp
                )
            }
        }
    }
}
