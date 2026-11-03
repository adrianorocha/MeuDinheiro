import { describe, expect, it } from "vitest";
import { compararParcelado, taxaImplicita, validarParcelado } from "./avistaParcelado";

describe("R97-C - taxa implícita", () => {
  it("à vista 1000 x 10x de 110 => 1,7715% a.m. (23,46% a.a.)", () => {
    const i = taxaImplicita({ aVista: 1000, n: 10, parcela: 110 })!;
    expect(i).toBeCloseTo(0.0177154269, 9);
    // fecha a conta: soma de 110/(1+i)^k = 1000
    let vp = 0;
    for (let k = 1; k <= 10; k++) vp += 110 / Math.pow(1 + i, k);
    expect(vp).toBeCloseTo(1000, 6);
  });
  it("sem juros => 0", () => {
    expect(taxaImplicita({ aVista: 1000, n: 10, parcela: 100 })).toBe(0);
  });
  it("com entrada: 1000 à vista, entrada 200, 10x de 120 => 8,1442% a.m.", () => {
    expect(taxaImplicita({ aVista: 1000, n: 10, parcela: 120, entrada: 200 })).toBeCloseTo(0.0814416565, 8);
  });
  it("parcelado mais barato que à vista => taxa negativa", () => {
    expect(taxaImplicita({ aVista: 1000, n: 10, parcela: 95 })!).toBeLessThan(0);
  });
  it("entradas inválidas => null", () => {
    expect(taxaImplicita({ aVista: 0, n: 10, parcela: 100 })).toBeNull();
    expect(taxaImplicita({ aVista: 1000, n: 0, parcela: 100 })).toBeNull();
    expect(taxaImplicita({ aVista: 1000, n: 3, parcela: 100, entrada: 1000 })).toBeNull();
    expect(validarParcelado({ aVista: 1000, n: 3, parcela: 0 })).not.toBeNull();
  });
});

describe("R97-C - comparação com o rendimento líquido (CDI 1,08% a.m., 100%)", () => {
  it("1000 à vista x 10x de 110: à vista custa menos em valor presente", () => {
    const r = compararParcelado({ aVista: 1000, n: 10, parcela: 110 }, 0.0108, 1)!;
    expect(r.totalPago).toBe(1100);
    expect(r.juros).toBe(100);
    expect(r.taxaAnual).toBeCloseTo(0.234571631, 8);
    expect(r.valorPresente).toBe(1049.77);
    expect(r.diferenca).toBe(49.77);
    expect(r.descontoBreakEven).toBeCloseTo(0.04566525, 7);
    expect(r.descontoOferecido).toBeCloseTo(0.0909090909, 9);
    expect(r.menorCusto).toBe("AVISTA");
  });
  it("1000 à vista x 10x de 102: o parcelado custa menos em valor presente", () => {
    const r = compararParcelado({ aVista: 1000, n: 10, parcela: 102 }, 0.0108, 1)!;
    expect(r.valorPresente).toBe(973.42);
    expect(r.diferenca).toBe(-26.58);
    expect(r.menorCusto).toBe("PARCELADO");
    // o desconto de equilíbrio depende só das parcelas e da taxa: 4,5665% do total parcelado
    expect(r.descontoBreakEven).toBeCloseTo(0.04566525, 7);
  });
  it("preço à vista igual ao VP => empate", () => {
    const r = compararParcelado({ aVista: 1049.77, n: 10, parcela: 110 }, 0.0108, 1)!;
    expect(r.menorCusto).toBe("EMPATE");
  });
  it("rejeita entradas inválidas", () => {
    expect(compararParcelado({ aVista: 1000, n: 10, parcela: 110 }, Number.NaN, 1)).toBeNull();
    expect(compararParcelado({ aVista: -1, n: 10, parcela: 110 }, 0.01, 1)).toBeNull();
  });
});
