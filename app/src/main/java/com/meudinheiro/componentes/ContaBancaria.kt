package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.viewModel.ContaSaldoViewModel
import com.meudinheiro.viewModel.ContaSaldoViewModelFactory

@Composable
fun ContaBancaria(
    viewModelFactory: ContaSaldoViewModelFactory,
    onClose: () -> Unit
) {
    val viewModel: ContaSaldoViewModel = viewModel(factory = viewModelFactory)
    val contasExistentes by viewModel.contaSaldo.observeAsState(emptyList())
    val context = LocalContext.current
    val repository = remember { MainRepository(context) }

    val bancosNomes = viewModel.bancos.value.map { it.nome }

    ContaBancariaDialog(
        bancos = bancosNomes,
        onAdicionar = { banco, agencia, contaCorrente ->
            val exists = contasExistentes.any { domain ->
                domain.banco.equals(banco.trim(), true) &&
                        domain.agencia.equals(agencia.trim(), true) &&
                        domain.conta.equals(contaCorrente.trim(), true)
            }

            if (exists) {
                Toast.makeText(
                    context,
                    "Essa conta já foi cadastrada. Verifique os dados.",
                    Toast.LENGTH_SHORT
                ).show()
                return@ContaBancariaDialog
            }

            val novaConta = ContaSaldo(
                id = 0,
                banco = banco.trim(),
                agencia = agencia.trim(),
                conta = contaCorrente.trim(),
                pic = repository.getPicBanco(banco.trim()) ,
                saldo = 0.00,
                titular = ""
            )

            viewModel.adicionarContaSaldo(novaConta)
            onClose()
        },
        onCancelar = onClose
    )
}
