import type { Dataset, Despesa } from "../finance/types";

/** R66 - trilha local de alterações de lançamentos (funções puras; o armazenamento fica em trilhaStore.ts). */

export const LIMITE_TRILHA = 2000;
const MAX_POR_OPERACAO = 50;

export type AcaoTrilha = "CRIAR" | "EDITAR" | "EXCLUIR" | "RESTAURAR_LIXEIRA" | "PAGAR" | "DESPAGAR" | "RESTAURAR_BACKUP" | "EXCLUIR_CARTAO";

export const ROTULO_ACAO: Record<AcaoTrilha, string> = {
  CRIAR: "Criado",
  EDITAR: "Editado",
  EXCLUIR: "Excluído",
  RESTAURAR_LIXEIRA: "Restaurado da lixeira",
  PAGAR: "Marcado como pago",
  DESPAGAR: "Marcado como pendente",
  RESTAURAR_BACKUP: "Backup restaurado",
  EXCLUIR_CARTAO: "Cartão excluído",
};

export interface MudancaCampo {
  campo: string;
  antigo: string;
  novo: string;
}

export interface EntradaTrilha {
  quando: number;
  acao: AcaoTrilha;
  id: number | null;
  descricao: string;
  valor: number | null;
  alteracoes: MudancaCampo[];
}

const p2 = (n: number) => String(n).padStart(2, "0");
const dataTxt = (ms: number) => {
  const d = new Date(ms);
  return `${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()}`;
};

/** Campos que mudaram (nomes e valores antigo→novo; sem dados sensíveis extras). */
export function diferencasDespesa(a: Despesa, b: Despesa): MudancaCampo[] {
  const r: MudancaCampo[] = [];
  const cmp = (campo: string, x: unknown, y: unknown) => {
    if (x !== y) r.push({ campo, antigo: x == null ? "" : String(x), novo: y == null ? "" : String(y) });
  };
  cmp("descricao", a.descricao, b.descricao);
  cmp("valor", a.valor, b.valor);
  cmp("categoria", a.categoria, b.categoria);
  cmp("conta", a.conta, b.conta);
  cmp("tipo", a.tipo, b.tipo);
  if (a.data !== b.data) r.push({ campo: "data", antigo: dataTxt(a.data), novo: dataTxt(b.data) });
  cmp("cartaoId", a.cartaoId, b.cartaoId);
  cmp("pago", a.pago, b.pago);
  cmp("tags", (a.tags ?? []).join(","), (b.tags ?? []).join(","));
  cmp("natureza", a.natureza, b.natureza);
  return r;
}

/**
 * Compara os lançamentos antes/depois de uma operação e devolve as entradas da trilha.
 * `dica` = "RESTAURAR_LIXEIRA" faz os lançamentos novos virarem "restaurado da lixeira" em vez de "criado".
 */
export function entradasDoDiff(antes: Dataset, depois: Dataset, quando: number, dica?: "RESTAURAR_LIXEIRA"): EntradaTrilha[] {
  const mapaAntes = new Map(antes.despesas.map((d) => [d.id, d]));
  const mapaDepois = new Map(depois.despesas.map((d) => [d.id, d]));
  const r: EntradaTrilha[] = [];
  const base = (acao: AcaoTrilha, d: Despesa, alteracoes: MudancaCampo[] = []): EntradaTrilha => ({
    quando, acao, id: d.id, descricao: d.descricao, valor: d.valor, alteracoes,
  });
  for (const d of depois.despesas) {
    const a = mapaAntes.get(d.id);
    if (!a) {
      r.push(base(dica ?? "CRIAR", d));
      continue;
    }
    if (a === d) continue;
    const dif = diferencasDespesa(a, d);
    if (dif.length === 0) continue;
    if (dif.length === 1 && dif[0].campo === "pago") r.push(base(d.pago ? "PAGAR" : "DESPAGAR", d));
    else r.push(base("EDITAR", d, dif));
  }
  for (const a of antes.despesas) if (!mapaDepois.has(a.id)) r.push(base("EXCLUIR", a));
  if (r.length <= MAX_POR_OPERACAO) return r;
  // Operação em lote (ex.: 120 parcelas): guarda as primeiras e um resumo.
  const resumo: EntradaTrilha = { quando, acao: r[0].acao, id: null, descricao: `… e mais ${r.length - MAX_POR_OPERACAO} lançamentos nesta operação`, valor: null, alteracoes: [] };
  return [...r.slice(0, MAX_POR_OPERACAO), resumo];
}

/** Acrescenta [novas] (mais antigas primeiro) ao fim e descarta as mais antigas além do [limite]. */
export function aplicarLimite(existentes: readonly EntradaTrilha[], novas: readonly EntradaTrilha[], limite = LIMITE_TRILHA): EntradaTrilha[] {
  const todas = [...existentes, ...novas];
  return todas.length > limite ? todas.slice(todas.length - limite) : todas;
}

export interface FiltroTrilha {
  de?: number | null;
  ate?: number | null;
  acao?: AcaoTrilha | null;
  busca?: string;
}

export function filtrarTrilha(lista: readonly EntradaTrilha[], f: FiltroTrilha): EntradaTrilha[] {
  const termo = (f.busca ?? "").trim().toLowerCase();
  return lista.filter(
    (e) =>
      (f.de == null || e.quando >= f.de) &&
      (f.ate == null || e.quando <= f.ate) &&
      (f.acao == null || e.acao === f.acao) &&
      (termo === "" ||
        e.descricao.toLowerCase().includes(termo) ||
        e.alteracoes.some((m) => `${m.campo} ${m.antigo} ${m.novo}`.toLowerCase().includes(termo)) ||
        (e.valor !== null && String(e.valor).includes(termo))),
  );
}
