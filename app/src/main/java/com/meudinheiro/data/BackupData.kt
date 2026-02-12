package com.meudinheiro.data

data class BackupData(
    val categorias: List<Categoria>,
    val despesas: List<Despesa>,
    val despesasFixas: List<DespesaFixa>,
    val contas: List<ContaSaldo>,
    val metas: List<Meta>,
    val orcamentos: List<Orcamento>
)