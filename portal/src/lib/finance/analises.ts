import {
  cartaoIdDe,
  faturaDaCompra,
  kpisDoMes,
  progressoOrcamento,
  saldoTotalContas,
  vencimentoFatura,
} from "./calc";
import { diasNoMes, fimDoMes, inicioDoDia, inicioDoMes, mesAnoDe, normalizaMes, somaMeses } from "./dates";
import { fromCents, round2, toCents } from "./money";
import { recorrenciaPausada } from "./operations";
import { jaccard, limparDescricao, normalizar, tokensDescricao } from "./texto";
import type { Cartao, Dataset, Despesa, Meta } from "./types";

// ---------------------------------------------------------------- R21 sugestão de categoria

/**
 * R21 - sugere a categoria pela similaridade da descrição (igual = 1, senão Jaccard dos tokens,
 * mínimo 0,5), somando por categoria. Empate: a categoria do lançamento mais recente.
 */
export function sugerirCategoria(descricao: string, despesas: readonly Despesa[]): string | null {
  const alvo = limparDescricao(descricao);
  if (!alvo) return null;
  const tokensAlvo = tokensDescricao(descricao);
  const mapa = new Map<string, { nome: string; soma: number; recente: number }>();
  for (const d of despesas) {
    if (d.natureza !== "NORMAL" || !d.categoria.trim()) continue;
    const outra = limparDescricao(d.descricao);
    if (!outra) continue;
    const sim = outra === alvo ? 1 : jaccard(tokensAlvo, tokensDescricao(d.descricao));
    if (sim < 0.5) continue;
    const chave = normalizar(d.categoria);
    const atual = mapa.get(chave) ?? { nome: d.categoria.trim(), soma: 0, recente: 0 };
    atual.soma += sim;
    if (d.data >= atual.recente) {
      atual.recente = d.data;
      atual.nome = d.categoria.trim();
    }
    mapa.set(chave, atual);
  }
  let melhor: { nome: string; soma: number; recente: number } | null = null;
  for (const c of mapa.values()) {
    if (!melhor || c.soma > melhor.soma + 1e-9 || (Math.abs(c.soma - melhor.soma) <= 1e-9 && c.recente > melhor.recente)) melhor = c;
  }
  return melhor ? melhor.nome : null;
}

// ---------------------------------------------------------------- R22 alertas de orçamento

export interface AlertaOrcamento {
  chave: string;
  categoria: string;
  limiar: 80 | 100;
  pct: number;
  gasto: number;
  limite: number;
}

export function chaveAlerta(categoria: string, mes: number, ano: number, limiar: 80 | 100): string {
  return `${normalizar(categoria)}|${ano}-${String(mes).padStart(2, "0")}|${limiar}`;
}

/**
 * R22 - alertas novos (ainda não avisados) para 80% e 100%. Ao cruzar 100% o aviso de 80% não é
 * repetido. `marcar` lista as chaves que devem ser gravadas como "já avisado".
 */
export function alertasOrcamento(
  ds: Pick<Dataset, "orcamentos" | "despesas">,
  agora: number,
  avisados: ReadonlySet<string>,
): { alertas: AlertaOrcamento[]; marcar: string[] } {
  const { mes, ano } = mesAnoDe(agora);
  const alertas: AlertaOrcamento[] = [];
  const marcar: string[] = [];
  for (const o of ds.orcamentos) {
    const p = progressoOrcamento(o, ds.despesas, mes, ano);
    const k80 = chaveAlerta(o.categoria, mes, ano, 80);
    const k100 = chaveAlerta(o.categoria, mes, ano, 100);
    const base = { categoria: o.categoria, pct: p.pct, gasto: p.gasto, limite: p.limite };
    if (p.pct >= 1) {
      if (!avisados.has(k100)) alertas.push({ ...base, chave: k100, limiar: 100 });
      for (const k of [k80, k100]) if (!avisados.has(k)) marcar.push(k);
    } else if (p.pct >= 0.8 && !avisados.has(k80)) {
      alertas.push({ ...base, chave: k80, limiar: 80 });
      marcar.push(k80);
    }
  }
  return { alertas, marcar };
}

// ---------------------------------------------------------------- R24 busca global

export interface ResultadoBusca {
  lancamentos: Despesa[];
  contas: Dataset["contas"];
  cartoes: Dataset["cartoes"];
  metas: Dataset["metas"];
  total: number;
}

function parseValorBusca(termo: string): number | null {
  const t = termo.trim();
  if (!/^\d+(?:[.,]\d{1,2})?$/.test(t)) return null;
  return toCents(Number(t.replace(",", ".")));
}

function parseDataBusca(termo: string): { dia: number; mes: number; ano: number | null } | null {
  const m = /^(\d{1,2})\/(\d{1,2})(?:\/(\d{4}))?$/.exec(termo.trim());
  if (!m) return null;
  const dia = Number(m[1]);
  const mes = Number(m[2]);
  if (dia < 1 || dia > 31 || mes < 1 || mes > 12) return null;
  return { dia, mes, ano: m[3] ? Number(m[3]) : null };
}

/** R24 - descrição, categoria, conta/banco, cartão, meta, valor (centavos) e data (dd/MM[/aaaa]). */
export function buscaGlobal(ds: Dataset, termoBruto: string): ResultadoBusca {
  const termo = normalizar(termoBruto);
  const vazio: ResultadoBusca = { lancamentos: [], contas: [], cartoes: [], metas: [], total: 0 };
  if (!termo) return vazio;
  const cents = parseValorBusca(termo);
  const data = parseDataBusca(termo);
  const bancoDe = (num: string) => ds.contas.find((c) => c.conta === num)?.banco ?? "";
  const cartaoDe = (id: number | null) => (id === null ? "" : (ds.cartoes.find((c) => c.id === id)?.nome ?? ""));

  const lancamentos = ds.despesas
    .filter((d) => {
      if (normalizar(d.descricao).includes(termo) || normalizar(d.categoria).includes(termo)) return true;
      if (normalizar(d.conta).includes(termo) || normalizar(bancoDe(d.conta)).includes(termo)) return true;
      if (normalizar(cartaoDe(cartaoIdDe(d))).includes(termo)) return true;
      if (cents !== null && toCents(d.valor) === cents) return true;
      if (data) {
        const dt = new Date(d.data);
        if (dt.getDate() === data.dia && dt.getMonth() + 1 === data.mes && (data.ano === null || dt.getFullYear() === data.ano)) return true;
      }
      return false;
    })
    .sort((a, b) => b.data - a.data || b.id - a.id);
  const contas = ds.contas.filter((c) => normalizar(`${c.banco} ${c.conta} ${c.titular}`).includes(termo));
  const cartoes = ds.cartoes.filter((c) => normalizar(c.nome).includes(termo));
  const metas = ds.metas.filter((m) => normalizar(m.nome).includes(termo));
  return { lancamentos, contas, cartoes, metas, total: lancamentos.length + contas.length + cartoes.length + metas.length };
}

// ---------------------------------------------------------------- R25 reserva de emergência

export type StatusReserva = "CRITICO" | "ATENCAO" | "OK";

export interface Reserva {
  mediaDespesas3m: number | null;
  liquidez: number;
  meses: number | null;
  meta: number | null;
  faltante: number | null;
  status: StatusReserva | null;
}

export const MESES_META_RESERVA = 6;

export function reservaEmergencia(ds: Dataset, agora: number): Reserva {
  const { mes, ano } = mesAnoDe(agora);
  const totais: number[] = [];
  for (let i = 1; i <= 3; i++) {
    const p = normalizaMes(mes - i, ano);
    const ini = inicioDoMes(p.mes, p.ano);
    const fim = fimDoMes(p.mes, p.ano);
    if (!ds.despesas.some((d) => d.data >= ini && d.data <= fim)) continue; // mês sem lançamentos
    totais.push(toCents(kpisDoMes(ds.despesas, p.mes, p.ano).despesasTotal));
  }
  const liquidez = fromCents(
    toCents(saldoTotalContas(ds)) +
      ds.investimentos.filter((i) => i.tipo === "Renda Fixa").reduce((a, i) => a + toCents(i.valorAtual), 0),
  );
  if (totais.length === 0) return { mediaDespesas3m: null, liquidez, meses: null, meta: null, faltante: null, status: null };
  const media = fromCents(Math.round(totais.reduce((a, b) => a + b, 0) / totais.length));
  const meta = round2(MESES_META_RESERVA * media);
  const faltante = Math.max(0, round2(meta - liquidez));
  if (!(media > 0)) return { mediaDespesas3m: media, liquidez, meses: null, meta, faltante, status: "OK" };
  const meses = liquidez / media;
  return { mediaDespesas3m: media, liquidez, meses, meta, faltante, status: meses < 3 ? "CRITICO" : meses < MESES_META_RESERVA ? "ATENCAO" : "OK" };
}

// ---------------------------------------------------------------- R26 assinaturas

export interface Assinatura {
  nome: string;
  valorMedio: number;
  ultimaData: number | null;
  categoria: string;
  origem: "FIXA" | "DETECTADA";
  totalMensal: number;
  totalAnual: number;
}

function chaveRecorrente(descricao: string): string {
  return limparDescricao(descricao.replace(/\(\s*\d+\s*\/\s*\d+\s*\)/g, " "));
}

function mediana(v: number[]): number {
  const s = [...v].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

const DIA_MS = 86_400_000;

export function detectarAssinaturas(ds: Dataset, agora: number): Assinatura[] {
  const out: Assinatura[] = [];
  const cobertas = new Set<string>();
  for (const f of ds.despesasFixas) {
    if (f.tipo !== "DEBITO") continue;
    cobertas.add(chaveRecorrente(f.descricao));
    if (recorrenciaPausada(f, agora)) continue; // R47: pausada não entra nas projeções
    out.push({
      nome: f.descricao,
      valorMedio: round2(f.valor),
      ultimaData: f.ultimaDataLancamento,
      categoria: f.categoria,
      origem: "FIXA",
      totalMensal: round2(f.valor),
      totalAnual: round2(12 * f.valor),
    });
  }
  const desde = somaMeses(agora, -6);
  const grupos = new Map<string, Despesa[]>();
  for (const d of ds.despesas) {
    if (d.natureza !== "NORMAL" || d.tipo !== "DEBITO" || d.data < desde || d.data > agora) continue;
    const k = chaveRecorrente(d.descricao);
    if (!k || cobertas.has(k)) continue;
    const l = grupos.get(k) ?? [];
    l.push(d);
    grupos.set(k, l);
  }
  for (const itens of grupos.values()) {
    const meses = new Set(itens.map((d) => `${new Date(d.data).getFullYear()}-${new Date(d.data).getMonth()}`));
    if (meses.size < 3) continue;
    const ultima = itens.reduce((a, b) => (b.data > a.data ? b : a));
    if (agora - ultima.data > 45 * DIA_MS) continue;
    const med = mediana(itens.map((d) => d.valor));
    if (!(med > 0) || itens.some((d) => Math.abs(d.valor - med) / med > 0.1 + 1e-9)) continue;
    const dias = itens.map((d) => new Date(d.data).getDate());
    if (Math.max(...dias) - Math.min(...dias) > 5) continue;
    const medio = round2(itens.reduce((a, d) => a + d.valor, 0) / itens.length);
    out.push({
      nome: ultima.descricao.replace(/\(\s*\d+\s*\/\s*\d+\s*\)/g, "").trim() || ultima.descricao,
      valorMedio: medio,
      ultimaData: ultima.data,
      categoria: ultima.categoria,
      origem: "DETECTADA",
      totalMensal: medio,
      totalAnual: round2(12 * medio),
    });
  }
  return out.sort((a, b) => b.totalMensal - a.totalMensal);
}

export function totaisAssinaturas(lista: readonly Assinatura[]): { mensal: number; anual: number } {
  return {
    mensal: fromCents(lista.reduce((a, s) => a + toCents(s.totalMensal), 0)),
    anual: fromCents(lista.reduce((a, s) => a + toCents(s.totalAnual), 0)),
  };
}

// ---------------------------------------------------------------- R27 metas com prazo

export type StatusMeta = "CONCLUIDA" | "ATRASADA" | "NO_RITMO" | "ABAIXO";

export interface PrazoMeta {
  restante: number;
  mesesRestantes: number | null;
  aporteMensalNecessario: number | null;
  ritmo: number;
  status: StatusMeta | null;
}

export function analisarMeta(meta: Meta, despesas: readonly Despesa[], agora: number): PrazoMeta {
  const restante = Math.max(0, fromCents(toCents(meta.valorObjetivo) - toCents(meta.valorGuardado)));
  const desde = somaMeses(agora, -3);
  const desc = `Aporte: ${meta.nome}`;
  const aportes = despesas
    .filter((d) => d.natureza === "APORTE_META" && d.descricao === desc && d.data >= desde && d.data <= agora)
    .reduce((a, d) => a + toCents(d.valor), 0);
  const ritmo = fromCents(Math.round(aportes / 3));
  const concluida = meta.valorGuardado >= meta.valorObjetivo;
  if (meta.dataAlvo === null) {
    return { restante, mesesRestantes: null, aporteMensalNecessario: null, ritmo, status: concluida ? "CONCLUIDA" : null };
  }
  const hoje = inicioDoDia(agora);
  const alvo = inicioDoDia(meta.dataAlvo);
  let k = 0;
  while (k < 1200 && somaMeses(hoje, k) < alvo) k++;
  const mesesRestantes = Math.max(1, k);
  const aporteMensalNecessario = round2(restante / mesesRestantes);
  let status: StatusMeta;
  if (concluida) status = "CONCLUIDA";
  else if (alvo < hoje) status = "ATRASADA";
  else if (ritmo >= aporteMensalNecessario) status = "NO_RITMO";
  else status = "ABAIXO";
  return { restante, mesesRestantes, aporteMensalNecessario, ritmo, status };
}

// ---------------------------------------------------------------- R28 simulador

export interface EntradaSimulador {
  valorAVista: number;
  n: number;
  valorParcela: number;
  taxaMensal?: number;
  entrada?: number;
}

export interface ResultadoSimulador {
  valorPresente: number;
  parcelarVale: boolean;
  diferenca: number;
  jurosImplicitosMensais: number;
}

function vpParcelas(parcela: number, n: number, taxa: number): number {
  let vp = 0;
  for (let k = 1; k <= n; k++) vp += parcela / Math.pow(1 + taxa, k);
  return vp;
}

export function simularParcelamento(e: EntradaSimulador): ResultadoSimulador {
  const taxa = e.taxaMensal ?? 0.01;
  const entrada = e.entrada ?? 0;
  const vp = entrada + vpParcelas(e.valorParcela, e.n, taxa);
  let juros = 0;
  const alvo = e.valorAVista - entrada;
  if (e.n * e.valorParcela + entrada > e.valorAVista && alvo > 0) {
    let lo = 0;
    let hi = 1;
    for (let i = 0; i < 80; i++) {
      const mid = (lo + hi) / 2;
      if (vpParcelas(e.valorParcela, e.n, mid) > alvo) lo = mid;
      else hi = mid;
    }
    juros = (lo + hi) / 2;
  }
  return { valorPresente: round2(vp), parcelarVale: vp < e.valorAVista, diferenca: round2(e.valorAVista - vp), jurosImplicitosMensais: juros };
}

// ---------------------------------------------------------------- R29 50/30/20

export const CATEGORIAS_NECESSIDADES = ["supermercado", "saude", "educacao", "transporte", "combustivel", "oficina", "casa", "aluguel", "moradia", "contas", "luz", "agua", "internet"];

export type StatusGrupo = "OK" | "ACIMA" | "ABAIXO";

export interface GrupoRegra {
  valor: number;
  percentual: number;
  alvo: number;
  status: StatusGrupo;
}

export interface Regra503020 {
  receitas: number;
  necessidades: GrupoRegra;
  desejos: GrupoRegra;
  poupanca: GrupoRegra;
}

export function regra503020(despesas: readonly Despesa[], mes: number, ano: number): Regra503020 {
  const ini = inicioDoMes(mes, ano);
  const fim = fimDoMes(mes, ano);
  const receitas = kpisDoMes(despesas, mes, ano).receitasRealizadas;
  let nec = 0;
  let des = 0;
  let poup = 0;
  for (const d of despesas) {
    if (d.data < ini || d.data > fim) continue;
    if (d.natureza === "APORTE_META") poup += toCents(d.valor);
    else if (d.natureza === "RESGATE_META") poup -= toCents(d.valor);
    else if (d.natureza === "NORMAL" && d.tipo === "DEBITO") {
      const cat = normalizar(d.categoria);
      if (cat === "reserva") poup += toCents(d.valor);
      else if (CATEGORIAS_NECESSIDADES.includes(cat)) nec += toCents(d.valor);
      else des += toCents(d.valor);
    }
  }
  const grupo = (cents: number, alvo: number, tipo: "max" | "min"): GrupoRegra => {
    const valor = fromCents(cents);
    const percentual = receitas > 0 ? valor / receitas : 0;
    const ok = tipo === "max" ? percentual <= alvo + 1e-9 : percentual >= alvo - 1e-9;
    return { valor, percentual, alvo, status: ok ? "OK" : tipo === "max" ? "ACIMA" : "ABAIXO" };
  };
  return { receitas, necessidades: grupo(nec, 0.5, "max"), desejos: grupo(des, 0.3, "max"), poupanca: grupo(poup, 0.2, "min") };
}

// ---------------------------------------------------------------- R30 melhor dia de compra

export interface MelhorDia {
  dia: number;
  mes: number;
  ano: number;
  data: number;
  vencimento: number;
  prazoMaximoDias: number;
}

/** R30 - melhor dia para comprar no ciclo do mês de referência. */
export function melhorDiaCompra(cartao: Pick<Cartao, "diaFechamento" | "diaVencimento">, mes: number, ano: number): MelhorDia {
  const ultimo = diasNoMes(mes, ano);
  const alvo = cartao.diaFechamento >= ultimo ? normalizaMes(mes + 1, ano) : { mes, ano };
  const dia = cartao.diaFechamento >= ultimo ? 1 : cartao.diaFechamento + 1;
  const data = new Date(alvo.ano, alvo.mes - 1, dia).getTime();
  const fatura = faturaDaCompra(cartao, data);
  const vencimento = vencimentoFatura(cartao, fatura.mes, fatura.ano);
  return { dia, mes: alvo.mes, ano: alvo.ano, data, vencimento, prazoMaximoDias: Math.round((vencimento - data) / DIA_MS) };
}
