/**
 * R58 - quitação de dívidas (app e portal, mesmas regras e mesmos números nos testes).
 *
 * Tudo em CENTAVOS inteiros na lógica; o armazenamento segue o padrão (reais com 2 casas, `round2`).
 * Juros compostos mensais: `juros = saldo × taxa`, arredondado meio para cima a cada mês, em centavos inteiros
 * (a taxa vira "centésimos de ponto percentual": 2,5% a.m. = 250). Dívida e simulação NÃO mexem em saldo de conta:
 * o pagamento é um lançamento comum (despesa) e o saldo devedor é um controle próprio.
 */
import { cartaoIdDe, recalcularTudo } from "./calc";
import { brlCentavos } from "./centavos";
import { inserirItem, atualizarItem, montaDespesa, type Ctx } from "./operations";
import { novoIdLong } from "./ids";
import { fromCents, round2, toCents } from "./money";
import type { Dataset, Despesa, Divida, Result, TipoDivida } from "./types";

export const TIPOS_DIVIDA: readonly TipoDivida[] = [
  "EMPRESTIMO",
  "FINANCIAMENTO",
  "CHEQUE_ESPECIAL",
  "CARTAO_ROTATIVO",
  "CONSIGNADO",
  "OUTRA",
];

export const ROTULO_TIPO_DIVIDA: Record<TipoDivida, string> = {
  EMPRESTIMO: "Empréstimo",
  FINANCIAMENTO: "Financiamento",
  CHEQUE_ESPECIAL: "Cheque especial",
  CARTAO_ROTATIVO: "Cartão (rotativo)",
  CONSIGNADO: "Consignado",
  OUTRA: "Outra",
};

export const CATEGORIA_DIVIDAS = "Dívidas";
export const MAX_MESES = 600;
/**
 * Proteção: saldo total acima disto (R$ 1 bilhão) = juros explodindo, a dívida nunca é quitada. Com taxa <= 100% a.m. e este
 * limite, `saldo × taxaBp × 2` cabe em 2^53 (mesmos números em JS e Kotlin).
 */
export const LIMITE_SALDO_CENTAVOS = 100_000_000_000;
export const TAXA_MAXIMA_PERCENTUAL = 100;
export const SALDO_MAXIMO_REAIS = 1_000_000_000;

export const grupoIdDivida = (id: number) => `divida:${id}`;

// ------------------------------------------------------------ simulador (puro)

export type Estrategia = "MINIMOS" | "BOLA_DE_NEVE" | "AVALANCHE";

export interface DividaSim {
  id: number;
  nome: string;
  /** centavos */
  saldo: number;
  /** centésimos de ponto percentual ao mês (2,5% = 250) */
  taxaBp: number;
  /** pagamento mínimo mensal, centavos */
  minimo: number;
}

export interface MesSim {
  /** 1 = primeiro mês (o mês de referência) */
  mes: number;
  juros: number;
  pago: number;
  saldoFinal: number;
}

export interface QuitacaoSim {
  id: number;
  nome: string;
  mes: number;
}

export interface ResultadoSim {
  estrategia: Estrategia;
  /** meses até quitar tudo; null = nunca quita (limite de 600 meses) */
  meses: number | null;
  jurosTotal: number;
  pagoTotal: number;
  saldoRestante: number;
  nuncaQuita: boolean;
  cronograma: MesSim[];
  quitacoes: QuitacaoSim[];
}

/** Juros do mês em centavos: meio para cima, só com inteiros. */
export function jurosDoMes(saldo: number, taxaBp: number): number {
  if (saldo <= 0 || taxaBp <= 0) return 0;
  return Math.floor((saldo * taxaBp * 2 + 10000) / 20000);
}

/** Converte a dívida cadastrada (reais, % a.m.) para a entrada do simulador; inativa ou quitada = null. */
export function paraSim(d: Divida): DividaSim | null {
  const saldo = toCents(d.saldoDevedor);
  if (!d.ativa || saldo <= 0) return null;
  const taxa = d.taxaJurosMensal != null && Number.isFinite(d.taxaJurosMensal) && d.taxaJurosMensal > 0 ? d.taxaJurosMensal : 0;
  return {
    id: d.id,
    nome: d.nome,
    saldo,
    taxaBp: Math.round(Math.min(taxa, TAXA_MAXIMA_PERCENTUAL) * 100),
    minimo: d.parcelaMinima != null && d.parcelaMinima > 0 ? toCents(d.parcelaMinima) : 0,
  };
}

function prioridade(estrategia: Estrategia): (a: DividaSim, b: DividaSim) => number {
  if (estrategia === "AVALANCHE") return (a, b) => b.taxaBp - a.taxaBp || a.saldo - b.saldo || a.id - b.id;
  return (a, b) => a.saldo - b.saldo || b.taxaBp - a.taxaBp || a.id - b.id;
}

/**
 * Cronograma mês a mês. `MINIMOS` paga só o mínimo de cada dívida (sem repassar o que sobra de dívidas quitadas).
 * `BOLA_DE_NEVE` (menor saldo primeiro) e `AVALANCHE` (maior taxa primeiro) usam orçamento fixo = soma dos mínimos
 * iniciais + `extra`: cada dívida recebe o mínimo e o resto vai, em ordem de prioridade, para as demais (o mínimo
 * liberado por uma dívida quitada é repassado). Último pagamento da dívida é ajustado ao saldo.
 */
export function simular(dividas: readonly DividaSim[], extra: number, estrategia: Estrategia): ResultadoSim {
  const itens = dividas.filter((d) => d.saldo > 0).map((d) => ({ ...d }));
  const extraOk = Math.max(0, Math.trunc(extra));
  const orcamento = estrategia === "MINIMOS" ? 0 : itens.reduce((s, d) => s + d.minimo, 0) + extraOk;
  const cmp = prioridade(estrategia);
  const cronograma: MesSim[] = [];
  const quitacoes: QuitacaoSim[] = [];
  let jurosTotal = 0;
  let pagoTotal = 0;
  let meses: number | null = itens.length === 0 ? 0 : null;
  const soma = () => itens.reduce((s, d) => s + d.saldo, 0);

  for (let mes = 1; mes <= MAX_MESES && itens.length > 0; mes++) {
    const antes = new Set(itens.filter((d) => d.saldo > 0).map((d) => d.id));
    let jurosMes = 0;
    for (const d of itens) {
      if (d.saldo <= 0) continue;
      const j = jurosDoMes(d.saldo, d.taxaBp);
      d.saldo += j;
      jurosMes += j;
    }
    let pagoMes = 0;
    if (estrategia === "MINIMOS") {
      for (const d of itens) {
        if (d.saldo <= 0) continue;
        const p = Math.min(d.minimo, d.saldo);
        d.saldo -= p;
        pagoMes += p;
      }
    } else {
      let disp = orcamento;
      for (const d of itens) {
        if (d.saldo <= 0) continue;
        const p = Math.min(d.minimo, d.saldo, disp);
        d.saldo -= p;
        disp -= p;
        pagoMes += p;
      }
      const ordem = itens.filter((d) => d.saldo > 0).sort(cmp);
      for (const d of ordem) {
        if (disp <= 0) break;
        const p = Math.min(d.saldo, disp);
        d.saldo -= p;
        disp -= p;
        pagoMes += p;
      }
    }
    for (const d of itens) if (antes.has(d.id) && d.saldo <= 0) quitacoes.push({ id: d.id, nome: d.nome, mes });
    const saldoFinal = soma();
    jurosTotal += jurosMes;
    pagoTotal += pagoMes;
    cronograma.push({ mes, juros: jurosMes, pago: pagoMes, saldoFinal });
    if (saldoFinal <= 0) {
      meses = mes;
      break;
    }
    if (saldoFinal > LIMITE_SALDO_CENTAVOS) break;
  }

  const saldoRestante = soma();
  const nuncaQuita = meses === null;
  return { estrategia, meses, jurosTotal, pagoTotal, saldoRestante: nuncaQuita ? saldoRestante : 0, nuncaQuita, cronograma, quitacoes };
}

/** Mês/ano do último pagamento: o 1º pagamento acontece no mês de referência (`mes` 1-12). */
export function dataQuitacao(ref: { ano: number; mes: number }, meses: number | null): { ano: number; mes: number } | null {
  if (meses === null || meses <= 0) return null;
  const total = ref.ano * 12 + (ref.mes - 1) + (meses - 1);
  return { ano: Math.floor(total / 12), mes: (total % 12) + 1 };
}

export interface Comparativo {
  minimos: ResultadoSim;
  bolaDeNeve: ResultadoSim;
  avalanche: ResultadoSim;
  /** juros economizados (centavos) entre estratégias; null quando alguma delas nunca quita */
  economia: { bolaVsMinimos: number | null; avalancheVsMinimos: number | null; avalancheVsBola: number | null };
  avisos: string[];
}

export function compararEstrategias(dividas: readonly DividaSim[], extra: number): Comparativo {
  const minimos = simular(dividas, extra, "MINIMOS");
  const bolaDeNeve = simular(dividas, extra, "BOLA_DE_NEVE");
  const avalanche = simular(dividas, extra, "AVALANCHE");
  const dif = (a: ResultadoSim, b: ResultadoSim) => (a.nuncaQuita || b.nuncaQuita ? null : a.jurosTotal - b.jurosTotal);
  const avisos: string[] = [];
  for (const d of dividas) {
    if (d.saldo <= 0) continue;
    if (d.minimo <= 0) {
      avisos.push(`${d.nome}: sem pagamento mínimo informado.`);
    } else {
      const j = jurosDoMes(d.saldo, d.taxaBp);
      if (d.minimo <= j) avisos.push(`${d.nome}: o pagamento mínimo (${brlCentavos(d.minimo)}) não supera os juros do mês (${brlCentavos(j)}).`);
    }
  }
  if (minimos.nuncaQuita && dividas.some((d) => d.saldo > 0)) avisos.push(`Pagando só os mínimos, as dívidas nunca são quitadas (limite de ${MAX_MESES} meses).`);
  if (bolaDeNeve.nuncaQuita || avalanche.nuncaQuita) avisos.push(`Mesmo com o valor extra, o pagamento não cobre os juros: não quita em ${MAX_MESES} meses.`);
  return {
    minimos,
    bolaDeNeve,
    avalanche,
    economia: {
      bolaVsMinimos: dif(minimos, bolaDeNeve),
      avalancheVsMinimos: dif(minimos, avalanche),
      avalancheVsBola: dif(bolaDeNeve, avalanche),
    },
    avisos,
  };
}

/** Comparativo direto das dívidas cadastradas (ativas e com saldo). */
export function compararDasDividas(dividas: readonly Divida[], extraReais: number): Comparativo {
  const sim = dividas.map(paraSim).filter((d): d is DividaSim => d !== null);
  return compararEstrategias(sim, toCents(Math.max(0, extraReais)));
}

// ------------------------------------------------------------ pagamento / juros (puro, em centavos)

export interface ResultadoPagamento {
  ok: true;
  /** saldo devedor depois dos juros e do pagamento */
  saldo: number;
}

/** Saldo = saldo + juros − pago; recusa pagamento maior que o devido. */
export function aplicarPagamento(saldo: number, juros: number, pago: number): ResultadoPagamento | { ok: false; erro: string } {
  if (!(pago > 0)) return { ok: false, erro: "O valor do pagamento deve ser maior que zero." };
  if (juros < 0) return { ok: false, erro: "Os juros não podem ser negativos." };
  const devido = saldo + juros;
  if (pago > devido) return { ok: false, erro: `O pagamento passa do saldo devedor (${brlCentavos(devido)}).` };
  return { ok: true, saldo: devido - pago };
}

/** Progresso de quitação 0..1: pago ÷ max(original, saldo). */
export function progressoQuitacao(d: Pick<Divida, "saldoDevedor" | "valorOriginal">): number {
  const saldo = toCents(d.saldoDevedor);
  const base = Math.max(d.valorOriginal != null ? toCents(d.valorOriginal) : saldo, saldo);
  if (base <= 0) return 1;
  return Math.max(0, Math.min(1, (base - saldo) / base));
}

// ------------------------------------------------------------ operações sobre o dataset

type Op<T extends object = object> = Result<{ ds: Dataset } & T>;
const erro = (m: string) => ({ ok: false as const, erro: m });

export interface DadosDivida {
  nome: string;
  tipo: TipoDivida;
  credor?: string | null;
  saldoDevedor: number;
  valorOriginal?: number | null;
  taxaJurosMensal?: number | null;
  parcelaMinima?: number | null;
  parcelasRestantes?: number | null;
  diaVencimento?: number | null;
  conta?: string | null;
  ativa: boolean;
}

export interface EntradaEmAndamento {
  /** valor contratado (reais) */
  valorOriginal: number;
  parcelasTotal: number;
  parcelasPagas: number;
  /** % ao mês; 0/ausente = sem juros */
  taxaMensal?: number | null;
  /** parcela mensal (reais); ausente = calculada (tabela Price, ou valor ÷ parcelas sem juros) */
  parcela?: number | null;
}

export interface EstimativaEmAndamento {
  parcela: number;
  parcelasRestantes: number;
  saldoDevedor: number;
}

/**
 * Dívida contratada antes e já em andamento (ex.: empréstimo): estima o saldo devedor atual como o valor presente das
 * parcelas que faltam, à taxa do contrato. É só uma ajuda de preenchimento (o usuário pode ajustar o saldo ao do extrato do credor).
 */
export function estimarDividaEmAndamento(e: EntradaEmAndamento): EstimativaEmAndamento | string {
  if (!Number.isFinite(e.valorOriginal) || e.valorOriginal <= 0) return "Informe o valor original.";
  if (!Number.isInteger(e.parcelasTotal) || e.parcelasTotal <= 0) return "Informe o total de parcelas.";
  if (!Number.isInteger(e.parcelasPagas) || e.parcelasPagas < 0 || e.parcelasPagas > e.parcelasTotal) return "As parcelas pagas devem ficar entre 0 e o total.";
  const taxa = e.taxaMensal != null && Number.isFinite(e.taxaMensal) && e.taxaMensal > 0 ? Math.min(e.taxaMensal, TAXA_MAXIMA_PERCENTUAL) / 100 : 0;
  const restantes = e.parcelasTotal - e.parcelasPagas;
  let parcela = e.parcela != null && e.parcela > 0 ? e.parcela : 0;
  if (parcela <= 0) parcela = taxa > 0 ? (e.valorOriginal * taxa) / (1 - Math.pow(1 + taxa, -e.parcelasTotal)) : e.valorOriginal / e.parcelasTotal;
  const saldo = restantes === 0 ? 0 : taxa > 0 ? (parcela * (1 - Math.pow(1 + taxa, -restantes))) / taxa : parcela * restantes;
  return { parcela: round2(parcela), parcelasRestantes: restantes, saldoDevedor: round2(saldo) };
}

export function validarDivida(d: DadosDivida): string | null {
  if (!d.nome.trim()) return "Informe o nome da dívida.";
  if (!TIPOS_DIVIDA.includes(d.tipo)) return "Tipo de dívida inválido.";
  if (!Number.isFinite(d.saldoDevedor) || d.saldoDevedor < 0 || d.saldoDevedor > SALDO_MAXIMO_REAIS) return "Saldo devedor inválido.";
  if (d.taxaJurosMensal != null && (!Number.isFinite(d.taxaJurosMensal) || d.taxaJurosMensal < 0 || d.taxaJurosMensal > TAXA_MAXIMA_PERCENTUAL)) return "Taxa de juros inválida (0 a 100% ao mês).";
  if (d.parcelaMinima != null && (!Number.isFinite(d.parcelaMinima) || d.parcelaMinima < 0)) return "A parcela não pode ser negativa.";
  if (d.parcelasRestantes != null && (!Number.isInteger(d.parcelasRestantes) || d.parcelasRestantes < 0)) return "Parcelas restantes inválidas.";
  if (d.diaVencimento != null && (!Number.isInteger(d.diaVencimento) || d.diaVencimento < 1 || d.diaVencimento > 31)) return "O dia de vencimento deve estar entre 1 e 31.";
  return null;
}

function limparDivida(d: DadosDivida): Omit<Divida, "id"> {
  const saldo = round2(d.saldoDevedor);
  return {
    nome: d.nome.trim(),
    tipo: d.tipo,
    credor: d.credor?.trim() ? d.credor.trim() : null,
    saldoDevedor: saldo,
    valorOriginal: d.valorOriginal != null && d.valorOriginal > 0 ? round2(d.valorOriginal) : saldo,
    taxaJurosMensal: d.taxaJurosMensal != null && d.taxaJurosMensal > 0 ? d.taxaJurosMensal : 0,
    parcelaMinima: d.parcelaMinima != null && d.parcelaMinima > 0 ? round2(d.parcelaMinima) : null,
    parcelasRestantes: d.parcelasRestantes ?? null,
    diaVencimento: d.diaVencimento ?? null,
    conta: d.conta?.trim() ? d.conta.trim() : null,
    ativa: d.ativa,
  };
}

/** Cria ou edita a dívida (`id` informado = edição). */
export function salvarDivida(ds: Dataset, dados: DadosDivida, id: number | undefined, ctx: Ctx): Op<{ divida: Divida }> {
  const msg = validarDivida(dados);
  if (msg) return erro(msg);
  const limpo = limparDivida(dados);
  if (id !== undefined) {
    const atual = ds.dividas.find((x) => x.id === id);
    if (!atual) return erro("Dívida não encontrada.");
    // edição: o valor original só muda se informado; senão preserva o já gravado
    const patch = { ...limpo, valorOriginal: dados.valorOriginal != null && dados.valorOriginal > 0 ? limpo.valorOriginal : (atual.valorOriginal ?? limpo.valorOriginal) };
    const dividas = atualizarItem(ds.dividas, id, patch);
    return { ok: true, ds: { ...ds, dividas }, divida: dividas.find((x) => x.id === id) as Divida };
  }
  const { lista, item } = inserirItem(ds.dividas, limpo, ctx);
  return { ok: true, ds: { ...ds, dividas: lista }, divida: item };
}

export function excluirDivida(ds: Dataset, id: number): Op {
  if (!ds.dividas.some((x) => x.id === id)) return erro("Dívida não encontrada.");
  return { ok: true, ds: { ...ds, dividas: ds.dividas.filter((x) => x.id !== id) } };
}

/** Juros do mês (informados pelo usuário) acrescentam ao saldo devedor; nada em conta é alterado. */
export function registrarJurosDivida(ds: Dataset, id: number, juros: number): Op {
  const d = ds.dividas.find((x) => x.id === id);
  if (!d) return erro("Dívida não encontrada.");
  if (d.lancamentoIds?.length) return erro("Esta dívida está vinculada a pendências: dê baixa nos lançamentos (tela Pendências) para abater o saldo.");
  const j = toCents(juros);
  if (!(j > 0)) return erro("Os juros devem ser maiores que zero.");
  const saldo = fromCents(toCents(d.saldoDevedor) + j);
  return { ok: true, ds: { ...ds, dividas: atualizarItem(ds.dividas, id, { saldoDevedor: saldo, ativa: true }) } };
}

export interface PagamentoDivida {
  dividaId: number;
  valor: number;
  /** número da conta de onde sai o dinheiro */
  conta: string;
  data?: number;
  /** padrão "Dívidas" */
  categoria?: string;
  /** juros do mês a acrescentar ao saldo devedor ANTES de abater (opcional) */
  juros?: number;
  autor?: string | null;
}

/**
 * Paga a dívida: cria o lançamento NORMAL de despesa (pago) na conta, `grupoId = "divida:<id>"`, e abate o saldo
 * devedor na mesma operação. O saldo da conta já cai pelo lançamento; o saldo devedor é controle próprio.
 * Saldo zero = dívida quitada (`ativa = false`).
 */
export function pagarDivida(ds: Dataset, input: PagamentoDivida, ctx: Ctx): Op<{ pagamento: Despesa; saldoDevedor: number; quitada: boolean }> {
  const d = ds.dividas.find((x) => x.id === input.dividaId);
  if (!d) return erro("Dívida não encontrada.");
  if (d.lancamentoIds?.length) return erro("Esta dívida está vinculada a pendências: dê baixa nos lançamentos (tela Pendências) para abater o saldo.");
  if (!ds.contas.some((c) => c.conta === input.conta)) return erro("Conta não encontrada.");
  const categoria = (input.categoria ?? CATEGORIA_DIVIDAS).trim() || CATEGORIA_DIVIDAS;
  const pago = toCents(input.valor);
  const r = aplicarPagamento(toCents(d.saldoDevedor), toCents(input.juros ?? 0), pago);
  if (!r.ok) return erro(r.erro);
  const data = input.data ?? ctx.agora;
  const id = novoIdLong(ds.despesas.map((x) => x.id), ctx.agora);
  const pic = ds.categorias.find((c) => c.nome.trim().toLowerCase() === categoria.toLowerCase())?.pic;
  const pagamento = montaDespesa(
    {
      descricao: `Pagamento: ${d.nome}`,
      valor: fromCents(pago),
      data,
      categoria,
      conta: input.conta,
      pic,
      tipo: "DEBITO",
      pago: true,
      grupoId: grupoIdDivida(d.id),
      autor: input.autor ?? null,
    },
    id,
  );
  const quitada = r.saldo <= 0;
  const dividas = atualizarItem(ds.dividas, d.id, { saldoDevedor: fromCents(r.saldo), ativa: !quitada });
  return {
    ok: true,
    ds: recalcularTudo({ ...ds, dividas, despesas: [...ds.despesas, pagamento] }),
    pagamento,
    saldoDevedor: fromCents(r.saldo),
    quitada,
  };
}

// ------------------------------------------------------------ R73 dívida vinculada a pendências

/** Pendência que pode virar dívida: despesa NORMAL, não paga, fora de cartão (as mesmas da tela Pendências). */
export function pendenciasVinculaveis(ds: Dataset): Despesa[] {
  return ds.despesas.filter((d) => d.natureza === "NORMAL" && d.tipo === "DEBITO" && !d.pago && cartaoIdDe(d) === null).sort((a, b) => a.data - b.data);
}

/**
 * Saldo devedor, parcelas restantes e parcela seguinte de uma dívida vinculada = o que ainda não foi pago nos lançamentos
 * vinculados (lançamento excluído deixa de contar). Dívida sem vínculo ou sem nada a mudar mantém a MESMA referência.
 */
export function sincronizarDividasVinculadas(ds: Dataset): Dataset {
  if (!ds.dividas.some((d) => d.lancamentoIds && d.lancamentoIds.length > 0)) return ds;
  let mudou = false;
  const dividas = ds.dividas.map((d) => {
    if (!d.lancamentoIds || d.lancamentoIds.length === 0) return d;
    const ids = new Set(d.lancamentoIds);
    const abertos = ds.despesas.filter((x) => ids.has(x.id) && !x.pago).sort((a, b) => a.data - b.data);
    const saldo = fromCents(abertos.reduce((s, x) => s + toCents(x.valor), 0));
    const proxima = abertos.length > 0 ? round2(abertos[0].valor) : (d.parcelaMinima ?? null);
    const ativa = abertos.length > 0;
    if (d.saldoDevedor === saldo && d.parcelasRestantes === abertos.length && (d.parcelaMinima ?? null) === proxima && d.ativa === ativa) return d;
    mudou = true;
    return { ...d, saldoDevedor: saldo, parcelasRestantes: abertos.length, parcelaMinima: proxima, ativa };
  });
  return mudou ? { ...ds, dividas } : ds;
}

function selecionarPendencias(ds: Dataset, ids: readonly number[]): Despesa[] | string {
  const unicos = [...new Set(ids)];
  if (unicos.length === 0) return "Escolha ao menos uma pendência.";
  const ok = new Map(pendenciasVinculaveis(ds).map((d) => [d.id, d]));
  const out: Despesa[] = [];
  for (const id of unicos) {
    const d = ok.get(id);
    if (!d) return "Há pendência escolhida que já foi paga, excluída ou não pode ser vinculada.";
    out.push(d);
  }
  return out;
}

/** Cria a dívida a partir de pendências já lançadas (ex.: parcelas de um empréstimo): o saldo acompanha as baixas. */
export function criarDividaDePendencias(
  ds: Dataset,
  dados: Pick<DadosDivida, "nome" | "tipo" | "credor" | "taxaJurosMensal" | "diaVencimento">,
  ids: readonly number[],
  ctx: Ctx,
): Op<{ divida: Divida }> {
  const sel = selecionarPendencias(ds, ids);
  if (typeof sel === "string") return erro(sel);
  const total = sel.reduce((s, d) => s + toCents(d.valor), 0);
  const base: DadosDivida = { ...dados, saldoDevedor: fromCents(total), valorOriginal: fromCents(total), conta: sel[0].conta, ativa: true };
  const msg = validarDivida(base);
  if (msg) return erro(msg);
  const { lista, item } = inserirItem(ds.dividas, { ...limparDivida(base), lancamentoIds: sel.map((d) => d.id) }, ctx);
  const novo = sincronizarDividasVinculadas({ ...ds, dividas: lista });
  return { ok: true, ds: novo, divida: novo.dividas.find((x) => x.id === item.id) as Divida };
}

/** Soma mais pendências a uma dívida já vinculada ou transforma uma dívida comum em vinculada (o valor original sobe junto). */
export function vincularPendencias(ds: Dataset, dividaId: number, ids: readonly number[]): Op {
  const d = ds.dividas.find((x) => x.id === dividaId);
  if (!d) return erro("Dívida não encontrada.");
  const sel = selecionarPendencias(ds, ids);
  if (typeof sel === "string") return erro(sel);
  const atuais = d.lancamentoIds ?? [];
  const novos = sel.filter((x) => !atuais.includes(x.id));
  const extra = novos.reduce((s, x) => s + toCents(x.valor), 0);
  const dividas = atualizarItem(ds.dividas, dividaId, {
    lancamentoIds: [...atuais, ...novos.map((x) => x.id)],
    valorOriginal: fromCents(toCents(d.valorOriginal ?? d.saldoDevedor) + extra),
  });
  return { ok: true, ds: sincronizarDividasVinculadas({ ...ds, dividas }) };
}

/** Solta o vínculo: o saldo devedor atual fica como está e volta a ser controle próprio. */
export function desvincularDivida(ds: Dataset, dividaId: number): Op {
  const d = ds.dividas.find((x) => x.id === dividaId);
  if (!d) return erro("Dívida não encontrada.");
  const { lancamentoIds: _ids, ...resto } = d;
  void _ids;
  return { ok: true, ds: { ...ds, dividas: ds.dividas.map((x) => (x.id === dividaId ? resto : x)) } };
}
