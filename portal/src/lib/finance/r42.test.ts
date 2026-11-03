import { describe, expect, it } from "vitest";
import { kpisDoMes, gastoCategoria, limiteGrupo, saldoConta, previsaoMes } from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import {
  adicionarLancamento,
  ajustarSaldoConta,
  editarCartao,
  editarLancamento,
  modalidadeDaCompra,
  processarDespesasFixas,
  type Ctx,
} from "./operations";
import { gerarRelatorio, filtroPadrao } from "./relatorios";
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

const contas = [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222", banco: "B2" })];

/** F1 (conta 1, 1000) e F2 (conta 2, 500); V virtual de F1; sem compras. */
function base(): Dataset {
  return dataset({
    contas,
    cartoes: [
      cartao({ id: 10, nome: "F1", contaId: 1, limiteTotal: 1000, limiteDisponivel: 1000 }),
      cartao({ id: 20, nome: "F2", contaId: 2, limiteTotal: 500, limiteDisponivel: 500, diaFechamento: 10, diaVencimento: 20, tipo: "MÚLTIPLO" }),
      cartao({ id: 11, nome: "V", contaId: 1, limiteTotal: 1000, limiteDisponivel: 1000, cartaoPrincipalId: 10 }),
    ],
    despesas: [desp({ id: 1, tipo: "CREDITO", valor: 2000, conta: "111" }), desp({ id: 2, tipo: "CREDITO", valor: 800, conta: "222" })],
  });
}

describe("R42-A vínculo editável físico↔virtual", () => {
  it("virtual muda de físico: herda campos, move compras para a conta do novo e recalcula os dois limites", () => {
    let ds = base();
    ds = ok(adicionarLancamento(ds, { descricao: "Compra", valor: 100, data: dt(2025, 3, 5), categoria: "X", tipo: "DEBITO", cartaoId: 11 }, ctx())).ds;
    expect(ds.cartoes.find((c) => c.id === 10)!.limiteDisponivel).toBe(900);
    const r = ok(editarCartao(ds, 11, { cartaoPrincipalId: 20 }));
    const v = r.ds.cartoes.find((c) => c.id === 11)!;
    expect(v.cartaoPrincipalId).toBe(20);
    expect(v.limiteTotal).toBe(500);
    expect(v.contaId).toBe(2);
    expect(v.diaFechamento).toBe(10);
    expect(v.tipo).toBe("MÚLTIPLO");
    expect(r.ds.despesas.find((d) => d.cartaoId === 11)!.conta).toBe("222");
    expect(r.ds.cartoes.find((c) => c.id === 10)!.limiteDisponivel).toBe(1000);
    expect(r.ds.cartoes.find((c) => c.id === 20)!.limiteDisponivel).toBe(400);
    expect(v.limiteDisponivel).toBe(400);
  });

  it("físico sem virtuais vira virtual de outro físico", () => {
    const r = ok(editarCartao(base(), 20, { cartaoPrincipalId: 10 }));
    const c = r.ds.cartoes.find((x) => x.id === 20)!;
    expect(c.cartaoPrincipalId).toBe(10);
    expect(c.limiteTotal).toBe(1000);
    expect(c.contaId).toBe(1);
  });

  it("recusa: físico com virtuais, alvo inexistente/virtual/ele mesmo", () => {
    expect(falha(editarCartao(base(), 10, { cartaoPrincipalId: 20 }))).toMatch(/virtuais/);
    expect(editarCartao(base(), 20, { cartaoPrincipalId: 999 }).ok).toBe(false);
    expect(editarCartao(base(), 20, { cartaoPrincipalId: 11 }).ok).toBe(false);
    expect(editarCartao(base(), 20, { cartaoPrincipalId: 20 }).ok).toBe(false);
  });

  it("revalida o limite próprio contra o limite total do novo físico", () => {
    const ds = base();
    ds.cartoes = ds.cartoes.map((c) => (c.id === 11 ? { ...c, limiteProprio: 800 } : c));
    expect(falha(editarCartao(ds, 11, { cartaoPrincipalId: 20 }))).toMatch(/limite próprio/);
    const r = ok(editarCartao(ds, 11, { cartaoPrincipalId: 20, limiteProprio: 300 }));
    expect(r.ds.cartoes.find((c) => c.id === 11)!.limiteProprio).toBe(300);
  });

  it("virtual -> null vira físico independente, mantém herdados e zera limiteProprio", () => {
    let ds = base();
    ds.cartoes = ds.cartoes.map((c) => (c.id === 11 ? { ...c, limiteProprio: 200 } : c));
    const r = ok(editarCartao(ds, 11, { cartaoPrincipalId: null }));
    const v = r.ds.cartoes.find((c) => c.id === 11)!;
    expect(v.cartaoPrincipalId).toBeNull();
    expect(v.limiteProprio).toBeNull();
    expect(v.limiteTotal).toBe(1000);
    expect(v.contaId).toBe(1);
    ds = r.ds;
    // o limite do grupo antigo não é mais compartilhado
    const c = ok(adicionarLancamento(ds, { descricao: "C", valor: 50, data: dt(2025, 3, 5), categoria: "X", tipo: "DEBITO", cartaoId: 11 }, ctx()));
    expect(c.ds.cartoes.find((x) => x.id === 10)!.limiteDisponivel).toBe(1000);
    expect(c.ds.cartoes.find((x) => x.id === 11)!.limiteDisponivel).toBe(950);
  });

  it("virtual órfão pode ser corrigido apontando para um físico existente", () => {
    const ds = base();
    ds.cartoes = ds.cartoes.map((c) => (c.id === 11 ? { ...c, cartaoPrincipalId: 999 } : c));
    const r = ok(editarCartao(ds, 11, { cartaoPrincipalId: 10 }));
    expect(r.ds.cartoes.find((c) => c.id === 11)!.cartaoPrincipalId).toBe(10);
  });

  it("o limite do grupo é sempre o do físico principal", () => {
    const ds = ok(editarCartao(base(), 11, { cartaoPrincipalId: 20 })).ds;
    const v = ds.cartoes.find((c) => c.id === 11)!;
    expect(limiteGrupo(v, ds.cartoes, ds.despesas)).toBe(500);
  });
});

describe("R42-B modalidade da compra no cartão", () => {
  it("modalidadeDaCompra", () => {
    expect(modalidadeDaCompra({ tipo: "CRÉDITO" }, "DEBITO")).toBe("CREDITO");
    expect(modalidadeDaCompra({ tipo: "DÉBITO" }, "CREDITO")).toBe("DEBITO");
    expect(modalidadeDaCompra({ tipo: "DÉBITO" })).toBe("DEBITO");
    expect(modalidadeDaCompra({ tipo: "MÚLTIPLO" })).toBe("CREDITO");
    expect(modalidadeDaCompra({ tipo: "MÚLTIPLO" }, "DEBITO")).toBe("DEBITO");
    expect(modalidadeDaCompra({ tipo: "MULTIPLO" }, "DEBITO")).toBe("DEBITO");
  });

  const dsDeb = (): Dataset => {
    const ds = base();
    ds.cartoes = ds.cartoes.map((c) => (c.id === 10 ? { ...c, tipo: "DÉBITO" } : c));
    return ds;
  };
  const compra = { descricao: "Mercado", valor: 90, data: dt(2025, 3, 5), categoria: "X", tipo: "DEBITO" as const, cartaoId: 10 };

  it("cartão DÉBITO: sai direto da conta, sem limite nem fatura, parcelas forçadas a 1", () => {
    const r = ok(adicionarLancamento(dsDeb(), { ...compra, parcelas: 3 }, ctx()));
    expect(r.criados).toHaveLength(1);
    const d = r.criados[0];
    expect(d.cartaoId).toBeNull();
    expect(d.conta).toBe("111");
    expect(d.pago).toBe(true);
    expect(saldoConta(r.ds.despesas, "111")).toBe(1910);
    expect(r.ds.cartoes.find((c) => c.id === 10)!.limiteDisponivel).toBe(1000);
  });

  it("débito com data futura fica pendente (pago = data <= agora)", () => {
    const r = ok(adicionarLancamento(dsDeb(), { ...compra, data: dt(2025, 4, 5) }, ctx()));
    expect(r.criados[0].pago).toBe(false);
  });

  it("cartão CRÉDITO ignora modalidade DEBITO e segue como compra em aberto", () => {
    const r = ok(adicionarLancamento(base(), { ...compra, modalidade: "DEBITO" }, ctx()));
    expect(r.criados[0].cartaoId).toBe(10);
    expect(r.criados[0].pago).toBe(false);
    expect(r.ds.cartoes.find((c) => c.id === 10)!.limiteDisponivel).toBe(910);
  });

  it("MÚLTIPLO usa a modalidade pedida (padrão crédito)", () => {
    const c = { ...compra, cartaoId: 20 };
    const cred = ok(adicionarLancamento(base(), c, ctx()));
    expect(cred.criados[0].cartaoId).toBe(20);
    const deb = ok(adicionarLancamento(base(), { ...c, modalidade: "DEBITO" }, ctx()));
    expect(deb.criados[0].cartaoId).toBeNull();
    expect(deb.criados[0].conta).toBe("222");
    expect(saldoConta(deb.ds.despesas, "222")).toBe(710);
  });

  it("editar: trocar para cartão débito (ou modalidade débito em MÚLTIPLO) leva a compra para a conta", () => {
    const ds1 = ok(adicionarLancamento(base(), { ...compra, cartaoId: 20 }, ctx())).ds;
    const id = ds1.despesas.find((d) => d.cartaoId === 20)!.id;
    const r = ok(editarLancamento(ds1, id, { cartaoId: 20, modalidade: "DEBITO" }, ctx()));
    const d = r.ds.despesas.find((x) => x.id === id)!;
    expect(d.cartaoId).toBeNull();
    expect(d.conta).toBe("222");
    expect(d.pago).toBe(true);
    expect(r.ds.cartoes.find((c) => c.id === 20)!.limiteDisponivel).toBe(500);

    const ds2 = ok(adicionarLancamento(base(), { ...compra, cartaoId: 20 }, ctx())).ds;
    const id2 = ds2.despesas.find((d) => d.cartaoId === 20)!.id;
    const dsDebito = { ...ds2, cartoes: ds2.cartoes.map((c) => (c.id === 10 ? { ...c, tipo: "DÉBITO" } : c)) };
    const r2 = ok(editarLancamento(dsDebito, id2, { cartaoId: 10 }, ctx()));
    expect(r2.ds.despesas.find((x) => x.id === id2)!.cartaoId).toBeNull();
    expect(r2.ds.despesas.find((x) => x.id === id2)!.conta).toBe("111");
  });

  it("editar sem trocar cartão/modalidade mantém a compra no cartão", () => {
    const ds1 = ok(adicionarLancamento(base(), { ...compra, cartaoId: 20 }, ctx())).ds;
    const id = ds1.despesas.find((d) => d.cartaoId === 20)!.id;
    const r = ok(editarLancamento(ds1, id, { cartaoId: 20, valor: 95 }, ctx()));
    expect(r.ds.despesas.find((x) => x.id === id)!.cartaoId).toBe(20);
  });

  it("recorrência: cartão DÉBITO gera na conta; CRÉDITO e MÚLTIPLO ficam no cartão", () => {
    const fixa = (cartaoId: number) => ({ id: 1, descricao: "Streaming", valor: 40, conta: "111", categoria: "Lazer", pic: "", tipo: "DEBITO" as const, diaVencimento: 5, ultimaDataLancamento: null, cartaoId });
    const rodar = (cartaoId: number, tipo: string) => {
      const ds = base();
      ds.cartoes = ds.cartoes.map((c) => (c.id === cartaoId ? { ...c, tipo } : c));
      ds.despesasFixas = [fixa(cartaoId)];
      return ok(processarDespesasFixas(ds, ctx())).criados[0];
    };
    const deb = rodar(10, "DÉBITO");
    expect(deb.cartaoId).toBeNull();
    expect(deb.conta).toBe("111");
    expect(rodar(10, "CRÉDITO").cartaoId).toBe(10);
    expect(rodar(20, "MÚLTIPLO").cartaoId).toBe(20);
    expect(rodar(20, "MÚLTIPLO").conta).toBe("222");
  });
});

describe("R42-C ajuste de saldo da conta", () => {
  it("positivo: cria CREDITO AJUSTE pago e iguala o saldo", () => {
    const r = ok(ajustarSaldoConta(base(), { conta: "111", saldoReal: 2050.55 }, ctx()));
    expect(r.diferenca).toBe(50.55);
    const a = r.ajuste;
    expect(a.natureza).toBe("AJUSTE");
    expect(a.tipo).toBe("CREDITO");
    expect(a.valor).toBe(50.55);
    expect(a.pago).toBe(true);
    expect(a.cartaoId).toBeNull();
    expect(a.categoria).toBe("Ajuste de saldo");
    expect(a.descricao).toBe("Ajuste de saldo (conferido com o banco)");
    expect(a.data).toBe(AGORA);
    expect(saldoConta(r.ds.despesas, "111")).toBe(2050.55);
    expect(r.ds.contas.find((c) => c.conta === "111")!.saldo).toBe(2050.55);
  });

  it("negativo: DEBITO, com observação e data", () => {
    const r = ok(ajustarSaldoConta(base(), { conta: "111", saldoReal: 1900, observacao: "tarifa", data: dt(2025, 3, 1) }, ctx()));
    expect(r.diferenca).toBe(-100);
    expect(r.ajuste.tipo).toBe("DEBITO");
    expect(r.ajuste.valor).toBe(100);
    expect(r.ajuste.descricao).toContain("tarifa");
    expect(r.ajuste.data).toBe(dt(2025, 3, 1));
    expect(saldoConta(r.ds.despesas, "111")).toBe(1900);
  });

  it("saldo igual: erro; conta inexistente: erro", () => {
    expect(falha(ajustarSaldoConta(base(), { conta: "111", saldoReal: 2000 }, ctx()))).toBe("O saldo já confere com o informado.");
    expect(ajustarSaldoConta(base(), { conta: "999", saldoReal: 1 }, ctx()).ok).toBe(false);
  });

  it("não entra em receitas/despesas/orçamento/relatório/previsão, mas entra no saldo", () => {
    const ds0 = dataset({ contas, despesas: [desp({ id: 1, tipo: "CREDITO", valor: 1000, conta: "111", natureza: "SALDO_INICIAL", data: dt(2025, 3, 1) })] });
    const mais = ok(ajustarSaldoConta(ds0, { conta: "111", saldoReal: 1300, data: dt(2025, 3, 10) }, ctx())).ds;
    const menos = ok(ajustarSaldoConta(mais, { conta: "111", saldoReal: 1200, data: dt(2025, 3, 11) }, ctx())).ds;
    expect(saldoConta(menos.despesas, "111")).toBe(1200);
    const k = kpisDoMes(menos.despesas, 3, 2025);
    expect(k.receitasRealizadas).toBe(0);
    expect(k.despesasTotal).toBe(0);
    expect(gastoCategoria(menos.despesas, "Ajuste de saldo", 3, 2025)).toBe(0);
    const rel = gerarRelatorio(menos, filtroPadrao(dt(2025, 3, 1), dt(2025, 3, 31, 23)));
    expect(JSON.stringify(rel)).not.toContain("Ajuste de saldo");
    expect(previsaoMes(menos, AGORA).saldoAtual).toBe(1200);
  });
});
