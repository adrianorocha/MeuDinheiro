import { describe, expect, it } from "vitest";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import {
  adicionarLancamento,
  duplicarLancamento,
  esvaziarLixeira,
  excluirLancamento,
  purgarLixeira,
  repetirLancamento,
  restaurarDaLixeira,
  transferir,
  type Ctx,
} from "./operations";
import { exportarBackup, importarBackup } from "../store/backup";
import type { Dataset } from "./types";

let n = 0;
const ctx = (agora: number): Ctx => ({ agora, uuid: () => `u${++n}`, rng: (() => { let s = 0.3; return () => (s = (s + 0.171) % 1); })() });

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}

const base = (): Dataset =>
  dataset({
    contas: [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222" })],
    cartoes: [cartao({ id: 10, contaId: 1 })],
    despesas: [desp({ id: 1, tipo: "CREDITO", valor: 1000, natureza: "SALDO_INICIAL" }), desp({ id: 2, valor: 100, descricao: "Mercado", data: dt(2025, 1, 31) })],
  });

describe("R31 lixeira", () => {
  it("excluir envia o JSON para a lixeira e restaurar recria com o mesmo id", () => {
    const e = ok(excluirLancamento(base(), 2, {}, ctx(dt(2025, 3, 1))));
    expect(e.ds.lixeira).toHaveLength(1);
    expect(e.ds.lixeira[0]).toMatchObject({ tipo: "DESPESA", descricao: "Mercado", valor: 100, excluidoEm: dt(2025, 3, 1) });
    expect(e.ds.contas[0].saldo).toBe(1000);
    const r = ok(restaurarDaLixeira(e.ds, e.ds.lixeira[0].id, ctx(dt(2025, 3, 2))));
    expect(r.restaurados[0].id).toBe(2);
    expect(r.ds.lixeira).toHaveLength(0);
    expect(r.ds.contas[0].saldo).toBe(900);
  });
  it("restaurar usa novo id se o original estiver ocupado", () => {
    const e = ok(excluirLancamento(base(), 2, {}, ctx(dt(2025, 3, 1))));
    const ocupado = { ...e.ds, despesas: [...e.ds.despesas, desp({ id: 2, valor: 1 })] };
    const r = ok(restaurarDaLixeira(ocupado, ocupado.lixeira[0].id, ctx(dt(2025, 3, 1))));
    expect(r.restaurados[0].id).not.toBe(2);
    expect(new Set(r.ds.despesas.map((d) => d.id)).size).toBe(r.ds.despesas.length);
  });
  it("saldo inicial não vai para a lixeira; fatura é bloqueada", () => {
    const e = ok(excluirLancamento(base(), 1, {}, ctx(0)));
    expect(e.ds.lixeira).toHaveLength(0);
    const fat = { ...base(), despesas: [desp({ id: 5, natureza: "PAGAMENTO_FATURA" })] };
    expect(excluirLancamento(fat, 5, {}, ctx(0)).ok).toBe(false);
  });
  it("transferência: um registro por lado e restaura o par", () => {
    const t = ok(transferir(base(), { origem: "111", destino: "222", valor: 300 }, ctx(dt(2025, 3, 1))));
    const alvo = t.ds.despesas.find((d) => d.natureza === "TRANSFERENCIA")!;
    const e = ok(excluirLancamento(t.ds, alvo.id, {}, ctx(dt(2025, 3, 2))));
    expect(e.ds.lixeira).toHaveLength(2);
    expect(e.ds.contas.map((c) => c.saldo)).toEqual([900, 0]);
    const r = ok(restaurarDaLixeira(e.ds, e.ds.lixeira[0].id, ctx(dt(2025, 3, 3))));
    expect(r.restaurados).toHaveLength(2);
    expect(r.ds.contas.map((c) => c.saldo)).toEqual([600, 300]);
    expect(r.ds.lixeira).toHaveLength(0);
  });
  it("restaurar falha sem a conta e com payload corrompido", () => {
    const e = ok(excluirLancamento(base(), 2, {}, ctx(dt(2025, 3, 1))));
    expect(restaurarDaLixeira({ ...e.ds, contas: [] }, e.ds.lixeira[0].id, ctx(0)).ok).toBe(false);
    expect(restaurarDaLixeira({ ...e.ds, lixeira: [{ ...e.ds.lixeira[0], payload: "{x" }] }, e.ds.lixeira[0].id, ctx(0)).ok).toBe(false);
    expect(restaurarDaLixeira(e.ds, 999, ctx(0)).ok).toBe(false);
  });
  it("purga só itens com mais de 30 dias; esvaziar limpa tudo", () => {
    const e = ok(excluirLancamento(base(), 2, {}, ctx(dt(2025, 3, 1))));
    expect(purgarLixeira(e.ds, dt(2025, 3, 31))).toBe(e.ds); // exatamente 30 dias: mantém
    expect(purgarLixeira(e.ds, dt(2025, 3, 31, 13)).lixeira).toHaveLength(0);
    expect(esvaziarLixeira(e.ds).lixeira).toHaveLength(0);
    expect(esvaziarLixeira(base())).toEqual(base());
  });
  it("excluir todas as parcelas envia cada uma", () => {
    const r = ok(adicionarLancamento(base(), { descricao: "TV", valor: 90, data: dt(2025, 1, 5), categoria: "C", conta: "111", tipo: "DEBITO", parcelas: 3 }, ctx(dt(2025, 1, 5))));
    const e = ok(excluirLancamento(r.ds, r.criados[0].id, { grupoParcelas: true }, ctx(dt(2025, 1, 6))));
    expect(e.ds.lixeira).toHaveLength(3);
  });
});

describe("R23 duplicar e repetir", () => {
  it("duplicar: data agora, pendente, novo id, sem grupo; mantém cartão", () => {
    const ds = ok(adicionarLancamento(base(), { descricao: "Notebook", valor: 100, data: dt(2025, 1, 5), categoria: "C", tipo: "DEBITO", cartaoId: 10, parcelas: 2 }, ctx(dt(2025, 1, 5)))).ds;
    const origem = ds.despesas.find((d) => d.cartaoId === 10)!;
    const r = ok(duplicarLancamento(ds, origem.id, ctx(dt(2025, 4, 10))));
    expect(r.criado.data).toBe(dt(2025, 4, 10));
    expect(r.criado.pago).toBe(false);
    expect(r.criado.grupoId).toBeNull();
    expect(r.criado.cartaoId).toBe(10);
    expect(r.criado.id).not.toBe(origem.id);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(1000 - 100 - 50);
  });
  it("não duplica transferência/fatura/aporte", () => {
    const t = ok(transferir(base(), { origem: "111", destino: "222", valor: 10 }, ctx(dt(2025, 1, 1))));
    const alvo = t.ds.despesas.find((d) => d.natureza === "TRANSFERENCIA")!;
    expect(duplicarLancamento(t.ds, alvo.id, ctx(0)).ok).toBe(false);
    expect(repetirLancamento(t.ds, alvo.id, { n: 1, intervalo: 1, unidade: "DIAS" }, ctx(0)).ok).toBe(false);
  });
  it("repetir mensal sem deriva (31/01)", () => {
    const r = ok(repetirLancamento(base(), 2, { n: 3, intervalo: 1, unidade: "MESES" }, ctx(dt(2025, 2, 1))));
    expect(r.criados.map((d) => new Date(d.data).getDate())).toEqual([28, 31, 30]);
    expect(r.criados.every((d) => !d.pago && d.grupoId?.startsWith("rep:"))).toBe(true);
    expect(new Set(r.criados.map((d) => d.grupoId)).size).toBe(1);
  });
  it("repetir a cada 2 semanas e a cada 10 dias", () => {
    const sem = ok(repetirLancamento(base(), 2, { n: 2, intervalo: 2, unidade: "SEMANAS" }, ctx(0)));
    expect(sem.criados.map((d) => new Date(d.data).getDate())).toEqual([14, 28]); // 31/01 + 14 = 14/02
    const dias = ok(repetirLancamento(base(), 2, { n: 2, intervalo: 10, unidade: "DIAS" }, ctx(0)));
    expect(dias.criados.map((d) => [new Date(d.data).getMonth() + 1, new Date(d.data).getDate()])).toEqual([[2, 10], [2, 20]]);
  });
  it("valida n e intervalo", () => {
    expect(repetirLancamento(base(), 2, { n: 0, intervalo: 1, unidade: "DIAS" }, ctx(0)).ok).toBe(false);
    expect(repetirLancamento(base(), 2, { n: 1, intervalo: 0, unidade: "DIAS" }, ctx(0)).ok).toBe(false);
    expect(repetirLancamento(base(), 2, { n: 1000, intervalo: 1, unidade: "DIAS" }, ctx(0)).ok).toBe(false);
  });
});

describe("campos novos no backup", () => {
  it("autor, dataAlvo e lixeira fazem round-trip; ausentes viram null/vazio", () => {
    const ds = ok(adicionarLancamento(base(), { descricao: "x", valor: 5, data: dt(2025, 1, 5), categoria: "C", conta: "111", tipo: "DEBITO", autor: "Ana" }, ctx(dt(2025, 1, 5)))).ds;
    const comMeta: Dataset = { ...ds, metas: [{ id: 3, nome: "M", valorObjetivo: 10, valorGuardado: 0, icone: "", dataAlvo: dt(2026, 1, 1) }] };
    const e = ok(excluirLancamento(comMeta, 2, {}, ctx(dt(2025, 2, 1))));
    const r = importarBackup(JSON.stringify(exportarBackup(e.ds)), dt(2025, 2, 2));
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.despesas.find((d) => d.descricao === "x")?.autor).toBe("Ana");
    expect(r.dataset.metas[0].dataAlvo).toBe(dt(2026, 1, 1));
    expect(r.dataset.lixeira).toHaveLength(1);
    const antigo = importarBackup(JSON.stringify({ despesas: [{ id: 1, valor: 1, tipo: "DEBITO" }], metas: [{ nome: "x", valorObjetivo: 1 }] }), 0);
    if (!antigo.ok) throw new Error(antigo.erro);
    expect(antigo.dataset.despesas[0].autor).toBeNull();
    expect(antigo.dataset.metas[0].dataAlvo).toBeNull();
    expect(antigo.dataset.lixeira).toEqual([]);
  });
});
