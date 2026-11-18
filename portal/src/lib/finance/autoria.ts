import type { Dataset, Despesa } from "./types";

/**
 * R67 - autoria do lançamento (contas compartilhadas). Funções puras.
 * `autor` = nome (no portal, o e-mail) de quem lançou - é o `autorNome` do contrato; "Importação" = conciliador.
 * `autorUid` = uid do Firebase Auth, só no documento quando preenchido. Em modo local-only fica vazio.
 */

export interface Identidade {
  uid: string;
  nome: string;
}

export const AUTOR_IMPORTACAO_NOME = "Importação";

/**
 * Carimba os lançamentos NOVOS (ids ausentes em `prev`) de `next`.
 * - "preencher": só o que estiver vazio (mantém "Importação" e o autor original de itens restaurados/derivados);
 * - "executor": duplicar/repetir - o autor é quem executa a ação, nunca o do original.
 * Sem identidade (modo local) em "executor" limpa o autor copiado; em "preencher" não mexe.
 */
export function estamparNovos(prev: Dataset, next: Dataset, quem: Identidade | null, modo: "preencher" | "executor"): Dataset {
  const antigos = new Set(prev.despesas.map((d) => d.id));
  let mudou = false;
  const despesas = next.despesas.map((d) => {
    if (antigos.has(d.id)) return d;
    const novo = carimbar(d, quem, modo);
    if (novo !== d) mudou = true;
    return novo;
  });
  return mudou ? { ...next, despesas } : next;
}

function carimbar(d: Despesa, quem: Identidade | null, modo: "preencher" | "executor"): Despesa {
  if (modo === "executor") {
    const { autorUid: _antigo, ...resto } = d;
    void _antigo;
    return { ...resto, autor: quem ? quem.nome : null, ...(quem ? { autorUid: quem.uid } : {}) };
  }
  if (!quem) return d;
  const semUid = !d.autorUid;
  const semNome = !d.autor || !d.autor.trim();
  if (!semUid && !semNome) return d;
  return { ...d, ...(semNome ? { autor: quem.nome } : {}), ...(semUid ? { autorUid: quem.uid } : {}) };
}

export type SelecaoQuem = { tipo: "TODOS" } | { tipo: "EU" } | { tipo: "MEMBRO"; uid: string };

/**
 * Filtro "Quem lançou" e visão "Minhas despesas" x "Conjunta" (padrão = TODOS). Lançamento sem autorUid (dado antigo, modo local,
 * importação sem uid) NÃO pertence a "Minhas" nem a um membro específico; continua em "Conjunta".
 */
export function filtrarQuemLancou<T extends { autorUid?: string | null }>(itens: readonly T[], sel: SelecaoQuem, meuUid: string | null | undefined): T[] {
  if (sel.tipo === "TODOS") return [...itens];
  if (sel.tipo === "EU") return meuUid ? itens.filter((d) => d.autorUid === meuUid) : [];
  return itens.filter((d) => d.autorUid === sel.uid);
}

export interface AutorDistinto {
  uid: string;
  nome: string;
}

/** Autores distintos (uid -> último nome visto), sem o próprio usuário, por nome. */
export function autoresDistintos(itens: readonly Pick<Despesa, "autorUid" | "autor">[], meuUid: string | null | undefined): AutorDistinto[] {
  const mapa = new Map<string, string>();
  for (const d of itens) {
    if (d.autorUid && d.autorUid !== meuUid) mapa.set(d.autorUid, d.autor?.trim() || "Membro");
  }
  return [...mapa].map(([uid, nome]) => ({ uid, nome })).sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
}

/** Nome curto para exibir: e-mail vira a parte antes do @. */
export function nomeCurto(autor: string): string {
  const t = autor.trim();
  return t.includes("@") ? t.split("@")[0] : t;
}

/** Texto discreto "por <nome>"; só quando a conta tem membros e o lançamento traz autor. */
export function rotuloAutor(autor: string | null | undefined, contaTemMembros: boolean): string | null {
  if (!contaTemMembros) return null;
  const t = autor?.trim();
  return t ? `por ${nomeCurto(t)}` : null;
}
