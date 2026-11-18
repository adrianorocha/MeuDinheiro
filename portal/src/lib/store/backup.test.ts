import { describe, expect, it } from "vitest";
import { saldoConta } from "../finance/calc";
import { dt } from "../finance/fixtures";
import { criarDatasetDemo } from "../demo";
import { lancamentosParaCsv } from "../csv";
import { exportarBackup, importarBackup } from "./backup";
import { diffDataset } from "./diff";
import { emptyDataset } from "../finance/types";

const agora = dt(2025, 6, 15);

describe("backup v2", () => {
  it("round-trip preserva ids, datas e vínculos", () => {
    const demo = criarDatasetDemo(agora);
    const json = JSON.stringify(exportarBackup(demo));
    const r = importarBackup(json, agora);
    if (!r.ok) throw new Error(r.erro);
    expect(r.versao).toBe(2);
    expect(r.dataset.despesas.map((d) => d.id)).toEqual(demo.despesas.map((d) => d.id));
    expect(r.dataset.cartoes.map((c) => c.contaId)).toEqual(demo.cartoes.map((c) => c.contaId));
    expect(r.dataset.despesas[0].data).toBe(demo.despesas[0].data);
    expect(r.dataset.transacoes).toEqual(demo.transacoes);
    expect(diffDataset(r.dataset, r.dataset)).toEqual([]);
  });

  it("aceita backup v1 legado com data em texto e sem pago/natureza", () => {
    const v1 = {
      contas: [{ id: 3, saldo: 0, banco: "X", pic: "", agencia: "1", conta: "10", titular: "T" }],
      despesas: [
        { id: 7, descricao: "Antiga", valor: 10.005, data: "2024-03-10T12:00:00", categoria: "A", conta: "10", pic: "", tipo: "DEBITO", mes: 3, ano: 2024 },
        { id: 0, descricao: "Sem data válida", valor: 5, data: "ontem?", categoria: "A", conta: "10", pic: "", tipo: "CREDITO", mes: 1, ano: 2024 },
      ],
    };
    const r = importarBackup(JSON.stringify(v1), agora);
    if (!r.ok) throw new Error(r.erro);
    expect(r.versao).toBe(1);
    const [a, b] = r.dataset.despesas;
    expect(a.id).toBe(7);
    expect(a.valor).toBe(10.01);
    expect(a.data).toBe(new Date("2024-03-10T12:00:00").getTime());
    expect(a.pago).toBe(true);
    expect(a.natureza).toBe("NORMAL");
    expect(b.data).toBe(agora);
    expect(b.id).toBeGreaterThan(0);
    expect(b.id).not.toBe(a.id);
    expect(r.dataset.contas[0].saldo).toBe(-10.01 + 5);
  });

  it("cartaoPrincipalId: ausente vira null e é preservado no round-trip", () => {
    const base = { contas: [{ id: 1, saldo: 0, banco: "X", pic: "", agencia: "", conta: "1", titular: "" }] };
    const cartao = { nome: "F", finalCartao: "1", tipo: "CRÉDITO", limiteDisponivel: 10, limiteTotal: 10, diaFechamento: 1, diaVencimento: 5, contaId: 1 };
    const r = importarBackup(JSON.stringify({ ...base, cartoes: [{ id: 5, ...cartao }, { id: 6, ...cartao, nome: "V", cartaoPrincipalId: 5 }] }), agora);
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.cartoes.map((c) => c.cartaoPrincipalId)).toEqual([null, 5]);
    const volta = importarBackup(JSON.stringify(exportarBackup(r.dataset)), agora);
    if (!volta.ok) throw new Error(volta.erro);
    expect(volta.dataset.cartoes.map((c) => c.cartaoPrincipalId)).toEqual([null, 5]);
  });

  it("limiteProprio: ausente vira null e é preservado no round-trip", () => {
    const base = { contas: [{ id: 1, saldo: 0, banco: "X", pic: "", agencia: "", conta: "1", titular: "" }] };
    const cartao = { nome: "F", finalCartao: "1", tipo: "CRÉDITO", limiteDisponivel: 10, limiteTotal: 10, diaFechamento: 1, diaVencimento: 5, contaId: 1 };
    const r = importarBackup(JSON.stringify({ ...base, cartoes: [{ id: 5, ...cartao }, { id: 6, ...cartao, cartaoPrincipalId: 5, limiteProprio: 4.5 }] }), agora);
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.cartoes.map((c) => c.limiteProprio)).toEqual([null, 4.5]);
    const volta = importarBackup(JSON.stringify(exportarBackup(r.dataset)), agora);
    if (!volta.ok) throw new Error(volta.erro);
    expect(volta.dataset.cartoes.map((c) => c.limiteProprio)).toEqual([null, 4.5]);
  });

  it("rejeita JSON inválido e estrutura incorreta", () => {
    expect(importarBackup("{nao json", agora).ok).toBe(false);
    expect(importarBackup("[]", agora).ok).toBe(false);
    expect(importarBackup(JSON.stringify({ despesas: [{ id: 1, valor: "abc" }] }), agora).ok).toBe(false);
  });

  it("backup vazio resulta em dataset vazio", () => {
    const r = importarBackup(JSON.stringify({ versaoBackup: 2 }), agora);
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset).toEqual(emptyDataset());
  });
});

describe("dataset de demonstração", () => {
  const demo = criarDatasetDemo(agora);
  it("tem caches consistentes e saldos não negativos", () => {
    for (const c of demo.contas) {
      expect(c.saldo).toBe(saldoConta(demo.despesas, c.conta));
      expect(c.saldo).toBeGreaterThanOrEqual(0);
    }
    for (const k of demo.cartoes) expect(k.limiteDisponivel).toBeLessThanOrEqual(k.limiteTotal);
  });
  it("cobre as coleções principais", () => {
    expect(demo.categorias).toHaveLength(15);
    expect(demo.contas.length).toBeGreaterThan(1);
    expect(demo.patrimonio.length).toBeGreaterThanOrEqual(11);
    expect(demo.despesas.some((d) => d.natureza === "PAGAMENTO_FATURA")).toBe(true);
    expect(demo.despesas.some((d) => d.natureza === "TRANSFERENCIA")).toBe(true);
  });
});

describe("csv", () => {
  it("escapa campos e usa vírgula decimal", () => {
    const d = criarDatasetDemo(agora).despesas[0];
    const csv = lancamentosParaCsv([{ ...d, descricao: 'Com ; e "aspas"', valor: 12.5, tipo: "DEBITO" }]);
    expect(csv.startsWith("﻿Data;")).toBe(true);
    expect(csv).toContain('"Com ; e ""aspas"""');
    expect(csv).toContain(";-12,50;");
  });
});
