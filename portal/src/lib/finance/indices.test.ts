import { describe, expect, it } from "vitest";
import {
  acumuladoDosUltimos,
  acumular,
  anualDeMensal,
  carregarIndices,
  CHAVE_INDICES,
  competenciaAnterior,
  foiEditado,
  ganhoReal,
  indicesPadrao,
  mensalDeAnual,
  mesclarSalvo,
  restaurarIndices,
  salvarIndices,
  urlHistorico,
  valorMensalPct,
  type ArmazemTexto,
} from "./indices";

function armazem(inicial: Record<string, string> = {}): ArmazemTexto & { dados: Record<string, string> } {
  const dados = { ...inicial };
  return {
    dados,
    getItem: (k) => (k in dados ? dados[k] : null),
    setItem: (k, v) => {
      dados[k] = v;
    },
    removeItem: (k) => {
      delete dados[k];
    },
  };
}

describe("R97 - índices: semente", () => {
  it("traz os valores lidos da Debit em 28/10/2026", () => {
    const c = indicesPadrao();
    const por = (id: string) => c.itens.find((i) => i.id === id)!;
    expect(valorMensalPct(por("selic"))).toBe(1.08);
    expect(valorMensalPct(por("cdi"))).toBe(1.08);
    expect(valorMensalPct(por("poupanca"))).toBe(0.6624);
    expect(valorMensalPct(por("tr"))).toBe(0.1616);
    expect(por("ipca").serie).toEqual([0.07, -0.32, 0.82]);
    expect(por("igpm").serie).toEqual([-1.16, -0.22, 1.57]);
    expect(por("incc").serie).toEqual([0.61, 0.66, 0.22]);
    expect(c.salarioMinimo.centavos).toBe(162100);
    expect(c.conferidoEm).toBe("2026-10-28");
    expect(por("selic").aproximada).toBe(true);
    expect(por("ipca").aproximada).toBe(false);
    expect(c.extras.selicMetaAnual).toBeNull();
    expect(c.extras.inflacao12m).toBeNull();
  });

  it("link de histórico só para quem tem página", () => {
    const c = indicesPadrao();
    expect(urlHistorico(c.itens.find((i) => i.id === "ipca")!.slug)).toBe("https://www.debit.com.br/tabelas/ipca-indice-nacional-de-precos-ao-consumidor-amplo");
    expect(urlHistorico(c.itens.find((i) => i.id === "tbf")!.slug)).toBeNull();
  });

  it("competência anterior", () => {
    expect(competenciaAnterior("09/2026", 0)).toBe("09/2026");
    expect(competenciaAnterior("09/2026", 2)).toBe("07/2026");
    expect(competenciaAnterior("02/2026", 3)).toBe("11/2025");
    expect(competenciaAnterior("", 1)).toBe("");
  });
});

describe("R97 - índices: conversões", () => {
  it("mensal <-> anual", () => {
    expect(anualDeMensal(0.0108)).toBeCloseTo(0.1375822289, 9);
    expect(mensalDeAnual(anualDeMensal(0.0108))).toBeCloseTo(0.0108, 12);
    expect(anualDeMensal(0)).toBe(0);
  });
  it("acumulado por composição", () => {
    expect(acumular([0.0007, -0.0032, 0.0082])).toBeCloseTo(0.0056772416, 9);
    expect(acumular([])).toBe(0);
  });
  it("acumulado dos últimos N meses (usa o que houver e avisa)", () => {
    const r = acumuladoDosUltimos([0.07, -0.32, 0.82], 12);
    expect(r.mesesUsados).toBe(3);
    expect(r.incompleto).toBe(true);
    expect(r.acumulado).toBeCloseTo(0.0056772416, 9);
    const r2 = acumuladoDosUltimos([0.07, -0.32, 0.82], 2);
    expect(r2.incompleto).toBe(false);
    expect(r2.acumulado).toBeCloseTo(0.9968 * 1.0082 - 1, 12);
    expect(acumuladoDosUltimos([], 3)).toEqual({ acumulado: 0, mesesUsados: 0, incompleto: true });
  });
  it("ganho real", () => {
    expect(ganhoReal(0.1375, 0.05)).toBeCloseTo(0.083333333, 8);
    expect(ganhoReal(0.04, 0.05)).toBeLessThan(0);
    expect(ganhoReal(0.05, 0.05)).toBeCloseTo(0, 12);
  });
});

describe("R97 - índices: persistência local", () => {
  it("sem armazenamento devolve o padrão e não lança", () => {
    expect(carregarIndices(null)).toEqual(indicesPadrao());
    expect(() => salvarIndices(indicesPadrao(), null)).not.toThrow();
  });
  it("grava e relê edições", () => {
    const a = armazem();
    const c = indicesPadrao();
    c.itens.find((i) => i.id === "cdi")!.serie = [1.1];
    c.extras.selicMetaAnual = 14.5;
    salvarIndices(c, a);
    const lido = carregarIndices(a);
    expect(valorMensalPct(lido.itens.find((i) => i.id === "cdi")!)).toBe(1.1);
    expect(lido.extras.selicMetaAnual).toBe(14.5);
    expect(foiEditado(lido.itens.find((i) => i.id === "cdi")!)).toBe(true);
    expect(foiEditado(lido.itens.find((i) => i.id === "selic")!)).toBe(false);
  });
  it("JSON corrompido ou campos inválidos voltam ao padrão", () => {
    expect(carregarIndices(armazem({ [CHAVE_INDICES]: "{nao e json" }))).toEqual(indicesPadrao());
    const m = mesclarSalvo({ itens: [{ id: "cdi", serie: ["x"], competencia: "abc" }, { id: "desconhecido", serie: [1] }], salarioMinimo: { centavos: -5 }, extras: { taxaRotativoMensal: -1 } });
    expect(m).toEqual(indicesPadrao());
  });
  it("restaurar padrão limpa o armazenamento", () => {
    const a = armazem();
    salvarIndices(indicesPadrao(), a);
    expect(a.dados[CHAVE_INDICES]).toBeDefined();
    expect(restaurarIndices(a)).toEqual(indicesPadrao());
    expect(a.dados[CHAVE_INDICES]).toBeUndefined();
  });
});
