import { describe, expect, it } from "vitest";
import { cartaoDeDebito, debitosDoCartao, ehGrupoParcelas, grupoIdDebito, resumoFatura, totalDebitos } from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import { adicionarLancamento, editarLancamento, excluirCartao, excluirLancamento, type Ctx } from "./operations";
import type { Dataset } from "./types";

let n = 0;
const ctx = (agora: number): Ctx => ({ agora, uuid: () => `u${++n}`, rng: (() => { let s = 0.1; return () => (s = (s + 0.137) % 1); })() });
const AGORA = dt(2025, 3, 20);

function base(): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222", banco: "B" })],
    cartoes: [
      cartao({ id: 10, tipo: "DÉBITO", contaId: 1, limiteTotal: 0, limiteDisponivel: 0 }),
      cartao({ id: 11, tipo: "DÉBITO", contaId: 1, cartaoPrincipalId: 10, limiteTotal: 0, limiteDisponivel: 0 }),
      cartao({ id: 20, tipo: "MÚLTIPLO", contaId: 1 }),
    ],
    despesas: [desp({ id: 1, tipo: "CREDITO", valor: 1000, conta: "111" })],
  });
}
function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}
const compra = (cartaoId: number, extra = {}) => ({ descricao: "Mercado", valor: 50, data: dt(2025, 3, 10), categoria: "X", tipo: "DEBITO" as const, cartaoId, ...extra });

describe("R42b cartaoDeDebito", () => {
  it("lê apenas o prefixo debito:<id>", () => {
    expect(cartaoDeDebito({ grupoId: "debito:10" })).toBe(10);
    expect(cartaoDeDebito({ grupoId: "debito:" })).toBeNull();
    expect(cartaoDeDebito({ grupoId: "debito:x" })).toBeNull();
    expect(cartaoDeDebito({ grupoId: "parc:10" })).toBeNull();
    expect(cartaoDeDebito({ grupoId: "fixa:3:2025-03" })).toBeNull();
    expect(cartaoDeDebito({ grupoId: null })).toBeNull();
    expect(ehGrupoParcelas({ grupoId: "debito:10" })).toBe(false);
    expect(ehGrupoParcelas({ grupoId: "parc:abc" })).toBe(true);
    expect(grupoIdDebito(7)).toBe("debito:7");
  });
});

describe("R42b adicionarLancamento", () => {
  it("débito (físico ou virtual) grava debito:<id usado> e sai da conta", () => {
    let ds = ok(adicionarLancamento(base(), compra(10), ctx(AGORA))).ds;
    ds = ok(adicionarLancamento(ds, compra(11, { descricao: "Padaria" }), ctx(AGORA))).ds;
    const a = ds.despesas.find((d) => d.descricao === "Mercado")!;
    const b = ds.despesas.find((d) => d.descricao === "Padaria")!;
    expect(a.cartaoId).toBeNull();
    expect(a.grupoId).toBe("debito:10");
    expect(b.grupoId).toBe("debito:11");
    expect(a.pago).toBe(true);
  });

  it("débito parcelado é forçado a 1x e não vira parc:", () => {
    const ds = ok(adicionarLancamento(base(), compra(10, { parcelas: 5 }), ctx(AGORA))).ds;
    const novos = ds.despesas.filter((d) => d.descricao === "Mercado");
    expect(novos).toHaveLength(1);
    expect(ehGrupoParcelas(novos[0])).toBe(false);
  });

  it("MÚLTIPLO: débito vincula, crédito não", () => {
    const deb = ok(adicionarLancamento(base(), compra(20, { modalidade: "DEBITO" }), ctx(AGORA))).ds.despesas.find((d) => d.descricao === "Mercado")!;
    const cred = ok(adicionarLancamento(base(), compra(20, { modalidade: "CREDITO" }), ctx(AGORA))).ds.despesas.find((d) => d.descricao === "Mercado")!;
    expect(deb.grupoId).toBe("debito:20");
    expect(deb.cartaoId).toBeNull();
    expect(cred.grupoId).toBeNull();
    expect(cred.cartaoId).toBe(20);
  });
});

describe("R42b editarLancamento", () => {
  it("converter compra de crédito do MÚLTIPLO para débito vincula; voltar para crédito desvincula", () => {
    let ds = ok(adicionarLancamento(base(), compra(20), ctx(AGORA))).ds;
    const id = ds.despesas.find((d) => d.descricao === "Mercado")!.id;
    ds = ok(editarLancamento(ds, id, { cartaoId: 20, modalidade: "DEBITO" }, ctx(AGORA))).ds;
    expect(ds.despesas.find((d) => d.id === id)).toMatchObject({ cartaoId: null, grupoId: "debito:20" });
    ds = ok(editarLancamento(ds, id, { cartaoId: 20, modalidade: "CREDITO" }, ctx(AGORA))).ds;
    expect(ds.despesas.find((d) => d.id === id)).toMatchObject({ cartaoId: 20, grupoId: null });
  });

  it("editar valor (mesmo com cartaoId null e mesma conta) preserva o vínculo; mover de conta desfaz", () => {
    let ds = ok(adicionarLancamento(base(), compra(10), ctx(AGORA))).ds;
    const id = ds.despesas.find((d) => d.descricao === "Mercado")!.id;
    ds = ok(editarLancamento(ds, id, { valor: 60, cartaoId: null, conta: "111" }, ctx(AGORA))).ds;
    expect(ds.despesas.find((d) => d.id === id)!.grupoId).toBe("debito:10");
    ds = ok(editarLancamento(ds, id, { conta: "222", cartaoId: null }, ctx(AGORA))).ds;
    expect(ds.despesas.find((d) => d.id === id)!.grupoId).toBeNull();
  });

  it("trocar para outro cartão de débito reaponta o vínculo", () => {
    let ds = ok(adicionarLancamento(base(), compra(10), ctx(AGORA))).ds;
    const id = ds.despesas.find((d) => d.descricao === "Mercado")!.id;
    ds = ok(editarLancamento(ds, id, { cartaoId: 11 }, ctx(AGORA))).ds;
    expect(ds.despesas.find((d) => d.id === id)!.grupoId).toBe("debito:11");
  });

  it("não sobrescreve parc: ao converter para débito", () => {
    let ds = ok(adicionarLancamento(base(), compra(20, { parcelas: 3 }), ctx(AGORA))).ds;
    const p = ds.despesas.find((d) => ehGrupoParcelas(d))!;
    const grupo = p.grupoId;
    ds = ok(editarLancamento(ds, p.id, { cartaoId: 20, modalidade: "DEBITO" }, ctx(AGORA))).ds;
    expect(ds.despesas.find((d) => d.id === p.id)!.grupoId).toBe(grupo);
  });
});

describe("R42b exclusão e grupos", () => {
  it("excluir com grupoParcelas=true NÃO remove outros débitos do mesmo cartão", () => {
    let ds = ok(adicionarLancamento(base(), compra(10), ctx(AGORA))).ds;
    ds = ok(adicionarLancamento(ds, compra(10, { descricao: "Outro" }), ctx(AGORA))).ds;
    const alvo = ds.despesas.find((d) => d.descricao === "Mercado")!;
    const r = ok(excluirLancamento(ds, alvo.id, { grupoParcelas: true }, ctx(AGORA)));
    expect(r.removidos).toBe(1);
    expect(r.ds.despesas.some((d) => d.descricao === "Outro")).toBe(true);
  });

  it("excluir cartão virtual reaponta o vínculo para o físico", () => {
    let ds = ok(adicionarLancamento(base(), compra(11), ctx(AGORA))).ds;
    ds = ok(excluirCartao(ds, 11)).ds;
    expect(ds.despesas.find((d) => d.descricao === "Mercado")!.grupoId).toBe("debito:10");
  });
});

describe("R42b debitosDoCartao", () => {
  it("filtra por grupo, mês civil e cartão; ignora outras naturezas; não afeta a fatura", () => {
    let ds = ok(adicionarLancamento(base(), compra(10), ctx(AGORA))).ds;
    ds = ok(adicionarLancamento(ds, compra(11, { descricao: "Virtual", valor: 25.5 }), ctx(AGORA))).ds;
    ds = ok(adicionarLancamento(ds, compra(10, { descricao: "Abril", data: dt(2025, 4, 2) }), ctx(AGORA))).ds;
    ds = ok(adicionarLancamento(ds, compra(20, { descricao: "Credito" }), ctx(AGORA))).ds;
    ds = { ...ds, despesas: [...ds.despesas, desp({ id: 999, grupoId: "debito:10", natureza: "AJUSTE", data: dt(2025, 3, 11), conta: "111" })] };
    const grupo = [ds.cartoes[0], ds.cartoes[1]];

    const mar = debitosDoCartao(grupo, ds.despesas, 3, 2025);
    expect(mar.map((d) => d.descricao).sort()).toEqual(["Mercado", "Virtual"]);
    expect(totalDebitos(mar)).toBe(75.5);
    expect(debitosDoCartao(grupo, ds.despesas, 3, 2025, 11).map((d) => d.descricao)).toEqual(["Virtual"]);
    expect(debitosDoCartao(grupo, ds.despesas, 4, 2025).map((d) => d.descricao)).toEqual(["Abril"]);
    expect(debitosDoCartao([ds.cartoes[2]], ds.despesas, 3, 2025)).toEqual([]);
    const f = resumoFatura(ds.cartoes[2], ds.despesas, 3, 2025, ds.cartoes);
    expect(f.itens.map((d) => d.descricao)).toEqual(["Credito"]);
  });
});
