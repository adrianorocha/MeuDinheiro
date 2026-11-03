import { describe, expect, it } from "vitest";
import {
  alertasOrcamento,
  analisarMeta,
  buscaGlobal,
  chaveAlerta,
  detectarAssinaturas,
  melhorDiaCompra,
  regra503020,
  reservaEmergencia,
  simularParcelamento,
  sugerirCategoria,
  totaisAssinaturas,
} from "./analises";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import type { Meta } from "./types";

describe("R21 sugerirCategoria", () => {
  const hist = [
    desp({ descricao: "Posto Shell 123", categoria: "Combustível", data: dt(2025, 1, 1) }),
    desp({ descricao: "Posto Ipiranga", categoria: "Combustível", data: dt(2025, 1, 2) }),
    desp({ descricao: "Padaria do Zé", categoria: "Alimentação", data: dt(2025, 1, 3) }),
  ];
  it("igualdade normalizada (acento, dígitos, caixa) vale 1", () => {
    expect(sugerirCategoria("POSTO SHELL 999", hist)).toBe("Combustível");
  });
  it("Jaccard abaixo de 0,5 não sugere", () => {
    expect(sugerirCategoria("Posto Ipiranga Centro Norte Sul", hist)).toBeNull(); // 2/5 = 0,4 -> nenhum candidato
  });
  it("soma por categoria e desempata pela mais recente", () => {
    const h = [
      desp({ descricao: "Almoço Restaurante", categoria: "A", data: dt(2025, 1, 1) }),
      desp({ descricao: "Almoço Restaurante", categoria: "B", data: dt(2025, 2, 1) }),
    ];
    expect(sugerirCategoria("almoco restaurante", h)).toBe("B");
    const h2 = [...h, desp({ descricao: "Almoço Restaurante", categoria: "A", data: dt(2024, 1, 1) })];
    expect(sugerirCategoria("almoco restaurante", h2)).toBe("A");
  });
  it("ignora não-NORMAL, sem categoria e descrição vazia/curta", () => {
    expect(sugerirCategoria("", hist)).toBeNull();
    expect(sugerirCategoria("12 3", hist)).toBeNull();
    expect(sugerirCategoria("Posto Shell", [desp({ descricao: "Posto Shell", categoria: "X", natureza: "TRANSFERENCIA" })])).toBeNull();
    expect(sugerirCategoria("Posto Shell", [desp({ descricao: "Posto Shell", categoria: " " })])).toBeNull();
  });
});

describe("R22 alertasOrcamento", () => {
  const agora = dt(2025, 3, 15);
  const mk = (gasto: number) =>
    dataset({
      orcamentos: [{ id: 1, categoria: "Lazer", valorLimite: 100 }],
      despesas: [desp({ categoria: "Lazer", valor: gasto, data: dt(2025, 3, 5) })],
    });
  it("alerta 80% uma única vez", () => {
    const r1 = alertasOrcamento(mk(85), agora, new Set());
    expect(r1.alertas.map((a) => a.limiar)).toEqual([80]);
    const r2 = alertasOrcamento(mk(85), agora, new Set(r1.marcar));
    expect(r2.alertas).toHaveLength(0);
  });
  it("ao cruzar 100% só avisa 100% e marca o 80% também", () => {
    const r = alertasOrcamento(mk(120), agora, new Set());
    expect(r.alertas.map((a) => a.limiar)).toEqual([100]);
    expect(r.marcar).toContain(chaveAlerta("Lazer", 3, 2025, 80));
    expect(alertasOrcamento(mk(85), agora, new Set(r.marcar)).alertas).toHaveLength(0);
  });
  it("100% exato conta; 79,99% não; mês novo reinicia", () => {
    expect(alertasOrcamento(mk(100), agora, new Set()).alertas[0].limiar).toBe(100);
    expect(alertasOrcamento(mk(79.99), agora, new Set()).alertas).toHaveLength(0);
    const marcados = new Set(alertasOrcamento(mk(120), agora, new Set()).marcar);
    const abril = dataset({
      orcamentos: [{ id: 1, categoria: "Lazer", valorLimite: 100 }],
      despesas: [desp({ categoria: "lazer ", valor: 90, data: dt(2025, 4, 5) })],
    });
    expect(alertasOrcamento(abril, dt(2025, 4, 10), marcados).alertas).toHaveLength(1);
  });
  it("já avisado 80 e depois cruza 100 avisa 100", () => {
    const r = alertasOrcamento(mk(110), agora, new Set([chaveAlerta("Lazer", 3, 2025, 80)]));
    expect(r.alertas.map((a) => a.limiar)).toEqual([100]);
  });
});

describe("R24 buscaGlobal", () => {
  const ds = dataset({
    contas: [conta({ banco: "Nubank", conta: "777" })],
    cartoes: [cartao({ nome: "Platinum" })],
    metas: [{ id: 1, nome: "Viagem Japão", valorObjetivo: 1, valorGuardado: 0, icone: "", dataAlvo: null }],
    despesas: [
      desp({ id: 1, descricao: "Pão", categoria: "Alimentação", valor: 123.45, data: dt(2025, 3, 5), conta: "777" }),
      desp({ id: 2, descricao: "Compra x", categoria: "Outros", valor: 10, data: dt(2025, 4, 7), cartaoId: 10, conta: "777" }),
      desp({ id: 3, descricao: "Outra", categoria: "Outros", valor: 5, data: dt(2024, 3, 5), conta: "999" }),
    ],
  });
  it("ignora acento e caixa", () => {
    expect(buscaGlobal(ds, "ALIMENTACAO").lancamentos.map((d) => d.id)).toEqual([1]);
    expect(buscaGlobal(ds, "japao").metas).toHaveLength(1);
  });
  it("valor por centavos com vírgula ou ponto", () => {
    expect(buscaGlobal(ds, "123,45").lancamentos.map((d) => d.id)).toEqual([1]);
    expect(buscaGlobal(ds, "123.45").lancamentos.map((d) => d.id)).toEqual([1]);
    expect(buscaGlobal(ds, "123,4").lancamentos).toHaveLength(0);
  });
  it("data dd/MM e dd/MM/aaaa", () => {
    expect(buscaGlobal(ds, "05/03").lancamentos.map((d) => d.id).sort()).toEqual([1, 3]);
    expect(buscaGlobal(ds, "05/03/2025").lancamentos.map((d) => d.id)).toEqual([1]);
    expect(buscaGlobal(ds, "32/13").lancamentos).toHaveLength(0);
  });
  it("conta/banco e cartão; termo vazio", () => {
    expect(buscaGlobal(ds, "nubank").lancamentos.map((d) => d.id).sort()).toEqual([1, 2]);
    expect(buscaGlobal(ds, "plat").lancamentos.map((d) => d.id)).toEqual([2]);
    expect(buscaGlobal(ds, "plat").cartoes).toHaveLength(1);
    expect(buscaGlobal(ds, "  ").total).toBe(0);
  });
});

describe("R25 reservaEmergencia", () => {
  const agora = dt(2025, 6, 15);
  it("sem dados -> null", () => {
    expect(reservaEmergencia(dataset({ contas: [conta()] }), agora).status).toBeNull();
  });
  it("média dos 3 meses completos anteriores, ignorando meses vazios", () => {
    const ds = dataset({
      contas: [conta()],
      despesas: [
        desp({ valor: 1000, data: dt(2025, 5, 5) }),
        desp({ valor: 2000, data: dt(2025, 4, 5) }),
        // março sem lançamentos: ignorado
        desp({ valor: 99999, data: dt(2025, 6, 2) }), // mês corrente não conta
      ],
    });
    const r = reservaEmergencia(ds, agora);
    expect(r.mediaDespesas3m).toBe(1500);
  });
  it("status por meses de cobertura e faltante", () => {
    const base = (saldo: number) =>
      reservaEmergencia(
        dataset({
          contas: [conta()],
          investimentos: [{ id: 1, nome: "CDB", tipo: "Renda Fixa", valorInvestido: 0, valorAtual: 500 }, { id: 2, nome: "Ação", tipo: "Ações", valorInvestido: 0, valorAtual: 9999 }],
          despesas: [desp({ tipo: "CREDITO", valor: saldo + 1000, data: dt(2025, 1, 1) }), desp({ valor: 1000, data: dt(2025, 5, 5) })],
        }),
        agora,
      );
    const critico = base(1000); // liquidez 1500 -> 1,5 meses
    expect(critico.status).toBe("CRITICO");
    expect(critico.faltante).toBe(4500);
    expect(base(3000).meses).toBe(3.5);
    expect(base(3000).status).toBe("ATENCAO");
    expect(base(5500).status).toBe("OK"); // 6000/1000 = 6
    expect(base(5500).faltante).toBe(0);
  });
});

describe("R26 detectarAssinaturas", () => {
  const agora = dt(2025, 6, 20);
  const serie = (nome: string, valores: number[], dias = [5, 5, 6, 5], meses = [3, 4, 5, 6]) =>
    meses.map((m, i) => desp({ descricao: nome, valor: valores[i], data: dt(2025, m, dias[i]), categoria: "Streaming" }));
  it("detecta recorrente estável e calcula mensal/anual", () => {
    const ds = dataset({ despesas: serie("Netflix", [39.9, 39.9, 40, 39.9]) });
    const r = detectarAssinaturas(ds, agora);
    expect(r).toHaveLength(1);
    expect(r[0].origem).toBe("DETECTADA");
    expect(r[0].totalAnual).toBeCloseTo(r[0].valorMedio * 12, 2);
    expect(totaisAssinaturas(r).anual).toBe(r[0].totalAnual);
  });
  it("rejeita variação >10%, dispersão de dia >5, <3 meses e última há >45 dias", () => {
    expect(detectarAssinaturas(dataset({ despesas: serie("A", [10, 10, 10, 12]) }), agora)).toHaveLength(0);
    expect(detectarAssinaturas(dataset({ despesas: serie("B", [10, 10, 10, 10], [1, 12, 1, 1]) }), agora)).toHaveLength(0);
    expect(detectarAssinaturas(dataset({ despesas: serie("C", [10, 10], [5, 5], [5, 6]) }), agora)).toHaveLength(0);
    expect(detectarAssinaturas(dataset({ despesas: serie("D", [10, 10, 10], [5, 5, 5], [1, 2, 3]) }), dt(2025, 6, 20))).toHaveLength(0);
  });
  it("inclui regras fixas e ignora detectadas já cobertas por elas", () => {
    const ds = dataset({
      despesasFixas: [{ id: 1, descricao: "Netflix", valor: 40, conta: "111", categoria: "Streaming", pic: "", tipo: "DEBITO", diaVencimento: 5, ultimaDataLancamento: null, cartaoId: null }],
      despesas: serie("Netflix", [39.9, 39.9, 40, 39.9]),
    });
    const r = detectarAssinaturas(ds, agora);
    expect(r.map((x) => x.origem)).toEqual(["FIXA"]);
    expect(r[0].totalMensal).toBe(40);
  });
  it("agrupa por descrição sem sufixo (i/n) e sem dígitos", () => {
    const ds = dataset({
      despesas: [3, 4, 5].map((m, i) => desp({ descricao: `Plano ${i + 1} (${i + 1}/3)`, valor: 50, data: dt(2025, m, 10) })),
    });
    expect(detectarAssinaturas(ds, dt(2025, 6, 1))).toHaveLength(1);
  });
});

describe("R27 analisarMeta", () => {
  const meta = (over: Partial<Meta> = {}): Meta => ({ id: 1, nome: "Viagem", valorObjetivo: 1200, valorGuardado: 0, icone: "", dataAlvo: dt(2025, 12, 15), ...over });
  const agora = dt(2025, 6, 15);
  it("sem dataAlvo só informa o restante", () => {
    const r = analisarMeta(meta({ dataAlvo: null, valorGuardado: 200 }), [], agora);
    expect(r.restante).toBe(1000);
    expect(r.aporteMensalNecessario).toBeNull();
    expect(r.status).toBeNull();
  });
  it("aporte necessário = restante / meses restantes (teto)", () => {
    const r = analisarMeta(meta(), [], agora);
    expect(r.mesesRestantes).toBe(6);
    expect(r.aporteMensalNecessario).toBe(200);
    expect(analisarMeta(meta({ dataAlvo: dt(2025, 6, 20) }), [], agora).mesesRestantes).toBe(1);
    expect(analisarMeta(meta({ dataAlvo: dt(2025, 12, 16) }), [], agora).mesesRestantes).toBe(7);
  });
  it("status: concluída, atrasada, no ritmo, abaixo", () => {
    expect(analisarMeta(meta({ valorGuardado: 1200 }), [], agora).status).toBe("CONCLUIDA");
    expect(analisarMeta(meta({ dataAlvo: dt(2025, 5, 1) }), [], agora).status).toBe("ATRASADA");
    expect(analisarMeta(meta({ dataAlvo: dt(2025, 5, 1), valorGuardado: 1200 }), [], agora).status).toBe("CONCLUIDA");
    const aportes = [4, 5, 6].map((m) => desp({ natureza: "APORTE_META", descricao: "Aporte: Viagem", valor: 300, data: dt(2025, m, 10) }));
    const r = analisarMeta(meta({ valorGuardado: 900 }), aportes, agora);
    expect(r.ritmo).toBe(300);
    expect(r.status).toBe("NO_RITMO"); // precisa 50/mês
    expect(analisarMeta(meta(), [], agora).status).toBe("ABAIXO");
    const outros = [desp({ natureza: "APORTE_META", descricao: "Aporte: Outra", valor: 999, data: dt(2025, 6, 1) })];
    expect(analisarMeta(meta(), outros, agora).ritmo).toBe(0);
  });
});

describe("R28 simularParcelamento", () => {
  it("parcelar vale quando o valor presente é menor", () => {
    const r = simularParcelamento({ valorAVista: 1000, n: 10, valorParcela: 100, taxaMensal: 0.01 });
    expect(r.valorPresente).toBeCloseTo(947.13, 2);
    expect(r.parcelarVale).toBe(true);
    expect(r.diferenca).toBeCloseTo(52.87, 2);
    expect(r.jurosImplicitosMensais).toBe(0); // 10x100 = à vista
  });
  it("juros implícitos por bisseção", () => {
    const r = simularParcelamento({ valorAVista: 1000, n: 12, valorParcela: 100 });
    expect(r.jurosImplicitosMensais).toBeGreaterThan(0.02);
    expect(r.jurosImplicitosMensais).toBeLessThan(0.03);
    expect(r.parcelarVale).toBe(false);
  });
  it("com entrada e taxa padrão 1%", () => {
    const r = simularParcelamento({ valorAVista: 1000, n: 2, valorParcela: 520, entrada: 0 });
    expect(r.valorPresente).toBeGreaterThan(1000);
    const e = simularParcelamento({ valorAVista: 1000, n: 2, valorParcela: 400, entrada: 200 });
    expect(e.jurosImplicitosMensais).toBe(0);
  });
});

describe("R29 regra503020", () => {
  it("classifica e calcula status", () => {
    const ds = [
      desp({ tipo: "CREDITO", valor: 1000, categoria: "Salário", data: dt(2025, 3, 1) }),
      desp({ valor: 400, categoria: "Supermercado", data: dt(2025, 3, 2) }),
      desp({ valor: 350, categoria: "Cinema", data: dt(2025, 3, 3) }),
      desp({ valor: 50, categoria: "Reserva", data: dt(2025, 3, 4) }),
      desp({ valor: 100, natureza: "APORTE_META", categoria: "Reserva", data: dt(2025, 3, 5) }),
      desp({ valor: 30, tipo: "CREDITO", natureza: "RESGATE_META", data: dt(2025, 3, 6) }),
      desp({ valor: 999, natureza: "TRANSFERENCIA", data: dt(2025, 3, 7) }),
    ];
    const r = regra503020(ds, 3, 2025);
    expect(r.necessidades.valor).toBe(400);
    expect(r.necessidades.percentual).toBe(0.4);
    expect(r.necessidades.status).toBe("OK");
    expect(r.desejos.valor).toBe(350);
    expect(r.desejos.status).toBe("ACIMA");
    expect(r.poupanca.valor).toBe(120); // 100 + 50 - 30
    expect(r.poupanca.status).toBe("ABAIXO");
  });
  it("sem receita: percentuais 0", () => {
    const r = regra503020([desp({ valor: 10, categoria: "Cinema", data: dt(2025, 3, 3) })], 3, 2025);
    expect(r.desejos.percentual).toBe(0);
  });
});

describe("R30 melhorDiaCompra", () => {
  it("dia seguinte ao fechamento e prazo até o vencimento", () => {
    const r = melhorDiaCompra({ diaFechamento: 25, diaVencimento: 5 }, 3, 2025);
    expect(r.dia).toBe(26);
    expect(r.prazoMaximoDias).toBe(40); // 26/03 -> fatura de abril, vence 05/05
  });
  it("fechamento no último dia do mês vira dia 1 do mês seguinte", () => {
    const r = melhorDiaCompra({ diaFechamento: 31, diaVencimento: 10 }, 2, 2025);
    expect([r.dia, r.mes, r.ano]).toEqual([1, 3, 2025]);
    const dez = melhorDiaCompra({ diaFechamento: 31, diaVencimento: 10 }, 12, 2025);
    expect([dez.dia, dez.mes, dez.ano]).toEqual([1, 1, 2026]);
  });
  it("vencimento no mesmo mês do fechamento", () => {
    const r = melhorDiaCompra({ diaFechamento: 5, diaVencimento: 15 }, 3, 2025);
    expect(r.dia).toBe(6);
    expect(r.prazoMaximoDias).toBe(40); // 06/03 -> fatura abril (vence 15/04)
  });
});
