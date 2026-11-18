import type { SnapshotIndices } from "./bcb";

/**
 * R97 - índices econômicos e taxas de REFERÊNCIA (portal).
 *
 * Os valores iniciais ("semente") foram lidos manualmente da página https://www.debit.com.br/tabelas/indicadores-economicos
 * em 28/10/2026. A página não informa a data de atualização; por isso as competências são APROXIMADAS. Não há consulta
 * de rede em tempo de execução: o usuário edita os valores e a edição fica só em localStorage (nunca no backup/Firestore).
 *
 * Convenção: a semente e a tela usam PERCENTUAIS como na fonte (1,08 = 1,08% a.m.); as funções de conversão e de
 * composição usam FRAÇÕES decimais (0,0108). `pctParaFracao` / `fracaoParaPct` fazem a ponte.
 */

export const FONTE_INDICES = "Fonte: Banco Central do Brasil (SGS/PTAX); semente Debit (28/10/2026) como valor de reserva; confira antes de decidir";
export const FONTE_SEMENTE = "Fonte: Debit — conferido em 28/10/2026; confira antes de decidir";
export const URL_FONTE_INDICES = "https://www.debit.com.br/tabelas/indicadores-economicos";
export const DATA_SEMENTE = "2026-10-28";

export type IndiceId =
  | "selic"
  | "cdi"
  | "tr"
  | "poupanca"
  | "tbf"
  | "tjlp"
  | "ipca"
  | "inpc"
  | "igpm"
  | "igpdi"
  | "incc"
  | "ipcfipe"
  | "ipcae"
  | "ivar";

export type GrupoIndice = "TAXA" | "INFLACAO";

/** R99: de onde veio o valor exibido. "manual" (edição do usuário) sempre prevalece sobre "bcb". */
export type OrigemIndice = "semente" | "bcb" | "manual";

export interface ItemIndice {
  id: IndiceId;
  nome: string;
  grupo: GrupoIndice;
  /** Últimos valores em % no mês, do mais antigo para o mais recente (o último é o "valor mensal"). */
  serie: number[];
  /** Competência do ÚLTIMO valor ("MM/AAAA"); vazio = desconhecida. */
  competencia: string;
  /** true = competência estimada (a fonte não informa). */
  aproximada: boolean;
  /** Final do endereço https://www.debit.com.br/tabelas/<slug>; null = sem página específica. */
  slug: string | null;
  /** R99: origem do valor (ausente = semente). */
  origem?: OrigemIndice;
  /** R99: competência é o mês corrente (acumulado parcial do mês). */
  parcial?: boolean;
}

export interface SalarioMinimo {
  /** Centavos. */
  centavos: number;
  competencia: string;
  origem?: OrigemIndice;
}

/** Valores que a página NÃO traz: o usuário informa (null = não informado). Os de referência são estimativas editáveis. */
export interface ExtrasIndices {
  /** Meta da Selic, % a.a. (null = não informada). */
  selicMetaAnual: number | null;
  /** R99: origem da meta da Selic exibida (null = não informada). */
  selicMetaOrigem?: OrigemIndice | null;
  /** Inflação acumulada em 12 meses, % (null = não informada). */
  inflacao12m: number | null;
  /** Estimativa (edite): juros do rotativo do cartão, % a.m. */
  taxaRotativoMensal: number;
  /** Estimativa (edite): juros do cheque especial, % a.m. */
  taxaChequeEspecialMensal: number;
}

export interface ConjuntoIndices {
  /** Data (AAAA-MM-DD) em que o usuário conferiu/atualizou os valores. */
  conferidoEm: string;
  itens: ItemIndice[];
  salarioMinimo: SalarioMinimo;
  extras: ExtrasIndices;
}

const SLUGS: Partial<Record<IndiceId, string>> = {
  ipca: "ipca-indice-nacional-de-precos-ao-consumidor-amplo",
  igpm: "igpm-fgv-indice-geral-de-precos-mercado",
  inpc: "inpc-indice-nacional-de-precos-ao-consumidor",
  selic: "selic",
  cdi: "cdi",
  poupanca: "poupanca",
  tr: "tr-bacen",
  incc: "incc-indice-nacional-da-construcao-civil",
  ipcfipe: "ipc-indice-de-precos-ao-consumidor-fipe",
};
export const SLUG_SALARIO_MINIMO = "salario-minimo";

export function urlHistorico(slug: string | null): string | null {
  return slug ? `https://www.debit.com.br/tabelas/${slug}` : null;
}

function item(id: IndiceId, nome: string, grupo: GrupoIndice, serie: number[], competencia: string, aproximada: boolean): ItemIndice {
  return { id, nome, grupo, serie, competencia, aproximada, slug: SLUGS[id] ?? null };
}

/** Semente (28/10/2026). Taxas: competência aproximada (10/2026). Inflação: colunas 07, 08 e 09/2026. */
export function indicesPadrao(): ConjuntoIndices {
  return {
    conferidoEm: DATA_SEMENTE,
    itens: [
      item("selic", "Selic", "TAXA", [1.09, 1.08], "10/2026", true),
      item("cdi", "CDI", "TAXA", [1.09, 1.08], "10/2026", true),
      item("tr", "TR", "TAXA", [0.1693, 0.169, 0.1616], "10/2026", true),
      item("poupanca", "Poupança (já inclui a TR)", "TAXA", [0.6701, 0.6698, 0.6624], "10/2026", true),
      item("tbf", "TBF", "TAXA", [0.9749], "10/2026", true),
      item("tjlp", "TJLP", "TAXA", [0.73, 0.73, 0.74], "10/2026", true),
      item("ipca", "IPCA", "INFLACAO", [0.07, -0.32, 0.82], "09/2026", false),
      item("inpc", "INPC", "INFLACAO", [-0.01, -0.32, 0.82], "09/2026", false),
      item("igpm", "IGP-M", "INFLACAO", [-1.16, -0.22, 1.57], "09/2026", false),
      item("igpdi", "IGP-DI", "INFLACAO", [-0.86, 0.06, 1.5], "09/2026", false),
      item("incc", "INCC-DI", "INFLACAO", [0.61, 0.66, 0.22], "09/2026", false),
      item("ipcfipe", "IPC-Fipe", "INFLACAO", [-0.03, 0.01, 0.51], "09/2026", false),
      item("ipcae", "IPCA-E", "INFLACAO", [0.06, -0.4, 0.7], "09/2026", false),
      item("ivar", "IVAR", "INFLACAO", [0.66, 0.29, 0.28], "09/2026", false),
    ],
    salarioMinimo: { centavos: 162100, competencia: "2026" },
    extras: { selicMetaAnual: null, inflacao12m: null, taxaRotativoMensal: 14, taxaChequeEspecialMensal: 8 },
  };
}

// ------------------------------------------------------------------ conversões (frações decimais)

export const pctParaFracao = (pct: number): number => pct / 100;
export const fracaoParaPct = (f: number): number => f * 100;

/** Taxa anual equivalente: (1 + i_mensal)^12 − 1. */
export function anualDeMensal(mensal: number): number {
  return Math.pow(1 + mensal, 12) - 1;
}

/** Taxa mensal equivalente: (1 + i_anual)^(1/12) − 1. */
export function mensalDeAnual(anual: number): number {
  return Math.pow(1 + anual, 1 / 12) - 1;
}

/** Acumulado por composição de uma lista de taxas (frações): Π(1 + i) − 1. Lista vazia = 0. */
export function acumular(taxas: readonly number[]): number {
  let f = 1;
  for (const t of taxas) f *= 1 + t;
  return f - 1;
}

export interface AcumuladoN {
  /** Fração acumulada. */
  acumulado: number;
  /** Meses efetivamente compostos (pode ser menor que o pedido). */
  mesesUsados: number;
  /** true quando a série tinha menos meses que o pedido. */
  incompleto: boolean;
}

/** Acumulado dos ÚLTIMOS `n` meses de uma série em % (a mais recente por último), por composição. */
export function acumuladoDosUltimos(seriePct: readonly number[], n: number): AcumuladoN {
  const k = Math.max(0, Math.min(Math.trunc(n), seriePct.length));
  const fatia = k === 0 ? [] : seriePct.slice(seriePct.length - k);
  return { acumulado: acumular(fatia.map(pctParaFracao)), mesesUsados: k, incompleto: k < Math.trunc(n) };
}

/** Ganho real = (1 + nominal) / (1 + inflação) − 1 (frações). */
export function ganhoReal(nominal: number, inflacao: number): number {
  return (1 + nominal) / (1 + inflacao) - 1;
}

/** Valor mensal vigente (último da série), em %; NaN se vazio. */
export function valorMensalPct(i: Pick<ItemIndice, "serie">): number {
  return i.serie.length ? i.serie[i.serie.length - 1] : Number.NaN;
}

export function buscarIndice(c: ConjuntoIndices, id: IndiceId): ItemIndice {
  return c.itens.find((i) => i.id === id) ?? (indicesPadrao().itens.find((i) => i.id === id) as ItemIndice);
}

/** "MM/AAAA" do mês `k` meses antes da competência dada (k=0 = a própria); "" se a competência for inválida. */
export function competenciaAnterior(competencia: string, k: number): string {
  const m = /^(\d{2})\/(\d{4})$/.exec(competencia.trim());
  if (!m) return "";
  const idx = Number(m[2]) * 12 + (Number(m[1]) - 1) - k;
  const ano = Math.floor(idx / 12);
  const mes = (idx % 12) + 1;
  return `${String(mes).padStart(2, "0")}/${ano}`;
}

// ------------------------------------------------------------------ validação e persistência local

export const CHAVE_INDICES = "meudinheiro:indices:v1";

const ok = (n: unknown): n is number => typeof n === "number" && Number.isFinite(n) && Math.abs(n) < 1000;

/**
 * Reconstrói o conjunto a partir do JSON salvo: sempre parte da semente e só aceita campos válidos (nunca lança).
 * Itens desconhecidos são ignorados; faltantes voltam ao padrão.
 */
export function mesclarSalvo(salvo: unknown): ConjuntoIndices {
  const base = indicesPadrao();
  if (!salvo || typeof salvo !== "object") return base;
  const s = salvo as Record<string, unknown>;
  if (typeof s.conferidoEm === "string" && /^\d{4}-\d{2}-\d{2}$/.test(s.conferidoEm)) base.conferidoEm = s.conferidoEm;
  if (Array.isArray(s.itens)) {
    for (const bruto of s.itens) {
      if (!bruto || typeof bruto !== "object") continue;
      const r = bruto as Record<string, unknown>;
      const alvo = base.itens.find((i) => i.id === r.id);
      if (!alvo) continue;
      if (Array.isArray(r.serie) && r.serie.length >= 1 && r.serie.length <= 24 && r.serie.every(ok)) alvo.serie = [...r.serie];
      if (typeof r.competencia === "string" && (r.competencia === "" || /^\d{2}\/\d{4}$/.test(r.competencia))) alvo.competencia = r.competencia;
      if (typeof r.aproximada === "boolean") alvo.aproximada = r.aproximada;
      // R99: edição do usuário = "manual" (também para dados salvos na R97, sem o campo, que diferem da semente)
      if (r.origem === "manual" || (r.origem === undefined && foiEditado(alvo))) alvo.origem = "manual";
    }
  }
  const sm = s.salarioMinimo as Record<string, unknown> | undefined;
  if (sm && typeof sm.centavos === "number" && Number.isInteger(sm.centavos) && sm.centavos > 0 && sm.centavos < 1e9) base.salarioMinimo.centavos = sm.centavos;
  if (sm && typeof sm.competencia === "string" && sm.competencia.length <= 12) base.salarioMinimo.competencia = sm.competencia;
  if (sm && (sm.origem === "manual" || (sm.origem === undefined && base.salarioMinimo.centavos !== indicesPadrao().salarioMinimo.centavos))) base.salarioMinimo.origem = "manual";
  const ex = s.extras as Record<string, unknown> | undefined;
  if (ex) {
    if (ex.selicMetaAnual === null || ok(ex.selicMetaAnual)) base.extras.selicMetaAnual = ex.selicMetaAnual as number | null;
    if (base.extras.selicMetaAnual !== null) base.extras.selicMetaOrigem = "manual";
    if (ex.inflacao12m === null || ok(ex.inflacao12m)) base.extras.inflacao12m = ex.inflacao12m as number | null;
    if (ok(ex.taxaRotativoMensal) && ex.taxaRotativoMensal >= 0) base.extras.taxaRotativoMensal = ex.taxaRotativoMensal;
    if (ok(ex.taxaChequeEspecialMensal) && ex.taxaChequeEspecialMensal >= 0) base.extras.taxaChequeEspecialMensal = ex.taxaChequeEspecialMensal;
  }
  return base;
}

/** Subconjunto de `Storage` usado aqui (facilita testes e SSR). */
export interface ArmazemTexto {
  getItem(chave: string): string | null;
  setItem(chave: string, valor: string): void;
  removeItem(chave: string): void;
}

function armazemPadrao(): ArmazemTexto | null {
  try {
    return typeof window !== "undefined" ? window.localStorage : null;
  } catch {
    return null;
  }
}

export function carregarIndices(armazem: ArmazemTexto | null = armazemPadrao()): ConjuntoIndices {
  try {
    const raw = armazem?.getItem(CHAVE_INDICES);
    return mesclarSalvo(raw ? JSON.parse(raw) : null);
  } catch {
    return indicesPadrao();
  }
}

export function salvarIndices(c: ConjuntoIndices, armazem: ArmazemTexto | null = armazemPadrao()): void {
  try {
    armazem?.setItem(CHAVE_INDICES, JSON.stringify(c));
  } catch {
    // sem armazenamento: a edição vale só na sessão
  }
}

export function restaurarIndices(armazem: ArmazemTexto | null = armazemPadrao()): ConjuntoIndices {
  try {
    armazem?.removeItem(CHAVE_INDICES);
  } catch {
    // ignora
  }
  return indicesPadrao();
}

/** Compara com a semente (para mostrar "editado"). */
export function foiEditado(i: ItemIndice): boolean {
  if (i.origem === "bcb") return false;
  if (i.origem === "manual") return true;
  const p = indicesPadrao().itens.find((x) => x.id === i.id);
  return !p || p.competencia !== i.competencia || p.serie.length !== i.serie.length || p.serie.some((v, k) => v !== i.serie[k]);
}

// ------------------------------------------------------------------ R99: mesclagem manual x Banco Central

/**
 * Conjunto EFETIVO = base salva (semente + edições manuais) com os valores do BCB por cima, EXCETO onde o usuário
 * editou ("manual" prevalece; nunca é sobrescrito). Não muta `base`.
 */
export function aplicarBcb(base: ConjuntoIndices, snap: SnapshotIndices | null): ConjuntoIndices {
  const ef = structuredClone(base);
  ef.extras.selicMetaOrigem = ef.extras.selicMetaAnual === null ? null : (base.extras.selicMetaOrigem ?? "manual");
  if (!snap) return ef;
  for (const it of ef.itens) {
    const b = snap.itens[it.id];
    if (!b || it.origem === "manual") continue;
    it.serie = [...b.serie];
    it.competencia = b.competencia;
    it.aproximada = false;
    it.parcial = b.parcial;
    it.origem = "bcb";
  }
  if (snap.salarioMinimo && ef.salarioMinimo.origem !== "manual") {
    ef.salarioMinimo = { centavos: snap.salarioMinimo.centavos, competencia: snap.salarioMinimo.competencia, origem: "bcb" };
  }
  if (snap.selicMeta && ef.extras.selicMetaAnual === null) {
    ef.extras.selicMetaAnual = snap.selicMeta.valor;
    ef.extras.selicMetaOrigem = "bcb";
  }
  return ef;
}

/** Remove da cópia a ser salva o que veio do BCB (só semente + edições manuais vão para o localStorage dos índices). */
export function paraSalvar(base: ConjuntoIndices): ConjuntoIndices {
  const c = structuredClone(base);
  for (const it of c.itens) {
    delete it.parcial;
    if (it.origem !== "manual") delete it.origem;
  }
  if (c.salarioMinimo.origem !== "manual") delete c.salarioMinimo.origem;
  c.extras.selicMetaOrigem = c.extras.selicMetaAnual === null ? null : "manual";
  return c;
}
