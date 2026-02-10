package com.meudinheiro.data

data class BackupData(
    val categorias: List<com.meudinheiro.data.Categoria>,
    val despesas: List<com.meudinheiro.data.Despesa>,
    val despesasFixas: List<com.meudinheiro.data.DespesaFixa>,
    val contas: List<com.meudinheiro.data.ContaSaldo>
)