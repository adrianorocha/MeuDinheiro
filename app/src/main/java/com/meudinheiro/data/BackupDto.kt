package com.meudinheiro.data

/** Formato do backup (versão 2). Ver docs/CONTRATO_DADOS.md, seção 3. */
data class BackupDto(
    val despesas: List<Despesa>? = emptyList(),
    val categorias: List<Categoria>? = emptyList(),
    val contas: List<ContaSaldo>? = emptyList(),
    val metas: List<Meta>? = emptyList(),
    val despesasFixas: List<DespesaFixa>? = emptyList(),
    val orcamentos: List<Orcamento>? = emptyList(),
    val investimentos: List<Investimento>? = emptyList(),
    val transacao: List<Transacao>? = emptyList(),
    val transferenciasAgendadas: List<TransferenciaAgendada>? = emptyList(),
    val patrimonio: List<PatrimonioHistorico>? = emptyList(),
    val cartoes: List<Cartao>? = emptyList(),
    val lixeira: List<Lixeira>? = emptyList(),
    val versaoBackup: Int = 2
)
