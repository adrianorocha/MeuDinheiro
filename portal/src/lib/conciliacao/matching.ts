import { cartaoIdDe, cartoesDoGrupo } from "../finance/calc";
import { inicioDoDia } from "../finance/dates";
import { toCents } from "../finance/money";
import { jaccard, tokensDescricao } from "../finance/texto";
import type { Dataset, Despesa } from "../finance/types";
import { limparDescricaoBanco, normalizarDescricaoBanco } from "./texto-banco";
import type { Candidato, ClasseMatch, Destino, ItemMatch, OpcoesMatching, ResultadoMatching, TransacaoBanco } from "./tipos";

const DIA_MS = 86_400_000;
export const JANELA_PADRAO_CONTA = 3;
export const JANELA_PADRAO_CARTAO = 5;
export const LIMIAR_AUTOMATICO = 0.8;

export function janelaPadrao(destino: Destino): number {
  return destino.tipo === "CARTAO" ? JANELA_PADRAO_CARTAO : JANELA_PADRAO_CONTA;
}

/** R36 - lançamentos do destino: conta (sem cartão) ou grupo do cartão (R18). */
export function lancamentosDoDestino(ds: Pick<Dataset, "despesas" | "cartoes">, destino: Destino): Despesa[] {
  if (destino.tipo === "CONTA") return ds.despesas.filter((d) => d.conta === destino.conta && cartaoIdDe(d) === null);
  const escolhido = ds.cartoes.find((c) => c.id === destino.cartaoId);
  if (!escolhido) return [];
  const ids = new Set(cartoesDoGrupo(escolhido, ds.cartoes).map((c) => c.id));
  return ds.despesas.filter((d) => {
    const cid = cartaoIdDe(d);
    return cid !== null && ids.has(cid);
  });
}

function diasEntre(a: number, b: number): number {
  return Math.round((inicioDoDia(a) - inicioDoDia(b)) / DIA_MS);
}

/** Similaridade de descrição: 1 se iguais (normalizadas), senão Jaccard dos tokens (R21). */
export function similaridadeDescricao(descBanco: string, descApp: string): number {
  const a = normalizarDescricaoBanco(descBanco);
  const b = normalizarDescricaoBanco(descApp);
  if (a && a === b) return 1;
  return jaccard(tokensDescricao(limparDescricaoBanco(descBanco)), tokensDescricao(descApp));
}

/** Tolerância de valor para DIFERENCA: max(1,00; 2%) do valor do extrato. */
export function tolerancia(centsExtrato: number, opcoes: OpcoesMatching): number {
  const pct = opcoes.toleranciaPct ?? 0.02;
  const min = Math.round((opcoes.toleranciaMin ?? 1) * 100);
  return Math.max(min, Math.round(Math.abs(centsExtrato) * pct));
}

interface Par {
  t: number; // índice da transação
  l: number; // índice do candidato
  score: number;
  tipo: "EXATO" | "DIFERENCA";
  dias: number;
  id: number;
}

/**
 * R37 - casamento extrato × lançamentos do destino.
 * `lancamentos` = todos os lançamentos do destino (conciliados entram só para detectar duplicados).
 */
export function casar(transacoes: readonly TransacaoBanco[], lancamentos: readonly Despesa[], opcoes: OpcoesMatching): ResultadoMatching {
  const janela = opcoes.janelaDias;
  const usados = new Set<string>();
  for (const d of lancamentos) if (d.conciliadoEm != null && d.fitid) usados.add(d.fitid);

  const candidatos = lancamentos.filter((d) => d.conciliadoEm == null);
  // índice por direção, ordenado por dia (busca binária da janela de datas)
  const porDirecao = { DEBITO: [] as { d: Despesa; dia: number; idx: number }[], CREDITO: [] as { d: Despesa; dia: number; idx: number }[] };
  candidatos.forEach((d, idx) => porDirecao[d.tipo].push({ d, dia: inicioDoDia(d.data), idx }));
  porDirecao.DEBITO.sort((a, b) => a.dia - b.dia);
  porDirecao.CREDITO.sort((a, b) => a.dia - b.dia);

  const limite = (arr: { dia: number }[], alvo: number): number => {
    let lo = 0;
    let hi = arr.length;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (arr[mid].dia < alvo) lo = mid + 1;
      else hi = mid;
    }
    return lo;
  };

  const classeInicial: (ClasseMatch | null)[] = transacoes.map((t) => (usados.has(t.fitid) ? "DUPLICADO" : null));
  const pares: Par[] = [];
  const porTransacao: Par[][] = transacoes.map(() => []);

  transacoes.forEach((t, ti) => {
    if (classeInicial[ti] === "DUPLICADO") return;
    const cents = toCents(t.valor);
    const alvoAbs = Math.abs(cents);
    const arr = porDirecao[cents < 0 ? "DEBITO" : "CREDITO"];
    const diaT = inicioDoDia(t.data);
    const tol = tolerancia(cents, opcoes);
    for (let k = limite(arr, diaT - janela * DIA_MS); k < arr.length && arr[k].dia <= diaT + janela * DIA_MS; k++) {
      const { d, dia, idx } = arr[k];
      const dif = Math.abs(toCents(d.valor) - alvoAbs);
      let tipo: "EXATO" | "DIFERENCA";
      if (dif === 0) tipo = "EXATO";
      else if (dif <= tol) tipo = "DIFERENCA";
      else continue;
      const dias = Math.round((dia - diaT) / DIA_MS);
      const prox = 1 - Math.abs(dias) / (janela + 1);
      const sim = similaridadeDescricao(t.descricao, d.descricao);
      const score = 0.55 * (tipo === "EXATO" ? 1 : 0.6) + 0.3 * prox + 0.15 * sim;
      const par: Par = { t: ti, l: idx, score, tipo, dias, id: d.id };
      pares.push(par);
      porTransacao[ti].push(par);
    }
  });

  const ordem = (a: Par, b: Par) => b.score - a.score || Math.abs(a.dias) - Math.abs(b.dias) || a.id - b.id || a.t - b.t;
  for (const lista of porTransacao) lista.sort(ordem);

  // atribuição um-para-um, determinística (maior score; empate: menor |dias|, menor id)
  pares.sort(ordem);
  const tomadaT = new Set<number>();
  const tomadaL = new Set<number>();
  const atribuido = new Map<number, Par>();
  for (const p of pares) {
    if (tomadaT.has(p.t) || tomadaL.has(p.l)) continue;
    tomadaT.add(p.t);
    tomadaL.add(p.l);
    atribuido.set(p.t, p);
  }

  const aCandidato = (p: Par): Candidato => ({ lancamentoId: p.id, score: p.score, tipo: p.tipo, dias: p.dias });
  const itens: ItemMatch[] = transacoes.map((t, ti) => {
    if (classeInicial[ti] === "DUPLICADO") return { indice: ti, transacao: t, classe: "DUPLICADO", alternativas: [] };
    const todas = porTransacao[ti];
    const par = atribuido.get(ti);
    if (!par) return { indice: ti, transacao: t, classe: "SO_NO_EXTRATO", alternativas: todas.map(aCandidato) };
    const unico = todas.length < 2 || todas[0].score > todas[1].score + 1e-12;
    const classe: ClasseMatch = par.score >= LIMIAR_AUTOMATICO && unico && par.tipo === "EXATO" ? "AUTOMATICO" : "SUGERIDO";
    return {
      indice: ti,
      transacao: t,
      classe,
      tipo: par.tipo,
      lancamentoId: par.id,
      score: par.score,
      dias: par.dias,
      alternativas: todas.filter((x) => x.id !== par.id).map(aCandidato),
    };
  });

  // SO_NO_APP: não conciliados do destino dentro de [min − janela, max + janela] sem par
  let janelaPeriodo: ResultadoMatching["janelaPeriodo"] = null;
  if (transacoes.length > 0) {
    let min = Infinity;
    let max = -Infinity;
    for (const t of transacoes) {
      if (t.data < min) min = t.data;
      if (t.data > max) max = t.data;
    }
    janelaPeriodo = { inicio: inicioDoDia(min) - janela * DIA_MS, fim: inicioDoDia(max) + janela * DIA_MS };
  }
  const soNoApp = janelaPeriodo
    ? candidatos.filter((d, idx) => !tomadaL.has(idx) && inicioDoDia(d.data) >= janelaPeriodo.inicio && inicioDoDia(d.data) <= janelaPeriodo.fim)
    : [];
  return { itens, soNoApp: soNoApp.sort((a, b) => b.data - a.data || b.id - a.id), janelaPeriodo };
}

/** Dias (lançamento − banco) para um par escolhido manualmente. */
export function diasDoPar(t: TransacaoBanco, d: Despesa): number {
  return diasEntre(d.data, t.data);
}
