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
    /** R55 */
    val regrasCategoria: List<RegraCategoria>? = emptyList(),
    /** R56 */
    val projetos: List<Projeto>? = emptyList(),
    /** R58/R59 — opcionais: backup antigo (sem estas chaves) vale como "sem dívidas/pessoas/acertos". */
    val dividas: List<Divida>? = emptyList(),
    val pessoas: List<Pessoa>? = emptyList(),
    val acertos: List<Acerto>? = emptyList(),
    /** R57 — documento `configuracoes/geral` (mapa tolerante; ver ConfigGeral.deDoc). */
    val configuracoes: Map<String, Any?>? = null,
    val versaoBackup: Int = 2
)
