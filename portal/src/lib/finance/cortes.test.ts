import { describe, expect, it } from "vitest";
import { aplicarCorteNoFluxo, calcularCortes, montarPlano } from "./cortes";
import { conta, dataset, desp, dt } from "./fixtures";
import { projetarFluxo } from "./fluxoCaixa";
import type { Despesa } from "./types";

const AGORA = dt(2025, 6, 15); // janela: mar, abr, mai/2025
let seq = 1;
const D = (o: Partial<Despesa>): Despesa => desp({ id: seq++, conta: "111", ...o });
/** mesmo gasto nos 3 meses da janela, com valor variando (x0,8 / x1 / x1,2: a média se mantém e não vira "assinatura") */
const trimestre = (o: Partial<Despesa>, dia = 10, fixo = false): Despesa[] => [3, 4, 5].map((m, i) => D({ ...o, valor: fixo ? o.valor : Math.round((o.valor ?? 0) * [0.8, 1, 1.2][i] * 100) / 100, data: dt(2025, m, dia) }));

function base(despesas: Despesa[]) {
  return dataset({ contas: [conta({ id: 1, conta: "111" })], despesas });
}

describe("R103 - onde cortar", () => {
  it("categoria flexível: 20% da média mensal; essencial: 5%; reserva e aportes ficam de fora", () => {
    const ds = base([
      ...trimestre({ descricao: "Cinema", valor: 300, categoria: "Lazer" }),
      ...trimestre({ descricao: "Mercado", valor: 1000, categoria: "Supermercado" }),
      ...trimestre({ descricao: "Poupar", valor: 500, categoria: "Reserva" }),
      ...trimestre({ descricao: "Meta", valor: 500, categoria: "Lazer", natureza: "APORTE_META" }),
    ]);
    const r = calcularCortes(ds, AGORA);
    const lazer = r.cortes.find((c) => c.id === "corte:categoria:lazer")!;
    const mercado = r.cortes.find((c) => c.id === "corte:categoria:supermercado")!;
    expect(lazer).toMatchObject({ tipo: "FLEXIVEL", mensalAtual: 30000, economiaMensal: 6000 });
    expect(mercado).toMatchObject({ tipo: "ESSENCIAL", mensalAtual: 100000, economiaMensal: 5000 });
    expect(r.cortes.some((c) => c.categoria === "Reserva")).toBe(false);
    expect(r.cortes.map((c) => c.tipo)).toEqual(["FLEXIVEL", "ESSENCIAL"]); // ordem de prioridade
    expect(r.totalMensal).toBe(11000);
    expect(r.totalAnual).toBe(132000);
  });

  it("percentuais ajustáveis", () => {
    const ds = base(trimestre({ descricao: "Cinema", valor: 300, categoria: "Lazer" }));
    expect(calcularCortes(ds, AGORA, { pctFlexivel: 0.5 }).totalMensal).toBe(15000);
  });

  it("juros e tarifas são 100% evitáveis e não contam de novo na categoria", () => {
    const ds = base([
      ...trimestre({ descricao: "Juros do rotativo", valor: 90, categoria: "Banco" }),
      ...trimestre({ descricao: "Padaria", valor: 200, categoria: "Banco" }),
    ]);
    const r = calcularCortes(ds, AGORA);
    const ev = r.cortes.find((c) => c.tipo === "EVITAVEL")!;
    expect(ev.economiaMensal).toBe(9000);
    const cat = r.cortes.find((c) => c.tipo === "FLEXIVEL")!;
    expect(cat.mensalAtual).toBe(20000); // só a padaria
  });

  it("assinatura detectada é um corte próprio e sai da categoria", () => {
    const ds = base([
      ...trimestre({ descricao: "Streaming Max", valor: 50, categoria: "Lazer" }, 5, true),
      ...trimestre({ descricao: "Cinema", valor: 200, categoria: "Lazer" }, 20),
    ]);
    const r = calcularCortes(ds, AGORA);
    const a = r.cortes.find((c) => c.tipo === "ASSINATURA")!;
    expect(a).toMatchObject({ economiaMensal: 5000 });
    expect(r.cortes.find((c) => c.tipo === "FLEXIVEL")?.mensalAtual).toBe(20000);
  });

  it("compras pequenas e frequentes: 30% do mensal; não vale para categoria essencial", () => {
    const cafes = [3, 4, 5].flatMap((m) => [2, 6, 9, 13, 17, 21].map((d) => D({ descricao: "Cafeteria", valor: 15, categoria: "Lazer", data: dt(2025, m, d) })));
    const r = calcularCortes(base(cafes), AGORA);
    const f = r.cortes.find((c) => c.tipo === "FREQUENTE")!;
    expect(f.mensalAtual).toBe(9000);
    expect(f.economiaMensal).toBe(2700);
    expect(r.cortes.some((c) => c.tipo === "FLEXIVEL")).toBe(false); // tudo já está no bloco frequente
    const ess = [3, 4, 5].flatMap((m) => [2, 6, 9, 13, 17, 21].map((d) => D({ descricao: "Feira", valor: 15, categoria: "Supermercado", data: dt(2025, m, d) })));
    expect(calcularCortes(base(ess), AGORA).cortes.some((c) => c.tipo === "FREQUENTE")).toBe(false);
  });

  it("estorno de cartão abate o gasto; blocos pequenos não aparecem; fora da janela não conta", () => {
    const ds = base([
      ...trimestre({ descricao: "Roupas", valor: 400, categoria: "Compras" }),
      D({ descricao: "Estorno roupas", valor: 300, tipo: "CREDITO", cartaoId: 10, categoria: "Compras", data: dt(2025, 4, 12) }),
      D({ descricao: "Bala", valor: 5, categoria: "Doces", data: dt(2025, 4, 1) }),
      D({ descricao: "Viagem", valor: 9000, categoria: "Viagem", data: dt(2025, 6, 2) }),
      D({ descricao: "Viagem antiga", valor: 9000, categoria: "Viagem", data: dt(2024, 12, 2) }),
    ]);
    const r = calcularCortes(ds, AGORA);
    expect(r.cortes).toHaveLength(1);
    expect(r.cortes[0]).toMatchObject({ categoria: "Compras", mensalAtual: Math.round(90000 / 3) });
  });

  it("dispensar tira o corte do total", () => {
    const ds = base(trimestre({ descricao: "Cinema", valor: 300, categoria: "Lazer" }));
    const r = calcularCortes(ds, AGORA, { dispensados: new Set(["corte:categoria:lazer"]) });
    expect(r.cortes).toEqual([]);
    expect(r.totalMensal).toBe(0);
  });

  it("maiores gastos: ranking por média mensal", () => {
    const ds = base([...trimestre({ descricao: "Restaurante A", valor: 600, categoria: "Lazer" }), ...trimestre({ descricao: "Cinema", valor: 90, categoria: "Lazer" })]);
    const r = calcularCortes(ds, AGORA);
    expect(r.maioresGastos.map((g) => g.nome)).toEqual(["Restaurante A", "Cinema"]);
    expect(r.maioresGastos[0].mensal).toBe(60000);
  });

  it("plano soma na ordem de prioridade até o alvo e diz quanto falta", () => {
    const ds = base([
      ...trimestre({ descricao: "Juros", valor: 40, categoria: "Banco" }),
      ...trimestre({ descricao: "Cinema", valor: 300, categoria: "Lazer" }),
      ...trimestre({ descricao: "Mercado", valor: 1000, categoria: "Supermercado" }),
    ]);
    const todos = calcularCortes(ds, AGORA).cortes;
    expect(todos.map((c) => c.economiaMensal)).toEqual([4000, 6000, 5000]);
    expect(montarPlano(todos, 0)).toBeNull();
    expect(montarPlano(todos, 3000)).toMatchObject({ economia: 4000, atinge: true, falta: 0 });
    expect(montarPlano(todos, 9000)?.selecionados).toHaveLength(2);
    expect(montarPlano(todos, 20000)).toMatchObject({ economia: 15000, atinge: false, falta: 5000 });
    expect(calcularCortes(ds, AGORA, { alvoMensal: 9000 }).plano?.selecionados).toHaveLength(2);
  });

  it("aplicar o corte no fluxo: saldo_j + economia×(j+1) e meses negativos que somem", () => {
    const ds = base([...trimestre({ descricao: "Cinema", valor: 800, categoria: "Lazer" })]);
    const fluxo = projetarFluxo(ds, AGORA, 6, 0);
    expect(fluxo.primeiroNegativo).not.toBeNull();
    const f = aplicarCorteNoFluxo(fluxo, fluxo.cortePorMes);
    expect(f.mesesNegativosAntes).toBeGreaterThan(0);
    expect(f.mesesNegativosDepois).toBe(0);
    expect(f.saldos[2]).toBe(fluxo.meses[2].saldoFinal + fluxo.cortePorMes * 3);
    expect(f.saldoFinalDepois).toBe(f.saldos[5]);
  });
});
