/**
 * R103 - "Onde cortar": aponta, com base nos 3 últimos meses COMPLETOS (mesma janela do fluxo de caixa, R97), onde dá para
 * economizar e quanto. Tudo em CENTAVOS; puro; nada é gravado. Estimativas, não recomendação personalizada.
 *
 * Só entram débitos NORMAL (e estornos de cartão, que abatem) — aportes, resgates e pagamentos de fatura ficam de fora.
 * Cada lançamento cai em UM único bloco (sem contar duas vezes), nesta ordem de prioridade (do menos ao mais doloroso):
 *  1 EVITAVEL   - juros, multa, mora, IOF, encargos, tarifas, anuidade, rotativo: 100% evitáveis. Mensal = total da janela / 3.
 *  2 ASSINATURA - assinaturas/recorrências de débito (`detectarAssinaturas`) fora de categorias essenciais: economia = valor mensal
 *                 (cancelar). Os lançamentos da assinatura detectada saem dos blocos abaixo.
 *  3 FREQUENTE  - mesma descrição em >= 6 compras, em >= 2 meses da janela, ticket médio <= R$ 40, categoria não essencial
 *                 ("efeito cafezinho"): economia = 30% do mensal (PCT_FREQUENTE).
 *  4 FLEXIVEL   - o restante das categorias NÃO essenciais: economia = média mensal x pct (padrão 20%, ajustável).
 *  5 ESSENCIAL  - categorias essenciais (`CATEGORIAS_NECESSIDADES`, R29): economia = média mensal x pct (padrão 5%: renegociar,
 *                 trocar de fornecedor, desperdício), nunca "cortar".
 * Categoria "reserva" (poupança) nunca é sugerida. Média mensal = total da janela / 3 (igual a `variavelBase`).
 * Blocos com média < R$ 20 ou economia < R$ 5 não aparecem.
 *
 * Plano: dado o alvo mensal (padrão = `cortePorMes` do fluxo), soma os cortes na ordem de prioridade (valor decrescente dentro do
 * tipo) até atingir o alvo; mostra o quanto cobre. `aplicarCorteNoFluxo` refaz o saldo mês a mês com a economia aplicada desde o
 * mês atual (saldo_j + economia x (j+1)), como o cálculo do corte necessário.
 */
import { CATEGORIAS_NECESSIDADES, chaveRecorrente, detectarAssinaturas } from "./analises";
import { cartaoIdDe } from "./calc";
import { fimDoMes, inicioDoMes, mesAnoDe, normalizaMes } from "./dates";
import { ehVariavel, type ResultadoFluxo } from "./fluxoCaixa";
import { toCents } from "./money";
import { normalizar } from "./texto";
import type { Dataset, Despesa } from "./types";

export type TipoCorte = "EVITAVEL" | "ASSINATURA" | "FREQUENTE" | "FLEXIVEL" | "ESSENCIAL";

export const PCT_FLEXIVEL_PADRAO = 0.2;
export const PCT_ESSENCIAL_PADRAO = 0.05;
export const PCT_FREQUENTE = 0.3;
export const MEDIA_MINIMA = 2000;
export const ECONOMIA_MINIMA = 500;
export const FREQ_MIN_COMPRAS = 6;
export const FREQ_MIN_MESES = 2;
export const FREQ_TICKET_MAX = 4000;

const PRIORIDADE: Record<TipoCorte, number> = { EVITAVEL: 1, ASSINATURA: 2, FREQUENTE: 3, FLEXIVEL: 4, ESSENCIAL: 5 };
const RE_EVITAVEL = /\b(juros|multa|mora|iof|encargos?|tarifas?|anuidade|rotativo|atraso|cesta de servicos|seguro prestamista)\b/;

export interface Corte {
  /** Estável entre recarregamentos (serve para dispensar). */
  id: string;
  tipo: TipoCorte;
  titulo: string;
  categoria: string;
  /** Média mensal atual do bloco (centavos). */
  mensalAtual: number;
  /** Economia mensal estimada (centavos). */
  economiaMensal: number;
  /** Linhas explicando regra e conta, em texto pt-BR. */
  calculo: string[];
}

export interface GastoPesado {
  nome: string;
  categoria: string;
  /** Média mensal (centavos) na janela. */
  mensal: number;
  compras: number;
}

export interface PlanoCorte {
  alvo: number;
  selecionados: Corte[];
  economia: number;
  /** economia >= alvo */
  atinge: boolean;
  /** Quanto do alvo ainda falta (centavos; 0 se atinge). */
  falta: number;
}

export interface ParametrosCortes {
  pctFlexivel?: number;
  pctEssencial?: number;
  /** Alvo mensal do plano (centavos). Ausente/0 = sem plano. */
  alvoMensal?: number;
  /** Ids de cortes dispensados pelo usuário: saem da lista, do total e do plano. */
  dispensados?: ReadonlySet<string>;
}

export interface ResultadoCortes {
  cortes: Corte[];
  totalMensal: number;
  totalAnual: number;
  /** Maiores gastos variáveis da janela (informativo; não somam economia). */
  maioresGastos: GastoPesado[];
  /** Meses da janela com algum gasto variável (0..3). */
  mesesComDados: number;
  plano: PlanoCorte | null;
}

interface Item {
  d: Despesa;
  /** centavos com sinal: débito +, estorno de cartão −. */
  v: number;
  chave: string;
  cat: string;
  catNorm: string;
  mes: string;
}

const MESES_JANELA = 3;

function itensDaJanela(ds: Dataset, agora: number): Item[] {
  const { mes, ano } = mesAnoDe(agora);
  const ini = inicioDoMes(normalizaMes(mes - MESES_JANELA, ano).mes, normalizaMes(mes - MESES_JANELA, ano).ano);
  const p = normalizaMes(mes - 1, ano);
  const fim = fimDoMes(p.mes, p.ano);
  const out: Item[] = [];
  for (const d of ds.despesas) {
    if (d.data < ini || d.data > fim || d.natureza !== "NORMAL") continue;
    const cents = toCents(d.valor);
    let v: number;
    if (d.tipo === "DEBITO") v = cents;
    else if (cartaoIdDe(d) !== null) v = -cents;
    else continue;
    const cat = d.categoria.trim() || "Sem categoria";
    const dt = new Date(d.data);
    out.push({ d, v, chave: chaveRecorrente(d.descricao), cat, catNorm: normalizar(cat), mes: `${dt.getFullYear()}-${dt.getMonth()}`});
  }
  return out;
}

const essencial = (catNorm: string): boolean => CATEGORIAS_NECESSIDADES.includes(catNorm);
const brl = (cents: number): string => {
  const abs = Math.abs(Math.round(cents));
  return `${cents < 0 ? "-" : ""}R$ ${String(Math.floor(abs / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ".")},${String(abs % 100).padStart(2, "0")}`;
};
const pct = (f: number): string => `${Math.round(f * 100)}%`;

function agrupar<T>(itens: readonly T[], chave: (i: T) => string): Map<string, T[]> {
  const m = new Map<string, T[]>();
  for (const i of itens) {
    const k = chave(i);
    const l = m.get(k);
    if (l) l.push(i);
    else m.set(k, [i]);
  }
  return m;
}

const soma = (l: readonly Item[]): number => l.reduce((s, i) => s + i.v, 0);
const nomeDe = (l: readonly Item[]): string => l.reduce((a, b) => (b.d.data > a.d.data ? b : a)).d.descricao.replace(/\(\s*\d+\s*\/\s*\d+\s*\)/g, "").trim() || l[0].d.descricao;

export function calcularCortes(ds: Dataset, agora: number, par: ParametrosCortes = {}): ResultadoCortes {
  const pctFlex = par.pctFlexivel ?? PCT_FLEXIVEL_PADRAO;
  const pctEss = par.pctEssencial ?? PCT_ESSENCIAL_PADRAO;
  const dispensados = par.dispensados ?? new Set<string>();
  const todos = itensDaJanela(ds, agora);
  const variaveis = todos.filter((i) => ehVariavel(i.d));
  const usados = new Set<number>();
  const cortes: Corte[] = [];
  const mesesComDados = new Set(todos.filter((i) => ehVariavel(i.d) && i.v > 0).map((i) => i.mes)).size;

  // 1) evitáveis: qualquer débito NORMAL (inclusive recorrente, ex.: tarifa fixa) cuja descrição/categoria indique juros e tarifas
  const evitaveis = todos.filter((i) => i.d.tipo === "DEBITO" && RE_EVITAVEL.test(normalizar(`${i.d.descricao} ${i.cat}`)));
  for (const [k, l] of agrupar(evitaveis, (i) => i.chave)) {
    const total = soma(l);
    const mensal = Math.round(total / MESES_JANELA);
    l.forEach((i) => usados.add(i.d.id));
    if (mensal < ECONOMIA_MINIMA) continue;
    cortes.push({
      id: `corte:evitavel:${k}`,
      tipo: "EVITAVEL",
      titulo: `Evitar: ${nomeDe(l)}`,
      categoria: l[0].cat,
      mensalAtual: mensal,
      economiaMensal: mensal,
      calculo: [`${l.length} lançamento(s) de juros/multas/tarifas somam ${brl(total)} em ${MESES_JANELA} meses (média ${brl(mensal)}/mês)`, "Regra: juros, multa, mora, IOF, encargos, tarifas e anuidade são evitáveis (pague em dia, negocie isenção/troque de pacote)."],
    });
  }

  // 2) assinaturas fora de categorias essenciais
  const chavesAssinatura = new Set<string>();
  for (const a of detectarAssinaturas(ds, agora)) {
    if (essencial(normalizar(a.categoria))) continue;
    const mensal = toCents(a.totalMensal);
    const k = chaveRecorrente(a.nome);
    chavesAssinatura.add(k);
    if (mensal < ECONOMIA_MINIMA) continue;
    cortes.push({
      id: `corte:assinatura:${k}`,
      tipo: "ASSINATURA",
      titulo: `Cancelar ou renegociar: ${a.nome}`,
      categoria: a.categoria,
      mensalAtual: mensal,
      economiaMensal: mensal,
      calculo: [`${a.origem === "FIXA" ? "Recorrência cadastrada" : "Assinatura detectada"}: ${brl(mensal)}/mês (${brl(mensal * 12)}/ano)`, "Regra: economia = valor mensal inteiro, se cancelar; renegociar plano ou dividir o acesso reduz parte."],
    });
  }

  // pool dos demais blocos: variável, fora de evitáveis e de assinaturas
  const pool = variaveis.filter((i) => !usados.has(i.d.id) && !chavesAssinatura.has(i.chave) && i.catNorm !== "reserva");

  // 3) compras pequenas e frequentes (não essenciais)
  for (const [k, l] of agrupar(pool.filter((i) => !essencial(i.catNorm) && i.v > 0), (i) => i.chave)) {
    if (!k || l.length < FREQ_MIN_COMPRAS || new Set(l.map((i) => i.mes)).size < FREQ_MIN_MESES) continue;
    const total = soma(l);
    if (total / l.length > FREQ_TICKET_MAX) continue;
    l.forEach((i) => usados.add(i.d.id));
    const mensal = Math.round(total / MESES_JANELA);
    const eco = Math.round(mensal * PCT_FREQUENTE);
    if (eco < ECONOMIA_MINIMA) continue;
    cortes.push({
      id: `corte:frequente:${k}`,
      tipo: "FREQUENTE",
      titulo: `Compras pequenas e frequentes: ${nomeDe(l)}`,
      categoria: l[0].cat,
      mensalAtual: mensal,
      economiaMensal: eco,
      calculo: [
        `${l.length} compras em ${MESES_JANELA} meses, ticket médio ${brl(Math.round(total / l.length))}, total ${brl(total)} (média ${brl(mensal)}/mês)`,
        `Regra: ${FREQ_MIN_COMPRAS}+ compras em ${FREQ_MIN_MESES}+ meses, ticket até ${brl(FREQ_TICKET_MAX)}. Estimativa = ${pct(PCT_FREQUENTE)} do mensal = ${brl(eco)}/mês`,
      ],
    });
  }

  // 4 e 5) categorias: flexíveis e essenciais
  for (const [, l] of agrupar(pool.filter((i) => !usados.has(i.d.id)), (i) => i.catNorm)) {
    const total = soma(l);
    const media = Math.round(total / MESES_JANELA);
    if (media < MEDIA_MINIMA) continue;
    const ess = essencial(l[0].catNorm);
    const p = ess ? pctEss : pctFlex;
    const eco = Math.round(media * p);
    if (eco < ECONOMIA_MINIMA) continue;
    const topo = [...agrupar(l, (i) => i.chave).values()]
      .map((g) => ({ nome: nomeDe(g), total: soma(g) }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 3)
      .map((g) => `${g.nome} ${brl(Math.round(g.total / MESES_JANELA))}/mês`);
    cortes.push({
      id: `corte:categoria:${l[0].catNorm}`,
      tipo: ess ? "ESSENCIAL" : "FLEXIVEL",
      titulo: ess ? `Pesquisar preço e desperdício: ${l[0].cat}` : `Reduzir gastos com ${l[0].cat}`,
      categoria: l[0].cat,
      mensalAtual: media,
      economiaMensal: eco,
      calculo: [
        `Média dos últimos ${MESES_JANELA} meses: ${brl(media)}/mês`,
        `Regra: categoria ${ess ? "essencial" : "flexível"}, redução de ${pct(p)} = ${brl(eco)}/mês`,
        ...(topo.length > 0 ? [`O que mais pesa: ${topo.join("; ")}`] : []),
      ],
    });
  }

  const ordenados = cortes
    .filter((c) => !dispensados.has(c.id))
    .sort((a, b) => PRIORIDADE[a.tipo] - PRIORIDADE[b.tipo] || b.economiaMensal - a.economiaMensal || a.titulo.localeCompare(b.titulo, "pt-BR"));
  const totalMensal = ordenados.reduce((s, c) => s + c.economiaMensal, 0);

  const maioresGastos: GastoPesado[] = [...agrupar(variaveis.filter((i) => i.catNorm !== "reserva"), (i) => i.chave).entries()]
    .filter(([k]) => k !== "")
    .map(([, l]) => ({ nome: nomeDe(l), categoria: l[0].cat, mensal: Math.round(soma(l) / MESES_JANELA), compras: l.filter((i) => i.v > 0).length }))
    .filter((g) => g.mensal > 0)
    .sort((a, b) => b.mensal - a.mensal || a.nome.localeCompare(b.nome, "pt-BR"))
    .slice(0, 8);

  return { cortes: ordenados, totalMensal, totalAnual: totalMensal * 12, maioresGastos, mesesComDados, plano: montarPlano(ordenados, par.alvoMensal ?? 0) };
}

/** Soma os cortes na ordem de prioridade até atingir o alvo (centavos/mês); null se não há alvo. */
export function montarPlano(cortes: readonly Corte[], alvo: number): PlanoCorte | null {
  if (!(alvo > 0)) return null;
  const sel: Corte[] = [];
  let acc = 0;
  for (const c of cortes) {
    if (acc >= alvo) break;
    sel.push(c);
    acc += c.economiaMensal;
  }
  return { alvo, selecionados: sel, economia: acc, atinge: acc >= alvo, falta: Math.max(0, alvo - acc) };
}

export interface FluxoComCorte {
  saldoFinalDepois: number;
  mesesNegativosAntes: number;
  mesesNegativosDepois: number;
  primeiroNegativoDepois: number | null;
  /** Saldo projetado de cada mês com a economia aplicada desde o mês atual. */
  saldos: number[];
}

/** Refaz os saldos do fluxo com `economiaMensal` (centavos) aplicada a partir do mês atual: saldo_j + economia x (j+1). */
export function aplicarCorteNoFluxo(r: ResultadoFluxo, economiaMensal: number): FluxoComCorte {
  const saldos = r.meses.map((m, j) => m.saldoFinal + economiaMensal * (j + 1));
  const primeiro = saldos.findIndex((s) => s < 0);
  return {
    saldoFinalDepois: saldos.length > 0 ? saldos[saldos.length - 1] : r.saldoInicial,
    mesesNegativosAntes: r.meses.filter((m) => m.negativo).length,
    mesesNegativosDepois: saldos.filter((s) => s < 0).length,
    primeiroNegativoDepois: primeiro === -1 ? null : primeiro,
    saldos,
  };
}
