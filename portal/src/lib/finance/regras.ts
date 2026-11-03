import { normalizar } from "./texto";
import type { Despesa, RegraCategoria, Tipo, TipoMatchRegra } from "./types";

/**
 * R55 - Regras de categorização automática. Funções puras; espelham `domain/RegrasCategoria.kt`
 * (mesmos cenários nos testes). Texto comparado sem acento, sem diferenciar maiúsculas/minúsculas.
 */

export const TIPOS_MATCH: readonly TipoMatchRegra[] = ["CONTEM", "COMECA_COM", "IGUAL"];

export const ROTULO_MATCH: Record<TipoMatchRegra, string> = {
  CONTEM: "contém",
  COMECA_COM: "começa com",
  IGUAL: "é igual a",
};

/** Especificidade do tipo de casamento (usada só no desempate final). */
const PESO_MATCH: Record<TipoMatchRegra, number> = { CONTEM: 0, COMECA_COM: 1, IGUAL: 2 };

export interface EntradaRegra {
  descricao: string;
  conta?: string | null;
  tipo?: Tipo | null;
}

/** Descrição normalizada (a mesma usada para casar regras e para o aprendizado). */
export function descricaoNormalizada(s: string): string {
  return normalizar(s);
}

function casa(padraoNorm: string, tipoMatch: TipoMatchRegra, descNorm: string): boolean {
  if (!padraoNorm) return false;
  switch (tipoMatch) {
    case "CONTEM":
      return descNorm.includes(padraoNorm);
    case "COMECA_COM":
      return descNorm.startsWith(padraoNorm);
    case "IGUAL":
      return descNorm === padraoNorm;
    default:
      return false;
  }
}

/** A regra se aplica a esta entrada? (ativa, padrão não vazio, casamento, restrições de conta e tipo). */
export function regraCasa(r: RegraCategoria, entrada: EntradaRegra): boolean {
  if (!r.ativa) return false;
  if (!r.categoria || !r.categoria.trim()) return false;
  if (!casa(normalizar(r.padrao), r.tipoMatch, normalizar(entrada.descricao))) return false;
  const conta = r.conta?.trim();
  if (conta && conta !== (entrada.conta ?? "").trim()) return false;
  if (r.tipo && r.tipo !== entrada.tipo) return false;
  return true;
}

/**
 * Regra vencedora: maior `prioridade`; empate = padrão normalizado mais longo; depois tipo de casamento mais
 * específico (IGUAL > COMECA_COM > CONTEM); depois restrição de conta/tipo; por fim a de menor id (mais antiga).
 */
export function aplicarRegras(regras: readonly RegraCategoria[], entrada: EntradaRegra): RegraCategoria | null {
  let melhor: RegraCategoria | null = null;
  for (const r of regras) {
    if (!regraCasa(r, entrada)) continue;
    if (melhor === null || vence(r, melhor)) melhor = r;
  }
  return melhor;
}

function restricoes(r: RegraCategoria): number {
  return (r.conta?.trim() ? 1 : 0) + (r.tipo ? 1 : 0);
}

function vence(a: RegraCategoria, b: RegraCategoria): boolean {
  if (a.prioridade !== b.prioridade) return a.prioridade > b.prioridade;
  const la = normalizar(a.padrao).length;
  const lb = normalizar(b.padrao).length;
  if (la !== lb) return la > lb;
  if (PESO_MATCH[a.tipoMatch] !== PESO_MATCH[b.tipoMatch]) return PESO_MATCH[a.tipoMatch] > PESO_MATCH[b.tipoMatch];
  if (restricoes(a) !== restricoes(b)) return restricoes(a) > restricoes(b);
  return a.id < b.id;
}

/** Categoria sugerida pela regra vencedora (ou null). */
export function categoriaPorRegras(regras: readonly RegraCategoria[], entrada: EntradaRegra): string | null {
  return aplicarRegras(regras, entrada)?.categoria.trim() ?? null;
}

// ---------------------------------------------------------------- validação de uma regra nova

export function validarRegra(r: Pick<RegraCategoria, "padrao" | "categoria" | "tipoMatch" | "prioridade">): string | null {
  if (!normalizar(r.padrao)) return "Informe o texto da regra.";
  if (r.padrao.trim().length > 80) return "O texto da regra deve ter até 80 caracteres.";
  if (!r.categoria.trim()) return "Informe a categoria.";
  if (!TIPOS_MATCH.includes(r.tipoMatch)) return "Tipo de comparação inválido.";
  if (!Number.isFinite(r.prioridade) || !Number.isInteger(r.prioridade)) return "A prioridade deve ser um número inteiro.";
  return null;
}

// ---------------------------------------------------------------- R55(3): aplicar às existentes

export interface MudancaRegra {
  id: number;
  descricao: string;
  de: string;
  para: string;
  regraId: number;
}

/**
 * Prévia: lançamentos NORMAL cuja categoria mudaria se as regras fossem aplicadas agora. Não altera nada.
 * Só lista o que realmente muda (categoria diferente, comparada sem acento/caixa).
 */
export function previaRegrasExistentes(despesas: readonly Despesa[], regras: readonly RegraCategoria[]): MudancaRegra[] {
  const out: MudancaRegra[] = [];
  for (const d of despesas) {
    if (d.natureza !== "NORMAL") continue;
    const r = aplicarRegras(regras, { descricao: d.descricao, conta: d.conta, tipo: d.tipo });
    if (!r) continue;
    const para = r.categoria.trim();
    if (normalizar(para) === normalizar(d.categoria)) continue;
    out.push({ id: d.id, descricao: d.descricao, de: d.categoria, para, regraId: r.id });
  }
  return out;
}

/** Aplica as mudanças (de uma prévia confirmada) e devolve as novas despesas + o que é preciso para desfazer. */
export function aplicarMudancas(
  despesas: readonly Despesa[],
  mudancas: readonly MudancaRegra[],
): { despesas: Despesa[]; desfazer: { id: number; categoria: string }[] } {
  const por = new Map(mudancas.map((m) => [m.id, m]));
  const desfazer: { id: number; categoria: string }[] = [];
  const novas = despesas.map((d) => {
    const m = por.get(d.id);
    if (!m || d.natureza !== "NORMAL") return d;
    desfazer.push({ id: d.id, categoria: d.categoria });
    return { ...d, categoria: m.para };
  });
  return { despesas: novas, desfazer };
}

/** Desfaz: restaura a categoria anterior só dos lançamentos que ainda existem. */
export function desfazerMudancas(despesas: readonly Despesa[], desfazer: readonly { id: number; categoria: string }[]): Despesa[] {
  const por = new Map(desfazer.map((m) => [m.id, m.categoria]));
  return despesas.map((d) => (por.has(d.id) ? { ...d, categoria: por.get(d.id) as string } : d));
}

// ---------------------------------------------------------------- R55(4): aprendizado leve

export interface SugestaoRegra {
  /** descrição normalizada (vira o padrão da regra, IGUAL) */
  padrao: string;
  categoria: string;
  vezes: number;
}

export const MIN_VEZES_APRENDIZADO = 2;

/**
 * Depois de o usuário trocar a categoria de um lançamento: se já existem 2+ lançamentos NORMAL com a mesma
 * descrição normalizada e esta MESMA categoria (contando o editado) e nenhuma regra ativa já dá essa categoria
 * para a descrição, sugere "Criar regra". Nunca cria sozinho.
 */
export function sugerirRegraAprendida(
  despesas: readonly Despesa[],
  regras: readonly RegraCategoria[],
  editado: Pick<Despesa, "id" | "descricao" | "categoria" | "conta" | "tipo">,
): SugestaoRegra | null {
  const padrao = normalizar(editado.descricao);
  const categoria = editado.categoria.trim();
  if (!padrao || !categoria) return null;
  const vezes = despesas.filter(
    (d) => d.natureza === "NORMAL" && normalizar(d.descricao) === padrao && normalizar(d.categoria) === normalizar(categoria),
  ).length;
  if (vezes < MIN_VEZES_APRENDIZADO) return null;
  const atual = aplicarRegras(regras, { descricao: editado.descricao, conta: editado.conta, tipo: editado.tipo });
  if (atual && normalizar(atual.categoria) === normalizar(categoria)) return null;
  // regra inativa/igual já existente com o mesmo padrão e categoria: não insiste
  const jaTem = regras.some((r) => normalizar(r.padrao) === padrao && normalizar(r.categoria) === normalizar(categoria));
  if (jaTem) return null;
  return { padrao, categoria, vezes };
}


export function novaRegraAprendida(s: SugestaoRegra, id: number, agora: number): RegraCategoria {
  return { id, padrao: s.padrao, tipoMatch: "IGUAL", categoria: s.categoria, conta: null, tipo: null, ativa: true, prioridade: 0, criadaEm: agora };
}
