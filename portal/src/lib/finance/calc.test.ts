import { describe, expect, it } from "vitest";
import {
  faturaDaCompra,
  faturasPendentes,
  fechamentoDia,
  itensFatura,
  kpisDoMes,
  limiteCartao,
  patrimonioLiquido,
  previsaoMes,
  progressoOrcamento,
  recalcularTudo,
  resumoFatura,
  saldoConta,
  saudeFinanceira,
  vencimentoFatura,
} from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";

describe("R3 saldoConta", () => {
  it("soma créditos e debita débitos pagos, ignora pendentes e cartão", () => {
    const ds = [
      desp({ tipo: "CREDITO", valor: 1000 }),
      desp({ tipo: "DEBITO", valor: 250.5 }),
      desp({ tipo: "DEBITO", valor: 99, pago: false }),
      desp({ tipo: "DEBITO", valor: 300, cartaoId: 10, pago: true }),
      desp({ tipo: "DEBITO", valor: 5, conta: "999" }),
    ];
    expect(saldoConta(ds, "111")).toBe(749.5);
  });
  it("sem deriva de float", () => {
    const ds = [0.1, 0.2].map((v) => desp({ tipo: "CREDITO", valor: v }));
    expect(saldoConta(ds, "111")).toBe(0.3);
  });
  it("cartaoId 0 é tratado como null", () => {
    expect(saldoConta([desp({ tipo: "CREDITO", valor: 10, cartaoId: 0 })], "111")).toBe(10);
  });
});

describe("R4 limiteCartao", () => {
  const c = cartao({ limiteTotal: 1000 });
  it("desconta débitos não pagos e soma estornos", () => {
    const ds = [
      desp({ cartaoId: 10, valor: 300, pago: false }),
      desp({ cartaoId: 10, valor: 100, pago: false, tipo: "CREDITO" }),
      desp({ cartaoId: 10, valor: 500, pago: true }),
    ];
    expect(limiteCartao(c, ds)).toBe(800);
  });
  it("parcelas futuras consomem integralmente", () => {
    const ds = [1, 2, 3].map((m) => desp({ cartaoId: 10, valor: 100, pago: false, data: dt(2025, m, 5) }));
    expect(limiteCartao(c, ds)).toBe(700);
  });
});

describe("R5 KPIs", () => {
  const ds = [
    desp({ tipo: "CREDITO", valor: 5000, data: dt(2025, 3, 5) }),
    desp({ tipo: "CREDITO", valor: 500, data: dt(2025, 3, 6), pago: false }),
    desp({ tipo: "DEBITO", valor: 1000, data: dt(2025, 3, 7) }),
    desp({ tipo: "DEBITO", valor: 400, data: dt(2025, 3, 8), pago: false }),
    desp({ tipo: "DEBITO", valor: 300, data: dt(2025, 3, 9), cartaoId: 10, pago: false }),
    desp({ tipo: "CREDITO", valor: 50, data: dt(2025, 3, 9), cartaoId: 10, pago: false }),
    desp({ tipo: "DEBITO", valor: 999, data: dt(2025, 3, 9), natureza: "TRANSFERENCIA" }),
    desp({ tipo: "DEBITO", valor: 999, data: dt(2025, 3, 9), natureza: "PAGAMENTO_FATURA" }),
    desp({ tipo: "CREDITO", valor: 999, data: dt(2025, 3, 9), natureza: "SALDO_INICIAL" }),
    desp({ tipo: "DEBITO", valor: 77, data: dt(2025, 2, 28) }),
  ];
  it("calcula o mês e ignora naturezas não NORMAL", () => {
    const k = kpisDoMes(ds, 3, 2025);
    expect(k.receitasRealizadas).toBe(5000);
    expect(k.receitasPrevistas).toBe(500);
    expect(k.despesasTotal).toBe(1650); // 1000 + 400 + 300 - 50
    expect(k.despesasPagas).toBe(1000);
    expect(k.despesasPendentes).toBe(650);
    expect(k.resultado).toBe(3350);
    expect(k.taxaPoupanca).toBeCloseTo(0.67, 5);
  });
  it("taxa de poupança 0 sem receita", () => {
    expect(kpisDoMes([desp({ valor: 10 })], 3, 2025).taxaPoupanca).toBe(0);
  });
});

describe("R6 fatura", () => {
  const c = cartao({ diaFechamento: 25, diaVencimento: 5 });
  it("compra até o fechamento fica no mês, depois vai para o seguinte", () => {
    expect(faturaDaCompra(c, dt(2025, 3, 25))).toEqual({ mes: 3, ano: 2025 });
    expect(faturaDaCompra(c, dt(2025, 3, 26))).toEqual({ mes: 4, ano: 2025 });
  });
  it("fatura vira o ano", () => {
    expect(faturaDaCompra(c, dt(2025, 12, 28))).toEqual({ mes: 1, ano: 2026 });
  });
  it("fechamento 31 em fevereiro é clampado", () => {
    const c31 = cartao({ diaFechamento: 31, diaVencimento: 10 });
    expect(fechamentoDia(c31, 2, 2025)).toBe(28);
    expect(fechamentoDia(c31, 2, 2024)).toBe(29);
    expect(faturaDaCompra(c31, dt(2025, 2, 28))).toEqual({ mes: 2, ano: 2025 });
    expect(faturaDaCompra(c31, dt(2025, 3, 1))).toEqual({ mes: 3, ano: 2025 });
  });
  it("vencimento: dia <= fechamento vai para o mês seguinte", () => {
    const v = new Date(vencimentoFatura(c, 3, 2025));
    expect([v.getFullYear(), v.getMonth() + 1, v.getDate()]).toEqual([2025, 4, 5]);
  });
  it("vencimento: dia > fechamento fica no mesmo mês", () => {
    const c2 = cartao({ diaFechamento: 5, diaVencimento: 15 });
    const v = new Date(vencimentoFatura(c2, 3, 2025));
    expect([v.getMonth() + 1, v.getDate()]).toEqual([3, 15]);
  });
  it("vencimento em dezembro cruza o ano e clampa o dia", () => {
    const v = new Date(vencimentoFatura(cartao({ diaFechamento: 25, diaVencimento: 5 }), 12, 2025));
    expect([v.getFullYear(), v.getMonth() + 1, v.getDate()]).toEqual([2026, 1, 5]);
    const c3 = cartao({ diaFechamento: 28, diaVencimento: 31 });
    const v2 = new Date(vencimentoFatura(c3, 2, 2025));
    expect([v2.getMonth() + 1, v2.getDate()]).toEqual([2, 28]);
    const c4 = cartao({ diaFechamento: 20, diaVencimento: 10 });
    const v3 = new Date(vencimentoFatura(c4, 1, 2025));
    expect([v3.getMonth() + 1, v3.getDate()]).toEqual([2, 10]);
  });
  it("itens e resumo da fatura com estorno", () => {
    const ds = [
      desp({ cartaoId: 10, valor: 100, data: dt(2025, 2, 26), pago: false }), // fatura de março
      desp({ cartaoId: 10, valor: 40, data: dt(2025, 3, 10), pago: false }),
      desp({ cartaoId: 10, valor: 15, data: dt(2025, 3, 11), pago: false, tipo: "CREDITO" }),
      desp({ cartaoId: 10, valor: 500, data: dt(2025, 3, 26), pago: false }), // abril
    ];
    expect(itensFatura(c, ds, 3, 2025)).toHaveLength(3);
    const r = resumoFatura(c, ds, 3, 2025);
    expect(r.total).toBe(125);
    expect(r.emAberto).toBe(125);
    expect(r.paga).toBe(false);
  });
  it("faturasPendentes agrupa por ciclo", () => {
    const ds = [
      desp({ cartaoId: 10, valor: 100, data: dt(2025, 3, 10), pago: false }),
      desp({ cartaoId: 10, valor: 50, data: dt(2025, 3, 30), pago: false }),
      desp({ cartaoId: 10, valor: 999, data: dt(2025, 3, 10), pago: true }),
    ];
    const f = faturasPendentes([c], ds);
    expect(f).toHaveLength(2);
    expect(f[0].total).toBe(100);
    expect(f[1].total).toBe(50);
  });
});

describe("R13 orçamento", () => {
  const ds = [
    desp({ categoria: "Alimentação", valor: 80, data: dt(2025, 3, 5) }),
    desp({ categoria: " alimentação ", valor: 20, data: dt(2025, 3, 6) }),
    desp({ categoria: "Alimentação", valor: 10, data: dt(2025, 3, 7), cartaoId: 10, tipo: "CREDITO", pago: false }),
    desp({ categoria: "Alimentação", valor: 500, data: dt(2025, 4, 1) }),
    desp({ categoria: "Alimentação", valor: 500, data: dt(2025, 3, 8), natureza: "TRANSFERENCIA" }),
  ];
  it("calcula gasto/pct/status", () => {
    const p = progressoOrcamento({ id: 1, categoria: "ALIMENTAÇÃO", valorLimite: 100 }, ds, 3, 2025);
    expect(p.gasto).toBe(90);
    expect(p.pct).toBeCloseTo(0.9);
    expect(p.status).toBe("ATENCAO");
  });
  it("estourado passa de 100%", () => {
    const p = progressoOrcamento({ id: 1, categoria: "Alimentação", valorLimite: 50 }, ds, 3, 2025);
    expect(p.status).toBe("ESTOURADO");
    expect(p.pct).toBeGreaterThan(1);
  });
  it("OK abaixo de 80%", () => {
    expect(progressoOrcamento({ id: 1, categoria: "Alimentação", valorLimite: 1000 }, ds, 3, 2025).status).toBe("OK");
  });
});

describe("R14 patrimônio", () => {
  it("soma contas + investimentos + metas - faturas", () => {
    const ds = dataset({
      contas: [conta()],
      cartoes: [cartao()],
      despesas: [
        desp({ tipo: "CREDITO", valor: 1000 }),
        desp({ cartaoId: 10, valor: 200, pago: false }),
        desp({ cartaoId: 10, valor: 50, pago: false, tipo: "CREDITO" }),
      ],
      investimentos: [{ id: 1, nome: "CDB", tipo: "Renda Fixa", valorInvestido: 400, valorAtual: 450 }],
      metas: [{ id: 1, nome: "Viagem", valorObjetivo: 1000, valorGuardado: 100, icone: "", dataAlvo: null }],
    });
    expect(patrimonioLiquido(ds)).toBe(1000 + 450 + 100 - 150);
  });
});

describe("R15 previsão", () => {
  const agora = dt(2025, 3, 15);
  it("considera pendências até o fim do mês e faturas vencendo no mês", () => {
    const ds = dataset({
      contas: [conta()],
      cartoes: [cartao({ diaFechamento: 5, diaVencimento: 20 })],
      despesas: [
        desp({ tipo: "CREDITO", valor: 1000 }),
        desp({ tipo: "CREDITO", valor: 200, pago: false, data: dt(2025, 3, 20) }),
        desp({ tipo: "DEBITO", valor: 100, pago: false, data: dt(2025, 3, 1) }), // atrasada
        desp({ tipo: "DEBITO", valor: 300, pago: false, data: dt(2025, 4, 1) }), // fora
        desp({ cartaoId: 10, valor: 150, pago: false, data: dt(2025, 3, 3) }), // fatura mar, vence 20/03
      ],
    });
    const p = previsaoMes(ds, agora);
    expect(p.saldoAtual).toBe(1000);
    expect(p.saldoLivrePrevisto).toBe(950); // 1000 + 200 - 100 - 150
    expect(p.status).toBe("SEGURO");
  });
  it("risco com saldo zero e dívidas", () => {
    const ds = dataset({ contas: [conta()], despesas: [desp({ valor: 50, pago: false, data: dt(2025, 3, 2) })] });
    const p = previsaoMes(ds, agora);
    expect(p.margem).toBe(0);
    expect(p.status).toBe("RISCO");
  });
  it("saldo zero e previsão positiva => margem 1", () => {
    const ds = dataset({ contas: [conta()], despesas: [desp({ tipo: "CREDITO", valor: 50, pago: false, data: dt(2025, 3, 20) })] });
    expect(previsaoMes(ds, agora).margem).toBe(1);
  });
  it("limiares 40% / 5%", () => {
    const base = (livre: number) =>
      previsaoMes(
        dataset({
          contas: [conta()],
          despesas: [desp({ tipo: "CREDITO", valor: 1000 }), desp({ valor: 1000 - livre, pago: false, data: dt(2025, 3, 20) })],
        }),
        agora,
      ).status;
    expect(base(400)).toBe("SEGURO");
    expect(base(399)).toBe("ATENCAO");
    expect(base(50)).toBe("ATENCAO");
    expect(base(49)).toBe("RISCO");
  });
});

describe("R17 saúde", () => {
  it("limiares", () => {
    expect(saudeFinanceira(1000, 899, 0).status).toBe("ALERTA");
    expect(saudeFinanceira(1000, 900, 0).status).toBe("PERIGO");
    expect(saudeFinanceira(1000, 700, 0).status).toBe("ALERTA");
    expect(saudeFinanceira(1000, 699, 0).status).toBe("SAUDAVEL");
  });
  it("sem receita", () => {
    expect(saudeFinanceira(0, 10, 0).consumo).toBe(1);
    expect(saudeFinanceira(0, 0, 0).consumo).toBe(0);
  });
  it("variação de gastos", () => {
    expect(saudeFinanceira(1000, 150, 100).variacaoGastos).toBe(50);
    expect(saudeFinanceira(1000, 150, 0).variacaoGastos).toBe(0);
  });
});

describe("recalcularTudo", () => {
  it("atualiza caches e preserva referências inalteradas", () => {
    const ds = dataset({
      contas: [conta({ saldo: 999 }), conta({ id: 2, conta: "222", saldo: 0 })],
      cartoes: [cartao({ limiteDisponivel: 1 })],
      despesas: [desp({ tipo: "CREDITO", valor: 100 }), desp({ cartaoId: 10, valor: 40, pago: false })],
    });
    const r = recalcularTudo(ds);
    expect(r.contas[0].saldo).toBe(100);
    expect(r.contas[1]).toBe(ds.contas[1]);
    expect(r.cartoes[0].limiteDisponivel).toBe(960);
    expect(recalcularTudo(r)).toBe(r);
  });
});
