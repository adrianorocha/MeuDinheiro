import { detectarAssinaturas } from "./analises";
import { cartaoIdDe } from "./calc";
import { brlCentavos, decimosDe, decimosParaTexto } from "./centavos";
import { diasNoMes, fimDoMes, inicioDoMes, MESES_NOME, mesAnoDe, normalizaMes, somaMeses } from "./dates";
import { toCents } from "./money";
import { limparDescricao, normalizar } from "./texto";
import { emptyDataset } from "./types";
import type { Dataset, Despesa } from "./types";

/**
 * R53 - Resumo mensal/semanal inteligente. Função pura (sem campos novos): compara o período com o anterior e
 * devolve insights ordenados por relevância, em pt-BR e em centavos. Espelha `domain/ResumoInteligente.kt`.
 */

export type TipoPeriodo = "MES" | "SEMANA";
export type TipoInsight =
  | "DESPESAS_TOTAL"
  | "RECEITAS_TOTAL"
  | "POUPANCA"
  | "PROJECAO"
  | "CATEGORIA_ALTA"
  | "CATEGORIA_QUEDA"
  | "FORA_PADRAO"
  | "RECORRENCIA_NOVA"
  | "RECORRENCIA_AUMENTO"
  | "BOA_NOTICIA";
export type TomInsight = "POSITIVO" | "NEGATIVO" | "NEUTRO" | "ALERTA";

export interface Insight {
  id: string;
  tipo: TipoInsight;
  tom: TomInsight;
  /** quanto maior, mais acima na lista */
  relevancia: number;
  titulo: string;
  texto: string;
  valorCentavos: number | null;
  /** variação em décimos de ponto percentual (125 = 12,5%); null = sem base de comparação */
  variacaoDecimos: number | null;
}

export interface Comparativo {
  atualCentavos: number;
  anteriorCentavos: number;
  deltaCentavos: number;
  variacaoDecimos: number | null;
}

export interface PeriodoResumo {
  tipo: TipoPeriodo;
  inicio: number;
  fim: number;
  rotulo: string;
  emAndamento: boolean;
  diasDecorridos: number;
  diasTotal: number;
  anteriorInicio: number;
  anteriorFim: number;
  /** fim da janela anterior usada na comparação (igual ao ponto do período em andamento) */
  anteriorFimComparavel: number;
}

export interface ResumoInteligente {
  periodo: PeriodoResumo;
  despesas: Comparativo;
  receitas: Comparativo;
  resultadoCentavos: number;
  taxaPoupancaDecimos: number | null;
  projecaoCentavos: number | null;
  insights: Insight[];
  /** sem nenhum lançamento comum no período nem no anterior */
  vazio: boolean;
}

// -------------------------------------------------------------- limiares (documentados no contrato R53)

export const CATEGORIA_VARIACAO_MIN_PCT = 20;
export const CATEGORIA_DELTA_MIN_CENTAVOS = 5000;
/** 2,5x a mediana, em décimos */
export const FORA_PADRAO_MULTIPLO_X10 = 25;
export const FORA_PADRAO_MIN_CENTAVOS = 10000;
export const FORA_PADRAO_MIN_AMOSTRA = 3;
export const PROJECAO_MIN_DIAS = 3;

interface AgCat {
  nome: string;
  cents: number;
  ultimoMs: number;
}
interface Agregado {
  desp: number;
  rec: number;
  qtd: number;
  cats: Map<string, AgCat>;
}

/** Mesma regra dos relatórios/KPIs (R5/R19): só NORMAL; estorno de cartão abate a despesa. */
function agregar(despesas: readonly Despesa[], ini: number, fim: number): Agregado {
  const a: Agregado = { desp: 0, rec: 0, qtd: 0, cats: new Map() };
  for (const d of despesas) {
    if (d.natureza !== "NORMAL" || d.data < ini || d.data > fim) continue;
    a.qtd++;
    const c = toCents(d.valor);
    let contrib: number;
    if (d.tipo === "CREDITO") {
      if (cartaoIdDe(d) === null) {
        if (d.pago) a.rec += c;
        continue;
      }
      contrib = -c;
    } else contrib = c;
    a.desp += contrib;
    const k = normalizar(d.categoria);
    const nome = d.categoria.trim() || "Sem categoria";
    const g = a.cats.get(k) ?? { nome, cents: 0, ultimoMs: Number.NEGATIVE_INFINITY };
    g.cents += contrib;
    if (d.data >= g.ultimoMs) {
      g.ultimoMs = d.data;
      g.nome = nome;
    }
    a.cats.set(k, g);
  }
  return a;
}

function comparar(atual: number, anterior: number): Comparativo {
  const delta = atual - anterior;
  return { atualCentavos: atual, anteriorCentavos: anterior, deltaCentavos: delta, variacaoDecimos: decimosDe(delta, anterior) };
}

function sinalPct(decimos: number): string {
  return `${decimos >= 0 ? "+" : "-"}${decimosParaTexto(decimos)}%`;
}

function sinalBrl(cents: number): string {
  return `${cents >= 0 ? "+" : "-"}${brlCentavos(Math.abs(cents))}`;
}

// -------------------------------------------------------------- período

/** Mês civil do `referencia`, ou semana (segunda a domingo) que o contém; compara com o período anterior. */
export function periodoDoResumo(tipo: TipoPeriodo, referencia: number, agora: number): PeriodoResumo {
  const r = new Date(referencia);
  let inicio: number;
  let fim: number;
  let anteriorInicio: number;
  let anteriorFim: number;
  let diasTotal: number;
  let rotulo: string;
  if (tipo === "MES") {
    const { mes, ano } = mesAnoDe(referencia);
    const p = normalizaMes(mes - 1, ano);
    inicio = inicioDoMes(mes, ano);
    fim = fimDoMes(mes, ano);
    anteriorInicio = inicioDoMes(p.mes, p.ano);
    anteriorFim = fimDoMes(p.mes, p.ano);
    diasTotal = diasNoMes(mes, ano);
    rotulo = `${MESES_NOME[mes - 1]} de ${ano}`;
  } else {
    const dow = (r.getDay() + 6) % 7; // segunda = 0
    const y = r.getFullYear();
    const m = r.getMonth();
    const d = r.getDate() - dow;
    inicio = new Date(y, m, d, 0, 0, 0, 0).getTime();
    fim = new Date(y, m, d + 6, 23, 59, 59, 999).getTime();
    anteriorInicio = new Date(y, m, d - 7, 0, 0, 0, 0).getTime();
    anteriorFim = new Date(y, m, d - 1, 23, 59, 59, 999).getTime();
    diasTotal = 7;
    const f = (ms: number) => `${String(new Date(ms).getDate()).padStart(2, "0")}/${String(new Date(ms).getMonth() + 1).padStart(2, "0")}`;
    rotulo = `Semana de ${f(inicio)} a ${f(fim)}`;
  }
  const emAndamento = agora >= inicio && agora <= fim;
  let diasDecorridos = 0;
  if (agora > fim) diasDecorridos = diasTotal;
  else if (emAndamento) {
    const a = new Date(agora);
    diasDecorridos = tipo === "MES" ? a.getDate() : ((a.getDay() + 6) % 7) + 1;
  }
  let anteriorFimComparavel = anteriorFim;
  if (emAndamento) {
    const pi = new Date(anteriorInicio);
    const ate = new Date(pi.getFullYear(), pi.getMonth(), pi.getDate() + diasDecorridos - 1, 23, 59, 59, 999).getTime();
    anteriorFimComparavel = Math.min(ate, anteriorFim);
  }
  return { tipo, inicio, fim, rotulo, emAndamento, diasDecorridos, diasTotal, anteriorInicio, anteriorFim, anteriorFimComparavel };
}

// -------------------------------------------------------------- auxiliares

function chaveRec(descricao: string): string {
  return limparDescricao(descricao.replace(/\(\s*\d+\s*\/\s*\d+\s*\)/g, " "));
}

function ehParcela(d: Despesa): boolean {
  return (d.grupoId ?? "").startsWith("parc:");
}

/** 2 × mediana (inteiro) de uma lista de centavos. */
function mediana2(v: number[]): number {
  const s = [...v].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] * 2 : s[m - 1] + s[m];
}

// -------------------------------------------------------------- principal

export function resumoInteligente(
  ds: Pick<Dataset, "despesas" | "despesasFixas" | "orcamentos">,
  tipo: TipoPeriodo,
  referencia: number,
  agora: number,
): ResumoInteligente {
  const p = periodoDoResumo(tipo, referencia, agora);
  const atual = agregar(ds.despesas, p.inicio, p.fim);
  const ant = agregar(ds.despesas, p.anteriorInicio, p.anteriorFimComparavel);
  const antInteiro = p.emAndamento ? agregar(ds.despesas, p.anteriorInicio, p.anteriorFim) : ant;
  const despesas = comparar(atual.desp, ant.desp);
  const receitas = comparar(atual.rec, ant.rec);
  const resultado = atual.rec - atual.desp;
  const taxa = atual.rec > 0 ? decimosDe(resultado, atual.rec) : null;
  const vazio = atual.qtd === 0 && antInteiro.qtd === 0;

  const base: ResumoInteligente = {
    periodo: p,
    despesas,
    receitas,
    resultadoCentavos: resultado,
    taxaPoupancaDecimos: taxa,
    projecaoCentavos: null,
    insights: [],
    vazio,
  };
  if (vazio) return base;

  const mesmoPonto =
    tipo === "MES"
      ? p.emAndamento
        ? `ao mesmo período do mês anterior (até o dia ${p.diasDecorridos})`
        : "ao mês anterior"
      : p.emAndamento
        ? "ao mesmo ponto da semana anterior"
        : "à semana anterior";
  const nomeAnt = tipo === "MES" ? "mês anterior" : "semana anterior";
  const ins: Insight[] = [];
  const add = (i: Omit<Insight, "id"> & { id?: string }) => ins.push({ ...i, id: i.id ?? i.tipo });

  // (a) totais de despesas e receitas
  if (atual.desp !== 0 || ant.desp !== 0) {
    const c = despesas;
    let texto: string;
    if (c.variacaoDecimos === null) {
      texto = `Você gastou ${brlCentavos(c.atualCentavos)}. Não houve despesas no ${nomeAnt} para comparar.`;
    } else if (c.deltaCentavos === 0) {
      texto = `Você gastou ${brlCentavos(c.atualCentavos)}, igual ${mesmoPonto}.`;
    } else {
      texto = `Você gastou ${brlCentavos(c.atualCentavos)}: ${sinalBrl(c.deltaCentavos)} (${sinalPct(c.variacaoDecimos)}) em relação ${mesmoPonto}.`;
    }
    const tom: TomInsight = c.deltaCentavos < 0 ? "POSITIVO" : c.deltaCentavos > 0 && (c.variacaoDecimos === null || c.variacaoDecimos >= 100) ? "ALERTA" : "NEUTRO";
    add({ tipo: "DESPESAS_TOTAL", tom, relevancia: 90, titulo: "Despesas", texto, valorCentavos: c.atualCentavos, variacaoDecimos: c.variacaoDecimos });
  }
  if (atual.rec !== 0 || ant.rec !== 0) {
    const c = receitas;
    let texto: string;
    if (c.variacaoDecimos === null) {
      texto = `Você recebeu ${brlCentavos(c.atualCentavos)}. Não houve receitas no ${nomeAnt} para comparar.`;
    } else if (c.deltaCentavos === 0) {
      texto = `Você recebeu ${brlCentavos(c.atualCentavos)}, igual ${mesmoPonto}.`;
    } else {
      texto = `Você recebeu ${brlCentavos(c.atualCentavos)}: ${sinalBrl(c.deltaCentavos)} (${sinalPct(c.variacaoDecimos)}) em relação ${mesmoPonto}.`;
    }
    const tom: TomInsight = c.deltaCentavos > 0 ? "POSITIVO" : c.deltaCentavos < 0 && c.variacaoDecimos !== null && c.variacaoDecimos <= -100 ? "ALERTA" : "NEUTRO";
    add({ tipo: "RECEITAS_TOTAL", tom, relevancia: 85, titulo: "Receitas", texto, valorCentavos: c.atualCentavos, variacaoDecimos: c.variacaoDecimos });
  }

  // (e) economia / taxa de poupança
  if (taxa !== null) {
    if (resultado >= 0) {
      add({
        tipo: "POUPANCA",
        tom: taxa >= 200 ? "POSITIVO" : "NEUTRO",
        relevancia: 80,
        titulo: "Taxa de poupança",
        texto: `Sobraram ${brlCentavos(resultado)}, ${decimosParaTexto(taxa)}% da receita realizada${p.emAndamento ? " até agora" : ""}.`,
        valorCentavos: resultado,
        variacaoDecimos: taxa,
      });
    } else {
      add({
        tipo: "POUPANCA",
        tom: "NEGATIVO",
        relevancia: 80,
        titulo: "Taxa de poupança",
        texto: `Você gastou ${brlCentavos(-resultado)} a mais do que recebeu (receita realizada de ${brlCentavos(atual.rec)}).`,
        valorCentavos: resultado,
        variacaoDecimos: taxa,
      });
    }
  } else if (atual.desp > 0) {
    add({
      tipo: "POUPANCA",
      tom: "NEUTRO",
      relevancia: 55,
      titulo: "Taxa de poupança",
      texto: "Nenhuma receita realizada no período: não dá para calcular a taxa de poupança.",
      valorCentavos: resultado,
      variacaoDecimos: null,
    });
  }

  // (f) projeção do fechamento no ritmo atual
  let projecao: number | null = null;
  if (p.emAndamento && p.diasDecorridos >= PROJECAO_MIN_DIAS && atual.desp > 0) {
    projecao = Math.floor((atual.desp * p.diasTotal * 2 + p.diasDecorridos) / (2 * p.diasDecorridos));
    const alvo = tipo === "MES" ? "o mês" : "a semana";
    if (antInteiro.desp > 0) {
      const delta = projecao - antInteiro.desp;
      const pct = decimosDe(delta, antInteiro.desp) as number;
      const rel = delta === 0 ? "igual" : `${brlCentavos(Math.abs(delta))} ${delta > 0 ? "acima" : "abaixo"} (${sinalPct(pct)})`;
      add({
        tipo: "PROJECAO",
        tom: delta < 0 ? "POSITIVO" : pct >= 100 ? "ALERTA" : "NEUTRO",
        relevancia: 75,
        titulo: "Projeção de fechamento",
        texto: `No ritmo atual, ${alvo} deve fechar em ${brlCentavos(projecao)} em despesas: ${rel} do ${nomeAnt} inteiro (${brlCentavos(antInteiro.desp)}).`,
        valorCentavos: projecao,
        variacaoDecimos: pct,
      });
    } else {
      add({
        tipo: "PROJECAO",
        tom: "NEUTRO",
        relevancia: 75,
        titulo: "Projeção de fechamento",
        texto: `No ritmo atual, ${alvo} deve fechar em ${brlCentavos(projecao)} em despesas. Sem despesas no ${nomeAnt} para comparar.`,
        valorCentavos: projecao,
        variacaoDecimos: null,
      });
    }
  }

  // (b) categorias com maior alta/queda (só relevantes: >= 20% e >= R$ 50; sem base anterior conta como relevante)
  const chaves = new Set<string>([...atual.cats.keys(), ...ant.cats.keys()]);
  const altas: { k: string; nome: string; a: number; pr: number; delta: number }[] = [];
  const quedas: { k: string; nome: string; a: number; pr: number; delta: number }[] = [];
  for (const k of [...chaves].sort()) {
    const a = Math.max(0, atual.cats.get(k)?.cents ?? 0);
    const pr = Math.max(0, ant.cats.get(k)?.cents ?? 0);
    const delta = a - pr;
    if (Math.abs(delta) < CATEGORIA_DELTA_MIN_CENTAVOS) continue;
    if (pr > 0 && Math.abs(delta) * 100 < CATEGORIA_VARIACAO_MIN_PCT * pr) continue;
    const nome = atual.cats.get(k)?.nome ?? ant.cats.get(k)?.nome ?? "Sem categoria";
    (delta > 0 ? altas : quedas).push({ k, nome, a, pr, delta });
  }
  altas.sort((x, y) => y.delta - x.delta);
  quedas.sort((x, y) => x.delta - y.delta);
  for (const c of altas.slice(0, 3)) {
    const pct = decimosDe(c.delta, c.pr);
    add({
      id: `CATEGORIA_ALTA:${c.k}`,
      tipo: "CATEGORIA_ALTA",
      tom: "ALERTA",
      relevancia: 70 + Math.min(9, Math.floor(c.delta / 10000)),
      titulo: `${c.nome} em alta`,
      texto:
        pct === null
          ? `${c.nome}: ${brlCentavos(c.a)}, sem gasto equivalente no ${nomeAnt} (${sinalBrl(c.delta)}).`
          : `${c.nome}: ${brlCentavos(c.a)}, ${sinalBrl(c.delta)} (${sinalPct(pct)}) em relação ${mesmoPonto}.`,
      valorCentavos: c.a,
      variacaoDecimos: pct,
    });
  }
  for (const c of quedas.slice(0, 2)) {
    const pct = decimosDe(c.delta, c.pr);
    add({
      id: `CATEGORIA_QUEDA:${c.k}`,
      tipo: "CATEGORIA_QUEDA",
      tom: "POSITIVO",
      relevancia: 50 + Math.min(9, Math.floor(-c.delta / 10000)),
      titulo: `${c.nome} em queda`,
      texto: `${c.nome}: ${brlCentavos(c.a)}, ${sinalBrl(c.delta)} (${sinalPct(pct as number)}) em relação ${mesmoPonto}.`,
      valorCentavos: c.a,
      variacaoDecimos: pct,
    });
  }

  // (c) gasto fora do padrão: lançamento único > 2,5x a mediana da categoria (6 meses anteriores) e >= R$ 100
  const desde = somaMeses(p.inicio, -6);
  const hist = new Map<string, number[]>();
  for (const d of ds.despesas) {
    if (d.natureza !== "NORMAL" || d.tipo !== "DEBITO" || ehParcela(d) || d.data < desde || d.data >= p.inicio) continue;
    const k = normalizar(d.categoria);
    const l = hist.get(k) ?? [];
    l.push(toCents(d.valor));
    hist.set(k, l);
  }
  const fora: { d: Despesa; c: number; med2: number }[] = [];
  for (const d of ds.despesas) {
    if (d.natureza !== "NORMAL" || d.tipo !== "DEBITO" || ehParcela(d) || d.data < p.inicio || d.data > p.fim) continue;
    const c = toCents(d.valor);
    if (c < FORA_PADRAO_MIN_CENTAVOS) continue;
    const h = hist.get(normalizar(d.categoria));
    if (!h || h.length < FORA_PADRAO_MIN_AMOSTRA) continue;
    const med2 = mediana2(h); // 2 x mediana; c > 2,5 x mediana  <=>  c * 20 > 25 * med2
    if (med2 > 0 && c * 20 > FORA_PADRAO_MULTIPLO_X10 * med2) fora.push({ d, c, med2 });
  }
  fora.sort((x, y) => y.c - x.c || x.d.id - y.d.id);
  for (const f of fora.slice(0, 3)) {
    const razao = Math.round((f.c * 20) / f.med2);
    const cat = f.d.categoria.trim() || "Sem categoria";
    add({
      id: `FORA_PADRAO:${f.d.id}`,
      tipo: "FORA_PADRAO",
      tom: "ALERTA",
      relevancia: 78,
      titulo: "Gasto fora do padrão",
      texto: `${f.d.descricao.trim() || cat} (${cat}): ${brlCentavos(f.c)}, ${decimosParaTexto(razao)}x a mediana da categoria (${brlCentavos(Math.floor((f.med2 + 1) / 2))}).`,
      valorCentavos: f.c,
      variacaoDecimos: null,
    });
  }

  // (d) recorrências novas ou com aumento de valor (detectarAssinaturas no fim do período e no do anterior)
  if (agora >= p.inicio) {
    const tAgora = p.emAndamento ? agora : p.fim;
    const dsFull: Dataset = { ...emptyDataset(), despesas: ds.despesas as Despesa[], despesasFixas: ds.despesasFixas as Dataset["despesasFixas"] };
    const agoraSet = detectarAssinaturas(dsFull, tAgora).filter((a) => a.origem === "DETECTADA");
    const antSet = detectarAssinaturas(dsFull, p.anteriorFim);
    const antChaves = new Set(antSet.map((a) => chaveRec(a.nome)));
    const recs: Insight[] = [];
    for (const a of agoraSet) {
      const k = chaveRec(a.nome);
      if (antChaves.has(k) || a.ultimaData === null || a.ultimaData < p.inicio) continue;
      recs.push({
        id: `RECORRENCIA_NOVA:${k}`,
        tipo: "RECORRENCIA_NOVA",
        tom: "ALERTA",
        relevancia: 60,
        titulo: "Nova recorrência",
        texto: `${a.nome} passou a se repetir todo mês: cerca de ${brlCentavos(toCents(a.valorMedio))} por mês.`,
        valorCentavos: toCents(a.valorMedio),
        variacaoDecimos: null,
      });
    }
    for (const a of antSet.filter((x) => x.origem === "DETECTADA")) {
      const k = chaveRec(a.nome);
      const doPeriodo = ds.despesas
        .filter((d) => d.natureza === "NORMAL" && d.tipo === "DEBITO" && d.data >= p.inicio && d.data <= tAgora && chaveRec(d.descricao) === k)
        .sort((x, y) => y.data - x.data || y.id - x.id);
      if (doPeriodo.length === 0) continue;
      const c = toCents(doPeriodo[0].valor);
      const antes = toCents(a.valorMedio);
      const diff = c - antes;
      if (diff >= 100 && diff * 20 >= antes) {
        const pct = decimosDe(diff, antes) as number;
        recs.push({
          id: `RECORRENCIA_AUMENTO:${k}`,
          tipo: "RECORRENCIA_AUMENTO",
          tom: "ALERTA",
          relevancia: 60,
          titulo: "Recorrência mais cara",
          texto: `${a.nome} subiu de ${brlCentavos(antes)} para ${brlCentavos(c)} (${sinalPct(pct)}).`,
          valorCentavos: c,
          variacaoDecimos: pct,
        });
      }
    }
    for (const r of recs.slice(0, 4)) ins.push(r);
  }

  // (g) boa notícia (no máximo uma)
  const lim = ds.orcamentos.filter((o) => toCents(o.valorLimite) > 0);
  if (tipo === "MES" && lim.length > 0 && lim.every((o) => Math.max(0, atual.cats.get(normalizar(o.categoria))?.cents ?? 0) <= toCents(o.valorLimite))) {
    add({
      tipo: "BOA_NOTICIA",
      tom: "POSITIVO",
      relevancia: 45,
      titulo: "Boa notícia",
      texto: `Orçamento respeitado em ${lim.length === 1 ? "1 categoria" : `todas as ${lim.length} categorias`}${p.emAndamento ? " até agora" : ""}.`,
      valorCentavos: null,
      variacaoDecimos: null,
    });
  } else if (taxa !== null && taxa >= 200) {
    add({
      tipo: "BOA_NOTICIA",
      tom: "POSITIVO",
      relevancia: 45,
      titulo: "Boa notícia",
      texto: `Você está poupando ${decimosParaTexto(taxa)}% da receita, acima dos 20% recomendados.`,
      valorCentavos: null,
      variacaoDecimos: taxa,
    });
  }

  // ordem estável: relevância decrescente, depois ordem de geração
  const ordenado = ins
    .map((x, i) => ({ x, i }))
    .sort((a, b) => b.x.relevancia - a.x.relevancia || a.i - b.i)
    .map((e) => e.x);
  return { ...base, projecaoCentavos: projecao, insights: ordenado };
}

/** Texto pronto para copiar/compartilhar (WhatsApp, e-mail...). */
export function textoDoResumo(r: ResumoInteligente): string {
  const linhas = [`Resumo de ${r.periodo.rotulo} - MeuDinheiro`, ""];
  if (r.vazio || r.insights.length === 0) linhas.push("Sem lançamentos suficientes para gerar o resumo.");
  for (const i of r.insights) linhas.push(`- ${i.titulo}: ${i.texto}`);
  return linhas.join("\n");
}
