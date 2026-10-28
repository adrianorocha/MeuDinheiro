import { toCents, fromCents } from "./money";
import { cartaoIdDe } from "./calc";
import { normalizar } from "./texto";
import type { Despesa, Projeto } from "./types";

/**
 * R56 - Tags e projetos. Funções puras; espelham `domain/TagsProjetos.kt` (mesmos cenários nos testes).
 * - Rótulo: texto digitado, sem `#` inicial, espaços aparados/colapsados, até 30 caracteres.
 * - Chave: rótulo normalizado (minúsculas, sem acento). Duas tags com a mesma chave são a mesma tag.
 */

export const MAX_TAGS = 5;
export const MAX_TAG_CHARS = 30;

/** Rótulo limpo de UMA tag ("" se ficar vazia). */
export function rotuloTag(s: string): string {
  const t = s.trim().replace(/^#+/, "").replace(/\s+/g, " ").trim();
  return t.length > MAX_TAG_CHARS ? t.slice(0, MAX_TAG_CHARS).trim() : t;
}

/** Chave (minúsculas, sem acento, sem `#`) de uma tag ou de um rótulo. */
export function chaveTag(s: string): string {
  return normalizar(rotuloTag(s));
}

/** Lista de rótulos válida: limpa, sem repetidas (pela chave, mantém a 1ª), no máximo [MAX_TAGS]. */
export function normalizarTags(entrada: readonly string[] | null | undefined): string[] {
  const out: string[] = [];
  const vistas = new Set<string>();
  for (const bruto of entrada ?? []) {
    if (typeof bruto !== "string") continue;
    const r = rotuloTag(bruto);
    const k = normalizar(r);
    if (!k || vistas.has(k)) continue;
    vistas.add(k);
    out.push(r);
    if (out.length >= MAX_TAGS) break;
  }
  return out;
}

export function temTag(d: Pick<Despesa, "tags">, chave: string): boolean {
  const k = chaveTag(chave);
  return !!k && (d.tags ?? []).some((t) => chaveTag(t) === k);
}

/** Tags já usadas (rótulo do primeiro uso, ordenadas por uso decrescente e depois alfabeticamente), para sugestões. */
export function tagsUsadas(despesas: readonly Despesa[]): { chave: string; rotulo: string; usos: number }[] {
  const m = new Map<string, { chave: string; rotulo: string; usos: number }>();
  for (const d of despesas) {
    for (const t of d.tags ?? []) {
      const k = chaveTag(t);
      if (!k) continue;
      const g = m.get(k);
      if (g) g.usos += 1;
      else m.set(k, { chave: k, rotulo: rotuloTag(t), usos: 1 });
    }
  }
  return [...m.values()].sort((a, b) => b.usos - a.usos || a.rotulo.localeCompare(b.rotulo, "pt-BR"));
}

// ---------------------------------------------------------------- projetos

export type StatusProjeto = "SEM_ORCAMENTO" | "NO_ORCAMENTO" | "ATENCAO" | "ESTOUROU";

export interface ResumoProjeto {
  projeto: Projeto;
  /** gasto líquido em centavos (despesas somam, estornos de cartão abatem) */
  gastoCentavos: number;
  gasto: number;
  orcamento: number | null;
  /** 0..n (1 = 100% do orçamento); null sem orçamento */
  percentual: number | null;
  status: StatusProjeto;
  quantidade: number;
  porCategoria: { nome: string; total: number }[];
  porMes: { mes: string; total: number }[];
  /** lançamentos que compõem o gasto (mais recentes primeiro) */
  itens: Despesa[];
}

/** Lançamento conta para o projeto? NORMAL, com a tag e dentro do período (inicio/fim) quando definido. */
export function lancamentoDoProjeto(p: Pick<Projeto, "tag" | "inicio" | "fim">, d: Despesa): boolean {
  if (d.natureza !== "NORMAL") return false;
  if (!temTag(d, p.tag)) return false;
  if (p.inicio != null && d.data < p.inicio) return false;
  if (p.fim != null && d.data > p.fim) return false;
  return true;
}

/** Contribuição ao gasto (R19, tipo DESPESA): débito soma; estorno de cartão abate; receita comum fica de fora. */
function contribuicao(d: Despesa): number | null {
  const c = toCents(d.valor);
  if (d.tipo === "DEBITO") return c;
  return cartaoIdDe(d) !== null ? -c : null;
}

export function resumoProjeto(p: Projeto, despesas: readonly Despesa[]): ResumoProjeto {
  const sel: { d: Despesa; c: number }[] = [];
  for (const d of despesas) {
    if (!lancamentoDoProjeto(p, d)) continue;
    const c = contribuicao(d);
    if (c !== null) sel.push({ d, c });
  }
  const total = sel.reduce((a, s) => a + s.c, 0);
  const cat = new Map<string, { nome: string; cents: number }>();
  const mes = new Map<string, number>();
  for (const { d, c } of sel) {
    const k = normalizar(d.categoria);
    const g = cat.get(k) ?? { nome: d.categoria.trim() || "Sem categoria", cents: 0 };
    g.cents += c;
    cat.set(k, g);
    const dt = new Date(d.data);
    const mk = `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, "0")}`;
    mes.set(mk, (mes.get(mk) ?? 0) + c);
  }
  const orc = p.orcamento != null && p.orcamento > 0 ? p.orcamento : null;
  const percentual = orc !== null ? total / toCents(orc) : null;
  let status: StatusProjeto = "SEM_ORCAMENTO";
  if (percentual !== null) status = percentual > 1 ? "ESTOUROU" : percentual >= 0.8 ? "ATENCAO" : "NO_ORCAMENTO";
  return {
    projeto: p,
    gastoCentavos: total,
    gasto: fromCents(total),
    orcamento: orc,
    percentual,
    status,
    quantidade: sel.length,
    porCategoria: [...cat.values()]
      .sort((a, b) => Math.abs(b.cents) - Math.abs(a.cents) || a.nome.localeCompare(b.nome, "pt-BR"))
      .map((g) => ({ nome: g.nome, total: fromCents(g.cents) })),
    porMes: [...mes.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([m, c]) => ({ mes: m, total: fromCents(c) })),
    itens: sel.map((s) => s.d).sort((a, b) => b.data - a.data || b.id - a.id),
  };
}

export function validarProjeto(
  p: Pick<Projeto, "nome" | "tag" | "orcamento" | "inicio" | "fim">,
  outros: readonly Pick<Projeto, "id" | "tag">[] = [],
  idAtual?: number,
): string | null {
  if (!p.nome.trim()) return "Informe o nome do projeto.";
  if (!chaveTag(p.tag)) return "Informe a tag do projeto.";
  if (outros.some((o) => o.id !== idAtual && chaveTag(o.tag) === chaveTag(p.tag))) return "Já existe um projeto com esta tag.";
  if (p.orcamento != null && !(p.orcamento >= 0 && Number.isFinite(p.orcamento))) return "O orçamento deve ser um valor válido.";
  if (p.inicio != null && p.fim != null && p.fim < p.inicio) return "A data final não pode ser anterior à inicial.";
  return null;
}
