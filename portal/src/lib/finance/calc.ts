import { clampDia, diasNoMes, fimDoMes, inicioDoDia, inicioDoMes, mesAnoDe, normalizaMes } from "./dates";
import { fromCents, round2, somaCents, toCents } from "./money";
import type { Cartao, Dataset, Despesa, Investimento, Orcamento } from "./types";

/** R2 - `0` e tratado como `null`. */
export function cartaoIdDe(d: Pick<Despesa, "cartaoId">): number | null {
  return d.cartaoId ? d.cartaoId : null;
}

/** R18 - cartão principal (físico) do grupo; virtual órfão vale por si. */
export function principalDe(cartao: Cartao, cartoes: readonly Cartao[]): Cartao {
  if (cartao.cartaoPrincipalId == null) return cartao;
  return cartoes.find((c) => c.id === cartao.cartaoPrincipalId) ?? cartao;
}

export function ehVirtual(cartao: Pick<Cartao, "cartaoPrincipalId">): boolean {
  return cartao.cartaoPrincipalId != null;
}

/** R18 - principal + seus virtuais (principal primeiro). */
export function cartoesDoGrupo(cartao: Cartao, cartoes: readonly Cartao[]): Cartao[] {
  const p = principalDe(cartao, cartoes);
  return [p, ...cartoes.filter((c) => c.id !== p.id && c.cartaoPrincipalId === p.id)];
}

/** Valor com sinal em centavos (CREDITO positivo, DEBITO negativo). */
function assinadoCents(d: Despesa): number {
  return d.tipo === "CREDITO" ? toCents(d.valor) : -toCents(d.valor);
}

// ---------------------------------------------------------------- R3 / R4

/** R3 - saldo derivado do extrato. */
export function saldoConta(despesas: readonly Despesa[], numeroConta: string): number {
  let total = 0;
  for (const d of despesas) {
    if (d.conta === numeroConta && d.pago && cartaoIdDe(d) === null) total += assinadoCents(d);
  }
  return fromCents(total);
}

/** R4 - limite disponivel derivado das despesas nao pagas do cartao. */
export function limiteCartao(
  cartao: Pick<Cartao, "id" | "limiteTotal">,
  despesas: readonly Despesa[],
): number {
  let usado = 0;
  for (const d of despesas) {
    if (cartaoIdDe(d) === cartao.id && !d.pago) usado -= assinadoCents(d);
  }
  return fromCents(toCents(cartao.limiteTotal) - usado);
}

/** R18 - limite disponível do grupo (limiteTotal do principal menos o não pago de todos os cartões do grupo). */
export function limiteGrupo(cartao: Cartao, cartoes: readonly Cartao[], despesas: readonly Despesa[]): number {
  const grupo = cartoesDoGrupo(cartao, cartoes);
  const ids = new Set(grupo.map((c) => c.id));
  let usado = 0;
  for (const d of despesas) {
    const cid = cartaoIdDe(d);
    if (cid !== null && ids.has(cid) && !d.pago) usado -= assinadoCents(d);
  }
  return fromCents(toCents(grupo[0].limiteTotal) - usado);
}

export interface SaldoCartao {
  usado: number;
  limiteProprio: number | null;
  disponivel: number;
  disponivelGrupo: number;
  razao: number;
}

/** R41 - uso/saldo PRÓPRIO do cartão dentro do limite compartilhado do grupo. */
export function saldoDoCartao(cartao: Cartao, cartoes: readonly Cartao[], despesas: readonly Despesa[]): SaldoCartao {
  let usadoC = 0;
  for (const d of despesas) {
    if (cartaoIdDe(d) === cartao.id && !d.pago) usadoC -= assinadoCents(d);
  }
  usadoC = Math.max(0, usadoC);
  const principal = principalDe(cartao, cartoes);
  const disponivelGrupo = limiteGrupo(cartao, cartoes, despesas);
  const proprio = cartao.limiteProprio && cartao.limiteProprio > 0 ? cartao.limiteProprio : null;
  const disponivel = proprio != null ? Math.min(fromCents(toCents(proprio) - usadoC), disponivelGrupo) : disponivelGrupo;
  const base = toCents(proprio ?? principal.limiteTotal);
  return { usado: fromCents(usadoC), limiteProprio: proprio, disponivel, disponivelGrupo, razao: base > 0 ? usadoC / base : 0 };
}

// ---------------------------------------------------------------- R5

export interface Kpis {
  receitasRealizadas: number;
  receitasPrevistas: number;
  despesasTotal: number;
  despesasPagas: number;
  despesasPendentes: number;
  resultado: number;
  taxaPoupanca: number;
}

/**
 * R5 - KPIs do periodo `[inicio, fim]` (ms), so natureza NORMAL.
 * Estornos de cartao (CREDITO com cartaoId) abatem despesas e nao contam como receita
 * (evita dupla contagem; o contrato os subtrai de despesasTotal).
 */
export function kpisPeriodo(despesas: readonly Despesa[], inicio: number, fim: number): Kpis {
  let recReal = 0;
  let recPrev = 0;
  let debitos = 0;
  let estornos = 0;
  let debPagos = 0;
  for (const d of despesas) {
    if (d.natureza !== "NORMAL" || d.data < inicio || d.data > fim) continue;
    const c = toCents(d.valor);
    if (d.tipo === "CREDITO") {
      if (cartaoIdDe(d) !== null) estornos += c;
      else if (d.pago) recReal += c;
      else recPrev += c;
    } else {
      debitos += c;
      if (d.pago) debPagos += c;
    }
  }
  const despesasTotal = debitos - estornos;
  const resultado = recReal - despesasTotal;
  return {
    receitasRealizadas: fromCents(recReal),
    receitasPrevistas: fromCents(recPrev),
    despesasTotal: fromCents(despesasTotal),
    despesasPagas: fromCents(debPagos),
    despesasPendentes: fromCents(despesasTotal - debPagos),
    resultado: fromCents(resultado),
    taxaPoupanca: recReal > 0 ? resultado / recReal : 0,
  };
}

export function kpisDoMes(despesas: readonly Despesa[], mes: number, ano: number): Kpis {
  return kpisPeriodo(despesas, inicioDoMes(mes, ano), fimDoMes(mes, ano));
}

export function kpisTotal(despesas: readonly Despesa[]): Kpis {
  return kpisPeriodo(despesas, Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY);
}

// ---------------------------------------------------------------- R6

export function fechamentoDia(cartao: Pick<Cartao, "diaFechamento">, mes: number, ano: number): number {
  return Math.min(cartao.diaFechamento, diasNoMes(mes, ano));
}

export interface MesAno {
  mes: number;
  ano: number;
}

/** Fatura (identificada pelo mes de fechamento) a qual a compra pertence. */
export function faturaDaCompra(cartao: Pick<Cartao, "diaFechamento">, dataMs: number): MesAno {
  const { mes, ano } = mesAnoDe(dataMs);
  const dia = new Date(dataMs).getDate();
  return dia <= fechamentoDia(cartao, mes, ano) ? { mes, ano } : normalizaMes(mes + 1, ano);
}

/** Data de fechamento da fatura (ms, inicio do dia). */
export function dataFechamentoFatura(cartao: Pick<Cartao, "diaFechamento">, mes: number, ano: number): number {
  return new Date(ano, mes - 1, fechamentoDia(cartao, mes, ano)).getTime();
}

/** Data de vencimento da fatura (ms, inicio do dia). */
export function vencimentoFatura(
  cartao: Pick<Cartao, "diaFechamento" | "diaVencimento">,
  mes: number,
  ano: number,
): number {
  const alvo = cartao.diaVencimento > cartao.diaFechamento ? { mes, ano } : normalizaMes(mes + 1, ano);
  const dia = clampDia(cartao.diaVencimento, alvo.mes, alvo.ano);
  return new Date(alvo.ano, alvo.mes - 1, dia).getTime();
}

export function itensFatura(
  cartao: Pick<Cartao, "id" | "diaFechamento">,
  despesas: readonly Despesa[],
  mes: number,
  ano: number,
  idsGrupo: ReadonlySet<number> = new Set([cartao.id]),
): Despesa[] {
  return despesas
    .filter((d) => {
      const cid = cartaoIdDe(d);
      if (cid === null || !idsGrupo.has(cid)) return false;
      const f = faturaDaCompra(cartao, d.data);
      return f.mes === mes && f.ano === ano;
    })
    .sort((a, b) => a.data - b.data || a.id - b.id);
}

export interface ResumoFatura {
  itens: Despesa[];
  /** Soma DEBITO - soma CREDITO de todos os itens do ciclo. */
  total: number;
  /** Soma DEBITO - soma CREDITO dos itens nao pagos. */
  emAberto: number;
  paga: boolean;
  fechamento: number;
  vencimento: number;
}

/** Fatura única do grupo (R18), com o ciclo do principal. */
export function resumoFatura(
  cartaoOuVirtual: Cartao,
  despesas: readonly Despesa[],
  mes: number,
  ano: number,
  cartoes: readonly Cartao[] = [cartaoOuVirtual],
): ResumoFatura {
  const grupo = cartoesDoGrupo(cartaoOuVirtual, cartoes);
  const cartao = grupo[0];
  const itens = itensFatura(cartao, despesas, mes, ano, new Set(grupo.map((c) => c.id)));
  let total = 0;
  let aberto = 0;
  for (const d of itens) {
    const c = -assinadoCents(d);
    total += c;
    if (!d.pago) aberto += c;
  }
  return {
    itens,
    total: fromCents(total),
    emAberto: fromCents(aberto),
    paga: itens.length > 0 && itens.every((i) => i.pago),
    fechamento: dataFechamentoFatura(cartao, mes, ano),
    vencimento: vencimentoFatura(cartao, mes, ano),
  };
}

export interface FaturaPendente extends MesAno {
  cartaoId: number;
  total: number;
  vencimento: number;
}

/** Faturas com itens nao pagos (valor em aberto; pode ser <= 0 se so houver estornos). */
export function faturasPendentes(cartoes: readonly Cartao[], despesas: readonly Despesa[]): FaturaPendente[] {
  const out: FaturaPendente[] = [];
  for (const cartao of cartoes) {
    if (principalDe(cartao, cartoes) !== cartao) continue; // virtuais entram na fatura do principal
    const ids = new Set(cartoesDoGrupo(cartao, cartoes).map((c) => c.id));
    const grupos = new Map<string, { mes: number; ano: number; cents: number }>();
    for (const d of despesas) {
      const cid = cartaoIdDe(d);
      if (cid === null || !ids.has(cid) || d.pago) continue;
      const f = faturaDaCompra(cartao, d.data);
      const chave = `${f.ano}-${f.mes}`;
      const g = grupos.get(chave) ?? { mes: f.mes, ano: f.ano, cents: 0 };
      g.cents -= assinadoCents(d);
      grupos.set(chave, g);
    }
    for (const g of grupos.values()) {
      out.push({
        cartaoId: cartao.id,
        mes: g.mes,
        ano: g.ano,
        total: fromCents(g.cents),
        vencimento: vencimentoFatura(cartao, g.mes, g.ano),
      });
    }
  }
  return out.sort((a, b) => a.vencimento - b.vencimento);
}

// ---------------------------------------------------------------- R44

export interface LiberacaoFatura extends MesAno {
  /** Limite que volta ao pagar esta fatura (DEBITO - CREDITO em aberto, em R$). */
  valor: number;
  vencimento: number;
}

export interface ComprometimentoCartao {
  /** Em aberto no ciclo (fatura) corrente. */
  faturaAtual: number;
  /** Em aberto de faturas já anteriores ao ciclo corrente (vencidas/atrasadas). */
  anteriores: number;
  /** Em aberto de ciclos posteriores ao corrente (parcelas futuras). */
  parcelasFuturas: number;
  /** Total em aberto do grupo (= limite usado, R4/R18). */
  emAbertoTotal: number;
  limiteTotal: number;
  disponivel: number;
  /** Quanto de limite volta ao pagar cada fatura em aberto (valor > 0), em ordem cronológica. */
  liberacaoPorFatura: LiberacaoFatura[];
}

/** R44 - como o limite do grupo está comprometido: fatura atual × parcelas futuras × disponível. Puro. */
export function comprometimentoCartao(
  cartao: Cartao,
  cartoes: readonly Cartao[],
  despesas: readonly Despesa[],
  agora: number,
): ComprometimentoCartao {
  const grupo = cartoesDoGrupo(cartao, cartoes);
  const principal = grupo[0];
  const ids = new Set(grupo.map((c) => c.id));
  const ciclo = faturaDaCompra(principal, agora);
  const chaveCiclo = ciclo.ano * 12 + ciclo.mes;
  const porFatura = new Map<number, { mes: number; ano: number; cents: number }>();
  let atual = 0;
  let anteriores = 0;
  let futuras = 0;
  for (const d of despesas) {
    const cid = cartaoIdDe(d);
    if (cid === null || !ids.has(cid) || d.pago) continue;
    const f = faturaDaCompra(principal, d.data);
    const chave = f.ano * 12 + f.mes;
    const c = -assinadoCents(d);
    if (chave === chaveCiclo) atual += c;
    else if (chave < chaveCiclo) anteriores += c;
    else futuras += c;
    const g = porFatura.get(chave) ?? { mes: f.mes, ano: f.ano, cents: 0 };
    g.cents += c;
    porFatura.set(chave, g);
  }
  const liberacaoPorFatura = [...porFatura.entries()]
    .sort((a, b) => a[0] - b[0])
    .filter(([, g]) => g.cents > 0)
    .map(([, g]) => ({ mes: g.mes, ano: g.ano, valor: fromCents(g.cents), vencimento: vencimentoFatura(principal, g.mes, g.ano) }));
  return {
    faturaAtual: fromCents(atual),
    anteriores: fromCents(anteriores),
    parcelasFuturas: fromCents(futuras),
    emAbertoTotal: fromCents(atual + anteriores + futuras),
    limiteTotal: principal.limiteTotal,
    disponivel: limiteGrupo(cartao, cartoes, despesas),
    liberacaoPorFatura,
  };
}

export interface FaturaAberta extends MesAno {
  chave: string;
  vencimento: number;
  itens: Despesa[];
  /** DEBITO - CREDITO dos itens em aberto (R$). */
  subtotal: number;
}

/** R44 - itens em aberto do grupo agrupados por fatura (cronológico), para pagamento seletivo. */
export function faturasEmAberto(cartao: Cartao, cartoes: readonly Cartao[], despesas: readonly Despesa[]): FaturaAberta[] {
  const grupo = cartoesDoGrupo(cartao, cartoes);
  const principal = grupo[0];
  const ids = new Set(grupo.map((c) => c.id));
  const mapa = new Map<number, { mes: number; ano: number; itens: Despesa[]; cents: number }>();
  for (const d of despesas) {
    const cid = cartaoIdDe(d);
    if (cid === null || !ids.has(cid) || d.pago) continue;
    const f = faturaDaCompra(principal, d.data);
    const k = f.ano * 12 + f.mes;
    const g = mapa.get(k) ?? { mes: f.mes, ano: f.ano, itens: [], cents: 0 };
    g.itens.push(d);
    g.cents -= assinadoCents(d);
    mapa.set(k, g);
  }
  return [...mapa.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([, g]) => ({
      mes: g.mes,
      ano: g.ano,
      chave: `${g.ano}-${String(g.mes).padStart(2, "0")}`,
      vencimento: vencimentoFatura(principal, g.mes, g.ano),
      itens: g.itens.sort((a, b) => a.data - b.data || a.id - b.id),
      subtotal: fromCents(g.cents),
    }));
}

/** R44 - valor líquido (DEBITO - CREDITO) de um conjunto de itens, em R$. */
export function liquidoItens(itens: readonly Despesa[]): number {
  let c = 0;
  for (const d of itens) c -= assinadoCents(d);
  return fromCents(c);
}

// ---------------------------------------------------------------- R13

export function normalizaNome(s: string): string {
  return s.trim().toLowerCase().replace(/\s+/g, " ");
}

export function gastoCategoria(despesas: readonly Despesa[], categoria: string, mes: number, ano: number): number {
  const alvo = normalizaNome(categoria);
  const ini = inicioDoMes(mes, ano);
  const fim = fimDoMes(mes, ano);
  let total = 0;
  for (const d of despesas) {
    if (d.natureza !== "NORMAL" || d.data < ini || d.data > fim) continue;
    if (normalizaNome(d.categoria) !== alvo) continue;
    if (d.tipo === "DEBITO") total += toCents(d.valor);
    else if (cartaoIdDe(d) !== null) total -= toCents(d.valor);
  }
  return fromCents(total);
}

export type StatusOrcamento = "OK" | "ATENCAO" | "ESTOURADO";

export interface ProgressoOrcamento {
  orcamento: Orcamento;
  gasto: number;
  limite: number;
  /** razao gasto/limite (1 = 100%); pode passar de 1. */
  pct: number;
  status: StatusOrcamento;
}

export function statusOrcamento(pct: number): StatusOrcamento {
  if (pct >= 1) return "ESTOURADO";
  if (pct >= 0.8) return "ATENCAO";
  return "OK";
}

export function progressoOrcamento(
  orc: Orcamento,
  despesas: readonly Despesa[],
  mes: number,
  ano: number,
): ProgressoOrcamento {
  const gasto = gastoCategoria(despesas, orc.categoria, mes, ano);
  const limite = round2(orc.valorLimite);
  const pct = limite > 0 ? gasto / limite : gasto > 0 ? 1 : 0;
  return { orcamento: orc, gasto, limite, pct, status: statusOrcamento(pct) };
}

// ---------------------------------------------------------------- R14

export function saldoTotalContas(ds: Pick<Dataset, "contas" | "despesas">): number {
  return fromCents(ds.contas.reduce((acc, c) => acc + toCents(saldoConta(ds.despesas, c.conta)), 0));
}

export function totalFaturasEmAberto(ds: Pick<Dataset, "cartoes" | "despesas">): number {
  const ids = new Set(ds.cartoes.map((c) => c.id));
  let total = 0;
  for (const d of ds.despesas) {
    const cid = cartaoIdDe(d);
    if (cid !== null && ids.has(cid) && !d.pago) total -= assinadoCents(d);
  }
  return fromCents(total);
}

export function patrimonioLiquido(ds: Dataset): number {
  const cents =
    toCents(saldoTotalContas(ds)) +
    somaCents(ds.investimentos.map((i) => i.valorAtual)) +
    somaCents(ds.metas.map((m) => m.valorGuardado)) -
    toCents(totalFaturasEmAberto(ds));
  return fromCents(cents);
}

// ---------------------------------------------------------------- R15

export type StatusPrevisao = "SEGURO" | "ATENCAO" | "RISCO";

export interface Previsao {
  saldoAtual: number;
  saldoLivrePrevisto: number;
  margem: number;
  status: StatusPrevisao;
  rotulo: string;
}

export function statusPrevisao(margem: number): StatusPrevisao {
  if (margem >= 0.4) return "SEGURO";
  if (margem >= 0.05) return "ATENCAO";
  return "RISCO";
}

export const ROTULO_PREVISAO: Record<StatusPrevisao, string> = {
  SEGURO: "Mês seguro",
  ATENCAO: "Atenção",
  RISCO: "Risco",
};

export function previsaoMes(ds: Dataset, agora: number): Previsao {
  const { mes, ano } = mesAnoDe(agora);
  const fim = fimDoMes(mes, ano);
  const saldoAtual = saldoTotalContas(ds);
  let cents = toCents(saldoAtual);
  for (const d of ds.despesas) {
    if (d.natureza !== "NORMAL" || d.pago || cartaoIdDe(d) !== null || d.data > fim) continue;
    cents += assinadoCents(d);
  }
  for (const f of faturasPendentes(ds.cartoes, ds.despesas)) {
    if (f.vencimento <= fim && f.total > 0) cents -= toCents(f.total);
  }
  const saldoLivrePrevisto = fromCents(cents);
  const margem = saldoAtual > 0 ? saldoLivrePrevisto / saldoAtual : saldoLivrePrevisto > 0 ? 1 : 0;
  const status = statusPrevisao(margem);
  return { saldoAtual, saldoLivrePrevisto, margem, status, rotulo: ROTULO_PREVISAO[status] };
}

// ---------------------------------------------------------------- R17

export type StatusSaude = "SAUDAVEL" | "ALERTA" | "PERIGO";

export interface Saude {
  consumo: number;
  status: StatusSaude;
  variacaoGastos: number;
}

export function saudeFinanceira(receitas: number, despesas: number, despesasAnt: number): Saude {
  const consumo = receitas > 0 ? despesas / receitas : despesas > 0 ? 1 : 0;
  const status: StatusSaude = consumo >= 0.9 ? "PERIGO" : consumo >= 0.7 ? "ALERTA" : "SAUDAVEL";
  const variacaoGastos = despesasAnt > 0 ? ((despesas - despesasAnt) / despesasAnt) * 100 : 0;
  return { consumo, status, variacaoGastos };
}

// ---------------------------------------------------------------- Investimentos

export function rendimentoReal(i: Pick<Investimento, "valorAtual" | "valorInvestido">): number {
  return fromCents(toCents(i.valorAtual) - toCents(i.valorInvestido));
}

export function rentabilidadePercentual(i: Pick<Investimento, "valorAtual" | "valorInvestido">): number {
  return i.valorInvestido > 0 ? ((i.valorAtual - i.valorInvestido) / i.valorInvestido) * 100 : 0;
}

export function diversificacaoPorTipo(invs: readonly Investimento[]): { tipo: string; valor: number }[] {
  const mapa = new Map<string, number>();
  for (const i of invs) mapa.set(i.tipo, (mapa.get(i.tipo) ?? 0) + toCents(i.valorAtual));
  return [...mapa.entries()].map(([tipo, c]) => ({ tipo, valor: fromCents(c) })).sort((a, b) => b.valor - a.valor);
}

// ---------------------------------------------------------------- Outros

export function estaAtrasada(d: Despesa, agora: number): boolean {
  return !d.pago && cartaoIdDe(d) === null && d.data < inicioDoDia(agora);
}

// ---------------------------------------------------------------- recalcularTudo

/** Atualiza caches `contas.saldo` e `cartoes.limiteDisponivel`, preservando referencias inalteradas. */
export function recalcularTudo(ds: Dataset): Dataset {
  let mudou = false;
  const contas = ds.contas.map((c) => {
    const saldo = saldoConta(ds.despesas, c.conta);
    if (saldo === c.saldo) return c;
    mudou = true;
    return { ...c, saldo };
  });
  const cartoes = ds.cartoes.map((c) => {
    const limiteDisponivel = limiteGrupo(c, ds.cartoes, ds.despesas);
    if (limiteDisponivel === c.limiteDisponivel) return c;
    mudou = true;
    return { ...c, limiteDisponivel };
  });
  return mudou ? { ...ds, contas, cartoes } : ds;
}

// ------------------------------------------------------------ R42b vínculo da compra no débito

export const PREFIXO_DEBITO = "debito:";

/** R42b - `grupoId` de uma compra no débito feita com o cartão (físico ou virtual) `cartaoId`. */
export function grupoIdDebito(cartaoId: number): string {
  return `${PREFIXO_DEBITO}${cartaoId}`;
}

/** R42b - id do cartão de uma compra no débito (`grupoId = "debito:<id>"`), ou null. */
export function cartaoDeDebito(d: Pick<Despesa, "grupoId">): number | null {
  const g = d.grupoId;
  if (!g || !g.startsWith(PREFIXO_DEBITO)) return null;
  const resto = g.slice(PREFIXO_DEBITO.length);
  if (!/^\d+$/.test(resto)) return null;
  const id = Number(resto);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

/**
 * R42b - compras no débito do grupo de cartões (físico + virtuais) na data civil do lançamento (mes/ano).
 * São lançamentos da conta (cartaoId nulo): NÃO entram em fatura, limite nem em "Pagar fatura".
 * `somenteCartaoId` restringe a um cartão do grupo. Mais recentes primeiro.
 */
export function debitosDoCartao(
  cartoesDoGrupo: readonly Pick<Cartao, "id">[],
  despesas: readonly Despesa[],
  mes: number,
  ano: number,
  somenteCartaoId: number | null = null,
): Despesa[] {
  const ids = new Set(cartoesDoGrupo.map((c) => c.id));
  return despesas
    .filter((d) => {
      if (d.natureza !== "NORMAL" || cartaoIdDe(d) !== null) return false;
      const cid = cartaoDeDebito(d);
      if (cid === null || !ids.has(cid)) return false;
      if (somenteCartaoId !== null && cid !== somenteCartaoId) return false;
      const m = mesAnoDe(d.data);
      return m.mes === mes && m.ano === ano;
    })
    .sort((a, b) => b.data - a.data || b.id - a.id);
}

/** R42b - soma (centavos, DEBITO − CREDITO, em valor positivo = gasto líquido) das compras no débito. */
export function totalDebitos(itens: readonly Despesa[]): number {
  return fromCents(itens.reduce((s, d) => s - assinadoCents(d), 0));
}

/** `grupoId` que representa parcelamento (único que habilita "excluir todas as parcelas"). */
export function ehGrupoParcelas(d: Pick<Despesa, "grupoId">): boolean {
  return Boolean(d.grupoId?.startsWith("parc:"));
}
