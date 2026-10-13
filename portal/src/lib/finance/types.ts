import type { Anexo } from "./anexos";

export type Tipo = "DEBITO" | "CREDITO";

export const NATUREZAS = [
  "NORMAL",
  "SALDO_INICIAL",
  "TRANSFERENCIA",
  "APORTE_META",
  "RESGATE_META",
  "PAGAMENTO_FATURA",
  "AJUSTE",
] as const;

export type Natureza = (typeof NATUREZAS)[number];

export interface Conta {
  id: number;
  saldo: number;
  banco: string;
  pic: string;
  agencia: string;
  conta: string;
  titular: string;
}

export interface Despesa {
  id: number;
  descricao: string;
  valor: number;
  /** epoch ms */
  data: number;
  categoria: string;
  /** número da conta (contas.conta) */
  conta: string;
  pic: string;
  tipo: Tipo;
  mes: number;
  ano: number;
  cartaoId: number | null;
  valorOriginal: number;
  moedaOriginal: string;
  cotacaoNaData: number;
  pago: boolean;
  natureza: Natureza;
  grupoId: string | null;
  /** Quem lançou (R32); null em dados antigos. */
  autor: string | null;
  /** Id da transação no extrato do banco (R35); null se nunca conciliada. */
  fitid: string | null;
  /** Epoch ms da conciliação (R38); null = não conciliada. */
  conciliadoEm: number | null;
  /** R56 - rótulos de tags (máx. 5, 30 caracteres); ausente em dados antigos = sem tags. */
  tags?: string[];
  /** R60 - anexos (fotos/comprovantes; máx. 5); ausente em dados antigos = sem anexos. */
  anexos?: Anexo[];
}

export interface DespesaFixa {
  id: number;
  descricao: string;
  valor: number;
  conta: string;
  categoria: string;
  pic: string;
  tipo: Tipo;
  diaVencimento: number;
  ultimaDataLancamento: number | null;
  /** Cartão (físico ou virtual) usado como forma de pagamento; null = conta (R16). */
  cartaoId: number | null;
  /** R47 - recorrência pausada (ausente em dados antigos = não pausada). */
  pausada?: boolean;
  /** R47 - epoch ms da retomada automática; null/ausente = até retomar manualmente. */
  pausadaAte?: number | null;
}

export interface Categoria {
  id: number;
  nome: string;
  pic: string;
}

export interface Orcamento {
  id: number;
  categoria: string;
  valorLimite: number;
}

export type TipoMatchRegra = "CONTEM" | "COMECA_COM" | "IGUAL";

/** R55 - regra de categorização automática (maior prioridade vence; empate = padrão mais específico). */
export interface RegraCategoria {
  id: number;
  padrao: string;
  tipoMatch: TipoMatchRegra;
  categoria: string;
  /** Restringe a uma conta (número); null = qualquer. */
  conta?: string | null;
  /** Restringe ao tipo; null = qualquer. */
  tipo?: Tipo | null;
  ativa: boolean;
  prioridade: number;
  criadaEm: number;
}

/** R56 - projeto = agrupador de lançamentos por tag (`tag` é a CHAVE normalizada). */
export interface Projeto {
  id: number;
  nome: string;
  tag: string;
  orcamento?: number | null;
  /** epoch ms */
  inicio?: number | null;
  fim?: number | null;
  ativo: boolean;
  cor?: string | null;
}

export type TipoDivida = "EMPRESTIMO" | "FINANCIAMENTO" | "CHEQUE_ESPECIAL" | "CARTAO_ROTATIVO" | "CONSIGNADO" | "OUTRA";

/** R58 - dívida em quitação. O saldo devedor é controle próprio (não é saldo de conta). Valores em reais (2 casas). */
export interface Divida {
  id: number;
  nome: string;
  tipo: TipoDivida;
  credor?: string | null;
  saldoDevedor: number;
  /** Valor no cadastro, para o progresso de quitação; ausente = o saldo atual. */
  valorOriginal?: number | null;
  /** % ao mês (pode ser 0/nula). */
  taxaJurosMensal?: number | null;
  /** Parcela fixa ou mínimo mensal. */
  parcelaMinima?: number | null;
  parcelasRestantes?: number | null;
  diaVencimento?: number | null;
  /** Conta (número) de onde costuma sair o pagamento. */
  conta?: string | null;
  ativa: boolean;
}

/** R59 - pessoa com quem há acertos (rateios, empréstimos). */
export interface Pessoa {
  id: number;
  nome: string;
  contato?: string | null;
}

export type TipoAcerto = "A_RECEBER" | "A_PAGAR";

/** R59 - valor a receber/pagar entre pessoas. `despesaId` órfão (lançamento removido) é tolerado. */
export interface Acerto {
  id: number;
  pessoaId: number;
  tipo: TipoAcerto;
  valor: number;
  descricao: string;
  /** epoch ms */
  data: number;
  /** Lançamento de origem; null/ausente = acerto avulso. */
  despesaId?: number | null;
  quitado: boolean;
  quitadoEm?: number | null;
  /** `rateio:<despesaId>` quando nasceu de "Dividir esta despesa". */
  grupoRateio?: string | null;
}

export interface Meta {
  id: number;
  nome: string;
  valorObjetivo: number;
  valorGuardado: number;
  icone: string;
  /** Prazo da meta (R27), epoch ms. */
  dataAlvo: number | null;
}

export interface Investimento {
  id: number;
  nome: string;
  tipo: string;
  valorInvestido: number;
  valorAtual: number;
}

export interface Cartao {
  id: number;
  nome: string;
  finalCartao: string;
  tipo: string;
  limiteDisponivel: number;
  limiteTotal: number;
  diaFechamento: number;
  diaVencimento: number;
  contaId: number;
  /** null = cartão físico (principal); id do físico = cartão virtual (R18). */
  cartaoPrincipalId: number | null;
  /** R41 - teto de gasto deste cartão dentro do limite compartilhado; null = sem teto próprio. */
  limiteProprio: number | null;
}

export interface TransferenciaAgendada {
  id: number;
  dataAgendada: number;
  contaOrigem: string;
  contaDestino: string;
  valor: number;
  executada: boolean;
}

export interface PatrimonioPonto {
  id: number;
  dataMillis: number;
  valorTotal: number;
  mesReferencia: string;
}

export interface Transacao {
  id: number;
  descricao: string;
  valor: number;
  bancoNome: string;
  categoriaNome: string;
  categoriaCorHex: string;
  timestamp: number;
}

/** Item da lixeira (R31): JSON da despesa excluída. */
export interface Lixeira {
  id: number;
  tipo: "DESPESA";
  descricao: string;
  valor: number;
  excluidoEm: number;
  payload: string;
}

/** Todas as coleções do contrato (nomes = coleções do Firestore). */
export interface Dataset {
  contas: Conta[];
  despesas: Despesa[];
  despesasFixas: DespesaFixa[];
  categorias: Categoria[];
  orcamentos: Orcamento[];
  metas: Meta[];
  investimentos: Investimento[];
  cartoes: Cartao[];
  transferenciasAgendadas: TransferenciaAgendada[];
  patrimonio: PatrimonioPonto[];
  lixeira: Lixeira[];
  transacoes: Transacao[];
  /** R55 */
  regrasCategoria: RegraCategoria[];
  /** R56 */
  projetos: Projeto[];
  /** R58 */
  dividas: Divida[];
  /** R59 */
  pessoas: Pessoa[];
  acertos: Acerto[];
}

export type CollectionName = keyof Dataset;

export const COLLECTIONS: readonly CollectionName[] = [
  "contas",
  "despesas",
  "despesasFixas",
  "categorias",
  "orcamentos",
  "metas",
  "investimentos",
  "cartoes",
  "transferenciasAgendadas",
  "patrimonio",
  "lixeira",
  "transacoes",
  "regrasCategoria",
  "projetos",
  "dividas",
  "pessoas",
  "acertos",
];

export interface Entity {
  id: number;
}

export function emptyDataset(): Dataset {
  return {
    contas: [],
    despesas: [],
    despesasFixas: [],
    categorias: [],
    orcamentos: [],
    metas: [],
    investimentos: [],
    cartoes: [],
    transferenciasAgendadas: [],
    patrimonio: [],
    lixeira: [],
    transacoes: [],
    regrasCategoria: [],
    projetos: [],
    dividas: [],
    pessoas: [],
    acertos: [],
  };
}

export type Result<T = object> = ({ ok: true } & T) | { ok: false; erro: string };
