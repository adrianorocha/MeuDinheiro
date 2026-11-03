import { describe, expect, it } from "vitest";
import { cartao, conta, dataset, dt } from "./fixtures";
import {
  criarCartao,
  criarDespesaFixa,
  editarCartao,
  editarDespesaFixa,
  excluirCartao,
  excluirConta,
  processarDespesasFixas,
  recorrenciasDoCartao,
  type Ctx,
} from "./operations";
import { importarBackup } from "../store/backup";
import type { Dataset } from "./types";

const ctx = (agora: number): Ctx => ({ agora, uuid: () => "u", rng: (() => { let s = 0.2; return () => (s = (s + 0.13) % 1); })() });

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}

const base = (): Dataset =>
  dataset({
    contas: [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222", banco: "B2" })],
    cartoes: [cartao({ id: 10, contaId: 1 }), cartao({ id: 11, nome: "Virtual", contaId: 1, cartaoPrincipalId: 10, limiteTotal: 1000 })],
  });

const regra = { descricao: "Streaming", valor: 40, conta: "111", categoria: "Lazer", pic: "", tipo: "DEBITO" as const, diaVencimento: 5 };

describe("R16 com cartão", () => {
  it("ocorrência vira compra no cartão, na conta do cartão, pendente e consumindo limite", () => {
    const ds = ok(criarDespesaFixa(base(), { ...regra, conta: "222", cartaoId: 10 }, ctx(dt(2025, 3, 1)))).ds;
    expect(ds.despesasFixas[0].conta).toBe("111"); // conta segue o cartão
    const r = ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 10))));
    expect(r.criados).toHaveLength(1);
    expect(r.criados[0]).toMatchObject({ cartaoId: 10, conta: "111", pago: false, natureza: "NORMAL" });
    expect(r.ds.cartoes.map((c) => c.limiteDisponivel)).toEqual([960, 960]);
    expect(r.ds.contas[0].saldo).toBe(0); // não debita a conta
  });
  it("cartão virtual: compra no virtual e limite do grupo", () => {
    const ds = ok(criarDespesaFixa(base(), { ...regra, cartaoId: 11 }, ctx(dt(2025, 3, 1)))).ds;
    const r = ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 10))));
    expect(r.criados[0].cartaoId).toBe(11);
    expect(r.ds.cartoes.map((c) => c.limiteDisponivel)).toEqual([960, 960]);
  });
  it("catch-up de vários meses em cartão e idempotência", () => {
    let ds = ok(criarDespesaFixa(base(), { ...regra, cartaoId: 10 }, ctx(dt(2025, 1, 1)))).ds;
    ds = { ...ds, despesasFixas: ds.despesasFixas.map((f) => ({ ...f, ultimaDataLancamento: dt(2025, 1, 5) })) };
    const r = ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 10))));
    expect(r.criados.map((d) => d.mes)).toEqual([2, 3]);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(920);
    expect(ok(processarDespesasFixas(r.ds, ctx(dt(2025, 3, 11)))).criados).toHaveLength(0);
  });
  it("sem cartaoId mantém o comportamento na conta", () => {
    const ds = ok(criarDespesaFixa(base(), regra, ctx(dt(2025, 3, 1)))).ds;
    expect(ds.despesasFixas[0].cartaoId).toBeNull();
    const r = ok(processarDespesasFixas(ds, ctx(dt(2025, 3, 10))));
    expect(r.criados[0]).toMatchObject({ cartaoId: null, conta: "111", pago: false });
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(1000);
  });
  it("cartão inexistente ao criar é recusado", () => {
    expect(criarDespesaFixa(base(), { ...regra, cartaoId: 999 }, ctx(0)).ok).toBe(false);
  });
});

describe("troca de origem e vínculos", () => {
  it("editar alterna conta <-> cartão", () => {
    let ds = ok(criarDespesaFixa(base(), regra, ctx(0))).ds;
    const id = ds.despesasFixas[0].id;
    ds = ok(editarDespesaFixa(ds, id, { cartaoId: 11 })).ds;
    expect(ds.despesasFixas[0]).toMatchObject({ cartaoId: 11, conta: "111" });
    ds = ok(editarDespesaFixa(ds, id, { cartaoId: null, conta: "222" })).ds;
    expect(ds.despesasFixas[0]).toMatchObject({ cartaoId: null, conta: "222" });
    expect(editarDespesaFixa(ds, id, { cartaoId: 999 }).ok).toBe(false);
    expect(editarDespesaFixa(ds, id, { conta: "zzz" }).ok).toBe(false);
  });
  it("trocar a conta do cartão move as regras vinculadas", () => {
    let ds = ok(criarDespesaFixa(base(), { ...regra, cartaoId: 11 }, ctx(0))).ds;
    ds = ok(editarCartao(ds, 10, { contaId: 2 })).ds;
    expect(ds.despesasFixas[0].conta).toBe("222");
  });
  it("excluir cartão (ou virtual) com recorrência vinculada é bloqueado", () => {
    const ds = ok(criarDespesaFixa(base(), { ...regra, cartaoId: 11 }, ctx(0))).ds;
    expect(recorrenciasDoCartao(ds, 10)).toHaveLength(1);
    expect(excluirCartao(ds, 11, { forcar: true }).ok).toBe(false);
    expect(excluirCartao(ds, 10, { forcar: true }).ok).toBe(false);
    const livre = ok(editarDespesaFixa(ds, ds.despesasFixas[0].id, { cartaoId: null })).ds;
    expect(excluirCartao(livre, 11).ok).toBe(true);
  });
  it("excluir conta confirmada remove também as regras vinculadas (inclusive em cartão)", () => {
    const ds = ok(criarDespesaFixa(base(), { ...regra, cartaoId: 10 }, ctx(0))).ds;
    const r = ok(excluirConta(ds, 1, { forcar: true }));
    expect(r.ds.despesasFixas).toHaveLength(0);
  });
  it("virtual criado depois não afeta regra do principal", () => {
    const ds = ok(criarDespesaFixa(base(), { ...regra, cartaoId: 10 }, ctx(0))).ds;
    const v = ok(criarCartao(ds, { nome: "V2", finalCartao: "", tipo: "", limiteTotal: 0, diaFechamento: 1, diaVencimento: 1, contaId: 1, cartaoPrincipalId: 10 }, ctx(0)));
    expect(v.ds.despesasFixas[0].cartaoId).toBe(10);
  });
});

describe("backup", () => {
  it("regra sem cartaoId (backup antigo) ou 0 vira null; com cartaoId é preservado", () => {
    const json = {
      despesasFixas: [
        { id: 1, descricao: "A", valor: 1, conta: "1", categoria: "x", pic: "", tipo: "DEBITO", diaVencimento: 5 },
        { id: 2, descricao: "B", valor: 1, conta: "1", categoria: "x", pic: "", tipo: "DEBITO", diaVencimento: 5, cartaoId: 0 },
        { id: 3, descricao: "C", valor: 1, conta: "1", categoria: "x", pic: "", tipo: "DEBITO", diaVencimento: 5, cartaoId: 77 },
      ],
    };
    const r = importarBackup(JSON.stringify(json), 0);
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.despesasFixas.map((f) => f.cartaoId)).toEqual([null, null, 77]);
  });
});
