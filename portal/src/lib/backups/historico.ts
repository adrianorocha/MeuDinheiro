import type { Dataset } from "../finance/types";

/** R66 - contagens por coleção (mesmas chaves do app Android), comparação para a prévia de restauração e lembrete. */

export type Contagens = Record<string, number>;

export const COLECOES: readonly { chave: string; rotulo: string }[] = [
  { chave: "lancamentos", rotulo: "Lançamentos" },
  { chave: "contas", rotulo: "Contas" },
  { chave: "cartoes", rotulo: "Cartões" },
  { chave: "categorias", rotulo: "Categorias" },
  { chave: "orcamentos", rotulo: "Orçamentos" },
  { chave: "metas", rotulo: "Metas" },
  { chave: "investimentos", rotulo: "Investimentos" },
  { chave: "recorrencias", rotulo: "Recorrências" },
  { chave: "dividas", rotulo: "Dívidas" },
  { chave: "pessoas", rotulo: "Pessoas" },
  { chave: "acertos", rotulo: "Acertos" },
  { chave: "projetos", rotulo: "Projetos" },
  { chave: "regras", rotulo: "Regras" },
  { chave: "lixeira", rotulo: "Lixeira" },
];

export function contagens(ds: Dataset): Contagens {
  return {
    lancamentos: ds.despesas.length,
    contas: ds.contas.length,
    cartoes: ds.cartoes.length,
    categorias: ds.categorias.length,
    orcamentos: ds.orcamentos.length,
    metas: ds.metas.length,
    investimentos: ds.investimentos.length,
    recorrencias: ds.despesasFixas.length,
    dividas: ds.dividas.length,
    pessoas: ds.pessoas.length,
    acertos: ds.acertos.length,
    projetos: ds.projetos.length,
    regras: ds.regrasCategoria.length,
    lixeira: ds.lixeira.length,
  };
}

export interface LinhaComparacao {
  chave: string;
  rotulo: string;
  atual: number;
  arquivo: number;
  diferenca: number;
}

/** Compara o que existe hoje com o que o arquivo traria, por coleção. */
export function compararContagens(atual: Contagens, arquivo: Contagens): LinhaComparacao[] {
  return COLECOES.map(({ chave, rotulo }) => {
    const a = atual[chave] ?? 0;
    const b = arquivo[chave] ?? 0;
    return { chave, rotulo, atual: a, arquivo: b, diferenca: b - a };
  });
}

export const DIAS_LEMBRETE = 7;
export const MAX_HISTORICO = 7;

/** Avisa quando o último backup tem mais de [dias] dias (ou nunca houve um). */
export function precisaLembrete(ultimoEm: number | null, agora: number, dias = DIAS_LEMBRETE): boolean {
  if (ultimoEm === null) return true;
  return agora - ultimoEm > dias * 86_400_000;
}

export interface RegistroBackup {
  id: string;
  criadoEm: number;
  tipo: "MANUAL" | "SEGURANCA";
  contagens: Contagens;
  tamanho: number;
  /** Texto JSON no formato de store/backup.ts. */
  json: string;
}

/** Mantém só os [max] mais recentes (mais novo primeiro) e devolve também quem saiu. */
export function limitarHistorico<T extends { criadoEm: number }>(lista: readonly T[], max = MAX_HISTORICO): { manter: T[]; descartar: T[] } {
  const ordenada = [...lista].sort((a, b) => b.criadoEm - a.criadoEm);
  return { manter: ordenada.slice(0, max), descartar: ordenada.slice(max) };
}
