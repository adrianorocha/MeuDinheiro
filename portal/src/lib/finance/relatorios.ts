import { cartaoIdDe, resumoFatura } from "./calc";
import { fimDoMes, inicioDoMes, mesAnoDe, normalizaMes } from "./dates";
import { fromCents, round2, toCents } from "./money";
import { normalizar } from "./texto";
import type { Dataset, Despesa } from "./types";

// ---------------------------------------------------------------- R19 filtro e resultado

export type TipoRelatorio = "DESPESA" | "RECEITA" | "TODOS";

export interface FiltroRelatorio {
  inicio: number;
  fim: number;
  /** números de conta (`despesa.conta`) */
  contas: string[];
  /** ids de cartões; físico inclui seus virtuais, virtual filtra só ele */
  cartoes: number[];
  categorias: string[];
  tipo: TipoRelatorio;
  /** null = pagos e pendentes */
  pago: boolean | null;
  texto: string;
  incluirInternos?: boolean;
}

export interface FatiaCategoria {
  nome: string;
  total: number;
  percentual: number;
}

export interface PontoMes {
  mes: string;
  total: number;
}

export interface ResumoRelatorio {
  quantidade: number;
  total: number;
  media: number;
  maior: number;
  porCategoria: FatiaCategoria[];
  porMes: PontoMes[];
}

export interface ResultadoRelatorio extends ResumoRelatorio {
  itens: Despesa[];
  anterior: ResumoRelatorio;
  variacaoPercentual: number | null;
}

export function filtroPadrao(inicio: number, fim: number): FiltroRelatorio {
  return { inicio, fim, contas: [], cartoes: [], categorias: [], tipo: "DESPESA", pago: null, texto: "", incluirInternos: false };
}

/** Contribuição em centavos de um lançamento para o total (null = fora do tipo escolhido). */
function contribuicao(d: Despesa, tipo: TipoRelatorio): number | null {
  const c = toCents(d.valor);
  const cartao = cartaoIdDe(d) !== null;
  if (tipo === "DESPESA") {
    if (d.tipo === "DEBITO") return c;
    return cartao ? -c : null; // estorno de cartão abate
  }
  if (tipo === "RECEITA") return d.tipo === "CREDITO" && !cartao ? c : null;
  return d.tipo === "CREDITO" ? c : -c;
}

function idsDeCartoes(ds: Pick<Dataset, "cartoes">, escolhidos: readonly number[]): Set<number> {
  const ids = new Set<number>();
  for (const id of escolhidos) {
    ids.add(id);
    const c = ds.cartoes.find((x) => x.id === id);
    if (c && c.cartaoPrincipalId == null) for (const v of ds.cartoes) if (v.cartaoPrincipalId === id) ids.add(v.id);
  }
  return ids;
}

function selecionar(ds: Pick<Dataset, "despesas" | "cartoes">, f: FiltroRelatorio, inicio: number, fim: number): { d: Despesa; c: number }[] {
  const contas = new Set(f.contas);
  const cats = new Set(f.categorias.map(normalizar));
  const cartoes = idsDeCartoes(ds, f.cartoes);
  const texto = normalizar(f.texto);
  const out: { d: Despesa; c: number }[] = [];
  for (const d of ds.despesas) {
    if (d.data < inicio || d.data > fim) continue;
    if (!f.incluirInternos && d.natureza !== "NORMAL") continue;
    if (contas.size > 0 && !contas.has(d.conta)) continue;
    if (cartoes.size > 0) {
      const cid = cartaoIdDe(d);
      if (cid === null || !cartoes.has(cid)) continue;
    }
    if (cats.size > 0 && !cats.has(normalizar(d.categoria))) continue;
    if (f.pago !== null && d.pago !== f.pago) continue;
    if (texto && !normalizar(`${d.descricao} ${d.categoria}`).includes(texto)) continue;
    const c = contribuicao(d, f.tipo);
    if (c === null) continue;
    out.push({ d, c });
  }
  return out;
}

function resumir(sel: { d: Despesa; c: number }[]): ResumoRelatorio {
  const total = sel.reduce((a, s) => a + s.c, 0);
  const cat = new Map<string, { nome: string; cents: number }>();
  const mes = new Map<string, number>();
  let maior = 0;
  for (const { d, c } of sel) {
    const k = normalizar(d.categoria);
    const g = cat.get(k) ?? { nome: d.categoria.trim() || "Sem categoria", cents: 0 };
    g.cents += c;
    cat.set(k, g);
    const dt = new Date(d.data);
    const mk = `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, "0")}`;
    mes.set(mk, (mes.get(mk) ?? 0) + c);
    maior = Math.max(maior, toCents(d.valor));
  }
  const somaAbs = [...cat.values()].reduce((a, g) => a + Math.abs(g.cents), 0);
  return {
    quantidade: sel.length,
    total: fromCents(total),
    media: sel.length > 0 ? round2(fromCents(total) / sel.length) : 0,
    maior: fromCents(maior),
    porCategoria: [...cat.values()]
      .sort((a, b) => Math.abs(b.cents) - Math.abs(a.cents) || a.nome.localeCompare(b.nome, "pt-BR"))
      .map((g) => ({ nome: g.nome, total: fromCents(g.cents), percentual: somaAbs > 0 ? Math.abs(g.cents) / somaAbs : 0 })),
    porMes: [...mes.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([m, c]) => ({ mes: m, total: fromCents(c) })),
  };
}

/** R19 - aplica o filtro e calcula totais, distribuição e comparação com o período anterior. */
export function gerarRelatorio(ds: Pick<Dataset, "despesas" | "cartoes">, f: FiltroRelatorio): ResultadoRelatorio {
  const sel = selecionar(ds, f, f.inicio, f.fim);
  const atual = resumir(sel);
  const dur = f.fim - f.inicio + 1;
  const anterior = Number.isFinite(dur) ? resumir(selecionar(ds, f, f.inicio - dur, f.inicio - 1)) : resumir([]);
  return {
    ...atual,
    itens: sel.map((s) => s.d).sort((a, b) => b.data - a.data || b.id - a.id),
    anterior,
    variacaoPercentual: anterior.total > 0 ? ((atual.total - anterior.total) / anterior.total) * 100 : null,
  };
}

// ---------------------------------------------------------------- períodos rápidos

export type PeriodoRapido = "mes" | "anterior" | "ano";

export function periodoRapido(id: PeriodoRapido, agora: number): { inicio: number; fim: number } {
  const { mes, ano } = mesAnoDe(agora);
  if (id === "mes") return { inicio: inicioDoMes(mes, ano), fim: fimDoMes(mes, ano) };
  if (id === "anterior") {
    const p = normalizaMes(mes - 1, ano);
    return { inicio: inicioDoMes(p.mes, p.ano), fim: fimDoMes(p.mes, p.ano) };
  }
  return { inicio: inicioDoMes(1, ano), fim: fimDoMes(12, ano) };
}

// ---------------------------------------------------------------- R20 modelos e documentos

export type Celula = string | number;

export interface Documento {
  titulo: string;
  filtros: string[];
  totais: { rotulo: string; valor: string }[];
  colunas: string[];
  /** índices de colunas numéricas em R$ (formatadas no PDF, `1234,56` no CSV) */
  colunasMoeda: number[];
  linhas: Celula[][];
  grafico: { titulo: string; dados: { rotulo: string; valor: number }[] } | null;
  nomeArquivo: string;
}

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dataFmt = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
const moeda = (v: number) => brl.format(v);
const dia = (ms: number) => dataFmt.format(new Date(ms));

export function descreverFiltro(f: FiltroRelatorio, ds: Pick<Dataset, "contas" | "cartoes">): string[] {
  const linhas: string[] = [];
  linhas.push(Number.isFinite(f.inicio) ? `Período: ${dia(f.inicio)} a ${dia(f.fim)}` : "Período: todo o histórico");
  if (f.contas.length) linhas.push(`Contas: ${f.contas.map((n) => ds.contas.find((c) => c.conta === n)?.banco ?? n).join(", ")}`);
  if (f.cartoes.length) linhas.push(`Cartões: ${f.cartoes.map((id) => ds.cartoes.find((c) => c.id === id)?.nome ?? String(id)).join(", ")}`);
  if (f.categorias.length) linhas.push(`Categorias: ${f.categorias.join(", ")}`);
  linhas.push(`Tipo: ${f.tipo === "DESPESA" ? "Despesas" : f.tipo === "RECEITA" ? "Receitas" : "Receitas e despesas"}`);
  if (f.pago !== null) linhas.push(`Situação: ${f.pago ? "Pagos" : "Pendentes"}`);
  if (f.texto.trim()) linhas.push(`Texto: "${f.texto.trim()}"`);
  return linhas;
}

function origem(d: Despesa, ds: Pick<Dataset, "contas" | "cartoes">): string {
  const cid = cartaoIdDe(d);
  if (cid !== null) return `Cartão ${ds.cartoes.find((c) => c.id === cid)?.nome ?? cid}`;
  return ds.contas.find((c) => c.conta === d.conta)?.banco ?? d.conta;
}

function slug(s: string): string {
  return normalizar(s).replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "relatorio";
}

/** Documento padrão (tabela de lançamentos + gráfico por categoria) a partir de um resultado R19. */
export function documentoDeResultado(
  titulo: string,
  f: FiltroRelatorio,
  r: ResultadoRelatorio,
  ds: Pick<Dataset, "contas" | "cartoes">,
): Documento {
  const totais = [
    { rotulo: "Lançamentos", valor: String(r.quantidade) },
    { rotulo: "Total", valor: moeda(r.total) },
    { rotulo: "Média", valor: moeda(r.media) },
    { rotulo: "Maior", valor: moeda(r.maior) },
    {
      rotulo: "Período anterior",
      valor: r.variacaoPercentual === null ? moeda(r.anterior.total) : `${moeda(r.anterior.total)} (${r.variacaoPercentual >= 0 ? "+" : ""}${r.variacaoPercentual.toFixed(1).replace(".", ",")}%)`,
    },
  ];
  return {
    titulo,
    filtros: descreverFiltro(f, ds),
    totais,
    colunas: ["Data", "Descrição", "Categoria", "Conta/Cartão", "Situação", "Valor"],
    colunasMoeda: [5],
    linhas: r.itens.map((d) => [dia(d.data), d.descricao, d.categoria, origem(d, ds), d.pago ? "Pago" : "Pendente", d.tipo === "CREDITO" ? d.valor : -d.valor]),
    grafico: r.porCategoria.length ? { titulo: "Por categoria", dados: r.porCategoria.slice(0, 10).map((c) => ({ rotulo: c.nome, valor: Math.abs(c.total) })) } : null,
    nomeArquivo: slug(titulo),
  };
}

/** Fatura do cartão (mês de fechamento), com a fatura única do grupo (R18). */
export function documentoFatura(ds: Dataset, cartaoId: number, mes: number, ano: number): Documento | null {
  const cartao = ds.cartoes.find((c) => c.id === cartaoId);
  if (!cartao) return null;
  const r = resumoFatura(cartao, ds.despesas, mes, ano, ds.cartoes);
  const porCat = new Map<string, number>();
  for (const d of r.itens) porCat.set(d.categoria, (porCat.get(d.categoria) ?? 0) + (d.tipo === "DEBITO" ? toCents(d.valor) : -toCents(d.valor)));
  const titulo = `Fatura ${cartao.nome} ${String(mes).padStart(2, "0")}/${ano}`;
  return {
    titulo,
    filtros: [`Cartão: ${cartao.nome}`, `Fechamento: ${dia(r.fechamento)} · Vencimento: ${dia(r.vencimento)}`],
    totais: [
      { rotulo: "Total da fatura", valor: moeda(r.total) },
      { rotulo: "Em aberto", valor: moeda(r.emAberto) },
      { rotulo: "Lançamentos", valor: String(r.itens.length) },
    ],
    colunas: ["Data", "Descrição", "Categoria", "Cartão", "Situação", "Valor"],
    colunasMoeda: [5],
    linhas: r.itens.map((d) => [dia(d.data), d.descricao, d.categoria, origem(d, ds), d.pago ? "Paga" : "Em aberto", d.tipo === "CREDITO" ? -d.valor : d.valor]),
    grafico: porCat.size ? { titulo: "Por categoria", dados: [...porCat.entries()].map(([rotulo, c]) => ({ rotulo, valor: fromCents(c) })).filter((x) => x.valor > 0).sort((a, b) => b.valor - a.valor).slice(0, 10) } : null,
    nomeArquivo: slug(titulo),
  };
}

/** Evolução do patrimônio (snapshots R14). */
export function documentoPatrimonio(ds: Pick<Dataset, "patrimonio">): Documento {
  const pontos = [...ds.patrimonio].sort((a, b) => a.dataMillis - b.dataMillis);
  const primeiro = pontos[0]?.valorTotal ?? 0;
  const ultimo = pontos[pontos.length - 1]?.valorTotal ?? 0;
  return {
    titulo: "Evolução do patrimônio",
    filtros: [pontos.length ? `Período: ${dia(pontos[0].dataMillis)} a ${dia(pontos[pontos.length - 1].dataMillis)}` : "Sem registros"],
    totais: [
      { rotulo: "Registros", valor: String(pontos.length) },
      { rotulo: "Inicial", valor: moeda(primeiro) },
      { rotulo: "Atual", valor: moeda(ultimo) },
      { rotulo: "Variação", valor: moeda(round2(ultimo - primeiro)) },
    ],
    colunas: ["Mês", "Data", "Patrimônio líquido"],
    colunasMoeda: [2],
    linhas: pontos.map((p) => [p.mesReferencia, dia(p.dataMillis), p.valorTotal]),
    grafico: pontos.length ? { titulo: "Patrimônio", dados: pontos.map((p) => ({ rotulo: p.mesReferencia, valor: p.valorTotal })) } : null,
    nomeArquivo: "evolucao-patrimonio",
  };
}

/** Filtros dos modelos R20 que se baseiam em R19. */
export const MODELOS = {
  extratoConta: (conta: string, inicio: number, fim: number): FiltroRelatorio => ({ ...filtroPadrao(inicio, fim), contas: [conta], tipo: "TODOS", incluirInternos: true }),
  gastosCategoria: (inicio: number, fim: number): FiltroRelatorio => filtroPadrao(inicio, fim),
  receitasDespesas: (mes: number, ano: number): FiltroRelatorio => ({ ...filtroPadrao(inicioDoMes(mes, ano), fimDoMes(mes, ano)), tipo: "TODOS" }),
  anualIR: (ano: number): FiltroRelatorio => ({ ...filtroPadrao(inicioDoMes(1, ano), fimDoMes(12, ano)), categorias: ["Saúde", "Educação"], tipo: "DESPESA" }),
};

/** CSV com `;`, valores `1234,56` e BOM UTF-8. */
export function csvDeDocumento(doc: Documento): string {
  const cel = (v: Celula, moedaCol: boolean): string => {
    const t = typeof v === "number" ? (moedaCol ? v.toFixed(2).replace(".", ",") : String(v).replace(".", ",")) : v;
    return /[";\n\r]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
  };
  const linhas = [doc.colunas.map((c) => cel(c, false)).join(";")];
  for (const l of doc.linhas) linhas.push(l.map((v, i) => cel(v, doc.colunasMoeda.includes(i))).join(";"));
  return `﻿${linhas.join("\r\n")}\r\n`;
}
