import { describe, expect, it } from "vitest";
import { aliquotaIR, aporteParaMeta, compararRendimento, fatorLiquido, poupancaPelaRegra, taxaLiquidaMensal, validarRendimento, type EntradaRendimento } from "./rendimento";

const BASE: EntradaRendimento = { valorInicial: 10000, aporteMensal: 0, meses: 12, cdiMensal: 0.0108, pctCdi: 1, poupancaMensal: 0.006624, inflacaoMensal: 0.0082 };

describe("R97-B - IR regressivo", () => {
  it("alíquota por faixa de dias (limites inclusivos no fim da faixa)", () => {
    expect([1, 180, 181, 360, 361, 720, 721, 1000].map(aliquotaIR)).toEqual([0.225, 0.225, 0.2, 0.2, 0.175, 0.175, 0.15, 0.15]);
  });
  it("fator líquido: 12 meses (360 dias) a 1,08% a.m. => IR de 20% sobre o rendimento", () => {
    expect(fatorLiquido(0.0108, 12)).toBeCloseTo(1 + (Math.pow(1.0108, 12) - 1) * 0.8, 12);
    expect(fatorLiquido(0.0108, 13)).toBeCloseTo(1 + (Math.pow(1.0108, 13) - 1) * 0.825, 12);
  });
});

describe("R97-B - poupança pela regra", () => {
  it("Selic > 8,5%: 0,5% a.m. + TR (composto) = 0,6624% com TR 0,1616%", () => {
    expect(poupancaPelaRegra(0.1375, 0.001616)).toBeCloseTo(0.0066241, 7);
  });
  it("Selic <= 8,5%: 70% da Selic anual + TR", () => {
    const esperado = (1 + (Math.pow(1.049, 1 / 12) - 1)) * 1.001616 - 1; // 0,7 x 7% = 4,9% a.a.
    expect(poupancaPelaRegra(0.07, 0.001616)).toBeCloseTo(esperado, 12);
    expect(poupancaPelaRegra(0.085, 0)).toBeCloseTo(Math.pow(1 + 0.7 * 0.085, 1 / 12) - 1, 12);
  });
});

describe("R97-B - poupança x CDI 100% em 12 meses, R$ 10.000, CDI 1,08% a.m.", () => {
  const r = compararRendimento(BASE);
  it("poupança: 10.000 x 1,006624^12", () => {
    expect(r.totalAplicado).toBe(1000000);
    expect(r.poupanca.saldoFinal).toBe(1082449);
    expect(r.poupanca.ganhoLiquido).toBe(82449);
  });
  it("CDI: bruto 11.375,82; IR 20% = 275,16; líquido 11.100,66", () => {
    expect(r.cdi.ir).toBe(27516);
    expect(r.cdi.saldoFinal).toBe(1110066);
    expect(r.cdi.ganhoLiquido).toBe(110066);
    expect(r.aliquotaInicial).toBe(0.2);
  });
  it("ganho real descontada a inflação (0,82% a.m.): corrigido = 11.029,61", () => {
    expect(r.aplicadoCorrigido).toBe(1102961);
    expect(r.cdi.ganhoReal).toBe(7105);
    expect(r.poupanca.ganhoReal).toBe(-20512);
    expect(r.parado.saldoFinal).toBe(1000000);
    expect(r.parado.ganhoReal).toBe(-102961);
  });
  it("série de evolução começa no valor aplicado e termina nos saldos finais", () => {
    expect(r.serie).toHaveLength(13);
    expect(r.serie[0]).toEqual({ mes: 0, poupanca: 1000000, cdiLiquido: 1000000, parado: 1000000, corrigidoInflacao: 1000000 });
    expect(r.serie[12].cdiLiquido).toBe(r.cdi.saldoFinal);
    expect(r.serie[6].cdiLiquido).toBe(1051596);
  });
});

describe("R97-B - com aportes (R$ 1.000 + R$ 500/mês, 24 meses, 110% do CDI)", () => {
  const r = compararRendimento({ valorInicial: 1000, aporteMensal: 500, meses: 24, cdiMensal: 0.0108, pctCdi: 1.1, poupancaMensal: 0.006624, inflacaoMensal: 0.005 });
  it("totais (IR por lote, prazo próprio de cada aporte)", () => {
    expect(r.totalAplicado).toBe(1300000);
    expect(r.poupanca.saldoFinal).toBe(1413180);
    expect(r.cdi.saldoFinal).toBe(1473314);
    expect(r.cdi.ir).toBe(38616);
    expect(r.cdi.ganhoLiquido).toBe(173314);
    expect(r.parado.ganhoLiquido).toBe(0);
    expect(r.aplicadoCorrigido).toBe(1384314);
  });
  it("o aporte do último mês não rende nem paga IR", () => {
    const so = compararRendimento({ valorInicial: 0, aporteMensal: 100, meses: 1, cdiMensal: 0.01, pctCdi: 1, poupancaMensal: 0.01, inflacaoMensal: 0 });
    expect(so.cdi.saldoFinal).toBe(10000);
    expect(so.poupanca.saldoFinal).toBe(10000);
    expect(so.cdi.ir).toBe(0);
  });
});

describe("R97-B - validação e meta", () => {
  it("valida entradas", () => {
    expect(validarRendimento(BASE)).toBeNull();
    expect(validarRendimento({ ...BASE, meses: 0 })).not.toBeNull();
    expect(validarRendimento({ ...BASE, valorInicial: 0, aporteMensal: 0 })).not.toBeNull();
    expect(validarRendimento({ ...BASE, aporteMensal: -1 })).not.toBeNull();
    expect(validarRendimento({ ...BASE, cdiMensal: Number.NaN })).not.toBeNull();
  });
  it("taxa líquida mensal equivalente em 12 meses", () => {
    expect(taxaLiquidaMensal(0.0108, 12)).toBeCloseTo(0.00873958, 7);
  });
  it("aporte para meta: R$ 12.000 em 12 meses com R$ 1.000 guardados => R$ 864,70/mês", () => {
    expect(aporteParaMeta(12000, 1000, 12, taxaLiquidaMensal(0.0108, 12))).toBe(86470);
    expect(aporteParaMeta(12000, 1000, 12, 0)).toBe(91667); // 11.000/12 = 916,666... arredondado para cima
    expect(aporteParaMeta(1000, 5000, 12, 0.01)).toBe(0);
  });
});
