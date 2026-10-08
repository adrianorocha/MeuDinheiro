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
  };
}

export type Result<T = object> = ({ ok: true } & T) | { ok: false; erro: string };
