import { describe, expect, it } from "vitest";
import { cartao, conta, dataset, desp } from "./fixtures";
import { apagarLancamentosDoCartao, excluirCartao, excluirCartaoForcado, resumoExclusaoCartao } from "./operations";
import type { Dataset, DespesaFixa } from "./types";

const regra = { id: 900, descricao: "Netflix", valor: 40, cartaoId: 10, conta: "111" } as unknown as DespesaFixa;

function base(): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" })],
    cartoes: [cartao({ id: 10, contaId: 1 }), cartao({ id: 11, nome: "Virtual", contaId: 1, cartaoPrincipalId: 10 })],
    despesas: [
      desp({ id: 1, tipo: "CREDITO", valor: 1000, conta: "111", natureza: "SALDO_INICIAL" }),
      desp({ id: 2, valor: 100, conta: "111", cartaoId: 10, pago: false }),
      desp({ id: 3, valor: 50, conta: "111", cartaoId: 11, pago: true }),
      desp({ id: 4, valor: 30, conta: "111", cartaoId: null, grupoId: "debito:10", pago: true }),
    ],
    despesasFixas: [regra],
  });
}

describe("R74 - exclusão forçada de cartão", () => {
  it("a exclusão comum continua bloqueada com compra em aberto e recorrência", () => {
    expect(excluirCartao(base(), 10, { forcar: true }).ok).toBe(false);
  });

  it("apaga cartão, virtuais, compras (abertas e pagas) e recorrências; débito vira lançamento comum", () => {
    const r = excluirCartaoForcado(base(), 10);
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.cartoes).toHaveLength(0);
    expect(r.ds.despesasFixas).toHaveLength(0);
    expect(r.ds.despesas.map((d) => d.id).sort()).toEqual([1, 4]);
    expect(r.ds.despesas.find((d) => d.id === 4)?.grupoId).toBeNull();
    expect(r.resumo).toMatchObject({ lancamentos: 2, abertos: 1, debito: 1, debitoApagado: false, valorApagado: 150, recorrencias: ["Netflix"] });
  });

  it("com apagarDebito também remove as compras no débito (mexe no saldo)", () => {
    const r = excluirCartaoForcado(base(), 10, { apagarDebito: true });
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.despesas.map((d) => d.id)).toEqual([1]);
    expect(r.resumo.valorApagado).toBe(180);
    expect(r.ds.contas[0].saldo).toBe(1000);
  });

  it("cartão virtual: só as compras dele; o principal e as dele ficam", () => {
    const r = excluirCartaoForcado(base(), 11);
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.cartoes.map((c) => c.id)).toEqual([10]);
    expect(r.ds.despesas.map((d) => d.id).sort()).toEqual([1, 2, 4]);
    expect(r.ds.despesasFixas).toHaveLength(1);
  });

  it("prévia não altera e cartão inexistente falha", () => {
    const ds = base();
    expect(resumoExclusaoCartao(ds, 10)?.lancamentos).toBe(2);
    expect(ds.cartoes).toHaveLength(2);
    expect(excluirCartaoForcado(ds, 999).ok).toBe(false);
    expect(resumoExclusaoCartao(ds, 999)).toBeNull();
  });
});

describe("R75 - apagar só lançamentos do cartão", () => {
  it("apaga os escolhidos e mantém cartão e recorrências", () => {
    const r = apagarLancamentosDoCartao(base(), 10, [2, 4]);
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.despesas.map((d) => d.id).sort()).toEqual([1, 3]);
    expect(r.ds.cartoes).toHaveLength(2);
    expect(r.ds.despesasFixas).toHaveLength(1);
    expect(r.resumo).toMatchObject({ lancamentos: 2, abertos: 1, valorApagado: 130, cartaoExcluido: false });
  });
  it("com excluirCartao leva todas as compras de crédito, recorrências e cartões; débito não escolhido é desvinculado", () => {
    const r = apagarLancamentosDoCartao(base(), 10, [], { excluirCartao: true });
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.cartoes).toHaveLength(0);
    expect(r.ds.despesasFixas).toHaveLength(0);
    expect(r.ds.despesas.map((d) => d.id).sort()).toEqual([1, 4]);
    expect(r.ds.despesas.find((d) => d.id === 4)?.grupoId).toBeNull();
  });
  it("recusa seleção vazia e lançamento de outro cartão", () => {
    expect(apagarLancamentosDoCartao(base(), 10, []).ok).toBe(false);
    expect(apagarLancamentosDoCartao(base(), 10, [1]).ok).toBe(false);
    expect(apagarLancamentosDoCartao(base(), 11, [2]).ok).toBe(false);
  });
});

describe("R76 - cashback no cartão de crédito", () => {
  it("é um crédito no cartão que abate a fatura e é reconhecido como cashback", async () => {
    const { adicionarLancamento, ehCashbackDoCartao, CATEGORIA_CASHBACK } = await import("./operations");
    const { resumoFatura } = await import("./calc");
    const ctx = { agora: Date.UTC(2025, 2, 5, 12), uuid: () => "u" };
    const dsBase = dataset({ contas: [conta({ id: 1, conta: "111" })], cartoes: [cartao({ id: 10, contaId: 1 })] });
    const compra = adicionarLancamento(dsBase, { descricao: "Loja", valor: 200, data: Date.UTC(2025, 2, 3, 12), categoria: "Compras", tipo: "DEBITO", cartaoId: 10 }, ctx);
    if (!compra.ok) throw new Error(compra.erro);
    const cb = adicionarLancamento(compra.ds, { descricao: "Cashback", valor: 12.5, data: Date.UTC(2025, 2, 4, 12), categoria: CATEGORIA_CASHBACK, tipo: "CREDITO", cartaoId: 10 }, ctx);
    if (!cb.ok) throw new Error(cb.erro);
    const item = cb.ds.despesas.find((d) => d.descricao === "Cashback");
    expect(item && ehCashbackDoCartao(item)).toBe(true);
    const f = cb.ds.despesas.find((d) => d.descricao === "Loja")!;
    const r = resumoFatura(cb.ds.cartoes[0], cb.ds.despesas, new Date(f.data).getMonth() + 1, new Date(f.data).getFullYear(), cb.ds.cartoes);
    expect(r.total).toBeLessThanOrEqual(200);
    expect(r.total).toBe(187.5);
  });
});
