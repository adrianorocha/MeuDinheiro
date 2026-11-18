import { describe, expect, it } from "vitest";
import { custoDoAtraso, custoRotativo } from "./custoRotativo";
import { compararComAjustes, dividasParaSimular } from "./dividasSimulacao";
import { fluxoVazio, projetarFluxo } from "./fluxoCaixa";
import { conta, dataset, desp, dt } from "./fixtures";
import { gerarInsights, insight503020, insightAssinaturas, insightsCategorias, insightsMetas } from "./insightsEconomia";
import { aplicarReajuste, calcularReajustes } from "./reajuste";
import type { Divida, DespesaFixa, Meta } from "./types";

const AGORA = dt(2026, 10, 28);

function divida(id: number, nome: string, saldo: number, taxa: number | null, minima: number | null): Divida {
  return { id, nome, tipo: "OUTRA", saldoDevedor: saldo, taxaJurosMensal: taxa, parcelaMinima: minima, ativa: true };
}

describe("R97-D - rotativo / cheque especial", () => {
  it("R$ 1.000 a 14% a.m.: 1 mês = 140,00; 3 meses = 481,54", () => {
    const r = custoRotativo(1000, 0.14)!;
    expect(r.m1).toEqual({ meses: 1, juros: 140, total: 1140 });
    expect(r.m3).toEqual({ meses: 3, juros: 481.54, total: 1481.54 });
    expect(r.taxaAnual).toBeCloseTo(3.8179048, 6);
  });
  it("entradas inválidas => null", () => {
    expect(custoDoAtraso(0, 0.1, 1)).toBeNull();
    expect(custoDoAtraso(100, -0.1, 1)).toBeNull();
    expect(custoRotativo(100, Number.NaN)).toBeNull();
  });
  it("taxa zero não gera custo", () => {
    expect(custoDoAtraso(100, 0, 3)).toEqual({ meses: 3, juros: 0, total: 100 });
  });
});

describe("R97-D - avalanche x bola de neve (3 dívidas, extra R$ 200)", () => {
  const dividas = [divida(1, "Cartão", 3000, 12, 300), divida(2, "Empréstimo", 8000, 3, 400), divida(3, "Cheque especial", 1000, 8, 100)];
  const c = compararComAjustes(dividas, {}, 200);
  it("bola de neve (menor saldo primeiro): 20 meses, R$ 7.467,25 de juros", () => {
    expect(c.bolaDeNeve.meses).toBe(20);
    expect(c.bolaDeNeve.jurosTotal).toBe(746725);
    expect(c.bolaDeNeve.pagoTotal).toBe(1946725);
    expect(c.bolaDeNeve.quitacoes.map((q) => [q.id, q.mes])).toEqual([[3, 5], [1, 14], [2, 20]]);
  });
  it("avalanche (maior juro primeiro): 19 meses, R$ 6.695,63 de juros", () => {
    expect(c.avalanche.meses).toBe(19);
    expect(c.avalanche.jurosTotal).toBe(669563);
    expect(c.avalanche.pagoTotal).toBe(1869563);
    expect(c.avalanche.quitacoes.map((q) => [q.id, q.mes])).toEqual([[1, 12], [3, 13], [2, 19]]);
  });
  it("economia da avalanche sobre a bola de neve = R$ 771,62", () => {
    expect(c.economia.avalancheVsBola).toBe(77162);
  });
  it("só mínimos: o mínimo do cartão (300) não cobre os juros (360) => nunca quita e avisa", () => {
    expect(c.minimos.nuncaQuita).toBe(true);
    expect(c.economia.avalancheVsMinimos).toBeNull();
    expect(c.avisos.some((a) => a.includes("Cartão"))).toBe(true);
  });
  it("ajustes do simulador (taxa/mínimo) não alteram a dívida cadastrada", () => {
    const sem = [divida(9, "Sem taxa", 1000, null, null)];
    const ajustada = dividasParaSimular(sem, { 9: { taxaPct: 5, minimo: 100 } });
    expect(ajustada).toEqual([{ id: 9, nome: "Sem taxa", saldo: 100000, taxaBp: 500, minimo: 10000 }]);
    expect(sem[0].taxaJurosMensal).toBeNull();
    expect(dividasParaSimular(sem, {})[0]).toMatchObject({ taxaBp: 0, minimo: 0 });
    // taxa acima de 100% a.m. é limitada
    expect(dividasParaSimular(sem, { 9: { taxaPct: 500 } })[0].taxaBp).toBe(10000);
    // inativa ou quitada não entra
    expect(dividasParaSimular([{ ...sem[0], ativa: false }, divida(8, "Zero", 0, 1, 1)], {})).toEqual([]);
  });
});

describe("R97-F - reajuste", () => {
  it("composto: R$ 2.000 com 0,07%, -0,32%, 0,82% => IPCA 3 meses 0,5677% => R$ 2.011,35", () => {
    const l = calcularReajustes(2000, 3, [
      { id: "ipca", nome: "IPCA", serie: [0.07, -0.32, 0.82] },
      { id: "igpm", nome: "IGP-M", serie: [-1.16, -0.22, 1.57] },
    ])!;
    expect(l[0].acumulado).toBeCloseTo(0.0056772416, 9);
    expect(l[0].novoValor).toBe(201135);
    expect(l[0].diferenca).toBe(1135);
    expect(l[1].acumulado).toBeCloseTo(0.0017092607, 9);
    expect(l[1].novoValor).toBe(200342);
  });
  it("série mais curta que N: usa os meses disponíveis e sinaliza", () => {
    const l = calcularReajustes(1000, 12, [{ id: "ipca", nome: "IPCA", serie: [0.07, -0.32, 0.82] }])!;
    expect(l[0].mesesUsados).toBe(3);
    expect(l[0].incompleto).toBe(true);
  });
  it("acumulado informado substitui a composição (IGP-M 12 meses = 4,5%)", () => {
    const l = calcularReajustes(1500, 12, [{ id: "igpm", nome: "IGP-M", serie: [1], acumuladoInformadoPct: 4.5 }])!;
    expect(l[0].informado).toBe(true);
    expect(l[0].novoValor).toBe(156750);
    expect(aplicarReajuste(1500, 0.045)).toBe(156750);
  });
  it("índice negativo reduz o valor; entradas inválidas => null", () => {
    expect(aplicarReajuste(1000, -0.01)).toBe(99000);
    expect(calcularReajustes(0, 12, [])).toBeNull();
    expect(calcularReajustes(1000, 0, [])).toBeNull();
  });
});

describe("R97-E - fluxo de caixa", () => {
  const fixa = (over: Partial<DespesaFixa>): DespesaFixa => ({
    id: 1,
    descricao: "Fixa",
    valor: 100,
    conta: "111",
    categoria: "Casa",
    pic: "",
    tipo: "DEBITO",
    diaVencimento: 10,
    ultimaDataLancamento: dt(2026, 10, 5),
    cartaoId: null,
    ...over,
  });
  const base = () => [
    desp({ descricao: "Saldo", tipo: "CREDITO", valor: 5000, data: dt(2026, 6, 1), categoria: "Salário" }),
    desp({ descricao: "Mercado", valor: 1000, data: dt(2026, 7, 10) }),
    desp({ descricao: "Mercado", valor: 1000, data: dt(2026, 8, 10) }),
    desp({ descricao: "Mercado", valor: 1000, data: dt(2026, 9, 10) }),
  ];

  it("déficit: salário 5.000, fixa 5.500, variável 1.000 => corte de R$ 1.291,67 por mês em 12 meses", () => {
    const ds = dataset({
      contas: [conta()],
      despesas: base(),
      despesasFixas: [fixa({ id: 1, descricao: "Salário", tipo: "CREDITO", valor: 5000, diaVencimento: 5 }), fixa({ id: 2, descricao: "Aluguel", valor: 5500 })],
    });
    const r = projetarFluxo(ds, AGORA, 12, 0);
    expect(r.saldoInicial).toBe(200000);
    expect(r.variavelBase).toBe(100000);
    expect(r.mesesComDados).toBe(3);
    expect(r.meses).toHaveLength(12);
    // outubro: sem fixas (já lançadas), só a variável restante: 2.000 - 1.000 = 1.000
    expect(r.meses[0]).toMatchObject({ entradas: 0, saidas: 0, variavel: 100000, saldoFinal: 100000, negativo: false });
    // novembro: +5.000 -5.500 -1.000 => saldo -500
    expect(r.meses[1]).toMatchObject({ entradas: 500000, saidas: 550000, saldoFinal: -50000, negativo: true });
    expect(r.primeiroNegativo).toBe(1);
    expect(r.meses[11].saldoFinal).toBe(-1550000);
    expect(r.cortePorMes).toBe(129167);
  });

  it("superávit: sem meses negativos e sem corte", () => {
    const ds = dataset({
      contas: [conta()],
      despesas: base(),
      despesasFixas: [fixa({ id: 1, descricao: "Salário", tipo: "CREDITO", valor: 5000, diaVencimento: 5 }), fixa({ id: 2, descricao: "Aluguel", valor: 2000 })],
    });
    const r = projetarFluxo(ds, AGORA, 6, 0);
    expect(r.meses).toHaveLength(6);
    expect(r.primeiroNegativo).toBeNull();
    expect(r.cortePorMes).toBe(0);
    expect(r.meses[5].saldoFinal).toBe(100000 + 5 * 200000);
  });

  it("inflação corrige a despesa variável (1% a.m.)", () => {
    const ds = dataset({ contas: [conta()], despesas: base() });
    const r = projetarFluxo(ds, AGORA, 6, 0.01);
    // média corrigida: 1000 x (1,01^1 + 1,01^2 + 1,01^3)/3 = 1020,1...
    expect(r.variavelBase).toBe(Math.round((100000 * (1.01 + 1.01 ** 2 + 1.01 ** 3)) / 3));
    expect(r.meses[2].variavel).toBe(Math.round(r.variavelBase * 1.01 ** 2));
  });

  it("horizonte limitado a 6..12 meses e sem dados nada é estimado", () => {
    const vazio = dataset();
    expect(projetarFluxo(vazio, AGORA, 3, 0).meses).toHaveLength(6);
    expect(projetarFluxo(vazio, AGORA, 30, 0).meses).toHaveLength(12);
    const r = projetarFluxo(vazio, AGORA, 6, 0);
    expect(r.mesesComDados).toBe(0);
    expect(fluxoVazio(r)).toBe(true);
  });

  it("parcela futura e fatura entram como saída conhecida", () => {
    const ds = dataset({
      contas: [conta()],
      despesas: [
        desp({ descricao: "TV (2/3)", valor: 300, data: dt(2026, 11, 20), pago: false, grupoId: "parc:abc" }),
        desp({ descricao: "TV (3/3)", valor: 300, data: dt(2026, 12, 20), pago: false, grupoId: "parc:abc" }),
      ],
    });
    const r = projetarFluxo(ds, AGORA, 6, 0);
    expect(r.meses[1].saidas).toBe(30000);
    expect(r.meses[2].saidas).toBe(30000);
    expect(r.meses[3].saidas).toBe(0);
  });
});

describe("R97-E - insights de economia", () => {
  const par = { inflacaoMensal: 0.005, cdiMensal: 0.0108, pctCdi: 1 };

  it("categoria acima da inflação + 5 p.p.: média 200 x 100 anterior => estimativa R$ 96,96/mês", () => {
    const despesas = [];
    for (const m of [1, 2, 3, 4, 5, 6]) despesas.push(desp({ descricao: "Cinema", categoria: "Lazer", valor: 100, data: dt(2026, m, 12) }));
    for (const m of [7, 8, 9]) despesas.push(desp({ descricao: "Cinema", categoria: "Lazer", valor: 200, data: dt(2026, m, 12) }));
    // categoria estável: 100 -> 102 (2% < limite de 8,04%)
    for (const m of [1, 2, 3, 4, 5, 6]) despesas.push(desp({ descricao: "Feira", categoria: "Mercado", valor: 100, data: dt(2026, m, 15) }));
    for (const m of [7, 8, 9]) despesas.push(desp({ descricao: "Feira", categoria: "Mercado", valor: 102, data: dt(2026, m, 15) }));
    const r = insightsCategorias(dataset({ despesas }), AGORA, 0.005);
    expect(r).toHaveLength(1);
    expect(r[0].id).toBe("categoria:lazer");
    expect(r[0].valorMensal).toBe(9696);
    expect(r[0].calculo.length).toBeGreaterThanOrEqual(3);
  });

  it("categoria sem histórico anterior suficiente (< 3 meses) não gera insight", () => {
    const despesas = [desp({ categoria: "Nova", valor: 500, data: dt(2026, 5, 3) }), ...[7, 8, 9].map((m) => desp({ categoria: "Nova", valor: 500, data: dt(2026, m, 3) }))];
    expect(insightsCategorias(dataset({ despesas }), AGORA, 0.005)).toEqual([]);
  });

  it("assinaturas: soma e a de maior peso", () => {
    const fx = (id: number, nome: string, valor: number): DespesaFixa => ({ id, descricao: nome, valor, conta: "111", categoria: "Assinaturas", pic: "", tipo: "DEBITO", diaVencimento: 5, ultimaDataLancamento: null, cartaoId: null });
    const i = insightAssinaturas(dataset({ despesasFixas: [fx(1, "Streaming", 50), fx(2, "Música", 20)] }), AGORA)!;
    expect(i.valorMensal).toBe(5000);
    expect(i.calculo[0]).toContain("R$ 70,00/mês");
    expect(i.calculo[1]).toContain("Streaming");
    expect(insightAssinaturas(dataset(), AGORA)).toBeNull();
  });

  it("50/30/20 do mês anterior: receita 10.000, necessidades 4.000, desejos 4.000, poupança 0 => R$ 2.000", () => {
    const despesas = [
      desp({ tipo: "CREDITO", valor: 10000, categoria: "Salário", data: dt(2026, 9, 5) }),
      desp({ valor: 4000, categoria: "Supermercado", data: dt(2026, 9, 6) }),
      desp({ valor: 4000, categoria: "Lazer", data: dt(2026, 9, 7) }),
    ];
    const i = insight503020(dataset({ despesas }), AGORA)!;
    expect(i.valorMensal).toBe(200000);
    expect(i.calculo.join(" ")).toContain("faltou R$ 2.000,00");
    expect(i.calculo.join(" ")).toContain("excesso R$ 1.000,00");
    // dentro dos alvos => sem insight; sem receita => sem insight
    const ok = [
      desp({ tipo: "CREDITO", valor: 10000, categoria: "Salário", data: dt(2026, 9, 5) }),
      desp({ valor: 5000, categoria: "Supermercado", data: dt(2026, 9, 6) }),
      desp({ valor: 3000, categoria: "Lazer", data: dt(2026, 9, 7) }),
      desp({ valor: 2000, categoria: "Reserva", data: dt(2026, 9, 8) }),
    ];
    expect(insight503020(dataset({ despesas: ok }), AGORA)).toBeNull();
    expect(insight503020(dataset(), AGORA)).toBeNull();
  });

  it("meta: R$ 12.000 em 12 meses com R$ 1.000 guardados => aporte de R$ 864,70 (sem rendimento: R$ 916,67)", () => {
    const meta: Meta = { id: 7, nome: "Viagem", valorObjetivo: 12000, valorGuardado: 1000, icone: "", dataAlvo: dt(2027, 10, 28, 0) };
    const [i] = insightsMetas(dataset({ metas: [meta] }), AGORA, par);
    expect(i.id).toBe("meta:7");
    expect(i.valorMensal).toBe(86470);
    expect(i.calculo.join(" ")).toContain("R$ 916,67");
    // meta concluída ou sem prazo => nada
    expect(insightsMetas(dataset({ metas: [{ ...meta, valorGuardado: 12000 }, { ...meta, id: 8, dataAlvo: null }] }), AGORA, par)).toEqual([]);
  });

  it("gerarInsights ordena do maior para o menor valor estimado", () => {
    const meta: Meta = { id: 7, nome: "Viagem", valorObjetivo: 12000, valorGuardado: 1000, icone: "", dataAlvo: dt(2027, 10, 28, 0) };
    const lista = gerarInsights(dataset({ metas: [meta], despesas: [desp({ tipo: "CREDITO", valor: 10000, categoria: "Salário", data: dt(2026, 9, 5) })] }), AGORA, par);
    const valores = lista.map((i) => i.valorMensal);
    expect([...valores].sort((a, b) => b - a)).toEqual(valores);
    expect(lista.some((i) => i.tipo === "META")).toBe(true);
  });
});
