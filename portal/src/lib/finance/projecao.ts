/**
 * R51 (limite diário seguro) e R52 (calendário financeiro) - projeção de caixa em centavos.
 * Espelha `domain/ProjecaoFinanceira.kt` (app): mesmos números nos mesmos cenários.
 * Só cálculo sobre dados existentes; nada é gravado.
 */
import { cartaoIdDe, cartoesDoGrupo, faturaDaCompra, principalDe, vencimentoFatura } from "./calc";
import { clampDia } from "./dates";
import { toCents } from "./money";
import type { Cartao, Dataset, Despesa, DespesaFixa } from "./types";

// ------------------------------------------------------------------ datas (dia serial)

/** Dias desde 1970-01-01 para a data civil (mes 1-12), sem fuso (algoritmo de Hinnant). */
export function diaSerial(ano: number, mes: number, dia: number): number {
  const y = mes <= 2 ? ano - 1 : ano;
  const era = Math.floor(y / 400);
  const yoe = y - era * 400;
  const doy = Math.floor((153 * (mes + (mes > 2 ? -3 : 9)) + 2) / 5) + dia - 1;
  const doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy;
  return era * 146097 + doe - 719468;
}

export interface DataCivil {
  ano: number;
  mes: number;
  dia: number;
}

export function dataDeSerial(serial: number): DataCivil {
  const z = serial + 719468;
  const era = Math.floor(z / 146097);
  const doe = z - era * 146097;
  const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365);
  const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100));
  const mp = Math.floor((5 * doy + 2) / 153);
  const dia = doy - Math.floor((153 * mp + 2) / 5) + 1;
  const mes = mp < 10 ? mp + 3 : mp - 9;
  const ano = yoe + era * 400 + (mes <= 2 ? 1 : 0);
  return { ano, mes, dia };
}

/** Dia serial da data LOCAL de um instante (epoch ms). */
export function serialDeMs(ms: number): number {
  const d = new Date(ms);
  return diaSerial(d.getFullYear(), d.getMonth() + 1, d.getDate());
}

/** Último dia serial do mês (mes 1-12). */
export function ultimoDiaSerial(mes: number, ano: number): number {
  return mes === 12 ? diaSerial(ano + 1, 1, 1) - 1 : diaSerial(ano, mes + 1, 1) - 1;
}

/** 0 = domingo ... 6 = sábado. */
export function diaDaSemana(serial: number): number {
  return (((serial + 4) % 7) + 7) % 7;
}

function ddmm(serial: number): string {
  const { mes, dia } = dataDeSerial(serial);
  return `${String(dia).padStart(2, "0")}/${String(mes).padStart(2, "0")}`;
}

/** "R$ 1.234,56" - escrito à mão (independe de Intl/locale). Negativo vira "-R$ ...". */
export function brlCentavos(centavos: number): string {
  const neg = centavos < 0;
  const abs = Math.abs(centavos);
  const reais = Math.floor(abs / 100);
  const cents = abs % 100;
  const milhar = String(reais).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${neg ? "-" : ""}R$ ${milhar},${String(cents).padStart(2, "0")}`;
}

// ------------------------------------------------------------------ eventos previstos

export type TipoEvento = "DESPESA" | "RECEITA" | "FATURA" | "RECORRENCIA_DESPESA" | "RECORRENCIA_RECEITA";

export interface EventoProjecao {
  /** Dia serial em que o dinheiro se move (pendência vencida é antecipada para hoje). */
  dia: number;
  /** Com sinal: recebimento positivo, pagamento negativo. */
  centavos: number;
  tipo: TipoEvento;
  descricao: string;
  /** Banco da conta ou nome do cartão. */
  origem: string;
  /** Vencimento anterior a hoje e ainda não pago. */
  atrasado: boolean;
  /** Id do lançamento real (pendências); null em fatura e recorrência ainda não lançada. */
  lancamentoId: number | null;
  /** Cartão principal da fatura; null nos demais. */
  cartaoId: number | null;
}

export interface GastoHoje {
  /** Compra positiva, estorno negativo. */
  centavos: number;
  /** null = saiu da conta; senão, dia serial do vencimento da fatura em que a compra caiu. */
  vencimentoFatura: number | null;
}

export interface OpcoesProjecao {
  /** Só estas contas (número em `contas.conta`); null/ausente = todas. */
  contas?: readonly string[] | null;
  /** Último dia serial para o qual gerar ocorrências de recorrência. */
  ate: number;
}

const ORDEM_TIPO: Record<TipoEvento, number> = {
  RECEITA: 0,
  RECORRENCIA_RECEITA: 1,
  DESPESA: 2,
  RECORRENCIA_DESPESA: 3,
  FATURA: 4,
};

export function ordenarEventos(eventos: EventoProjecao[]): EventoProjecao[] {
  return [...eventos].sort(
    (a, b) =>
      a.dia - b.dia ||
      ORDEM_TIPO[a.tipo] - ORDEM_TIPO[b.tipo] ||
      (a.descricao < b.descricao ? -1 : a.descricao > b.descricao ? 1 : 0) ||
      a.centavos - b.centavos,
  );
}

function assinado(d: Despesa): number {
  return d.tipo === "CREDITO" ? toCents(d.valor) : -toCents(d.valor);
}

/** R47 (projeção): a ocorrência de `dataMs` está pausada? */
function ocorrenciaPausada(regra: DespesaFixa, dataMs: number, agora: number): boolean {
  if (!regra.pausada) return false;
  return regra.pausadaAte == null || dataMs < regra.pausadaAte || dataMs <= agora;
}

function ehCredito(cartao: Cartao): boolean {
  const t = cartao.tipo.normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase().trim();
  return t !== "DEBITO";
}

function numeroDaConta(ds: Dataset, contaId: number): string | null {
  return ds.contas.find((c) => c.id === contaId)?.conta ?? null;
}

function nomeDoBanco(ds: Dataset, numero: string): string {
  return ds.contas.find((c) => c.conta === numero)?.banco ?? numero;
}

/**
 * Converte os dados em eventos previstos (a partir de hoje):
 * pendências NORMAL em conta, faturas de cartão com valor a pagar (no vencimento) e
 * ocorrências de recorrência ainda não lançadas (exceto pausadas). Pendência/fatura vencida
 * entra em hoje. Compra recorrente no cartão de crédito soma na fatura do ciclo.
 */
export function eventosPrevistos(ds: Dataset, agora: number, op: OpcoesProjecao): EventoProjecao[] {
  const hoje = serialDeMs(agora);
  const filtro = op.contas ? new Set(op.contas) : null;
  const contaOk = (numero: string | null): boolean => filtro === null || (numero !== null && filtro.has(numero));
  const eventos: EventoProjecao[] = [];

  // 1) Pendências em conta (R15: NORMAL, sem cartão, não pagas).
  for (const d of ds.despesas) {
    if (d.natureza !== "NORMAL" || d.pago || cartaoIdDe(d) !== null || !contaOk(d.conta)) continue;
    const dia = serialDeMs(d.data);
    const credito = d.tipo === "CREDITO";
    eventos.push({
      dia: Math.max(dia, hoje),
      centavos: assinado(d),
      tipo: credito ? "RECEITA" : "DESPESA",
      descricao: d.descricao,
      origem: nomeDoBanco(ds, d.conta),
      atrasado: dia < hoje,
      lancamentoId: d.id,
      cartaoId: null,
    });
  }

  // 2) Faturas (uma por grupo de cartão e ciclo) + compras recorrentes no crédito.
  const principais = ds.cartoes.filter((c) => principalDe(c, ds.cartoes) === c);
  const faturas = new Map<string, { cartao: Cartao; mes: number; ano: number; cents: number }>();
  const somaFatura = (cartao: Cartao, dataMs: number, cents: number) => {
    const f = faturaDaCompra(cartao, dataMs);
    const chave = `${cartao.id}:${f.ano}-${f.mes}`;
    const g = faturas.get(chave) ?? { cartao, mes: f.mes, ano: f.ano, cents: 0 };
    g.cents += cents;
    faturas.set(chave, g);
  };
  for (const cartao of principais) {
    const ids = new Set(cartoesDoGrupo(cartao, ds.cartoes).map((c) => c.id));
    for (const d of ds.despesas) {
      const cid = cartaoIdDe(d);
      if (cid === null || !ids.has(cid) || d.pago) continue;
      somaFatura(cartao, d.data, -assinado(d));
    }
  }

  // 3) Recorrências ainda não lançadas.
  const dHoje = new Date(agora);
  const idxHoje = dHoje.getFullYear() * 12 + dHoje.getMonth();
  const dAte = dataDeSerial(op.ate);
  const idxAte = dAte.ano * 12 + dAte.mes - 1;
  const lancados = new Set(ds.despesas.map((d) => d.grupoId).filter((g): g is string => !!g));
  for (const regra of ds.despesasFixas) {
    const ult = regra.ultimaDataLancamento;
    const idxUlt = ult === null ? null : new Date(ult).getFullYear() * 12 + new Date(ult).getMonth();
    const cartaoRegra = regra.cartaoId ? ds.cartoes.find((c) => c.id === regra.cartaoId) : undefined;
    const contaCartao = cartaoRegra ? numeroDaConta(ds, cartaoRegra.contaId) : null;
    const noCredito = !!cartaoRegra && contaCartao !== null && ehCredito(cartaoRegra);
    const numeroConta = cartaoRegra && contaCartao !== null ? contaCartao : regra.conta;
    if (!contaOk(numeroConta)) continue;
    for (let idx = idxHoje; idx <= idxAte; idx++) {
      if (idxUlt !== null && idx <= idxUlt) continue;
      const ano = Math.floor(idx / 12);
      const mes = (idx % 12) + 1;
      const grupoId = `fixa:${regra.id}:${ano}-${String(mes).padStart(2, "0")}`;
      if (lancados.has(grupoId)) continue;
      const dia = clampDia(regra.diaVencimento, mes, ano);
      const dataMs = new Date(ano, mes - 1, dia).getTime();
      if (ocorrenciaPausada(regra, dataMs, agora)) continue;
      const cents = toCents(regra.valor);
      const sinal = regra.tipo === "CREDITO" ? 1 : -1;
      if (noCredito && cartaoRegra) {
        somaFatura(principalDe(cartaoRegra, ds.cartoes), dataMs, -sinal * cents);
        continue;
      }
      eventos.push({
        dia: Math.max(diaSerial(ano, mes, dia), hoje),
        centavos: sinal * cents,
        tipo: regra.tipo === "CREDITO" ? "RECORRENCIA_RECEITA" : "RECORRENCIA_DESPESA",
        descricao: regra.descricao,
        origem: nomeDoBanco(ds, numeroConta),
        atrasado: false,
        lancamentoId: null,
        cartaoId: null,
      });
    }
  }

  for (const g of faturas.values()) {
    if (g.cents <= 0) continue;
    const numero = numeroDaConta(ds, g.cartao.contaId);
    if (!contaOk(numero)) continue;
    const venc = serialDeMs(vencimentoFatura(g.cartao, g.mes, g.ano));
    eventos.push({
      dia: Math.max(venc, hoje),
      centavos: -g.cents,
      tipo: "FATURA",
      descricao: `Fatura ${g.cartao.nome}`,
      origem: g.cartao.nome,
      atrasado: venc < hoje,
      lancamentoId: null,
      cartaoId: g.cartao.id,
    });
  }
  return ordenarEventos(eventos);
}

/** Saldo atual (centavos) das contas consideradas - R3. */
export function saldoCentavos(ds: Dataset, contas?: readonly string[] | null): number {
  const filtro = contas ? new Set(contas) : null;
  let total = 0;
  for (const d of ds.despesas) {
    if (!d.pago || cartaoIdDe(d) !== null) continue;
    if (filtro !== null && !filtro.has(d.conta)) continue;
    if (!ds.contas.some((c) => c.conta === d.conta)) continue;
    total += assinado(d);
  }
  return total;
}

/** Gasto de hoje (NORMAL, débito em conta ou compra no cartão, líquido de estornos de cartão). */
export function gastosDeHoje(ds: Dataset, agora: number, contas?: readonly string[] | null): GastoHoje[] {
  const hoje = serialDeMs(agora);
  const filtro = contas ? new Set(contas) : null;
  const out: GastoHoje[] = [];
  for (const d of ds.despesas) {
    if (d.natureza !== "NORMAL" || serialDeMs(d.data) !== hoje) continue;
    const cid = cartaoIdDe(d);
    if (cid === null) {
      if (d.tipo !== "DEBITO") continue;
      if (filtro !== null && !filtro.has(d.conta)) continue;
      out.push({ centavos: toCents(d.valor), vencimentoFatura: null });
      continue;
    }
    const cartao = ds.cartoes.find((c) => c.id === cid);
    if (!cartao) continue;
    const principal = principalDe(cartao, ds.cartoes);
    const numero = numeroDaConta(ds, principal.contaId);
    if (filtro !== null && (numero === null || !filtro.has(numero))) continue;
    const f = faturaDaCompra(principal, d.data);
    out.push({ centavos: -assinado(d), vencimentoFatura: Math.max(serialDeMs(vencimentoFatura(principal, f.mes, f.ano)), hoje) });
  }
  return out;
}

// ------------------------------------------------------------------ R51

export type StatusLimite = "VERDE" | "AMARELO" | "VERMELHO";

export interface EntradaLimiteDiario {
  saldoCentavos: number;
  eventos: readonly EventoProjecao[];
  hoje: number;
  /** Último dia serial do mês corrente. */
  fimDoMes: number;
  /** Período termina no dia anterior à próxima receita prevista (se houver). */
  ateProximaReceita: boolean;
  gastosHoje: readonly GastoHoje[];
}

export interface ResultadoLimiteDiario {
  fimPeriodo: number;
  diasRestantes: number;
  receitasPrevistasCentavos: number;
  despesasPrevistasCentavos: number;
  disponivelCentavos: number;
  limiteDiarioCentavos: number;
  gastoHojeCentavos: number;
  restanteHojeCentavos: number;
  status: StatusLimite;
  mesNoVermelho: boolean;
  faltaCentavos: number;
  mensagem: string;
}

/** R51 - quanto posso gastar hoje (tudo em centavos inteiros). */
export function calcularLimiteDiario(e: EntradaLimiteDiario): ResultadoLimiteDiario {
  let fim = e.fimDoMes;
  if (e.ateProximaReceita) {
    let proxima: number | null = null;
    for (const ev of e.eventos) {
      const receita = ev.tipo === "RECEITA" || ev.tipo === "RECORRENCIA_RECEITA";
      if (receita && ev.centavos > 0 && ev.dia > e.hoje && (proxima === null || ev.dia < proxima)) proxima = ev.dia;
    }
    if (proxima !== null) fim = proxima - 1;
  }
  if (fim < e.hoje) fim = e.hoje;

  let receitas = 0;
  let despesas = 0;
  for (const ev of e.eventos) {
    if (ev.dia > fim) continue;
    if (ev.centavos >= 0) receitas += ev.centavos;
    else despesas += -ev.centavos;
  }
  const disponivel = e.saldoCentavos + receitas - despesas;

  let gasto = 0;
  let jaDeduzido = 0;
  for (const g of e.gastosHoje) {
    gasto += g.centavos;
    if (g.vencimentoFatura === null || g.vencimentoFatura <= fim) jaDeduzido += g.centavos;
  }
  gasto = Math.max(0, gasto);
  jaDeduzido = Math.max(0, jaDeduzido);

  const dias = fim - e.hoje + 1;
  const base = disponivel + jaDeduzido;
  const limite = Math.floor(Math.max(0, base) / dias);
  const restante = limite - gasto;
  const mesNoVermelho = disponivel <= 0;
  const falta = Math.max(0, -disponivel);

  let status: StatusLimite;
  if (mesNoVermelho || gasto > limite) status = "VERMELHO";
  else if (limite > 0 && gasto * 10 >= limite * 8) status = "AMARELO";
  else status = "VERDE";

  let mensagem: string;
  if (mesNoVermelho) {
    mensagem =
      falta > 0
        ? `Mês no vermelho: faltam ${brlCentavos(falta)} para cobrir tudo até ${ddmm(fim)}.`
        : `Mês no vermelho: não sobra nada para gastar até ${ddmm(fim)}.`;
  } else if (status === "VERMELHO") {
    mensagem = `Hoje você já passou do limite em ${brlCentavos(gasto - limite)}.`;
  } else if (status === "AMARELO") {
    mensagem = `Atenção: restam ${brlCentavos(restante)} do limite de hoje.`;
  } else {
    mensagem = `Hoje você pode gastar até ${brlCentavos(restante)}.`;
  }

  return {
    fimPeriodo: fim,
    diasRestantes: dias,
    receitasPrevistasCentavos: receitas,
    despesasPrevistasCentavos: despesas,
    disponivelCentavos: disponivel,
    limiteDiarioCentavos: limite,
    gastoHojeCentavos: gasto,
    restanteHojeCentavos: restante,
    status,
    mesNoVermelho,
    faltaCentavos: falta,
    mensagem,
  };
}

export interface OpcoesLimiteDiario {
  ateProximaReceita?: boolean;
  contas?: readonly string[] | null;
}

/** R51 sobre o dataset completo. */
export function limiteDiarioDoDataset(ds: Dataset, agora: number, op: OpcoesLimiteDiario = {}): ResultadoLimiteDiario {
  const hoje = serialDeMs(agora);
  const d = new Date(agora);
  const fimMes = ultimoDiaSerial(d.getMonth() + 1, d.getFullYear());
  // Horizonte: fim do mês seguinte (a próxima receita pode estar lá).
  const ate = ultimoDiaSerial(d.getMonth() === 11 ? 1 : d.getMonth() + 2, d.getMonth() === 11 ? d.getFullYear() + 1 : d.getFullYear());
  return calcularLimiteDiario({
    saldoCentavos: saldoCentavos(ds, op.contas),
    eventos: eventosPrevistos(ds, agora, { contas: op.contas, ate }),
    hoje,
    fimDoMes: fimMes,
    ateProximaReceita: !!op.ateProximaReceita,
    gastosHoje: gastosDeHoje(ds, agora, op.contas),
  });
}

// ------------------------------------------------------------------ R52

export interface DiaCalendario {
  /** Dia serial. */
  dia: number;
  ano: number;
  mes: number;
  diaDoMes: number;
  /** 0 = domingo. */
  diaDaSemana: number;
  eventos: EventoProjecao[];
  entradasCentavos: number;
  /** Positivo. */
  saidasCentavos: number;
  /** Saldo ao fim do dia; null em dias que já passaram (sem projeção). */
  saldoProjetadoCentavos: number | null;
  negativo: boolean;
  ehHoje: boolean;
  passado: boolean;
}

export interface ResultadoCalendario {
  mes: number;
  ano: number;
  dias: DiaCalendario[];
  menorSaldo: { centavos: number; dia: number } | null;
  primeiroDiaNegativo: number | null;
  /** Próximo recebimento previsto depois do primeiro dia negativo (dia serial). */
  proximoRecebimentoApos: number | null;
  alerta: string | null;
  saldoFimDoMesCentavos: number | null;
}

export interface EntradaCalendario {
  saldoCentavos: number;
  eventos: readonly EventoProjecao[];
  hoje: number;
  mes: number;
  ano: number;
}

/** R52 - saldo projetado ao fim de cada dia do mês (saldo atual + movimentos previstos acumulados). */
export function projetarMes(e: EntradaCalendario): ResultadoCalendario {
  const primeiro = diaSerial(e.ano, e.mes, 1);
  const ultimo = ultimoDiaSerial(e.mes, e.ano);
  const inicio = Math.max(e.hoje, primeiro);
  let corrente = e.saldoCentavos;
  for (const ev of e.eventos) if (ev.dia < inicio) corrente += ev.centavos;

  const dias: DiaCalendario[] = [];
  let menor: { centavos: number; dia: number } | null = null;
  let primeiroNeg: number | null = null;
  for (let s = primeiro; s <= ultimo; s++) {
    const evs = e.eventos.filter((ev) => ev.dia === s);
    let ent = 0;
    let sai = 0;
    for (const ev of evs) {
      if (ev.centavos >= 0) ent += ev.centavos;
      else sai += -ev.centavos;
    }
    let saldo: number | null = null;
    if (s >= inicio) {
      corrente += ent - sai;
      saldo = corrente;
      if (menor === null || saldo < menor.centavos) menor = { centavos: saldo, dia: s };
      if (saldo < 0 && primeiroNeg === null) primeiroNeg = s;
    }
    const c = dataDeSerial(s);
    dias.push({
      dia: s,
      ano: c.ano,
      mes: c.mes,
      diaDoMes: c.dia,
      diaDaSemana: diaDaSemana(s),
      eventos: evs,
      entradasCentavos: ent,
      saidasCentavos: sai,
      saldoProjetadoCentavos: saldo,
      negativo: saldo !== null && saldo < 0,
      ehHoje: s === e.hoje,
      passado: s < e.hoje,
    });
  }

  let proximoRec: number | null = null;
  let alerta: string | null = null;
  if (primeiroNeg !== null) {
    for (const ev of e.eventos) {
      const receita = ev.tipo === "RECEITA" || ev.tipo === "RECORRENCIA_RECEITA";
      if (receita && ev.centavos > 0 && ev.dia > primeiroNeg && (proximoRec === null || ev.dia < proximoRec)) proximoRec = ev.dia;
    }
    alerta =
      proximoRec !== null
        ? `Conta ficará negativa em ${ddmm(primeiroNeg)} antes do próximo recebimento.`
        : `Conta ficará negativa em ${ddmm(primeiroNeg)} e não há recebimento previsto.`;
  }
  const fim = dias[dias.length - 1];
  return {
    mes: e.mes,
    ano: e.ano,
    dias,
    menorSaldo: menor,
    primeiroDiaNegativo: primeiroNeg,
    proximoRecebimentoApos: proximoRec,
    alerta,
    saldoFimDoMesCentavos: fim ? fim.saldoProjetadoCentavos : null,
  };
}

/** R52 sobre o dataset completo (mês/ano = mês exibido). */
export function calendarioDoDataset(
  ds: Dataset,
  agora: number,
  mes: number,
  ano: number,
  contas?: readonly string[] | null,
): ResultadoCalendario {
  const prox = mes === 12 ? { m: 1, a: ano + 1 } : { m: mes + 1, a: ano };
  const ate = ultimoDiaSerial(prox.m, prox.a);
  return projetarMes({
    saldoCentavos: saldoCentavos(ds, contas),
    eventos: eventosPrevistos(ds, agora, { contas, ate }),
    hoje: serialDeMs(agora),
    mes,
    ano,
  });
}
