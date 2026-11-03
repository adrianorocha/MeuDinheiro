import type { Cartao, Despesa } from "../finance/types";

/** R35 - transação lida do extrato (valor com sinal: entrada +, saída −). */
export interface TransacaoBanco {
  fitid: string;
  /** epoch ms, meio-dia local do dia da transação */
  data: number;
  valor: number;
  descricao: string;
  saldo?: number;
  /** R101 - final (4 dígitos) do cartão usado, quando o arquivo informa (coluna própria ou texto da linha) */
  finalCartao?: string;
}

export interface SaldoArquivo {
  valor: number;
  /** epoch ms (meio-dia local) a que o saldo se refere */
  data: number;
}

export interface ArquivoExtrato {
  formato: "OFX" | "CSV";
  transacoes: TransacaoBanco[];
  /** ACCTID do OFX, quando existir */
  acctId?: string;
  /** OFX de cartão de crédito */
  ehCartao?: boolean;
  saldoFinal?: SaldoArquivo;
  periodo: { inicio: number; fim: number } | null;
  avisos: string[];
}

/** R36 - destino da conciliação. */
export type Destino = { tipo: "CONTA"; conta: string } | { tipo: "CARTAO"; cartaoId: number };

export type ClasseMatch = "AUTOMATICO" | "SUGERIDO" | "SO_NO_EXTRATO" | "DUPLICADO";
export type TipoMatch = "EXATO" | "DIFERENCA";

export interface Candidato {
  lancamentoId: number;
  score: number;
  tipo: TipoMatch;
  /** lançamento − banco, em dias corridos */
  dias: number;
}

export interface ItemMatch {
  /** posição da transação no arquivo */
  indice: number;
  transacao: TransacaoBanco;
  classe: ClasseMatch;
  tipo?: TipoMatch;
  lancamentoId?: number;
  score?: number;
  dias?: number;
  /** demais candidatos (para trocar o lançamento sugerido), melhor primeiro */
  alternativas: Candidato[];
}

export interface OpcoesMatching {
  /** janela em dias (padrão 3; cartão 5) */
  janelaDias: number;
  /** tolerância relativa do valor (padrão 0,02) */
  toleranciaPct?: number;
  /** tolerância mínima em R$ (padrão 1,00) */
  toleranciaMin?: number;
  /** R101 - cartões do grupo do destino: o final da linha desempata a favor do lançamento do cartão certo */
  cartoes?: readonly Pick<Cartao, "id" | "finalCartao">[];
}

export interface ResultadoMatching {
  itens: ItemMatch[];
  soNoApp: Despesa[];
  janelaPeriodo: { inicio: number; fim: number } | null;
}
