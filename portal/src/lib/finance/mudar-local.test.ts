import { describe, expect, it } from "vitest";
import { cartaoDeDebito, cartaoIdDe, limiteGrupo, saldoConta } from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import { editarLancamento, ondeEstaLancamento } from "./operations";
import type { Dataset } from "./types";

const agora = dt(2025, 4, 1);
const ctx = { agora, uuid: () => "u" };

function base(extra: Dataset["despesas"] = []): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222" })],
    cartoes: [
      cartao({ id: 10, contaId: 1, tipo: "MÚLTIPLO", limiteTotal: 1000, limiteDisponivel: 1000 }),
      cartao({ id: 11, nome: "Débito", contaId: 2, tipo: "DÉBITO", limiteTotal: 0, limiteDisponivel: 0 }),
    ],
    despesas: [desp({ id: 1, tipo: "CREDITO", valor: 1000, conta: "111", natureza: "SALDO_INICIAL" }), ...extra],
  });
}
function ok(r: { ok: boolean }) {
  if (!r.ok) throw new Error((r as { erro?: string }).erro);
  return r as { ok: true; ds: Dataset };
}
const item = (ds: Dataset, id: number) => ds.despesas.find((d) => d.id === id)!;

describe("R78 - mudar onde o lançamento foi feito", () => {
  const conta1 = () => base([desp({ id: 2, valor: 100, conta: "111", pago: true, data: dt(2025, 3, 3) })]);

  it("conta → cartão de crédito: sai do saldo, entra na fatura e reserva limite", () => {
    const ds0 = conta1();
    expect(saldoConta(ds0.despesas, "111")).toBe(900);
    const ds = ok(editarLancamento(ds0, 2, { cartaoId: 10, modalidade: "CREDITO" }, ctx)).ds;
    const d = item(ds, 2);
    expect(cartaoIdDe(d)).toBe(10);
    expect(d.pago).toBe(false);
    expect(d.conta).toBe("111");
    expect(ds.contas[0].saldo).toBe(1000);
    expect(limiteGrupo(ds.cartoes[0], ds.cartoes, ds.despesas)).toBe(900);
  });

  it("conta → cartão de débito: continua saindo da conta do cartão, vinculado", () => {
    const ds = ok(editarLancamento(conta1(), 2, { cartaoId: 11 }, ctx)).ds;
    const d = item(ds, 2);
    expect(cartaoIdDe(d)).toBeNull();
    expect(cartaoDeDebito(d)).toBe(11);
    expect(d.conta).toBe("222");
    expect(d.pago).toBe(true);
    expect(ds.contas.find((c) => c.conta === "111")!.saldo).toBe(1000);
    expect(ds.contas.find((c) => c.conta === "222")!.saldo).toBe(-100);
  });

  it("cartão de crédito → conta: vira lançamento de conta, abate o saldo e devolve o limite", () => {
    const ds1 = ok(editarLancamento(conta1(), 2, { cartaoId: 10, modalidade: "CREDITO" }, ctx)).ds;
    const ds = ok(editarLancamento(ds1, 2, { cartaoId: null, conta: "222", pago: true }, ctx)).ds;
    const d = item(ds, 2);
    expect(cartaoIdDe(d)).toBeNull();
    expect(d.conta).toBe("222");
    expect(ds.contas.find((c) => c.conta === "222")!.saldo).toBe(-100);
    expect(limiteGrupo(ds.cartoes[0], ds.cartoes, ds.despesas)).toBe(1000);
  });

  it("débito do cartão → conta: só solta o vínculo quando pedido (mesma conta)", () => {
    const ds1 = ok(editarLancamento(conta1(), 2, { cartaoId: 11 }, ctx)).ds;
    const so = ok(editarLancamento(ds1, 2, { cartaoId: null, conta: "222", pago: true, descricao: "x" }, ctx)).ds;
    expect(cartaoDeDebito(item(so, 2))).toBe(11);
    const solto = ok(editarLancamento(ds1, 2, { cartaoId: null, conta: "222", pago: true, desvincularDebito: true }, ctx)).ds;
    expect(cartaoDeDebito(item(solto, 2))).toBeNull();
    expect(item(solto, 2).grupoId).toBeNull();
  });

  it("crédito ↔ débito no cartão múltiplo", () => {
    const ds1 = ok(editarLancamento(conta1(), 2, { cartaoId: 10, modalidade: "CREDITO" }, ctx)).ds;
    const deb = ok(editarLancamento(ds1, 2, { cartaoId: 10, modalidade: "DEBITO" }, ctx)).ds;
    expect(cartaoIdDe(item(deb, 2))).toBeNull();
    expect(cartaoDeDebito(item(deb, 2))).toBe(10);
    expect(deb.contas[0].saldo).toBe(900);
    const cred = ok(editarLancamento(deb, 2, { cartaoId: 10, modalidade: "CREDITO" }, ctx)).ds;
    expect(cartaoIdDe(item(cred, 2))).toBe(10);
    expect(item(cred, 2).grupoId).toBeNull();
    expect(cred.contas[0].saldo).toBe(1000);
  });

  it("parcelas: a mudança de local vale para todo o grupo", () => {
    const parc = [
      desp({ id: 20, valor: 50, conta: "111", pago: true, grupoId: "parc:a", data: dt(2025, 2, 5) }),
      desp({ id: 21, valor: 50, conta: "111", pago: true, grupoId: "parc:a", data: dt(2025, 3, 5) }),
      desp({ id: 22, valor: 50, conta: "111", pago: false, grupoId: "parc:a", data: dt(2025, 5, 5) }),
    ];
    const ds = ok(editarLancamento(base(parc), 21, { cartaoId: 10, modalidade: "CREDITO" }, ctx)).ds;
    for (const i of [20, 21, 22]) {
      expect(cartaoIdDe(item(ds, i))).toBe(10);
      expect(item(ds, i).pago).toBe(false);
      expect(item(ds, i).grupoId).toBe("parc:a");
    }
    expect(limiteGrupo(ds.cartoes[0], ds.cartoes, ds.despesas)).toBe(850);
    const volta = ok(editarLancamento(ds, 22, { cartaoId: null, conta: "111", pago: false }, ctx)).ds;
    expect([20, 21, 22].every((i) => cartaoIdDe(item(volta, i)) === null)).toBe(true);
    expect(item(volta, 20).pago).toBe(true); // data passada
    expect(item(volta, 22).pago).toBe(false); // data futura
    expect(ondeEstaLancamento(item(volta, 20))).toBe("conta:111");
  });

  it("natureza gerada continua bloqueada", () => {
    const ds = base([desp({ id: 30, valor: 10, conta: "111", natureza: "PAGAMENTO_FATURA", grupoId: "fatura:10:2025-03", pago: true })]);
    expect(editarLancamento(ds, 30, { cartaoId: 10 }, ctx).ok).toBe(false);
  });
});
