import { cartoesDoGrupo, cartaoIdDe, comprometimentoCartao, kpisTotal, limiteGrupo, principalDe, recalcularTudo, saldoConta } from "./calc";
import { inicioDoDia } from "./dates";
import { formatBRL } from "../format";
import { fromCents, toCents } from "./money";
import { NATUREZAS, type Cartao, type Conta, type Dataset, type Despesa } from "./types";

/** R45 - conferência de saldos e limites (puro, não altera nada). */

export type ChaveDecomposicao =
  | "SALDO_INICIAL"
  | "RECEITAS"
  | "DESPESAS"
  | "PAGAMENTOS_FATURA"
  | "TRANSFERENCIAS_ENTRADA"
  | "TRANSFERENCIAS_SAIDA"
  | "APORTES_META"
  | "RESGATES_META"
  | "AJUSTES"
  | "OUTROS";

export const ROTULO_DECOMPOSICAO: Record<ChaveDecomposicao, string> = {
  SALDO_INICIAL: "Saldo inicial",
  RECEITAS: "Receitas recebidas",
  DESPESAS: "Despesas pagas",
  PAGAMENTOS_FATURA: "Pagamentos de fatura",
  TRANSFERENCIAS_ENTRADA: "Transferências recebidas",
  TRANSFERENCIAS_SAIDA: "Transferências enviadas",
  APORTES_META: "Aportes em metas",
  RESGATES_META: "Resgates de metas",
  AJUSTES: "Ajustes de saldo",
  OUTROS: "Outros (natureza desconhecida)",
};

const ORDEM: ChaveDecomposicao[] = [
  "SALDO_INICIAL",
  "RECEITAS",
  "DESPESAS",
  "PAGAMENTOS_FATURA",
  "TRANSFERENCIAS_ENTRADA",
  "TRANSFERENCIAS_SAIDA",
  "APORTES_META",
  "RESGATES_META",
  "AJUSTES",
  "OUTROS",
];

export interface LinhaDecomposicao {
  chave: ChaveDecomposicao;
  rotulo: string;
  /** Soma com sinal, em centavos (entradas positivas, saídas negativas). */
  centavos: number;
  qtd: number;
}

export interface PendenciasConta {
  receitasPrevistas: number;
  despesasAtrasadas: number;
  despesasAVencer30d: number;
  despesasFuturas: number;
  comprasCartaoEmAberto: number;
}

export interface ConferenciaConta {
  contaId: number;
  rotulo: string;
  numero: string;
  saldoGravado: number;
  saldoCalculado: number;
  diferenca: number;
  ok: boolean;
  decomposicao: LinhaDecomposicao[];
  /** Soma da decomposição em centavos (== toCents(saldoCalculado)). */
  totalDecomposicaoCentavos: number;
  pendencias: PendenciasConta;
}

export interface ConferenciaCartaoItem {
  cartaoId: number;
  rotulo: string;
  virtual: boolean;
  limiteGravado: number;
  ok: boolean;
}

export interface ConferenciaGrupoCartao {
  principalId: number;
  rotulo: string;
  limiteTotal: number;
  limiteCalculado: number;
  cartoes: ConferenciaCartaoItem[];
  ok: boolean;
  faturaAtual: number;
  anteriores: number;
  parcelasFuturas: number;
  emAbertoTotal: number;
}

export interface TotaisConferencia {
  entradasRealizadas: number;
  entradasPrevistas: number;
  saidasPagas: number;
  saidasPendentes: number;
  saidasTotal: number;
  /** entradasRealizadas − saidasTotal (o "resultado" mostrado nos KPIs). */
  resultado: number;
  saldoContas: number;
  explicacao: string;
}

export type TipoInconsistencia =
  | "CONTA_INEXISTENTE"
  | "CARTAO_INEXISTENTE"
  | "VALOR_INVALIDO"
  | "NATUREZA_DESCONHECIDA"
  | "ID_DUPLICADO"
  | "VIRTUAL_SEM_PRINCIPAL"
  | "PARCELAS_INCOMPLETAS";

export interface Inconsistencia {
  tipo: TipoInconsistencia;
  severidade: "erro" | "info";
  mensagem: string;
  /** Id do lançamento/cartão/conta envolvido, quando houver. */
  ref: number | null;
}

export interface RelatorioConferencia {
  contas: ConferenciaConta[];
  cartoes: ConferenciaGrupoCartao[];
  totais: TotaisConferencia;
  inconsistencias: Inconsistencia[];
  /** Contas + limites divergentes do cache gravado. */
  divergencias: number;
  agora: number;
}

const DIA = 86_400_000;

function assinado(d: Despesa): number {
  return d.tipo === "CREDITO" ? toCents(d.valor) : -toCents(d.valor);
}

function chaveDe(d: Despesa): ChaveDecomposicao {
  const credito = d.tipo === "CREDITO";
  switch (d.natureza) {
    case "NORMAL":
      return credito ? "RECEITAS" : "DESPESAS";
    case "SALDO_INICIAL":
      return "SALDO_INICIAL";
    case "PAGAMENTO_FATURA":
      return "PAGAMENTOS_FATURA";
    case "TRANSFERENCIA":
      return credito ? "TRANSFERENCIAS_ENTRADA" : "TRANSFERENCIAS_SAIDA";
    case "APORTE_META":
      return "APORTES_META";
    case "RESGATE_META":
      return "RESGATES_META";
    case "AJUSTE":
      return "AJUSTES";
    default:
      return "OUTROS";
  }
}

const rotuloConta = (c: Conta) => `${c.banco} · ${c.conta}`;
const rotuloCartao = (c: Cartao) => `${c.nome}${c.finalCartao ? ` ••${c.finalCartao}` : ""}`;

function conferirConta(ds: Dataset, c: Conta, agora: number): ConferenciaConta {
  const soma = new Map<ChaveDecomposicao, { centavos: number; qtd: number }>();
  const pend = { rec: 0, atr: 0, venc: 0, fut: 0, cartao: 0 };
  const hoje = inicioDoDia(agora);
  for (const d of ds.despesas) {
    if (d.conta !== c.conta) continue;
    const cartao = cartaoIdDe(d) !== null;
    const cents = toCents(d.valor);
    if (d.pago && !cartao) {
      const k = chaveDe(d);
      const g = soma.get(k) ?? { centavos: 0, qtd: 0 };
      g.centavos += assinado(d);
      g.qtd += 1;
      soma.set(k, g);
    } else if (!d.pago && cartao) {
      pend.cartao += d.tipo === "CREDITO" ? -cents : cents;
    } else if (!d.pago) {
      if (d.tipo === "CREDITO") pend.rec += cents;
      else if (d.data < hoje) pend.atr += cents;
      else if (d.data <= hoje + 30 * DIA) pend.venc += cents;
      else pend.fut += cents;
    }
  }
  const decomposicao: LinhaDecomposicao[] = ORDEM.filter((k) => soma.has(k)).map((k) => ({
    chave: k,
    rotulo: ROTULO_DECOMPOSICAO[k],
    centavos: soma.get(k)!.centavos,
    qtd: soma.get(k)!.qtd,
  }));
  const total = decomposicao.reduce((s, l) => s + l.centavos, 0);
  const saldoCalculado = saldoConta(ds.despesas, c.conta);
  const diferenca = fromCents(toCents(c.saldo) - toCents(saldoCalculado));
  return {
    contaId: c.id,
    rotulo: rotuloConta(c),
    numero: c.conta,
    saldoGravado: c.saldo,
    saldoCalculado,
    diferenca,
    ok: toCents(c.saldo) === toCents(saldoCalculado),
    decomposicao,
    totalDecomposicaoCentavos: total,
    pendencias: {
      receitasPrevistas: fromCents(pend.rec),
      despesasAtrasadas: fromCents(pend.atr),
      despesasAVencer30d: fromCents(pend.venc),
      despesasFuturas: fromCents(pend.fut),
      comprasCartaoEmAberto: fromCents(pend.cartao),
    },
  };
}

function conferirCartoes(ds: Dataset, agora: number): ConferenciaGrupoCartao[] {
  const vistos = new Set<number>();
  const grupos: ConferenciaGrupoCartao[] = [];
  for (const c of ds.cartoes) {
    const p = principalDe(c, ds.cartoes);
    if (vistos.has(p.id)) continue;
    vistos.add(p.id);
    const grupo = cartoesDoGrupo(p, ds.cartoes);
    const calculado = limiteGrupo(p, ds.cartoes, ds.despesas);
    const itens: ConferenciaCartaoItem[] = grupo.map((g) => ({
      cartaoId: g.id,
      rotulo: rotuloCartao(g),
      virtual: g.cartaoPrincipalId != null,
      limiteGravado: g.limiteDisponivel,
      ok: toCents(g.limiteDisponivel) === toCents(calculado),
    }));
    const comp = comprometimentoCartao(p, ds.cartoes, ds.despesas, agora);
    grupos.push({
      principalId: p.id,
      rotulo: rotuloCartao(p),
      limiteTotal: p.limiteTotal,
      limiteCalculado: calculado,
      cartoes: itens,
      ok: itens.every((i) => i.ok),
      faturaAtual: comp.faturaAtual,
      anteriores: comp.anteriores,
      parcelasFuturas: comp.parcelasFuturas,
      emAbertoTotal: comp.emAbertoTotal,
    });
  }
  return grupos;
}

function inconsistenciasDe(ds: Dataset): Inconsistencia[] {
  const out: Inconsistencia[] = [];
  const numeros = new Set(ds.contas.map((c) => c.conta));
  const idsCartao = new Set(ds.cartoes.map((c) => c.id));
  const naturezas = new Set<string>(NATUREZAS);
  const add = (tipo: TipoInconsistencia, mensagem: string, ref: number | null, severidade: "erro" | "info" = "erro") =>
    out.push({ tipo, severidade, mensagem, ref });

  for (const [nome, lista] of [
    ["lançamento", ds.despesas],
    ["conta", ds.contas],
    ["cartão", ds.cartoes],
  ] as const) {
    const vistos = new Set<number>();
    const repetidos = new Set<number>();
    for (const i of lista) {
      if (vistos.has(i.id)) repetidos.add(i.id);
      vistos.add(i.id);
    }
    for (const id of repetidos) add("ID_DUPLICADO", `Id ${id} repetido em ${nome}s.`, id);
  }

  for (const d of ds.despesas) {
    const nomeD = `"${d.descricao}" (id ${d.id})`;
    if (!numeros.has(d.conta)) add("CONTA_INEXISTENTE", `Lançamento ${nomeD} aponta para a conta "${d.conta}", que não existe.`, d.id);
    const cid = cartaoIdDe(d);
    if (cid !== null && !idsCartao.has(cid)) add("CARTAO_INEXISTENTE", `Lançamento ${nomeD} usa o cartão ${cid}, que não existe.`, d.id);
    if (!Number.isFinite(d.valor) || d.valor <= 0) add("VALOR_INVALIDO", `Lançamento ${nomeD} tem valor inválido (${String(d.valor)}).`, d.id);
    if (!naturezas.has(d.natureza)) add("NATUREZA_DESCONHECIDA", `Lançamento ${nomeD} tem natureza desconhecida (${String(d.natureza)}).`, d.id);
  }

  for (const c of ds.cartoes) {
    if (c.cartaoPrincipalId != null && !idsCartao.has(c.cartaoPrincipalId)) {
      add("VIRTUAL_SEM_PRINCIPAL", `Cartão virtual ${rotuloCartao(c)} aponta para um principal inexistente (${c.cartaoPrincipalId}).`, c.id);
    }
  }

  // grupos de parcelas incompletos (informativo: pode ser parcela inicial > 1 ou parcelas excluídas)
  const grupos = new Map<string, { n: number; qtd: number; desc: string }>();
  for (const d of ds.despesas) {
    if (!d.grupoId?.startsWith("parc:")) continue;
    const m = /\((\d+)\/(\d+)\)\s*$/.exec(d.descricao);
    const g = grupos.get(d.grupoId) ?? { n: 0, qtd: 0, desc: d.descricao.replace(/\s*\(\d+\/\d+\)\s*$/, "") };
    g.qtd += 1;
    if (m) g.n = Math.max(g.n, Number(m[2]));
    grupos.set(d.grupoId, g);
  }
  for (const [id, g] of grupos) {
    if (g.n > 0 && g.qtd < g.n) add("PARCELAS_INCOMPLETAS", `Parcelamento "${g.desc}" tem ${g.qtd} de ${g.n} parcelas lançadas (${id}).`, null, "info");
  }
  return out;
}

export function explicarResultadoVsSaldo(t: Omit<TotaisConferencia, "explicacao">): string {
  const partes = [
    `Entradas (${formatBRL(t.entradasRealizadas)}) contam só receitas já recebidas, enquanto Saídas (${formatBRL(t.saidasTotal)}) somam as despesas pagas (${formatBRL(t.saidasPagas)}) e as ainda pendentes ou futuras (${formatBRL(t.saidasPendentes)}).`,
    `Por isso Entradas − Saídas (${formatBRL(t.resultado)}) não é o saldo: o saldo das contas (${formatBRL(t.saldoContas)}) considera apenas o que já foi pago ou recebido e também inclui saldo inicial, transferências, metas, faturas pagas e ajustes.`,
  ];
  if (t.entradasPrevistas > 0) partes.push(`Há ainda ${formatBRL(t.entradasPrevistas)} de receitas previstas, que só entram no saldo quando recebidas.`);
  return partes.join(" ");
}

export function auditarSaldos(ds: Dataset, agora: number): RelatorioConferencia {
  const contas = ds.contas.map((c) => conferirConta(ds, c, agora));
  const cartoes = conferirCartoes(ds, agora);
  const k = kpisTotal(ds.despesas);
  const base = {
    entradasRealizadas: k.receitasRealizadas,
    entradasPrevistas: k.receitasPrevistas,
    saidasPagas: k.despesasPagas,
    saidasPendentes: k.despesasPendentes,
    saidasTotal: k.despesasTotal,
    resultado: k.resultado,
    saldoContas: fromCents(contas.reduce((s, c) => s + toCents(c.saldoCalculado), 0)),
  };
  const divergencias = contas.filter((c) => !c.ok).length + cartoes.reduce((s, g) => s + g.cartoes.filter((i) => !i.ok).length, 0);
  return {
    contas,
    cartoes,
    totais: { ...base, explicacao: explicarResultadoVsSaldo(base) },
    inconsistencias: inconsistenciasDe(ds),
    divergencias,
    agora,
  };
}

export interface CorrecaoSaldo {
  tipo: "conta" | "cartao";
  id: number;
  rotulo: string;
  antes: number;
  depois: number;
}

/** Recalcula caches de saldo/limite (recalcularTudo). Idempotente; não toca em lançamentos. */
export function recalcularSaldos(ds: Dataset): { ds: Dataset; correcoes: CorrecaoSaldo[] } {
  const novo = recalcularTudo(ds);
  const correcoes: CorrecaoSaldo[] = [];
  novo.contas.forEach((c, i) => {
    const antes = ds.contas[i];
    if (toCents(antes.saldo) !== toCents(c.saldo)) correcoes.push({ tipo: "conta", id: c.id, rotulo: rotuloConta(c), antes: antes.saldo, depois: c.saldo });
  });
  novo.cartoes.forEach((c, i) => {
    const antes = ds.cartoes[i];
    if (toCents(antes.limiteDisponivel) !== toCents(c.limiteDisponivel))
      correcoes.push({ tipo: "cartao", id: c.id, rotulo: rotuloCartao(c), antes: antes.limiteDisponivel, depois: c.limiteDisponivel });
  });
  return { ds: novo, correcoes };
}
