import { describe, expect, it } from "vitest";
import { limiteGrupo, saldoConta } from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import { adicionarLancamento, marcarItensFaturaComoPagos, pagarFatura, pagarItensFatura, type Ctx } from "./operations";
import type { Dataset } from "./types";

const AGORA = dt(2025, 3, 20);
const ctx = (agora = AGORA): Ctx => ({ agora, uuid: () => "u", rng: (() => { let s = 0.2; return () => (s = (s + 0.13) % 1); })() });

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}
function falha(r: { ok: boolean }): string {
  if (r.ok) throw new Error("esperava erro");
  return (r as unknown as { erro: string }).erro;
}

function base(): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111", saldo: 10000 })],
    cartoes: [
      cartao({ id: 10, nome: "F", contaId: 1, limiteTotal: 5000, limiteDisponivel: 5000 }),
      cartao({ id: 11, nome: "V", contaId: 1, limiteTotal: 5000, limiteDisponivel: 5000, cartaoPrincipalId: 10 }),
    ],
    despesas: [desp({ id: 1, tipo: "CREDITO", valor: 10000, conta: "111", natureza: "SALDO_INICIAL", data: dt(2025, 1, 1) })],
  });
}

/** 3 itens em aberto: 100 (F), 50 (V), estorno 20 (F). Líquido 130. */
function comItens(): Dataset {
  const b = base();
  return {
    ...b,
    despesas: [
      ...b.despesas,
      desp({ id: 20, valor: 100, cartaoId: 10, data: dt(2025, 3, 10), pago: false }),
      desp({ id: 21, valor: 50, cartaoId: 11, data: dt(2025, 3, 11), pago: false }),
      desp({ id: 22, tipo: "CREDITO", valor: 20, cartaoId: 10, data: dt(2025, 3, 12), pago: false }),
      desp({ id: 30, valor: 10, cartaoId: 10, data: dt(2025, 2, 1), pago: true }),
    ],
  };
}

describe("R46 - marcarItensFaturaComoPagos", () => {
  it("quita os itens sem criar lançamento nem mexer no saldo; limite restaurado pelo líquido", () => {
    const ds = comItens();
    const limiteAntes = limiteGrupo(ds.cartoes[0], ds.cartoes, ds.despesas);
    const saldoAntes = saldoConta(ds.despesas, "111");
    const r = ok(marcarItensFaturaComoPagos(ds, { cartaoId: 11, itemIds: [20, 21, 22] }));
    expect(r.liquido).toBe(130);
    expect(r.itens).toHaveLength(3);
    expect(r.ds.despesas).toHaveLength(ds.despesas.length);
    expect(r.ds.despesas.some((d) => d.natureza === "PAGAMENTO_FATURA")).toBe(false);
    expect([20, 21, 22].every((id) => r.ds.despesas.find((d) => d.id === id)?.pago)).toBe(true);
    expect(saldoConta(r.ds.despesas, "111")).toBe(saldoAntes);
    expect(r.ds.contas[0].saldo).toBe(ds.contas[0].saldo);
    expect(limiteGrupo(r.ds.cartoes[0], r.ds.cartoes, r.ds.despesas)).toBe(limiteAntes + 130);
  });

  it("aceita líquido <= 0 (só estornos)", () => {
    const r = ok(marcarItensFaturaComoPagos(comItens(), { cartaoId: 10, itemIds: [22] }));
    expect(r.liquido).toBe(-20);
    expect(r.ds.despesas.find((d) => d.id === 22)?.pago).toBe(true);
  });

  it("valida: sem itens, inexistente, de outro grupo, já pago, cartão inexistente", () => {
    const ds = comItens();
    ds.despesas.push(desp({ id: 40, valor: 5, cartaoId: null, conta: "111" }));
    expect(falha(marcarItensFaturaComoPagos(ds, { cartaoId: 10, itemIds: [] }))).toMatch(/ao menos um/);
    expect(falha(marcarItensFaturaComoPagos(ds, { cartaoId: 10, itemIds: [999] }))).toMatch(/não existe/);
    expect(falha(marcarItensFaturaComoPagos(ds, { cartaoId: 10, itemIds: [40] }))).toMatch(/não pertence/);
    expect(falha(marcarItensFaturaComoPagos(ds, { cartaoId: 10, itemIds: [30] }))).toMatch(/já está pago/);
    expect(falha(marcarItensFaturaComoPagos(ds, { cartaoId: 77, itemIds: [20] }))).toMatch(/Cartão/);
  });

  it("idempotente: repetir dá erro 'já está pago'", () => {
    const r = ok(marcarItensFaturaComoPagos(comItens(), { cartaoId: 10, itemIds: [20] }));
    expect(falha(marcarItensFaturaComoPagos(r.ds, { cartaoId: 10, itemIds: [20] }))).toMatch(/já está pago/);
  });

  it("quitar parcialmente e pagar o resto normalmente funciona", () => {
    const r = ok(marcarItensFaturaComoPagos(comItens(), { cartaoId: 10, itemIds: [20] }));
    const saldo = saldoConta(r.ds.despesas, "111");
    const p = ok(pagarItensFatura(r.ds, { cartaoId: 10, itemIds: [21] }, ctx()));
    expect(p.pagamento.valor).toBe(50);
    expect(saldoConta(p.ds.despesas, "111")).toBe(saldo - 50);
  });

  it("pagar por fora e depois pagarFatura da mesma fatura: 'Não há valor em aberto'", () => {
    const r = ok(marcarItensFaturaComoPagos(comItens(), { cartaoId: 10, itemIds: [20, 21, 22] }));
    expect(falha(pagarFatura(r.ds, 10, 3, 2025, ctx()))).toBe("Não há valor em aberto nesta fatura.");
  });

  it("funciona com parcelado (limite devolvido só das parcelas marcadas)", () => {
    const ds = ok(adicionarLancamento(base(), { descricao: "G", valor: 1200, data: dt(2025, 3, 10), categoria: "Casa", tipo: "DEBITO", cartaoId: 10, parcelas: 12 }, ctx())).ds;
    const id = ds.despesas.find((d) => d.cartaoId === 10 && !d.pago)!.id;
    const r = ok(marcarItensFaturaComoPagos(ds, { cartaoId: 10, itemIds: [id] }));
    expect(limiteGrupo(r.ds.cartoes[0], r.ds.cartoes, r.ds.despesas)).toBe(3900);
  });
});
