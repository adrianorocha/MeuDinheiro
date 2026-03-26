package com.meudinheiro.data

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
    val bancos: List<BancoDomain>? = emptyList(), // Opcional: se quiser salvar os bancos tbm

    val versaoBackup: Int = 1 // Útil para migrações futuras
)