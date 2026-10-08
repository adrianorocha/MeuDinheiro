import { describe, expect, it } from "vitest";
import { clampDia, diasNoMes, normalizaMes, somaMeses } from "./dates";
import { novoIdInt, novoIdLong } from "./ids";
import { round2, soma, toCents } from "./money";
import { dt } from "./fixtures";

describe("R1 - dinheiro", () => {
  it("arredonda meio para cima", () => {
    expect(round2(1.005)).toBe(1.01);
    expect(round2(2.675)).toBe(2.68);
    expect(round2(0.1 + 0.2)).toBe(0.3);
    expect(round2(-1.005)).toBe(-1.01);
  });
  it("converte em centavos inteiros", () => {
    expect(toCents(19.99)).toBe(1999);
    expect(toCents(0.29)).toBe(29);
    expect(Number.isInteger(toCents(1234.56))).toBe(true);
  });
  it("soma sem deriva de float", () => {
    expect(soma([0.1, 0.2, 0.3])).toBe(0.6);
    expect(soma(Array.from({ length: 10 }, () => 0.1))).toBe(1);
  });
  it("trata valores inválidos como zero", () => {
    expect(round2(Number.NaN)).toBe(0);
  });
});

describe("datas", () => {
  it("dias no mês e anos bissextos", () => {
    expect(diasNoMes(2, 2024)).toBe(29);
    expect(diasNoMes(2, 2025)).toBe(28);
    expect(diasNoMes(12, 2025)).toBe(31);
    expect(clampDia(31, 4, 2025)).toBe(30);
  });
  it("normaliza mês", () => {
    expect(normalizaMes(13, 2025)).toEqual({ mes: 1, ano: 2026 });
    expect(normalizaMes(0, 2025)).toEqual({ mes: 12, ano: 2024 });
  });
  it("somaMeses não deriva: 31/01 -> 28/02 -> 31/03", () => {
    const base = dt(2025, 1, 31);
    expect(new Date(somaMeses(base, 1)).getDate()).toBe(28);
    expect(new Date(somaMeses(base, 2)).getDate()).toBe(31);
    expect(new Date(somaMeses(base, 3)).getDate()).toBe(30);
    expect(new Date(somaMeses(base, 12)).getFullYear()).toBe(2026);
  });
  it("somaMeses atravessa o ano", () => {
    const r = new Date(somaMeses(dt(2025, 11, 15), 3));
    expect([r.getFullYear(), r.getMonth() + 1, r.getDate()]).toEqual([2026, 2, 15]);
  });
});

describe("ids", () => {
  it("int no intervalo e sem colisão", () => {
    const existentes = [1_500_000_000];
    const seq = [0.5, 0.25];
    const id = novoIdInt(existentes, () => seq.shift() ?? 0.9);
    expect(id).not.toBe(1_500_000_000);
    expect(id).toBeGreaterThanOrEqual(1_000_000_000);
    expect(id).toBeLessThan(2_000_000_000);
  });
  it("long incrementa se já existir", () => {
    expect(novoIdLong([100, 101], 100)).toBe(102);
  });
});
