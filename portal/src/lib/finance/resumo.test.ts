import { describe, expect, it } from "vitest";
import { brlCentavos } from "./centavos";
import { dataset, desp, dt } from "./fixtures";
import { periodoDoResumo, resumoInteligente, textoDoResumo, type Insight } from "./resumo";
import type { Despesa } from "./types";

// Mesmos cenários e números de app/src/test/java/com/meudinheiro/domain/ResumoInteligenteTest.kt.
const AGORA = dt(2026, 10, 15);
let n = 5000;
const d = (data: number, descricao: string, categoria: string, valor: number, over: Partial<Despesa> = {}) =>
  desp({ id: n++, data, descricao, categoria, valor, ...over });
const sal = (data: number, valor: number) => d(data, "Salário", "Salário", valor, { tipo: "CREDITO" });
const tipos = (i: Insight[]) => i.map((x) => x.tipo);
const achar = (i: Insight[], tipo: string) => i.find((x) => x.tipo === tipo);

describe("R53 resumo inteligente - mês em andamento (15/10)", () => {
  const despesas = [
    sal(dt(2026, 9, 2), 5000),
    d(dt(2026, 9, 5), "Aluguel", "Moradia", 1500),
    d(dt(2026, 9, 8), "Mercado", "Supermercado", 400),
    d(dt(2026, 9, 10), "Restaurante", "Restaurantes", 100),
    d(dt(2026, 9, 20), "Mercado", "Supermercado", 300),
    d(dt(2026, 9, 25), "Cinema", "Lazer", 200),
    d(dt(2026, 6, 10), "Farmácia A", "Farmácia", 50),
    d(dt(2026, 7, 10), "Farmácia B", "Farmácia", 50),
    d(dt(2026, 8, 10), "Farmácia C", "Farmácia", 50),
    sal(dt(2026, 10, 2), 5500),
    d(dt(2026, 10, 5), "Aluguel", "Moradia", 1500),
    d(dt(2026, 10, 8), "Mercado", "Supermercado", 600),
    d(dt(2026, 10, 10), "Restaurante", "Restaurantes", 400),
    d(dt(2026, 10, 14), "Farmácia grande", "Farmácia", 250),
    d(dt(2026, 10, 12), "Transferência", "Transferência", 2000, { natureza: "TRANSFERENCIA" }),
    d(dt(2026, 10, 13), "Fatura", "Cartão", 1000, { natureza: "PAGAMENTO_FATURA" }),
  ];
  const r = resumoInteligente(dataset({ despesas }), "MES", dt(2026, 10, 15), AGORA);

  it("compara despesas e receitas com o mesmo ponto do mês anterior, em centavos", () => {
    expect(r.periodo.emAndamento).toBe(true);
    expect(r.periodo.diasDecorridos).toBe(15);
    expect(r.periodo.rotulo).toBe("Outubro de 2026");
    expect(r.despesas).toEqual({ atualCentavos: 275000, anteriorCentavos: 200000, deltaCentavos: 75000, variacaoDecimos: 375 });
    expect(r.receitas).toEqual({ atualCentavos: 550000, anteriorCentavos: 500000, deltaCentavos: 50000, variacaoDecimos: 100 });
    expect(r.resultadoCentavos).toBe(275000);
    expect(r.taxaPoupancaDecimos).toBe(500);
    const desp = achar(r.insights, "DESPESAS_TOTAL")!;
    expect(desp.texto).toBe("Você gastou R$ 2.750,00: +R$ 750,00 (+37,5%) em relação ao mesmo período do mês anterior (até o dia 15).");
    expect(desp.tom).toBe("ALERTA");
  });

  it("projeta o fechamento no ritmo atual contra o mês anterior inteiro", () => {
    expect(r.projecaoCentavos).toBe(568333);
    const p = achar(r.insights, "PROJECAO")!;
    expect(p.variacaoDecimos).toBe(1273);
    expect(p.texto).toBe("No ritmo atual, o mês deve fechar em R$ 5.683,33 em despesas: R$ 3.183,33 acima (+127,3%) do mês anterior inteiro (R$ 2.500,00).");
  });

  it("lista só categorias relevantes, maiores altas primeiro (inclui categoria nova sem base)", () => {
    const altas = r.insights.filter((i) => i.tipo === "CATEGORIA_ALTA");
    expect(altas.map((i) => i.titulo)).toEqual(["Restaurantes em alta", "Farmácia em alta", "Supermercado em alta"]);
    expect(altas.map((i) => i.variacaoDecimos)).toEqual([3000, null, 500]);
    expect(altas.map((i) => i.relevancia)).toEqual([73, 72, 72]);
    expect(r.insights.some((i) => i.titulo.startsWith("Moradia"))).toBe(false); // sem variação
    expect(achar(r.insights, "CATEGORIA_QUEDA")).toBeUndefined();
  });

  it("detecta gasto fora do padrão: único > 2,5x a mediana da categoria (>= 3 lançamentos em 6 meses) e >= R$ 100", () => {
    const f = r.insights.filter((i) => i.tipo === "FORA_PADRAO");
    expect(f).toHaveLength(1);
    expect(f[0].valorCentavos).toBe(25000);
    expect(f[0].texto).toBe("Farmácia grande (Farmácia): R$ 250,00, 5,0x a mediana da categoria (R$ 50,00).");
    // Restaurantes tem só 1 lançamento anterior: amostra pequena demais
    expect(f.some((i) => i.texto.includes("Restaurante"))).toBe(false);
  });

  it("ordena por relevância, com empate pela ordem de geração, e ignora natureza interna", () => {
    expect(tipos(r.insights)).toEqual([
      "DESPESAS_TOTAL",
      "RECEITAS_TOTAL",
      "POUPANCA",
      "FORA_PADRAO",
      "PROJECAO",
      "CATEGORIA_ALTA",
      "CATEGORIA_ALTA",
      "CATEGORIA_ALTA",
      "BOA_NOTICIA",
    ]);
    expect(achar(r.insights, "BOA_NOTICIA")!.texto).toBe("Você está poupando 50,0% da receita, acima dos 20% recomendados.");
    expect(r.insights.map((i) => i.relevancia)).toEqual([90, 85, 80, 78, 75, 73, 72, 72, 45]);
  });

  it("texto compartilhável traz o título e cada insight", () => {
    const t = textoDoResumo(r);
    expect(t.startsWith("Resumo de Outubro de 2026 - MeuDinheiro")).toBe(true);
    expect(t).toContain("- Despesas: Você gastou R$ 2.750,00");
  });
});

describe("R53 recorrências novas e com aumento", () => {
  const despesas = [
    d(dt(2026, 7, 5), "Streaming X", "Streaming", 40),
    d(dt(2026, 8, 5), "Streaming X", "Streaming", 40),
    d(dt(2026, 9, 5), "Streaming X", "Streaming", 40),
    d(dt(2026, 10, 5), "Streaming X", "Streaming", 44),
    d(dt(2026, 8, 7), "Academia Y", "Academia", 100),
    d(dt(2026, 9, 7), "Academia Y", "Academia", 100),
    d(dt(2026, 10, 7), "Academia Y", "Academia", 100),
  ];
  const r = resumoInteligente(dataset({ despesas }), "MES", dt(2026, 10, 15), AGORA);
  it("aponta a nova e a que ficou mais cara", () => {
    const nova = achar(r.insights, "RECORRENCIA_NOVA")!;
    expect(nova.texto).toBe("Academia Y passou a se repetir todo mês: cerca de R$ 100,00 por mês.");
    const aumento = achar(r.insights, "RECORRENCIA_AUMENTO")!;
    expect(aumento.texto).toBe("Streaming X subiu de R$ 40,00 para R$ 44,00 (+10,0%).");
    expect(aumento.variacaoDecimos).toBe(100);
  });
});

describe("R53 mês anterior zerado (sem divisão por zero)", () => {
  const despesas = [sal(dt(2026, 10, 2), 3000), d(dt(2026, 10, 5), "Mercado", "Supermercado", 800)];
  const r = resumoInteligente(dataset({ despesas }), "MES", dt(2026, 10, 15), AGORA);
  it("variação nula e textos sem comparação", () => {
    expect(r.despesas.variacaoDecimos).toBeNull();
    expect(r.receitas.variacaoDecimos).toBeNull();
    expect(r.despesas.deltaCentavos).toBe(80000);
    expect(achar(r.insights, "DESPESAS_TOTAL")!.texto).toBe("Você gastou R$ 800,00. Não houve despesas no mês anterior para comparar.");
    const alta = achar(r.insights, "CATEGORIA_ALTA")!;
    expect(alta.variacaoDecimos).toBeNull();
    expect(alta.texto).toBe("Supermercado: R$ 800,00, sem gasto equivalente no mês anterior (+R$ 800,00).");
    expect(r.projecaoCentavos).toBe(165333);
    expect(achar(r.insights, "PROJECAO")!.variacaoDecimos).toBeNull();
    expect(r.taxaPoupancaDecimos).toBe(733);
    expect(JSON.stringify(r)).not.toMatch(/NaN|Infinity/);
  });
});

describe("R53 período fechado: ruído não vira insight, queda e orçamento respeitado", () => {
  const FIM = dt(2026, 11, 5);
  it("variações abaixo dos limiares (R$ 50 ou 20%) são ignoradas", () => {
    const despesas = [
      d(dt(2026, 9, 5), "Mercado", "Supermercado", 400),
      d(dt(2026, 9, 6), "Lazer", "Lazer", 1000),
      d(dt(2026, 10, 5), "Mercado", "Supermercado", 440), // +R$ 40 (10%): abaixo de R$ 50
      d(dt(2026, 10, 6), "Lazer", "Lazer", 1100), // +R$ 100 mas só 10%
    ];
    const r = resumoInteligente(dataset({ despesas }), "MES", dt(2026, 10, 10), FIM);
    expect(r.periodo.emAndamento).toBe(false);
    expect(r.projecaoCentavos).toBeNull();
    expect(tipos(r.insights)).not.toContain("CATEGORIA_ALTA");
    expect(tipos(r.insights)).not.toContain("CATEGORIA_QUEDA");
    expect(tipos(r.insights)).not.toContain("PROJECAO");
    expect(r.despesas.deltaCentavos).toBe(14000);
  });

  it("queda relevante, mês sem receita e boa notícia de orçamento", () => {
    const despesas = [
      d(dt(2026, 9, 5), "Lazer", "Lazer", 1000),
      d(dt(2026, 9, 6), "Mercado", "Supermercado", 400),
      d(dt(2026, 10, 5), "Lazer", "Lazer", 500),
      d(dt(2026, 10, 6), "Mercado", "Supermercado", 440),
    ];
    const orcamentos = [
      { id: 1, categoria: "Lazer", valorLimite: 600 },
      { id: 2, categoria: "supermercado", valorLimite: 500 },
    ];
    const r = resumoInteligente(dataset({ despesas, orcamentos }), "MES", dt(2026, 10, 10), FIM);
    expect(r.despesas).toEqual({ atualCentavos: 94000, anteriorCentavos: 140000, deltaCentavos: -46000, variacaoDecimos: -329 });
    expect(r.taxaPoupancaDecimos).toBeNull();
    const queda = achar(r.insights, "CATEGORIA_QUEDA")!;
    expect(queda.titulo).toBe("Lazer em queda");
    expect(queda.variacaoDecimos).toBe(-500);
    expect(queda.texto).toBe("Lazer: R$ 500,00, -R$ 500,00 (-50,0%) em relação ao mês anterior.");
    expect(achar(r.insights, "POUPANCA")!.texto).toBe("Nenhuma receita realizada no período: não dá para calcular a taxa de poupança.");
    expect(achar(r.insights, "BOA_NOTICIA")!.texto).toBe("Orçamento respeitado em todas as 2 categorias.");
    expect(tipos(r.insights)).toEqual(["DESPESAS_TOTAL", "POUPANCA", "CATEGORIA_QUEDA", "BOA_NOTICIA"]);
  });
});

describe("R53 semana", () => {
  it("segunda a domingo, comparando com o mesmo ponto da semana anterior", () => {
    const despesas = [
      d(dt(2026, 10, 6), "Mercado", "Supermercado", 200),
      d(dt(2026, 10, 9), "Mercado", "Supermercado", 100),
      d(dt(2026, 10, 13), "Mercado", "Supermercado", 300),
    ];
    const r = resumoInteligente(dataset({ despesas }), "SEMANA", dt(2026, 10, 14), AGORA);
    expect(r.periodo.rotulo).toBe("Semana de 12/10 a 18/10");
    expect(r.periodo.diasDecorridos).toBe(4);
    expect(r.despesas).toEqual({ atualCentavos: 30000, anteriorCentavos: 20000, deltaCentavos: 10000, variacaoDecimos: 500 });
    expect(r.projecaoCentavos).toBe(52500);
    expect(achar(r.insights, "PROJECAO")!.variacaoDecimos).toBe(750);
    expect(achar(r.insights, "CATEGORIA_ALTA")!.variacaoDecimos).toBe(500);
  });

  it("domingo pertence à semana que termina nele", () => {
    const p = periodoDoResumo("SEMANA", dt(2026, 10, 18), AGORA);
    expect(p.inicio).toBe(dt(2026, 10, 12, 0));
    expect(new Date(p.fim).getDate()).toBe(18);
  });
});

describe("R53 sem dados", () => {
  it("vazio, sem insights", () => {
    const r = resumoInteligente(dataset(), "MES", AGORA, AGORA);
    expect(r.vazio).toBe(true);
    expect(r.insights).toEqual([]);
    expect(textoDoResumo(r)).toContain("Sem lançamentos suficientes");
  });
  it("brlCentavos formata milhar e negativo", () => {
    expect(brlCentavos(123456789)).toBe("R$ 1.234.567,89");
    expect(brlCentavos(-5)).toBe("-R$ 0,05");
  });
});
