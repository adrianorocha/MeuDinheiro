/**
 * R97 - insights de economia: regras simples e explicáveis; cada um traz a regra, o cálculo e o valor estimado em R$/mês.
 * Valores em CENTAVOS. São estimativas sobre os lançamentos (não é recomendação personalizada de investimento).
 *
 * Regras:
 *  CATEGORIA  - média mensal da categoria nos 3 meses completos mais recentes (R) > média dos 6 meses anteriores (A, janela
 *               dos meses -9..-4) x (1 + limiar), com limiar = (1+pi)^6 - 1 + 5 p.p. (pi = inflação mensal informada). A categoria
 *               precisa ter gasto em >= 3 dos 6 meses anteriores e o excesso >= R$ 10. Estimativa = R - A x (1+pi)^6 (o que passou da inflação).
 *  ASSINATURAS - soma das assinaturas/recorrências de débito (`detectarAssinaturas`); estimativa = valor da MAIOR (o que se
 *               economizaria revisando-a); o texto mostra a soma e o peso da maior.
 *  REGRA_503020 - último mês completo com receita > 0: necessidades <= 50%, desejos <= 30%, poupança >= 20% (`regra503020`).
 *               Estimativa = max(excesso de desejos sobre 30%, faltante de poupança até 20%); só aparece se algum grupo fugir.
 *  META       - para metas com prazo futuro e valor guardado < objetivo: aporte mensal P que, rendendo à taxa LÍQUIDA mensal
 *               equivalente do produto CDI da simulação (R97-B) no prazo, atinge o objetivo (aportes no fim do mês):
 *               P = (objetivo - guardado x (1+r)^m) x r / ((1+r)^m - 1). Mostra também o aporte sem rendimento (falta/m).
 */
import { analisarMeta, detectarAssinaturas, regra503020 } from "./analises";
import { cartaoIdDe } from "./calc";
import { fimDoMes, inicioDoMes, mesAnoDe, normalizaMes } from "./dates";
import { ehVariavel } from "./fluxoCaixa";
import { toCents } from "./money";
import { aporteParaMeta, taxaLiquidaMensal } from "./rendimento";
import type { Dataset } from "./types";

export type TipoInsight = "CATEGORIA" | "ASSINATURAS" | "REGRA_503020" | "META";

export interface Insight {
  /** Estável entre recarregamentos (usado para dispensar). */
  id: string;
  tipo: TipoInsight;
  titulo: string;
  regra: string;
  /** Linhas do cálculo, já em texto pt-BR. */
  calculo: string[];
  /** Estimativa de economia/aporte em centavos por mês. */
  valorMensal: number;
}

export interface ParametrosInsights {
  inflacaoMensal: number;
  cdiMensal: number;
  /** 1 = 100% do CDI. */
  pctCdi: number;
}

export const brl = (cents: number): string => {
  const neg = cents < 0;
  const abs = Math.abs(Math.round(cents));
  const reais = String(Math.floor(abs / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${neg ? "-" : ""}R$ ${reais},${String(abs % 100).padStart(2, "0")}`;
};
export const pct = (f: number): string => `${(f * 100).toFixed(1).replace(".", ",")}%`;

export const LIMIAR_PP = 0.05;
export const MIN_EXCESSO_CENTAVOS = 1000;

/** Gasto por categoria (centavos) em cada mês de `de`..`ate` (offsets em relação ao mês atual; negativos = passado). */
function gastoPorCategoria(ds: Dataset, agora: number, de: number, ate: number): Map<string, { nome: string; porMes: number[] }> {
  const { mes, ano } = mesAnoDe(agora);
  const total = ate - de + 1;
  const mapa = new Map<string, { nome: string; porMes: number[] }>();
  for (let i = 0; i < total; i++) {
    const p = normalizaMes(mes + de + i, ano);
    const ini = inicioDoMes(p.mes, p.ano);
    const fim = fimDoMes(p.mes, p.ano);
    for (const d of ds.despesas) {
      if (d.data < ini || d.data > fim || !ehVariavel(d)) continue;
      const nome = d.categoria.trim();
      if (!nome) continue;
      const chave = nome.toLocaleLowerCase("pt-BR");
      const g = mapa.get(chave) ?? { nome, porMes: new Array<number>(total).fill(0) };
      const cents = toCents(d.valor);
      if (d.tipo === "DEBITO") g.porMes[i] += cents;
      else if (cartaoIdDe(d) !== null) g.porMes[i] -= cents;
      mapa.set(chave, g);
    }
  }
  return mapa;
}

export function insightsCategorias(ds: Dataset, agora: number, inflacaoMensal: number): Insight[] {
  const recentes = gastoPorCategoria(ds, agora, -3, -1);
  const anteriores = gastoPorCategoria(ds, agora, -9, -4);
  const corr6 = Math.pow(1 + inflacaoMensal, 6) - 1;
  const limiar = corr6 + LIMIAR_PP;
  const out: Insight[] = [];
  for (const [chave, r] of recentes) {
    const a = anteriores.get(chave);
    if (!a || a.porMes.filter((v) => v > 0).length < 3) continue;
    const mediaR = r.porMes.reduce((s, v) => s + Math.max(0, v), 0) / 3;
    const mediaA = a.porMes.reduce((s, v) => s + Math.max(0, v), 0) / 6;
    if (!(mediaA > 0) || !(mediaR > mediaA * (1 + limiar))) continue;
    const excesso = Math.round(mediaR - mediaA * (1 + corr6));
    if (excesso < MIN_EXCESSO_CENTAVOS) continue;
    out.push({
      id: `categoria:${chave}`,
      tipo: "CATEGORIA",
      titulo: `${r.nome}: gasto subiu acima da inflação`,
      regra: "Gasto médio dos últimos 3 meses acima da média dos 6 meses anteriores em mais que a inflação do período + 5 pontos percentuais.",
      calculo: [
        `Média dos últimos 3 meses: ${brl(Math.round(mediaR))}`,
        `Média dos 6 meses anteriores: ${brl(Math.round(mediaA))}`,
        `Variação: ${pct(mediaR / mediaA - 1)} (limite: inflação de 6 meses ${pct(corr6)} + 5 p.p. = ${pct(limiar)})`,
        `Estimativa = média recente − média anterior corrigida pela inflação = ${brl(excesso)}/mês`,
      ],
      valorMensal: excesso,
    });
  }
  return out.sort((x, y) => y.valorMensal - x.valorMensal);
}

export function insightAssinaturas(ds: Dataset, agora: number): Insight | null {
  const lista = detectarAssinaturas(ds, agora);
  if (lista.length === 0) return null;
  const total = lista.reduce((s, a) => s + toCents(a.totalMensal), 0);
  const maior = lista[0];
  const vMaior = toCents(maior.totalMensal);
  if (total <= 0) return null;
  return {
    id: "assinaturas",
    tipo: "ASSINATURAS",
    titulo: "Revise assinaturas e gastos recorrentes",
    regra: "Soma das assinaturas e recorrências de débito; a estimativa é o valor da maior, que seria economizado se for cancelada ou renegociada.",
    calculo: [
      `${lista.length} item(ns) somam ${brl(total)}/mês (${brl(total * 12)}/ano)`,
      `Maior peso: ${maior.nome} — ${brl(vMaior)}/mês (${pct(vMaior / total)} do total)`,
    ],
    valorMensal: vMaior,
  };
}

/** Último mês completo com receita; null se não houver ou se tudo estiver dentro dos alvos. */
export function insight503020(ds: Dataset, agora: number): Insight | null {
  const { mes, ano } = mesAnoDe(agora);
  const p = normalizaMes(mes - 1, ano);
  const r = regra503020(ds.despesas, p.mes, p.ano);
  if (!(r.receitas > 0)) return null;
  const rec = toCents(r.receitas);
  const excessoDesejos = Math.max(0, toCents(r.desejos.valor) - Math.round(rec * 0.3));
  const faltaPoup = Math.max(0, Math.round(rec * 0.2) - toCents(r.poupanca.valor));
  const excessoNec = Math.max(0, toCents(r.necessidades.valor) - Math.round(rec * 0.5));
  if (r.necessidades.status === "OK" && r.desejos.status === "OK" && r.poupanca.status === "OK") return null;
  const valor = Math.max(excessoDesejos, faltaPoup);
  return {
    id: `503020:${p.ano}-${p.mes}`,
    tipo: "REGRA_503020",
    titulo: "Distribuição 50/30/20 do último mês",
    regra: "Necessidades até 50% da receita, desejos até 30% e poupança de pelo menos 20%. Estimativa = maior entre o excesso em desejos e o que faltou poupar.",
    calculo: [
      `Receita do mês: ${brl(rec)}`,
      `Necessidades: ${brl(toCents(r.necessidades.valor))} (${pct(r.necessidades.percentual)}; alvo até 50%)${excessoNec > 0 ? ` — excesso ${brl(excessoNec)}` : ""}`,
      `Desejos: ${brl(toCents(r.desejos.valor))} (${pct(r.desejos.percentual)}; alvo até 30%)${excessoDesejos > 0 ? ` — excesso ${brl(excessoDesejos)}` : ""}`,
      `Poupança: ${brl(toCents(r.poupanca.valor))} (${pct(r.poupanca.percentual)}; alvo de 20% ou mais)${faltaPoup > 0 ? ` — faltou ${brl(faltaPoup)}` : ""}`,
    ],
    valorMensal: valor,
  };
}

export function insightsMetas(ds: Dataset, agora: number, par: ParametrosInsights): Insight[] {
  const out: Insight[] = [];
  const taxa = par.cdiMensal * par.pctCdi;
  for (const m of ds.metas) {
    if (m.dataAlvo === null || m.valorGuardado >= m.valorObjetivo) continue;
    const a = analisarMeta(m, ds.despesas, agora);
    const meses = a.mesesRestantes;
    if (meses === null || a.status === "ATRASADA") continue;
    const rn = taxaLiquidaMensal(taxa, meses);
    const comRend = aporteParaMeta(m.valorObjetivo, m.valorGuardado, meses, rn);
    const semRend = Math.ceil((toCents(m.valorObjetivo) - toCents(m.valorGuardado)) / meses);
    out.push({
      id: `meta:${m.id}`,
      tipo: "META",
      titulo: `Meta "${m.nome}": aporte mensal necessário`,
      regra: "Aporte mensal para atingir o valor-alvo no prazo, considerando o rendimento líquido (CDI × % informado, IR regressivo) da simulação de rendimento.",
      calculo: [
        `Faltam ${brl(toCents(m.valorObjetivo) - toCents(m.valorGuardado))} em ${meses} mês(es)`,
        `Taxa líquida equivalente: ${(rn * 100).toFixed(4).replace(".", ",")}% a.m.`,
        `Aporte com rendimento: ${brl(comRend)}/mês · sem rendimento: ${brl(semRend)}/mês`,
      ],
      valorMensal: comRend,
    });
  }
  return out;
}

/** Todos os insights, do maior para o menor valor estimado (empate: título). */
export function gerarInsights(ds: Dataset, agora: number, par: ParametrosInsights): Insight[] {
  const todos: Insight[] = [...insightsCategorias(ds, agora, par.inflacaoMensal)];
  const a = insightAssinaturas(ds, agora);
  if (a) todos.push(a);
  const r = insight503020(ds, agora);
  if (r) todos.push(r);
  todos.push(...insightsMetas(ds, agora, par));
  return todos.sort((x, y) => y.valorMensal - x.valorMensal || x.titulo.localeCompare(y.titulo, "pt-BR"));
}
