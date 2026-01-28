package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meudinheiro.data.Despesa
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendenciasScreen(
    userPrefs: UserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { MainRepository(context) }

    val daysAhead by userPrefs.notifDaysAheadFlow.collectAsState(initial = 3)
    val onlyCredit by userPrefs.notifOnlyCreditFlow.collectAsState(initial = false)

    var items by remember { mutableStateOf<List<Despesa>>(emptyList()) }

    LaunchedEffect(daysAhead, onlyCredit) {
        items = withContext(Dispatchers.IO) {
            repo.listarPendencias(daysAhead, onlyCredit)
        }
    }

    val nf = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val total = items.sumOf { it.valor }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pendências") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${items.size} pendente(s)", fontWeight = FontWeight.Bold)
                Text("Total: ${nf.format(total)}", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Voltar") }

            Spacer(Modifier.height(4.dp))

            if (items.isEmpty()) {
                Text(
                    text = "Nenhuma pendência encontrada.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { d ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(d.descricao, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Venc.: ${df.format(d.data)}  •  ${nf.format(d.valor)}")
                                Spacer(Modifier.height(2.dp))
                                Text("Conta: ${d.conta}  •  Categoria: ${d.categoria}  •  Tipo: ${d.tipo}")
                            }
                        }
                    }
                }
            }
        }
    }


}