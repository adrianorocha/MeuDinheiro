import { describe, expect, it } from "vitest";
import { comprometimentoCartao, faturasEmAberto, limiteGrupo, saldoConta } from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import { adicionarLancamento, pagarFatura, pagarItensFatura, type Ctx } from "./operations";
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

/** Conta 111 com 10.000; cartão F (limite 5.000, fecha dia 25, vence dia 5) + virtual V. */
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

function compra12x(): Dataset {
  return ok(
    adicionarLancamento(base(), { descricao: "Geladeira", valor: 1200, data: dt(2025, 3, 10), categoria: "Casa", tipo: "DEBITO", cartaoId: 10, parcelas: 12 }, ctx()),
  ).ds;
}

describe("R44 - confirmação: parcelado no cartão reserva o limite", () => {
  it("1.200 em 12x, limite 5.000: disponível 3.800; pagar 1 fatura devolve só aquela parcela", () => {
    const ds = compra12x();
    expect(ds.despesas.filter((d) => d.cartaoId === 10)).toHaveLength(12);
    expect(ds.despesas.filter((d) => d.cartaoId === 10).every((d) => !d.pago)).toBe(true);
    expect(limiteGrupo(ds.cartoes[0], ds.cartoes, ds.despesas)).toBe(3800);
    const r = ok(pagarFatura(ds, 10, 3, 2025, ctx()));
    expect(r.pagamento.valor).toBe(100);
    expect(limiteGrupo(r.ds.cartoes[0], r.ds.cartoes, r.ds.despesas)).toBe(3900);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(3900);
  });
});

describe("R44 - comprometimentoCartao", () => {
  it("separa fatura atual, parcelas futuras e libera por fatura em ordem", () => {
    const ds = compra12x();
    const c = comprometimentoCartao(ds.cartoes[0], ds.cartoes, ds.despesas, AGORA);
    expect(c.faturaAtual).toBe(100);
    expect(c.parcelasFuturas).toBe(1100);
    expect(c.anteriores).toBe(0);
    expect(c.emAbertoTotal).toBe(1200);
    expect(c.disponivel).toBe(3800);
    expect(c.liberacaoPorFatura).toHaveLength(12);
    expect(c.liberacaoPorFatura[0]).toMatchObject({ mes: 3, ano: 2025, valor: 100, vencimento: dt(2025, 4, 5, 0) });
    expect(c.liberacaoPorFatura[11]).toMatchObject({ mes: 2, ano: 2026 });
    const ord = c.liberacaoPorFatura.map((f) => f.vencimento);
    expect([...ord].sort((a, b) => a - b)).toEqual(ord);
  });
  it("faturas anteriores em aberto e estornos entram nos totais; virtual conta no grupo", () => {
    let ds = compra12x();
    ds = { ...ds, despesas: [...ds.despesas, desp({ id: 900, valor: 50, cartaoId: 11, data: dt(2025, 1, 10), pago: false }), desp({ id: 901, tipo: "CREDITO", valor: 20, cartaoId: 11, data: dt(2025, 3, 11), pago: false })] };
    const c = comprometimentoCartao(ds.cartoes[1], ds.cartoes, ds.despesas, AGORA);
    expect(c.anteriores).toBe(50);
    expect(c.faturaAtual).toBe(80);
    expect(c.emAbertoTotal).toBe(1230);
  });
});

describe("R44 - pagarItensFatura", () => {
  it("paga parcial: saldo cai pelo líquido, limite volta pelo mesmo valor, descrição e grupoId parciais", () => {
    const ds = base();
    const base2: Dataset = {
      ...ds,
      despesas: [
        ...ds.despesas,
        desp({ id: 20, descricao: "A", valor: 100, cartaoId: 10, data: dt(2025, 3, 10), pago: false }),
        desp({ id: 21, descricao: "B", valor: 60, cartaoId: 11, data: dt(2025, 3, 11), pago: false }),
        desp({ id: 22, descricao: "Estorno", tipo: "CREDITO", valor: 10, cartaoId: 10, data: dt(2025, 3, 12), pago: false }),
      ],
    };
    const antes = limiteGrupo(base2.cartoes[0], base2.cartoes, base2.despesas);
    expect(antes).toBe(4850);
    const r = ok(pagarItensFatura(base2, { cartaoId: 11, itemIds: [20, 22] }, ctx()));
    expect(r.pagamento.valor).toBe(90);
    expect(r.pagamento.descricao).toBe("Fatura F 03/2025 (parcial)");
    expect(r.pagamento.grupoId).toBe(`fatura:10:2025-03:p${AGORA}`);
    expect(r.pagamento.natureza).toBe("PAGAMENTO_FATURA");
    expect(saldoConta(r.ds.despesas, "111")).toBe(10000 - 90);
    expect(limiteGrupo(r.ds.cartoes[0], r.ds.cartoes, r.ds.despesas) - antes).toBe(90);
    // pagar o restante fecha a fatura, sem erro de centavos, com descrição de fatura completa
    const r2 = ok(pagarItensFatura(r.ds, { cartaoId: 10, itemIds: [21] }, ctx(AGORA + 1000)));
    expect(r2.pagamento.valor).toBe(60);
    expect(r2.pagamento.descricao).toBe("Fatura F 03/2025");
    expect(r2.pagamento.grupoId).toBe("fatura:10:2025-03");
    expect(limiteGrupo(r2.ds.cartoes[0], r2.ds.cartoes, r2.ds.despesas)).toBe(5000);
    expect(saldoConta(r2.ds.despesas, "111")).toBe(10000 - 150);
  });

  it("antecipa parcelas de várias faturas: descrição 'itens selecionados' e grupoId único", () => {
    const ds = compra12x();
    const f = faturasEmAberto(ds.cartoes[0], ds.cartoes, ds.despesas);
    const ids = [...f[1].itens, ...f[2].itens].map((d) => d.id);
    const r = ok(pagarItensFatura(ds, { cartaoId: 10, itemIds: ids }, ctx()));
    expect(r.pagamento.valor).toBe(200);
    expect(r.pagamento.descricao).toBe("Fatura F (itens selecionados)");
    expect(r.pagamento.grupoId).toBe(`fatura:10:multi:p${AGORA}`);
    expect(limiteGrupo(r.ds.cartoes[0], r.ds.cartoes, r.ds.despesas)).toBe(4000);
    const r2 = ok(pagarItensFatura(r.ds, { cartaoId: 10, itemIds: f[3].itens.map((d) => d.id) }, ctx()));
    expect(r2.pagamento.grupoId).not.toBe(r.pagamento.grupoId);
  });

  it("valida: ids inexistentes, de fora do grupo, já pagos, vazio e líquido <= 0", () => {
    const ds: Dataset = {
      ...base(),
      despesas: [
        ...base().despesas,
        desp({ id: 30, valor: 100, cartaoId: 10, data: dt(2025, 3, 10), pago: false }),
        desp({ id: 31, valor: 100, cartaoId: 10, data: dt(2025, 3, 10), pago: true }),
        desp({ id: 32, valor: 100, cartaoId: null, conta: "111", data: dt(2025, 3, 10), pago: false }),
        desp({ id: 33, tipo: "CREDITO", valor: 40, cartaoId: 10, data: dt(2025, 3, 10), pago: false }),
      ],
    };
    expect(falha(pagarItensFatura(ds, { cartaoId: 10, itemIds: [999] }, ctx()))).toMatch(/não existe/);
    expect(falha(pagarItensFatura(ds, { cartaoId: 10, itemIds: [32] }, ctx()))).toMatch(/não pertence/);
    expect(falha(pagarItensFatura(ds, { cartaoId: 10, itemIds: [31] }, ctx()))).toMatch(/já está pago/);
    expect(falha(pagarItensFatura(ds, { cartaoId: 10, itemIds: [] }, ctx()))).toMatch(/ao menos um/);
    expect(falha(pagarItensFatura(ds, { cartaoId: 10, itemIds: [33] }, ctx()))).toBe("Não há valor a pagar nos itens selecionados.");
    expect(falha(pagarItensFatura(ds, { cartaoId: 77, itemIds: [30] }, ctx()))).toMatch(/Cartão/);
  });

  it("pagarFatura continua igual (grupoId e descrição de antes)", () => {
    const ds = compra12x();
    const r = ok(pagarFatura(ds, 10, 3, 2025, ctx()));
    expect(r.pagamento.grupoId).toBe("fatura:10:2025-03");
    expect(r.pagamento.descricao).toBe("Fatura F 03/2025");
    expect(falha(pagarFatura(r.ds, 10, 3, 2025, ctx()))).toBe("Não há valor em aberto nesta fatura.");
  });
});

describe("R44 - parcela atual (compra em andamento)", () => {
  it("k>1 cria só k..N com as parcelas de dividirParcelas, primeira na data informada", () => {
    const r = ok(
      adicionarLancamento(base(), { descricao: "TV", valor: 1000, data: dt(2025, 3, 10), categoria: "Casa", tipo: "DEBITO", cartaoId: 10, parcelas: 3, parcelaAtual: 2 }, ctx()),
    );
    expect(r.criados.map((d) => d.descricao)).toEqual(["TV (2/3)", "TV (3/3)"]);
    expect(r.criados.map((d) => d.valor)).toEqual([333.33, 333.34]);
    expect(r.criados[0].data).toBe(dt(2025, 3, 10));
    expect(r.criados[1].data).toBe(dt(2025, 4, 10));
    expect(r.criados.every((d) => !d.pago && d.cartaoId === 10 && d.grupoId === "parc:u")).toBe(true);
    expect(limiteGrupo(r.ds.cartoes[0], r.ds.cartoes, r.ds.despesas)).toBe(5000 - 666.67);
  });
  it("valida 1 <= k <= N e exige cartão de crédito", () => {
    const d = { descricao: "TV", valor: 1000, data: dt(2025, 3, 10), categoria: "Casa", tipo: "DEBITO" as const };
    expect(falha(adicionarLancamento(base(), { ...d, cartaoId: 10, parcelas: 3, parcelaAtual: 4 }, ctx()))).toMatch(/entre 1 e 3/);
    expect(falha(adicionarLancamento(base(), { ...d, cartaoId: 10, parcelas: 3, parcelaAtual: 0 }, ctx()))).toMatch(/entre 1 e 3/);
    expect(falha(adicionarLancamento(base(), { ...d, conta: "111", parcelas: 3, parcelaAtual: 2 }, ctx()))).toMatch(/cartão de crédito/);
    expect(ok(adicionarLancamento(base(), { ...d, cartaoId: 10, parcelas: 3 }, ctx())).criados).toHaveLength(3);
    expect(ok(adicionarLancamento(base(), { ...d, cartaoId: 10, parcelas: 3, parcelaAtual: 3 }, ctx())).criados).toHaveLength(1);
  });
});
