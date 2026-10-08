import { describe, expect, it } from "vitest";
import { faturasPendentes, kpisDoMes, limiteCartao, previsaoMes, recalcularTudo, resumoFatura, saldoConta, totalFaturasEmAberto } from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import {
  adicionarLancamento,
  agendarTransferencia,
  alternarPago,
  aportarMeta,
  criarCartao,
  criarConta,
  editarCartao,
  criarDespesaFixa,
  dividirParcelas,
  editarConta,
  editarLancamento,
  excluirCartao,
  excluirConta,
  excluirLancamento,
  excluirMeta,
  executarAgendadasVencidas,
  pagarFatura,
  processarDespesasFixas,
  registrarPatrimonio,
  transferir,
  type Ctx,
} from "./operations";
import type { Dataset } from "./types";

let n = 0;
const ctx = (agora: number): Ctx => ({ agora, uuid: () => `u${++n}`, rng: (() => { let s = 0.1; return () => (s = (s + 0.137) % 1); })() });

function base(): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222", banco: "Banco B" })],
    cartoes: [cartao({ id: 10, contaId: 1 })],
    despesas: [desp({ id: 1, tipo: "CREDITO", valor: 1000, conta: "111" })],
  });
}

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}

describe("R8 parcelamento", () => {
  it("100/3 => 33.33, 33.33, 33.34", () => {
    expect(dividirParcelas(100, 3)).toEqual([33.33, 33.33, 33.34]);
  });
  it("soma exata em várias divisões", () => {
    for (const [total, parcelas] of [[100, 7], [0.1, 3], [999.99, 12], [1, 3]] as const) {
      const p = dividirParcelas(total, parcelas);
      expect(Math.round(p.reduce((a, b) => a + Math.round(b * 100), 0))).toBe(Math.round(total * 100));
    }
  });
  it("gera datas sem deriva (31/01) e descrição (i/n)", () => {
    const r = ok(
      adicionarLancamento(base(), { descricao: "TV", valor: 100, data: dt(2025, 1, 31), categoria: "Compras", conta: "111", tipo: "DEBITO", parcelas: 3 }, ctx(dt(2025, 1, 31))),
    );
    expect(r.criados.map((d) => d.descricao)).toEqual(["TV (1/3)", "TV (2/3)", "TV (3/3)"]);
    expect(r.criados.map((d) => new Date(d.data).getDate())).toEqual([31, 28, 31]);
    expect(r.criados.map((d) => d.valor)).toEqual([33.33, 33.33, 33.34]);
    expect(new Set(r.criados.map((d) => d.grupoId)).size).toBe(1);
    expect(new Set(r.criados.map((d) => d.id)).size).toBe(3);
    expect(r.criados.map((d) => d.pago)).toEqual([true, false, false]);
  });
  it("parcelas no cartão: todas pendentes e consomem o limite integral", () => {
    const r = ok(
      adicionarLancamento(base(), { descricao: "Notebook", valor: 600, data: dt(2025, 3, 10), categoria: "Compras", tipo: "DEBITO", cartaoId: 10, parcelas: 6 }, ctx(dt(2025, 3, 10))),
    );
    expect(r.criados.every((d) => !d.pago && d.cartaoId === 10 && d.conta === "111")).toBe(true);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(400);
    expect(r.ds.contas[0].saldo).toBe(1000);
  });
});

describe("lançamentos", () => {
  it("valida entrada e conta inexistente", () => {
    const c = ctx(dt(2025, 3, 10));
    const l = { descricao: "x", valor: 10, data: dt(2025, 3, 10), categoria: "A", conta: "111", tipo: "DEBITO" as const };
    expect(adicionarLancamento(base(), { ...l, valor: 0 }, c).ok).toBe(false);
    expect(adicionarLancamento(base(), { ...l, descricao: " " }, c).ok).toBe(false);
    expect(adicionarLancamento(base(), { ...l, conta: "zzz" }, c).ok).toBe(false);
    expect(adicionarLancamento(base(), { ...l, cartaoId: 999 }, c).ok).toBe(false);
  });
  it("despesa paga debita o saldo; pendente não", () => {
    const c = ctx(dt(2025, 3, 10));
    const l = { descricao: "x", valor: 100, data: dt(2025, 3, 10), categoria: "A", conta: "111", tipo: "DEBITO" as const };
    const a = ok(adicionarLancamento(base(), l, c));
    expect(a.ds.contas[0].saldo).toBe(900);
    const b = ok(adicionarLancamento(base(), { ...l, pago: false }, c));
    expect(b.ds.contas[0].saldo).toBe(1000);
  });
  it("alternar pago em conta recalcula saldo; em cartão só o limite", () => {
    const c = ctx(dt(2025, 3, 10));
    const r = ok(adicionarLancamento(base(), { descricao: "x", valor: 100, data: dt(2025, 3, 10), categoria: "A", conta: "111", tipo: "DEBITO", pago: false }, c));
    const pago = ok(alternarPago(r.ds, r.criados[0].id));
    expect(pago.ds.contas[0].saldo).toBe(900);
    const k = ok(adicionarLancamento(pago.ds, { descricao: "k", valor: 100, data: dt(2025, 3, 10), categoria: "A", tipo: "DEBITO", cartaoId: 10 }, c));
    expect(k.ds.cartoes[0].limiteDisponivel).toBe(900);
    const k2 = ok(alternarPago(k.ds, k.criados[0].id));
    expect(k2.ds.cartoes[0].limiteDisponivel).toBe(1000);
    expect(k2.ds.contas[0].saldo).toBe(900);
  });
  it("editar recalcula mês/ano e saldo", () => {
    const r = ok(editarLancamento(base(), 1, { valor: 1500, data: dt(2025, 7, 1) }));
    expect(r.ds.contas[0].saldo).toBe(1500);
    expect(r.ds.despesas[0].mes).toBe(7);
  });
  it("excluir recalcula saldo", () => {
    const r = ok(excluirLancamento(base(), 1));
    expect(r.ds.contas[0].saldo).toBe(0);
  });
  it("excluir despesa não paga não devolve saldo", () => {
    const ds = recalcularTudo({ ...base(), despesas: [...base().despesas, desp({ id: 2, valor: 100, pago: false })] });
    expect(ds.contas[0].saldo).toBe(1000);
    expect(ok(excluirLancamento(ds, 2)).ds.contas[0].saldo).toBe(1000);
  });
  it("exclui todas as parcelas quando pedido", () => {
    const r = ok(adicionarLancamento(base(), { descricao: "TV", valor: 90, data: dt(2025, 1, 5), categoria: "C", conta: "111", tipo: "DEBITO", parcelas: 3 }, ctx(dt(2025, 1, 5))));
    expect(ok(excluirLancamento(r.ds, r.criados[1].id, { grupoParcelas: true })).removidos).toBe(3);
    expect(ok(excluirLancamento(r.ds, r.criados[1].id)).removidos).toBe(1);
  });
});

describe("R9 transferência", () => {
  const c = ctx(dt(2025, 3, 10));
  it("cria par com mesmo grupoId e move saldo", () => {
    const r = ok(transferir(base(), { origem: "111", destino: "222", valor: 300 }, c));
    const par = r.ds.despesas.filter((d) => d.natureza === "TRANSFERENCIA");
    expect(par).toHaveLength(2);
    expect(par[0].grupoId).toBe(par[1].grupoId);
    expect(par[0].grupoId?.startsWith("transf:")).toBe(true);
    expect(par.map((p) => p.tipo).sort()).toEqual(["CREDITO", "DEBITO"]);
    expect(par.every((p) => p.pago && p.categoria === "Transferência")).toBe(true);
    expect(r.ds.contas.map((x) => x.saldo)).toEqual([700, 300]);
  });
  it("rejeita saldo insuficiente, mesma conta, valor inválido e conta inexistente", () => {
    expect(transferir(base(), { origem: "111", destino: "222", valor: 1000.01 }, c).ok).toBe(false);
    expect(transferir(base(), { origem: "111", destino: "111", valor: 1 }, c).ok).toBe(false);
    expect(transferir(base(), { origem: "111", destino: "222", valor: 0 }, c).ok).toBe(false);
    expect(transferir(base(), { origem: "111", destino: "999", valor: 1 }, c).ok).toBe(false);
  });
  it("aceita transferir exatamente o saldo", () => {
    expect(transferir(base(), { origem: "111", destino: "222", valor: 1000 }, c).ok).toBe(true);
  });
  it("excluir um lançamento exclui o par", () => {
    const r = ok(transferir(base(), { origem: "111", destino: "222", valor: 300 }, c));
    const alvo = r.ds.despesas.find((d) => d.natureza === "TRANSFERENCIA")!;
    const e = ok(excluirLancamento(r.ds, alvo.id));
    expect(e.removidos).toBe(2);
    expect(e.ds.contas.map((x) => x.saldo)).toEqual([1000, 0]);
  });
  it("não exclui nem edita transferência por ids soltos de outro tipo", () => {
    const r = ok(transferir(base(), { origem: "111", destino: "222", valor: 300 }, c));
    const alvo = r.ds.despesas.find((d) => d.natureza === "TRANSFERENCIA")!;
    expect(editarLancamento(r.ds, alvo.id, { valor: 1 }).ok).toBe(false);
  });
});

describe("transferência agendada", () => {
  it("executa as vencidas, marca executada e mantém as futuras", () => {
    const agora = dt(2025, 3, 10);
    let ds = ok(agendarTransferencia(base(), { dataAgendada: dt(2025, 3, 1), contaOrigem: "111", contaDestino: "222", valor: 100 }, ctx(agora))).ds;
    ds = ok(agendarTransferencia(ds, { dataAgendada: dt(2025, 4, 1), contaOrigem: "111", contaDestino: "222", valor: 50 }, ctx(agora))).ds;
    const r = ok(executarAgendadasVencidas(ds, ctx(agora)));
    expect(r.executadas).toBe(1);
    expect(r.ds.transferenciasAgendadas.filter((t) => t.executada)).toHaveLength(1);
    expect(r.ds.contas.map((x) => x.saldo)).toEqual([900, 100]);
    // idempotente
    expect(ok(executarAgendadasVencidas(r.ds, ctx(agora))).executadas).toBe(0);
  });
  it("saldo insuficiente fica pendente e é reportado", () => {
    const agora = dt(2025, 3, 10);
    const ds = ok(agendarTransferencia(base(), { dataAgendada: dt(2025, 3, 1), contaOrigem: "111", contaDestino: "222", valor: 5000 }, ctx(agora))).ds;
    const r = ok(executarAgendadasVencidas(ds, ctx(agora)));
    expect(r.executadas).toBe(0);
    expect(r.falhas).toHaveLength(1);
    expect(r.ds.transferenciasAgendadas[0].executada).toBe(false);
  });
});

describe("R7 pagar fatura", () => {
  function comCompras(): Dataset {
    const c = ctx(dt(2025, 3, 10));
    let ds = base();
    ds = ok(adicionarLancamento(ds, { descricao: "A", valor: 200, data: dt(2025, 3, 10), categoria: "X", tipo: "DEBITO", cartaoId: 10 }, c)).ds;
    ds = ok(adicionarLancamento(ds, { descricao: "B", valor: 50, data: dt(2025, 3, 12), categoria: "X", tipo: "CREDITO", cartaoId: 10 }, c)).ds;
    ds = ok(adicionarLancamento(ds, { descricao: "C", valor: 90, data: dt(2025, 3, 26), categoria: "X", tipo: "DEBITO", cartaoId: 10 }, c)).ds; // abril
    return ds;
  }
  it("paga o ciclo, cria lançamento, atualiza saldo e limite", () => {
    const ds = comCompras();
    expect(limiteCartao(ds.cartoes[0], ds.despesas)).toBe(760);
    const r = ok(pagarFatura(ds, 10, 3, 2025, ctx(dt(2025, 3, 28))));
    expect(r.pagamento.valor).toBe(150);
    expect(r.pagamento.natureza).toBe("PAGAMENTO_FATURA");
    expect(r.pagamento.categoria).toBe("Cartão");
    expect(r.pagamento.cartaoId).toBeNull();
    expect(r.pagamento.grupoId).toBe("fatura:10:2025-03");
    expect(r.ds.contas[0].saldo).toBe(850);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(910); // só a compra de abril segue aberta
  });
  it("não paga duas vezes nem fatura vazia", () => {
    const r = ok(pagarFatura(comCompras(), 10, 3, 2025, ctx(dt(2025, 3, 28))));
    expect(pagarFatura(r.ds, 10, 3, 2025, ctx(dt(2025, 3, 29))).ok).toBe(false);
    expect(pagarFatura(r.ds, 10, 9, 2025, ctx(dt(2025, 3, 29))).ok).toBe(false);
  });
  it("só estorno (total <= 0) não pode ser pago", () => {
    const c = ctx(dt(2025, 3, 10));
    const ds = ok(adicionarLancamento(base(), { descricao: "E", valor: 50, data: dt(2025, 3, 10), categoria: "X", tipo: "CREDITO", cartaoId: 10 }, c)).ds;
    expect(pagarFatura(ds, 10, 3, 2025, c).ok).toBe(false);
  });
  it("pagamento da fatura não pode ser excluído", () => {
    const r = ok(pagarFatura(comCompras(), 10, 3, 2025, ctx(dt(2025, 3, 28))));
    expect(excluirLancamento(r.ds, r.pagamento.id).ok).toBe(false);
    expect(editarLancamento(r.ds, r.pagamento.id, { valor: 1 }).ok).toBe(false);
  });
  it("fatura que vira o ano", () => {
    const c = ctx(dt(2025, 12, 28));
    let ds = ok(adicionarLancamento(base(), { descricao: "Natal", valor: 100, data: dt(2025, 12, 28), categoria: "X", tipo: "DEBITO", cartaoId: 10 }, c)).ds;
    expect(pagarFatura(ds, 10, 12, 2025, c).ok).toBe(false);
    ds = ok(pagarFatura(ds, 10, 1, 2026, c)).ds;
    expect(ds.cartoes[0].limiteDisponivel).toBe(1000);
  });
});

describe("R10 metas", () => {
  const meta = { id: 5, nome: "Viagem", valorObjetivo: 2000, valorGuardado: 0, icone: "ic", dataAlvo: null };
  it("aporte debita conta e soma na meta; exige saldo", () => {
    const ds = { ...base(), metas: [meta] };
    const r = ok(aportarMeta(ds, { metaId: 5, conta: "111", valor: 400 }, ctx(dt(2025, 3, 10))));
    expect(r.ds.metas[0].valorGuardado).toBe(400);
    expect(r.ds.contas[0].saldo).toBe(600);
    const l = r.ds.despesas.find((d) => d.natureza === "APORTE_META")!;
    expect(l.categoria).toBe("Reserva");
    expect(aportarMeta(ds, { metaId: 5, conta: "111", valor: 1000.01 }, ctx(dt(2025, 3, 10))).ok).toBe(false);
    expect(aportarMeta(ds, { metaId: 5, conta: "111", valor: 0 }, ctx(dt(2025, 3, 10))).ok).toBe(false);
  });
  it("excluir meta com saldo resgata para a conta destino", () => {
    const ds = { ...base(), metas: [meta] };
    const a = ok(aportarMeta(ds, { metaId: 5, conta: "111", valor: 400 }, ctx(dt(2025, 3, 10)))).ds;
    expect(excluirMeta(a, 5, null, ctx(dt(2025, 3, 11))).ok).toBe(false);
    const r = ok(excluirMeta(a, 5, "222", ctx(dt(2025, 3, 11))));
    expect(r.ds.metas).toHaveLength(0);
    expect(r.ds.contas.map((c) => c.saldo)).toEqual([600, 400]);
    expect(r.ds.despesas.some((d) => d.natureza === "RESGATE_META" && d.tipo === "CREDITO")).toBe(true);
  });
  it("aporte/resgate não entram nos KPIs", () => {
    const ds = { ...base(), metas: [meta] };
    const a = ok(aportarMeta(ds, { metaId: 5, conta: "111", valor: 400 }, ctx(dt(2025, 3, 10)))).ds;
    expect(kpisDoMes(a.despesas, 3, 2025).despesasTotal).toBe(0);
  });
});

describe("R16 despesas fixas", () => {
  const regra = { descricao: "Aluguel", valor: 1000, conta: "111", categoria: "Moradia", pic: "", tipo: "DEBITO" as const, diaVencimento: 31 };
  it("dia 31 vira o último dia dos meses curtos (catch-up)", () => {
    let ds = ok(criarDespesaFixa(base(), regra, ctx(dt(2025, 1, 10)))).ds;
    ds = { ...ds, despesasFixas: ds.despesasFixas.map((f) => ({ ...f, ultimaDataLancamento: dt(2025, 1, 31) })) };
    const r = ok(processarDespesasFixas(ds, ctx(dt(2025, 4, 30, 15))));
    const dias = r.criados.map((d) => [d.mes, new Date(d.data).getDate()]);
    expect(dias).toEqual([[2, 28], [3, 31], [4, 30]]);
    expect(r.criados.every((d) => !d.pago && d.natureza === "NORMAL")).toBe(true);
    expect(r.ds.despesasFixas[0].ultimaDataLancamento).toBe(new Date(2025, 3, 30).getTime());
  });
  it("nunca lançou: só o mês corrente, e só se o dia já chegou", () => {
    const ds = ok(criarDespesaFixa(base(), { ...regra, diaVencimento: 20 }, ctx(dt(2025, 3, 10)))).ds;
    expect(ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 10)))).criados).toHaveLength(0);
    const r = ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 20, 8))));
    expect(r.criados).toHaveLength(1);
    expect(r.criados[0].mes).toBe(3);
  });
  it("é idempotente", () => {
    const ds = ok(criarDespesaFixa(base(), { ...regra, diaVencimento: 5 }, ctx(dt(2025, 3, 10)))).ds;
    const a = ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 10))));
    expect(a.criados).toHaveLength(1);
    const b = ok(processarDespesasFixas(a.ds, ctx(dt(2025, 3, 11))));
    expect(b.criados).toHaveLength(0);
    expect(b.ds).toBe(a.ds);
  });
  it("limita o catch-up a 12 meses e cruza o ano", () => {
    let ds = ok(criarDespesaFixa(base(), { ...regra, diaVencimento: 10 }, ctx(dt(2023, 1, 10)))).ds;
    ds = { ...ds, despesasFixas: ds.despesasFixas.map((f) => ({ ...f, ultimaDataLancamento: dt(2023, 1, 10) })) };
    const r = ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 20))));
    expect(r.criados).toHaveLength(12);
    expect(r.criados[0].mes).toBe(4);
    expect(r.criados[0].ano).toBe(2024);
    expect(r.criados[11].mes).toBe(3);
    expect(r.criados[11].ano).toBe(2025);
  });
});

describe("contas e cartões", () => {
  it("saldo inicial vira lançamento SALDO_INICIAL", () => {
    const r = ok(criarConta(dataset(), { banco: "X", agencia: "1", conta: "77", titular: "T", saldoInicial: 250.55 }, ctx(dt(2025, 3, 1))));
    expect(r.ds.despesas).toHaveLength(1);
    expect(r.ds.despesas[0].natureza).toBe("SALDO_INICIAL");
    expect(r.ds.contas[0].saldo).toBe(250.55);
    expect(r.conta.id).toBeGreaterThanOrEqual(1_000_000_000);
  });
  it("rejeita número de conta duplicado", () => {
    expect(criarConta(base(), { banco: "X", agencia: "1", conta: "111", titular: "T" }, ctx(0)).ok).toBe(false);
  });
  it("renomear o número da conta migra lançamentos", () => {
    const r = ok(editarConta(base(), 1, { conta: "999" }));
    expect(r.ds.despesas[0].conta).toBe("999");
    expect(r.ds.contas[0].saldo).toBe(1000);
  });
  it("excluir conta com lançamentos exige confirmação", () => {
    expect(excluirConta(base(), 1).ok).toBe(false);
    const r = ok(excluirConta(base(), 1, { forcar: true }));
    expect(r.ds.contas.map((c) => c.id)).toEqual([2]);
    expect(r.ds.despesas).toHaveLength(0);
    expect(r.ds.cartoes).toHaveLength(0);
  });
  it("excluir conta sem dependências funciona direto", () => {
    expect(excluirConta(base(), 2).ok).toBe(true);
  });
  it("excluir cartão com compras exige confirmação", () => {
    const ds = ok(adicionarLancamento(base(), { descricao: "x", valor: 10, data: dt(2025, 3, 1), categoria: "A", tipo: "DEBITO", cartaoId: 10 }, ctx(dt(2025, 3, 1)))).ds;
    expect(excluirCartao(ds, 10, { forcar: true }).ok).toBe(false); // compra em aberto bloqueia (R18)
    const paga = ok(pagarFatura(ds, 10, 3, 2025, ctx(dt(2025, 3, 28)))).ds;
    expect(excluirCartao(paga, 10).ok).toBe(false); // histórico exige confirmação
    expect(ok(excluirCartao(paga, 10, { forcar: true })).ds.despesas).toHaveLength(2);
  });
});

describe("R14 snapshot", () => {
  it("dataset vazio não gera ponto zerado", () => {
    const vazio = dataset();
    expect(registrarPatrimonio(vazio, ctx(dt(2025, 3, 1)))).toBe(vazio);
  });
  it("um registro por mês, atualizado", () => {
    const ds = base();
    const a = registrarPatrimonio(ds, ctx(dt(2025, 3, 1)));
    expect(a.patrimonio).toHaveLength(1);
    const b = registrarPatrimonio({ ...a, despesas: [...a.despesas, desp({ tipo: "CREDITO", valor: 500 })] }, ctx(dt(2025, 3, 20)));
    expect(b.patrimonio).toHaveLength(1);
    expect(b.patrimonio[0].valorTotal).toBeGreaterThan(a.patrimonio[0].valorTotal);
    const c = registrarPatrimonio(b, ctx(dt(2025, 4, 2)));
    expect(c.patrimonio).toHaveLength(2);
    expect(c.patrimonio[1].mesReferencia).toBe("ABR");
    expect(registrarPatrimonio(c, ctx(dt(2025, 4, 2)))).toBe(c);
  });
});

describe("saldo consistente", () => {
  it("saldoConta == cache após sequência de operações", () => {
    const c = ctx(dt(2025, 3, 10));
    let ds = base();
    ds = ok(transferir(ds, { origem: "111", destino: "222", valor: 123.45 }, c)).ds;
    ds = ok(adicionarLancamento(ds, { descricao: "x", valor: 33.33, data: dt(2025, 3, 10), categoria: "A", conta: "222", tipo: "DEBITO" }, c)).ds;
    for (const ct of ds.contas) expect(ct.saldo).toBe(saldoConta(ds.despesas, ct.conta));
  });
});

describe("R18 cartões virtuais", () => {
  const c = ctx(dt(2025, 3, 10));
  function comVirtual(): Dataset {
    const r = ok(criarCartao(base(), { nome: "Virtual", finalCartao: "0001", tipo: "x", limiteTotal: 1, diaFechamento: 1, diaVencimento: 1, contaId: 2, cartaoPrincipalId: 10 }, c));
    return r.ds;
  }
  const compra = (ds: Dataset, cartaoId: number, valor: number, dia = 10) =>
    ok(adicionarLancamento(ds, { descricao: "c" + cartaoId, valor, data: dt(2025, 3, dia), categoria: "X", tipo: "DEBITO", cartaoId }, c)).ds;

  it("virtual copia campos do principal", () => {
    const ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    expect([v.contaId, v.limiteTotal, v.diaFechamento, v.diaVencimento, v.tipo]).toEqual([1, 1000, 25, 5, "CRÉDITO"]);
  });
  it("virtual de virtual e principal inexistente são inválidos", () => {
    const ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    const base2 = { nome: "V2", finalCartao: "", tipo: "", limiteTotal: 0, diaFechamento: 1, diaVencimento: 1, contaId: 1 };
    expect(criarCartao(ds, { ...base2, cartaoPrincipalId: v.id }, c).ok).toBe(false);
    expect(criarCartao(ds, { ...base2, cartaoPrincipalId: 999 }, c).ok).toBe(false);
  });
  it("limite compartilhado entre os dois cartões", () => {
    let ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    ds = compra(compra(ds, 10, 300), v.id, 200);
    expect(ds.cartoes.map((x) => x.limiteDisponivel)).toEqual([500, 500]);
    expect(limiteCartao(ds.cartoes[0], ds.despesas)).toBe(700); // visão isolada não é a do grupo
  });
  it("fatura única do grupo, com o cartão usado em cada compra", () => {
    let ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    ds = compra(compra(ds, 10, 300), v.id, 200);
    const porVirtual = resumoFatura(v, ds.despesas, 3, 2025, ds.cartoes);
    const porPrincipal = resumoFatura(ds.cartoes[0], ds.despesas, 3, 2025, ds.cartoes);
    expect(porVirtual.total).toBe(500);
    expect(porPrincipal.itens.map((i) => i.cartaoId).sort()).toEqual([10, v.id].sort());
    expect(faturasPendentes(ds.cartoes, ds.despesas)).toHaveLength(1);
    expect(totalFaturasEmAberto(ds)).toBe(500);
  });
  it("previsão não duplica a fatura do grupo", () => {
    let ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    ds = compra(compra(ds, 10, 300, 3), v.id, 200, 4);
    ds = { ...ds, cartoes: ds.cartoes.map((x) => ({ ...x, diaFechamento: 5, diaVencimento: 20 })) };
    expect(previsaoMes(ds, dt(2025, 3, 15)).saldoLivrePrevisto).toBe(500); // 1000 - 500
  });
  it("pagar por um virtual quita o grupo todo e usa o id do principal", () => {
    let ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    ds = compra(compra(ds, 10, 300), v.id, 200);
    const r = ok(pagarFatura(ds, v.id, 3, 2025, ctx(dt(2025, 3, 28))));
    expect(r.pagamento.valor).toBe(500);
    expect(r.pagamento.grupoId).toBe("fatura:10:2025-03");
    expect(r.ds.cartoes.map((x) => x.limiteDisponivel)).toEqual([1000, 1000]);
    expect(r.ds.contas[0].saldo).toBe(500);
  });
  it("editar o principal propaga aos virtuais e move as compras de conta", () => {
    let ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    ds = compra(ds, v.id, 100);
    const r = ok(editarCartao(ds, 10, { limiteTotal: 2000, diaFechamento: 10, contaId: 2 }));
    const v2 = r.ds.cartoes.find((x) => x.id === v.id)!;
    expect([v2.limiteTotal, v2.diaFechamento, v2.contaId]).toEqual([2000, 10, 2]);
    expect(r.ds.cartoes.map((x) => x.limiteDisponivel)).toEqual([1900, 1900]);
    expect(r.ds.despesas.find((d) => d.cartaoId === v.id)!.conta).toBe("222");
  });
  it("editar o virtual não altera os campos herdados", () => {
    const ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    const r = ok(editarCartao(ds, v.id, { nome: "Novo nome", limiteTotal: 5 }));
    const v2 = r.ds.cartoes.find((x) => x.id === v.id)!;
    expect([v2.nome, v2.limiteTotal]).toEqual(["Novo nome", 1000]);
    expect(editarCartao(ds, v.id, { cartaoPrincipalId: null }).ok).toBe(true); // R42: vínculo editável
  });
  it("excluir virtual com compra em aberto é bloqueado; pago migra para o principal", () => {
    let ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    ds = compra(ds, v.id, 100);
    expect(excluirCartao(ds, v.id, { forcar: true }).ok).toBe(false);
    ds = ok(pagarFatura(ds, 10, 3, 2025, ctx(dt(2025, 3, 28)))).ds;
    const r = ok(excluirCartao(ds, v.id));
    expect(r.ds.cartoes).toHaveLength(1);
    expect(r.ds.despesas.filter((d) => d.cartaoId === 10)).toHaveLength(1);
  });
  it("excluir principal: bloqueia com aberto; depois remove virtuais e compras", () => {
    let ds = comVirtual();
    const v = ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    ds = compra(ds, v.id, 100);
    expect(excluirCartao(ds, 10, { forcar: true }).ok).toBe(false);
    ds = ok(pagarFatura(ds, 10, 3, 2025, ctx(dt(2025, 3, 28)))).ds;
    const r = ok(excluirCartao(ds, 10, { forcar: true }));
    expect(r.ds.cartoes).toHaveLength(0);
    expect(r.ds.despesas.some((d) => d.cartaoId)).toBe(false);
    expect(r.ds.despesas.some((d) => d.natureza === "PAGAMENTO_FATURA")).toBe(true);
  });
});

describe("R41 limiteProprio", () => {
  const c = ctx(dt(2025, 3, 10));
  const novoV = (extra: object = {}) => ({ nome: "V", finalCartao: "0002", tipo: "x", limiteTotal: 1, diaFechamento: 1, diaVencimento: 1, contaId: 1, cartaoPrincipalId: 10, ...extra });

  it("cria virtual com teto próprio e valida limites", () => {
    const r = ok(criarCartao(base(), novoV({ limiteProprio: 400 }), c));
    expect(r.cartao.limiteProprio).toBe(400);
    expect(ok(criarCartao(base(), novoV(), c)).cartao.limiteProprio).toBeNull();
    expect(ok(criarCartao(base(), novoV({ limiteProprio: 0 }), c)).cartao.limiteProprio).toBeNull();
    for (const v of [-5, 1000.01]) {
      const e = criarCartao(base(), novoV({ limiteProprio: v }), c);
      expect(e.ok).toBe(false);
      if (!e.ok) expect(e.erro).toContain("limite próprio");
    }
  });

  it("editar o físico propaga herdados sem sobrescrever limiteProprio do virtual", () => {
    const v = ok(criarCartao(base(), novoV({ limiteProprio: 400 }), c));
    const r = ok(editarCartao(v.ds, 10, { limiteTotal: 800, limiteProprio: 600 }));
    const virt = r.ds.cartoes.find((x) => x.cartaoPrincipalId === 10)!;
    expect(virt.limiteProprio).toBe(400);
    expect(virt.limiteTotal).toBe(800);
    expect(r.ds.cartoes.find((x) => x.id === 10)!.limiteProprio).toBe(600);
  });

  it("baixar o limite do físico abaixo do limiteProprio de um virtual dá erro", () => {
    const v = ok(criarCartao(base(), novoV({ limiteProprio: 400 }), c));
    expect(editarCartao(v.ds, 10, { limiteTotal: 300 }).ok).toBe(false);
  });

  it("editar virtual altera/limpa limiteProprio", () => {
    const v = ok(criarCartao(base(), novoV({ limiteProprio: 400 }), c));
    const a = ok(editarCartao(v.ds, v.cartao.id, { limiteProprio: 500 }));
    expect(a.ds.cartoes.find((x) => x.id === v.cartao.id)!.limiteProprio).toBe(500);
    const b = ok(editarCartao(v.ds, v.cartao.id, { nome: "Z" }));
    expect(b.ds.cartoes.find((x) => x.id === v.cartao.id)!.limiteProprio).toBe(400);
    const l = ok(editarCartao(v.ds, v.cartao.id, { limiteProprio: null }));
    expect(l.ds.cartoes.find((x) => x.id === v.cartao.id)!.limiteProprio).toBeNull();
    expect(editarCartao(v.ds, v.cartao.id, { limiteProprio: 5000 }).ok).toBe(false);
  });
});
