import { describe, expect, it } from "vitest";
import { contasDoMes } from "./contasMes";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import { baixarContaMensal, desabilitarContaMensal, grupoOcorrencia, processarDespesasFixas, provisionarRecorrencia } from "./operations";
import { saldoConta } from "./calc";
import type { Dataset, DespesaFixa } from "./types";

const AGORA = dt(2025, 3, 10);
const ctx = { agora: AGORA };

const regra = (over: Partial<DespesaFixa> = {}): DespesaFixa => ({
  id: 1,
  descricao: "Conta de luz",
  valor: 200,
  conta: "111",
  categoria: "Moradia",
  pic: "",
  tipo: "DEBITO",
  diaVencimento: 15,
  ultimaDataLancamento: null,
  cartaoId: null,
  ...over,
});

const base = (fixas: DespesaFixa[] = [regra()]): Dataset =>
  dataset({
    contas: [conta({ id: 1, conta: "111", banco: "Banco A" }), conta({ id: 2, conta: "222", banco: "Banco B" })],
    despesasFixas: fixas,
  });

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error((r as unknown as { erro: string }).erro);
  return r as Extract<T, { ok: true }>;
}

describe("provisionarRecorrencia", () => {
  it("cria as ocorrências pendentes do mês corrente e dos próximos, sem mexer em ultimaDataLancamento", () => {
    const r = ok(provisionarRecorrencia(base(), 1, 3, ctx));
    expect(r.criados.map((d) => d.grupoId)).toEqual([grupoOcorrencia(1, 3, 2025), grupoOcorrencia(1, 4, 2025), grupoOcorrencia(1, 5, 2025)]);
    expect(r.criados.every((d) => !d.pago && d.valor === 200 && d.conta === "111" && d.tipo === "DEBITO")).toBe(true);
    expect(r.criados[0].data).toBe(new Date(2025, 2, 15).getTime());
    expect(r.ds.despesasFixas[0].ultimaDataLancamento).toBeNull();
  });

  it("é idempotente e o R16 não duplica quando o mês chega", () => {
    const um = ok(provisionarRecorrencia(base(), 1, 2, ctx));
    const dois = ok(provisionarRecorrencia(um.ds, 1, 2, ctx));
    expect(dois.criados).toHaveLength(0);
    const depois = ok(processarDespesasFixas(um.ds, { agora: dt(2025, 3, 20) }));
    expect(depois.criados).toHaveLength(0);
    expect(depois.ds.despesas.filter((d) => d.grupoId === grupoOcorrencia(1, 3, 2025))).toHaveLength(1);
  });

  it("vira o ano e valida o número de meses", () => {
    const r = ok(provisionarRecorrencia(base(), 1, 12, { agora: dt(2025, 11, 5) }));
    expect(r.criados.map((d) => d.grupoId?.slice(-7))).toContain("2026-01");
    expect(provisionarRecorrencia(base(), 1, 0, ctx).ok).toBe(false);
    expect(provisionarRecorrencia(base(), 1, 13, ctx).ok).toBe(false);
    expect(provisionarRecorrencia(base(), 99, 2, ctx).ok).toBe(false);
  });

  it("recusa conta mensal desabilitada", () => {
    const r = provisionarRecorrencia(base([regra({ pausada: true, pausadaAte: null })]), 1, 2, ctx);
    expect(r.ok).toBe(false);
  });
});

describe("baixarContaMensal", () => {
  const provisionado = () => ok(provisionarRecorrencia(base(), 1, 1, ctx)).ds;

  it("baixa na conta escolhida, com o valor e a data reais, e debita o saldo certo", () => {
    const ds = provisionado();
    const id = ds.despesas[0].id;
    const r = ok(baixarContaMensal(ds, id, { conta: "222", valor: 187.35, data: dt(2025, 3, 12) }, ctx));
    const d = r.ds.despesas.find((x) => x.id === id)!;
    expect(d).toMatchObject({ pago: true, conta: "222", valor: 187.35, data: dt(2025, 3, 12) });
    expect(saldoConta(r.ds.despesas, "222")).toBe(-187.35);
    expect(saldoConta(r.ds.despesas, "111")).toBe(0);
    expect(r.ds.despesasFixas[0].valor).toBe(200); // regra intacta sem atualizarRegra
  });

  it("atualizarRegra grava o novo valor para os próximos meses", () => {
    const ds = provisionado();
    const r = ok(baixarContaMensal(ds, ds.despesas[0].id, { valor: 215.9, atualizarRegra: true }, ctx));
    expect(r.ds.despesasFixas[0].valor).toBe(215.9);
  });

  it("valida: já pago, conta inexistente, valor inválido, cartão, lançamento sem regra", () => {
    const ds = provisionado();
    const id = ds.despesas[0].id;
    expect(baixarContaMensal(ds, id, { conta: "999" }, ctx).ok).toBe(false);
    expect(baixarContaMensal(ds, id, { valor: 0 }, ctx).ok).toBe(false);
    const paga = ok(baixarContaMensal(ds, id, {}, ctx)).ds;
    expect(baixarContaMensal(paga, id, {}, ctx).ok).toBe(false);
    const avulso = { ...ds, despesas: [{ ...ds.despesas[0], grupoId: null }] };
    expect(baixarContaMensal(avulso, id, { valor: 10, atualizarRegra: true }, ctx).ok).toBe(false);
    const noCartao = dataset({
      contas: base().contas,
      cartoes: [cartao({ id: 10, contaId: 1 })],
      despesasFixas: [regra({ cartaoId: 10 })],
    });
    const prov = ok(provisionarRecorrencia(noCartao, 1, 1, ctx)).ds;
    expect(baixarContaMensal(prov, prov.despesas[0].id, {}, ctx).ok).toBe(false);
  });
});

describe("desabilitarContaMensal", () => {
  it("pausa e remove só as provisionadas futuras em aberto (lixeira); vencidas e pagas ficam", () => {
    let ds = ok(provisionarRecorrencia(base(), 1, 4, ctx)).ds; // 15/03, 15/04, 15/05, 15/06
    const marco = ds.despesas.find((d) => d.grupoId === grupoOcorrencia(1, 3, 2025))!;
    ds = ok(baixarContaMensal(ds, marco.id, {}, ctx)).ds;
    const r = ok(desabilitarContaMensal(ds, 1, { removerProvisionadas: true }, ctx));
    expect(r.removidas).toBe(3);
    expect(r.ds.despesas.map((d) => d.grupoId)).toEqual([grupoOcorrencia(1, 3, 2025)]);
    expect(r.ds.despesasFixas[0].pausada).toBe(true);
    expect(r.ds.lixeira.length).toBe(3);
  });

  it("sem removerProvisionadas só pausa", () => {
    const ds = ok(provisionarRecorrencia(base(), 1, 2, ctx)).ds;
    const r = ok(desabilitarContaMensal(ds, 1, {}, ctx));
    expect(r.removidas).toBe(0);
    expect(r.ds.despesas).toHaveLength(2);
  });
});

describe("contasDoMes", () => {
  it("classifica: não lançada, provisionada, pendente, vencida, paga, desabilitada", () => {
    const fixas = [
      regra({ id: 1, descricao: "Luz", diaVencimento: 15 }),
      regra({ id: 2, descricao: "Telefone", diaVencimento: 5 }),
      regra({ id: 3, descricao: "Gás", diaVencimento: 25 }),
      regra({ id: 4, descricao: "Água", diaVencimento: 8 }),
      regra({ id: 5, descricao: "Internet", diaVencimento: 20, pausada: true, pausadaAte: null }),
      regra({ id: 6, descricao: "Salário", tipo: "CREDITO" }),
    ];
    let ds = base(fixas);
    ds = ok(provisionarRecorrencia(ds, 1, 1, ctx)).ds; // Luz provisionada (15/03 > hoje 10/03)
    ds = ok(provisionarRecorrencia(ds, 2, 1, ctx)).ds; // Telefone 05/03: vencido
    ds = ok(provisionarRecorrencia(ds, 4, 1, ctx)).ds; // Água 08/03
    const agua = ds.despesas.find((d) => d.grupoId === grupoOcorrencia(4, 3, 2025))!;
    ds = ok(baixarContaMensal(ds, agua.id, { valor: 90 }, ctx)).ds;
    const r = contasDoMes(ds, 3, 2025, AGORA);
    const est = Object.fromEntries(r.linhas.map((l) => [l.regra.descricao, l.estado]));
    expect(est).toEqual({ Luz: "PROVISIONADA", Telefone: "VENCIDA", Gás: "NAO_LANCADA", Água: "PAGA", Internet: "DESABILITADA" });
    expect(r.vencidas).toBe(1);
    expect(r.previsto).toBe(200 + 200 + 200 + 90); // exclui desabilitada
    expect(r.pago).toBe(90);
    expect(r.aPagar).toBe(600);
    expect(r.linhas.find((l) => l.regra.descricao === "Água")?.valorDifere).toBe(true);
    expect(r.linhas.map((l) => l.regra.descricao)).not.toContain("Salário");
  });

  it("reconhece lançamento antigo do app sem grupoId (um só) e não o duplica ao provisionar", () => {
    const ds0 = base();
    const legado = { ...desp({ id: 900, descricao: "conta de LUZ", valor: 190, data: dt(2025, 3, 14), tipo: "DEBITO", conta: "111", pago: false }), mes: 3, ano: 2025 };
    const ds1 = { ...ds0, despesas: [legado] };
    const r = contasDoMes(ds1, 3, 2025, AGORA);
    expect(r.linhas[0].ocorrencia?.id).toBe(900);
    const p = ok(provisionarRecorrencia(ds1, 1, 2, ctx));
    expect(p.criados.map((d) => d.grupoId)).toEqual([grupoOcorrencia(1, 4, 2025)]);
    const b = ok(baixarContaMensal(ds1, 900, { valor: 190, atualizarRegra: true, regraId: 1 }, ctx));
    expect(b.ds.despesasFixas[0].valor).toBe(190);
  });

  it("mês passado sem lançamento não lista a conta", () => {
    expect(contasDoMes(base(), 1, 2025, AGORA).linhas).toHaveLength(0);
  });
});
