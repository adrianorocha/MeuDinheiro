import { describe, expect, it } from "vitest";
import { criarPin, deveBloquear, lerPinGuardado, pinValido, verificarPin } from "./pin";

describe("R34 PIN", () => {
  it("valida 4 a 8 dígitos", () => {
    expect(pinValido("1234")).toBe(true);
    expect(pinValido("12345678")).toBe(true);
    expect(pinValido("123")).toBe(false);
    expect(pinValido("123456789")).toBe(false);
    expect(pinValido("12a4")).toBe(false);
  });
  it("hash não contém o PIN, usa sal e verifica", async () => {
    const a = await criarPin("4821", 1000);
    const b = await criarPin("4821", 1000);
    expect(JSON.stringify(a)).not.toContain("4821");
    expect(a.salt).not.toBe(b.salt);
    expect(a.hash).not.toBe(b.hash);
    expect(await verificarPin("4821", a)).toBe(true);
    expect(await verificarPin("4822", a)).toBe(false);
    expect(await verificarPin("abc", a)).toBe(false);
  });
  it("recusa PIN inválido ao criar", async () => {
    await expect(criarPin("12", 1000)).rejects.toThrow();
  });
  it("lê JSON guardado com tolerância a lixo", async () => {
    const g = await criarPin("1234", 1000);
    expect(lerPinGuardado(JSON.stringify(g))).toEqual(g);
    expect(lerPinGuardado("{}")).toBeNull();
    expect(lerPinGuardado("nao json")).toBeNull();
    expect(lerPinGuardado(null)).toBeNull();
  });
  it("bloqueio por inatividade", () => {
    expect(deveBloquear(0, 5 * 60_000, 5)).toBe(true);
    expect(deveBloquear(0, 5 * 60_000 - 1, 5)).toBe(false);
    expect(deveBloquear(0, 999_999_999, 0)).toBe(false);
  });
});
