import { ehPagamentoDeFatura } from "../conciliacao/plano";
import { cartaoIdDe, cartoesDoGrupo, faturaDaCompra, resumoFatura } from "./calc";
import { recalcularSaldos, rotuloCartao, rotuloConta, type CorrecaoSaldo } from "./conferencia";
import { fromCents, toCents } from "./money";
import { excluirLancamento } from "./operations";
import type { Dataset, Despesa } from "./types";

/** R70 - reprocessamento de saldos: recálculo (R45) + itens de fatura já paga que ficaram em aberto. */

/** Fatura quitada por inteiro (PAGAMENTO_FATURA R7/R44) que ainda tem itens em aberto. */
export interface FaturaPagaComPendencia {
  cartaoId: number;
  rotulo: string;
  mes: number;
  ano: number;
  itens: Despesa[];
  /** Σ DEBITO − Σ CREDITO dos itens em aberto */
  liquido: number;
}

const GRUPO_FATURA = /^fatura:(\d+):(\d{4})-(\d{2})$/;

/**
 * Faturas com pagamento completo (`fatura:<principal>:<AAAA-MM>`, sem sufixo `:p…`) cujos itens da mesma fatura,
 * lançados até a data do pagamento, continuam `pago=false` (ex.: importados/conciliados depois de a fatura ser paga).
 * Esses itens inflam o limite usado e a fatura em aberto sem que a conta tenha divergência real.
 */
/** Fatura (ciclo do cartão principal do grupo) a que o reprocessamento se restringe. */
export interface AlvoFatura {
  cartaoId: number;
  mes: number;
  ano: number;
}

/** Predicado: o lançamento é item da fatura `alvo` (qualquer cartão do grupo). Sem alvo, vale para todos. */
function naFatura(ds: Dataset, alvo?: AlvoFatura): (d: Despesa) => boolean {
  if (!alvo) return () => true;
  const escolhido = ds.cartoes.find((c) => c.id === alvo.cartaoId);
  if (!escolhido) return () => false;
  const grupo = cartoesDoGrupo(escolhido, ds.cartoes);
  const principal = grupo[0];
  const ids = new Set(grupo.map((c) => c.id));
  return (d) => {
    const cid = cartaoIdDe(d);
    if (cid === null || !ids.has(cid)) return false;
    const f = faturaDaCompra(principal, d.data);
    return f.mes === alvo.mes && f.ano === alvo.ano;
  };
}

export function faturasPagasComPendencias(ds: Dataset, alvo?: AlvoFatura): FaturaPagaComPendencia[] {
  const naAlvo = naFatura(ds, alvo);
  const out = new Map<string, FaturaPagaComPendencia>();
  for (const pg of ds.despesas) {
    if (pg.natureza !== "PAGAMENTO_FATURA" || !pg.grupoId) continue;
    const m = GRUPO_FATURA.exec(pg.grupoId);
    if (!m) continue;
    const principal = ds.cartoes.find((c) => c.id === Number(m[1]));
    if (!principal) continue;
    const mes = Number(m[3]);
    const ano = Number(m[2]);
    const ids = new Set(cartoesDoGrupo(principal, ds.cartoes).map((c) => c.id));
    const itens = ds.despesas.filter((d) => {
      const cid = cartaoIdDe(d);
      if (d.pago || cid === null || !ids.has(cid) || d.data > pg.data || !naAlvo(d)) return false;
      const f = faturaDaCompra(principal, d.data);
      return f.mes === mes && f.ano === ano;
    });
    if (itens.length === 0) continue;
    const chave = `${principal.id}:${ano}-${mes}`;
    if (out.has(chave)) continue;
    let cents = 0;
    for (const d of itens) cents += d.tipo === "DEBITO" ? toCents(d.valor) : -toCents(d.valor);
    out.set(chave, { cartaoId: principal.id, rotulo: principal.nome, mes, ano, itens, liquido: fromCents(cents) });
  }
  return [...out.values()].sort((a, b) => a.ano - b.ano || a.mes - b.mes || a.cartaoId - b.cartaoId);
}

/**
 * Créditos de "pagamento da fatura" que a conciliação criou DENTRO do cartão (o extrato do cartão traz o pagamento
 * da fatura anterior). Esse valor já está na conta como PAGAMENTO_FATURA; dentro do cartão ele abate a fatura em
 * dobro e deixa o total da fatura negativo/divergente do "em aberto".
 */
export function pagamentosNoCartao(ds: Dataset, alvo?: AlvoFatura): Despesa[] {
  const naAlvo = naFatura(ds, alvo);
  return ds.despesas.filter((d) => cartaoIdDe(d) !== null && naAlvo(d) && d.natureza === "NORMAL" && d.tipo === "CREDITO" && d.autor === "Importação" && ehPagamentoDeFatura(d.descricao));
}

/** Créditos (estornos, pagamentos, ajustes a crédito) que compõem a fatura `alvo`, mais recentes por último. */
export function creditosDaFatura(ds: Dataset, alvo: AlvoFatura): Despesa[] {
  const naAlvo = naFatura(ds, alvo);
  return ds.despesas.filter((d) => d.tipo === "CREDITO" && d.natureza === "NORMAL" && naAlvo(d)).sort((a, b) => a.data - b.data || a.id - b.id);
}

/** Descrição de crédito que lembra pagamento/quitação da fatura (heurística larga, só para pré-selecionar na prévia). */
export function pareceCreditoDePagamento(d: Pick<Despesa, "descricao">): boolean {
  const t = d.descricao.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
  return ehPagamentoDeFatura(d.descricao) || /(pagamento|pagto|pgto|quitacao|debito automatico|deb.? aut)/.test(t);
}

export interface ResultadoReprocessamento {
  ds: Dataset;
  correcoes: CorrecaoSaldo[];
  faturas: FaturaPagaComPendencia[];
  /** itens marcados como pagos (sem criar pagamento nem mexer no saldo da conta) */
  itensMarcados: number;
  /** créditos de pagamento de fatura removidos do cartão (foram para a lixeira) */
  pagamentosRemovidos: number;
}

/** Antes/depois de uma fatura (total do ciclo e em aberto) para a prévia do reprocessamento. */
export interface ResumoFaturaReproc {
  totalAntes: number;
  totalDepois: number;
  emAbertoAntes: number;
  emAbertoDepois: number;
}

/** R71 - reprocessa UMA fatura: mesmas regras de `reprocessarSaldos`, só nos itens dela, e devolve o antes/depois. */
export function reprocessarFatura(
  ds: Dataset,
  alvo: AlvoFatura,
  opcoes: { corrigirFaturas?: boolean; removerPagamentosNoCartao?: boolean; agora?: number; removerIds?: readonly number[] } = {},
): ResultadoReprocessamento & { resumo: ResumoFaturaReproc } {
  const antes = resumoFatura(ds.cartoes.find((c) => c.id === alvo.cartaoId) ?? ds.cartoes[0], ds.despesas, alvo.mes, alvo.ano, ds.cartoes);
  const r = reprocessarSaldos(ds, { ...opcoes, fatura: alvo });
  const depois = resumoFatura(r.ds.cartoes.find((c) => c.id === alvo.cartaoId) ?? r.ds.cartoes[0], r.ds.despesas, alvo.mes, alvo.ano, r.ds.cartoes);
  return { ...r, resumo: { totalAntes: antes.total, totalDepois: depois.total, emAbertoAntes: antes.emAberto, emAbertoDepois: depois.emAberto } };
}

/**
 * Marca como pagos os itens de faturas já quitadas (só quando `corrigirFaturas`) e recalcula saldos/limites.
 * Nunca cria lançamentos nem altera valores/datas. Idempotente.
 */
export function reprocessarSaldos(ds: Dataset, opcoes: { corrigirFaturas?: boolean; removerPagamentosNoCartao?: boolean; agora?: number; /** R71 - restringe aos itens desta fatura */ fatura?: AlvoFatura; /** créditos escolhidos pelo usuário para remover (lixeira); só valem se estiverem na fatura */ removerIds?: readonly number[] } = {}): ResultadoReprocessamento {
  const dsInicial = ds;
  let pagamentosRemovidos = 0;
  if (opcoes.removerPagamentosNoCartao) {
    for (const p of pagamentosNoCartao(ds, opcoes.fatura)) {
      const r = excluirLancamento(ds, p.id, {}, { agora: opcoes.agora ?? Date.now() });
      if (r.ok) {
        ds = r.ds;
        pagamentosRemovidos++;
      }
    }
  }
  if (opcoes.removerIds?.length) {
    const naAlvo = naFatura(ds, opcoes.fatura);
    for (const id of opcoes.removerIds) {
      const d = ds.despesas.find((x) => x.id === id);
      if (!d || d.tipo !== "CREDITO" || d.natureza !== "NORMAL" || cartaoIdDe(d) === null || !naAlvo(d)) continue;
      const r = excluirLancamento(ds, id, {}, { agora: opcoes.agora ?? Date.now() });
      if (r.ok) {
        ds = r.ds;
        pagamentosRemovidos++;
      }
    }
  }
  const faturas = opcoes.corrigirFaturas === false ? [] : faturasPagasComPendencias(ds, opcoes.fatura);
  const marcar = new Set(faturas.flatMap((f) => f.itens.map((d) => d.id)));
  const base = marcar.size === 0 ? ds : { ...ds, despesas: ds.despesas.map((d) => (marcar.has(d.id) ? { ...d, pago: true } : d)) };
  const r = recalcularSaldos(base);
  // correções contra o dataset ORIGINAL: ao quitar os itens o limite do cartão também sobe
  const correcoes: CorrecaoSaldo[] = [];
  r.ds.contas.forEach((c, i) => {
    if (toCents(dsInicial.contas[i].saldo) !== toCents(c.saldo)) correcoes.push({ tipo: "conta", id: c.id, rotulo: rotuloConta(c), antes: dsInicial.contas[i].saldo, depois: c.saldo });
  });
  r.ds.cartoes.forEach((c, i) => {
    if (toCents(dsInicial.cartoes[i].limiteDisponivel) !== toCents(c.limiteDisponivel)) correcoes.push({ tipo: "cartao", id: c.id, rotulo: rotuloCartao(c), antes: dsInicial.cartoes[i].limiteDisponivel, depois: c.limiteDisponivel });
  });
  return { ds: r.ds, correcoes, faturas, itensMarcados: marcar.size, pagamentosRemovidos };
}
