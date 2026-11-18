import { describe, expect, it } from "vitest";
import { alertasOrcamento } from "./analises";
import { chaveOrcamento, progressoOrcamento } from "./calc";
import { conta, dataset, desp, dt } from "./fixtures";
import { exportarBackup, importarBackup } from "../store/backup";
import type { Acerto, Dataset, Orcamento } from "./types";

const acerto = (over: Partial<Acerto>): Acerto => ({ id: 1, pessoaId: 7, tipo: "A_RECEBER", valor: 0, descricao: "x", data: dt(2025, 3, 10), despesaId: null, quitado: false, ...over });

function base(): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" })],
    pessoas: [{ id: 7, nome: "João" }, { id: 8, nome: "Maria" }],
    despesas: [
      desp({ id: 100, valor: 200, categoria: "Combustível", conta: "111", data: dt(2025, 3, 5) }), // meu
      desp({ id: 101, valor: 300, categoria: "Combustível", conta: "111", data: dt(2025, 3, 8) }), // do João
      desp({ id: 102, valor: 100, categoria: "Combustível", conta: "111", data: dt(2025, 3, 9) }), // rateio: 40 Maria
    ],
    acertos: [
      acerto({ id: 1, pessoaId: 7, valor: 300, despesaId: 101 }),
      acerto({ id: 2, pessoaId: 8, valor: 40, despesaId: 102, quitado: true }),
    ],
  });
}
const orc = (over: Partial<Orcamento> = {}): Orcamento => ({ id: 1, categoria: "Combustível", valorLimite: 500, ...over });
const gasto = (o: Orcamento, ds = base()) => progressoOrcamento(o, ds.despesas, 3, 2025, ds.acertos).gasto;

describe("R80 - orçamento por pessoa", () => {
  it("TODOS conta tudo, como sempre (com ou sem acertos)", () => {
    expect(gasto(orc())).toBe(600);
    expect(progressoOrcamento(orc(), base().despesas, 3, 2025).gasto).toBe(600);
  });
  it("EU desconta o que está a receber de outras pessoas (inclusive acerto quitado e rateio parcial)", () => {
    expect(gasto(orc({ escopo: "EU" }))).toBe(260); // 200 + 0 + 60
    expect(progressoOrcamento(orc({ escopo: "EU", valorLimite: 250 }), base().despesas, 3, 2025, base().acertos).status).toBe("ESTOURADO");
  });
  it("PESSOA soma só os acertos dela", () => {
    expect(gasto(orc({ escopo: "PESSOA", pessoaId: 7 }))).toBe(300);
    expect(gasto(orc({ escopo: "PESSOA", pessoaId: 8 }))).toBe(40);
    expect(gasto(orc({ escopo: "PESSOA", pessoaId: 99 }))).toBe(0);
  });
  it("acerto acima do valor do lançamento é limitado; A_PAGAR e avulsos não contam", () => {
    const ds = base();
    ds.acertos.push(acerto({ id: 3, pessoaId: 7, valor: 999, despesaId: 100 }), acerto({ id: 4, tipo: "A_PAGAR", valor: 50, despesaId: 100 }), acerto({ id: 5, valor: 50, despesaId: null }));
    expect(gasto(orc({ escopo: "EU" }), ds)).toBe(0 + 0 + 60 - 0); // 100 totalmente de terceiros; 101 e 102 como antes
    expect(gasto(orc({ escopo: "PESSOA", pessoaId: 7 }), ds)).toBe(300 + 200);
  });
  it("estorno de cartão abate só no escopo EU", () => {
    const ds = base();
    ds.despesas.push(desp({ id: 103, valor: 20, categoria: "Combustível", tipo: "CREDITO", cartaoId: 10, conta: "111", data: dt(2025, 3, 12) }));
    expect(gasto(orc({ escopo: "EU" }), ds)).toBe(240);
    expect(gasto(orc({ escopo: "PESSOA", pessoaId: 7 }), ds)).toBe(300);
    expect(gasto(orc(), ds)).toBe(580);
  });
  it("chaves distintas permitem vários orçamentos da mesma categoria", () => {
    const k = [orc(), orc({ escopo: "EU" }), orc({ escopo: "PESSOA", pessoaId: 7 }), orc({ escopo: "PESSOA", pessoaId: 8 })].map(chaveOrcamento);
    expect(new Set(k).size).toBe(4);
  });
  it("alertas: chave própria por escopo e rótulo com a pessoa", () => {
    const ds = { ...base(), orcamentos: [orc({ id: 1, valorLimite: 300 }), orc({ id: 2, escopo: "PESSOA", pessoaId: 7, valorLimite: 300 })] };
    const r = alertasOrcamento(ds, dt(2025, 3, 20), new Set());
    expect(r.alertas.map((a) => a.chave)).toEqual([...new Set(r.alertas.map((a) => a.chave))]);
    expect(r.alertas.some((a) => a.categoria.includes("só João"))).toBe(true);
    expect(r.alertas.some((a) => a.chave.endsWith("|2025-03|100") && !a.chave.includes("#"))).toBe(true);
  });
  it("backup: ida e volta com escopo e leitura de backup antigo", () => {
    const ds = { ...base(), orcamentos: [orc({ id: 1 }), orc({ id: 2, escopo: "EU" }), orc({ id: 3, escopo: "PESSOA", pessoaId: 7 })] };
    const v = importarBackup(JSON.stringify(exportarBackup(ds)), dt(2025, 3, 1));
    if (!v.ok) throw new Error(v.erro);
    expect(v.dataset.orcamentos.map((o) => [o.escopo, o.pessoaId])).toEqual([[undefined, undefined], ["EU", undefined], ["PESSOA", 7]]);
    const antigo = importarBackup(JSON.stringify({ versaoBackup: 2, orcamentos: [{ id: 1, categoria: "X", valorLimite: 10 }] }), dt(2025, 3, 1));
    if (!antigo.ok) throw new Error(antigo.erro);
    expect(antigo.dataset.orcamentos[0]).toEqual({ id: 1, categoria: "X", valorLimite: 10 });
  });
});
