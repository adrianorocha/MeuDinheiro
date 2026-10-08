import { describe, expect, it } from "vitest";
import { saldoConta } from "./calc";
import { conta, dataset, desp, dt } from "./fixtures";
import { adiantarOcorrenciaFixa, adicionarLancamento, anteciparPagamento, antecipaveisDoGrupo, processarDespesasFixas, type Ctx } from "./operations";
import type { Dataset } from "./types";

const ctx = (agora: number): Ctx => ({ agora, uuid: () => "u1", rng: (() => { let s = 0.1; return () => (s = (s + 0.137) % 1); })() });
const HOJE = dt(2026, 10, 7);

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}

/** Empréstimo de 4 parcelas de 250 nos meses seguintes + saldo de 5.000. */
function comEmprestimo(): Dataset {
  const base = dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [desp({ id: 1, tipo: "CREDITO", valor: 5000, pago: true })] });
  return ok(adicionarLancamento(base, { descricao: "Empréstimo", valor: 1000, data: dt(2026, 11, 10), categoria: "Dívidas", conta: "111", tipo: "DEBITO", parcelas: 4 }, ctx(HOJE))).ds;
}

const abertos = (ds: Dataset) => ds.despesas.filter((d) => !d.pago && d.descricao.startsWith("Empréstimo"));

describe("R40 antecipação de pagamento", () => {
  it("quita parcelas com desconto: sai só o valor pago e o desconto não vira receita", () => {
    const ds = comEmprestimo();
    const ids = abertos(ds).slice(2).map((d) => d.id); // últimas duas (500 devidos)
    const r = ok(anteciparPagamento(ds, { ids, valorPago: 460, desconto: 40 }, ctx(HOJE)));
    expect(r.economia).toBe(40);
    expect(r.restante).toBe(0);
    expect(r.pagos.map((d) => d.valor)).toEqual([230, 230]);
    expect(saldoConta(r.ds.despesas, "111")).toBe(5000 - 460);
    expect(r.pagos.every((d) => d.pago && d.data === HOJE && d.mes === 10)).toBe(true);
    expect(r.pagos[0].descricao).toContain("antecipada de 10/");
    expect(abertos(r.ds)).toHaveLength(2);
  });

  it("rateia o desconto em centavos sem perder nem criar dinheiro", () => {
    const ds = comEmprestimo();
    const ids = abertos(ds).slice(1).map((d) => d.id); // 3 x 250 = 750
    const r = ok(anteciparPagamento(ds, { ids, valorPago: 700, desconto: 50 }, ctx(HOJE)));
    expect(Math.round(r.pagos.reduce((s, d) => s + d.valor * 100, 0))).toBe(70000);
    expect(r.pagos.map((d) => d.valor)).toEqual([233.34, 233.34, 233.32]);
  });

  it("adiantamento parcial divide o lançamento: parte paga + restante em aberto", () => {
    const ds = comEmprestimo();
    const [primeira] = abertos(ds);
    const r = ok(anteciparPagamento(ds, { ids: [primeira.id], valorPago: 100 }, ctx(HOJE)));
    expect(r.restante).toBe(150);
    expect(r.pagos).toHaveLength(1);
    expect(r.pagos[0].valor).toBe(100);
    expect(r.pagos[0].descricao).toContain("adiantamento");
    const resto = r.ds.despesas.find((d) => d.id === primeira.id)!;
    expect(resto.pago).toBe(false);
    expect(resto.valor).toBe(150);
    expect(resto.data).toBe(primeira.data);
    expect(saldoConta(r.ds.despesas, "111")).toBe(4900);
  });

  it("valor que atravessa parcelas: consome em ordem e divide a última", () => {
    const ds = comEmprestimo();
    const ids = abertos(ds).map((d) => d.id);
    const r = ok(anteciparPagamento(ds, { ids, valorPago: 400 }, ctx(HOJE)));
    expect(r.pagos.map((d) => d.valor)).toEqual([250, 150]);
    expect(r.restante).toBe(600);
    expect(abertos(r.ds).map((d) => d.valor)).toEqual([100, 250, 250]);
  });

  it("rejeita excesso, valor zero, desconto negativo, pago e cartão", () => {
    const ds = comEmprestimo();
    const [a] = abertos(ds);
    expect(anteciparPagamento(ds, { ids: [a.id], valorPago: 200, desconto: 100 }, ctx(HOJE)).ok).toBe(false);
    expect(anteciparPagamento(ds, { ids: [a.id], valorPago: 0 }, ctx(HOJE)).ok).toBe(false);
    expect(anteciparPagamento(ds, { ids: [a.id], valorPago: 100, desconto: -1 }, ctx(HOJE)).ok).toBe(false);
    expect(anteciparPagamento(ds, { ids: [], valorPago: 10 }, ctx(HOJE)).ok).toBe(false);
    expect(anteciparPagamento(ds, { ids: [1], valorPago: 10 }, ctx(HOJE)).ok).toBe(false); // crédito
    const cartao = dataset({ ...ds, despesas: [...ds.despesas, desp({ id: 99, pago: false, cartaoId: 10 })] });
    expect(anteciparPagamento(cartao, { ids: [99], valorPago: 5 }, ctx(HOJE)).ok).toBe(false);
  });

  it("agrupa os antecipáveis do parcelamento em ordem de vencimento", () => {
    const ds = comEmprestimo();
    const lista = antecipaveisDoGrupo(ds, abertos(ds)[2]);
    expect(lista).toHaveLength(4);
    expect(lista.map((d) => d.data)).toEqual([...lista.map((d) => d.data)].sort((a, b) => a - b));
  });

  it("recorrência: paga o mês seguinte agora e o processamento não duplica", () => {
    let ds = dataset({
      contas: [conta({ id: 1, conta: "111" })],
      despesas: [desp({ id: 1, tipo: "CREDITO", valor: 5000 })],
      despesasFixas: [{ id: 7, descricao: "Aluguel", valor: 1500, conta: "111", categoria: "Moradia", pic: "", tipo: "DEBITO", diaVencimento: 5, ultimaDataLancamento: dt(2026, 10, 5), cartaoId: null }],
    });
    ds = ok(adiantarOcorrenciaFixa(ds, { fixaId: 7, mes: 11, ano: 2026, valorPago: 1450 }, ctx(HOJE))).ds;
    expect(saldoConta(ds.despesas, "111")).toBe(3550);
    const pago = ds.despesas.find((d) => d.grupoId === "fixa:7:2026-11")!;
    expect(pago.pago && pago.valor === 1450).toBe(true);
    // chega novembro: o mês já está lançado e pago, nada novo é criado
    const depois = ok(processarDespesasFixas(ds, ctx(dt(2026, 11, 20))));
    expect(depois.criados).toHaveLength(0);
    expect(depois.ds.despesas.filter((d) => d.grupoId === "fixa:7:2026-11")).toHaveLength(1);
    // o mesmo mês não pode ser pago de novo
    expect(adiantarOcorrenciaFixa(ds, { fixaId: 7, mes: 11, ano: 2026, valorPago: 1500 }, ctx(HOJE)).ok).toBe(false);
  });

  it("recorrência: mês que já venceu e recorrência no cartão são recusados", () => {
    const base = dataset({
      contas: [conta({ id: 1, conta: "111" })],
      despesasFixas: [{ id: 7, descricao: "Aluguel", valor: 1500, conta: "111", categoria: "Moradia", pic: "", tipo: "DEBITO", diaVencimento: 5, ultimaDataLancamento: null, cartaoId: null }],
    });
    expect(adiantarOcorrenciaFixa(base, { fixaId: 7, mes: 9, ano: 2026, valorPago: 1500 }, ctx(HOJE)).ok).toBe(false);
    const cartao = { ...base, despesasFixas: [{ ...base.despesasFixas[0], cartaoId: 10 }] };
    expect(adiantarOcorrenciaFixa(cartao, { fixaId: 7, mes: 12, ano: 2026, valorPago: 1500 }, ctx(HOJE)).ok).toBe(false);
  });
});
