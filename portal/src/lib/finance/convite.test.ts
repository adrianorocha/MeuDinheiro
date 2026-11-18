import { describe, expect, it } from "vitest";
import {
  ALFABETO,
  codigoValido,
  estadoDoConvite,
  gerarCodigo,
  mensagemDoEstado,
  normalizarCodigo,
  novoConviteDoc,
  VALIDADE_CONVITE_MS,
} from "./convite";

// Os mesmos vetores estão em ConviteCodigoTest.kt (app).
describe("R67 código de convite", () => {
  it("alfabeto sem símbolos ambíguos e com 31 símbolos", () => {
    expect(ALFABETO.length).toBe(31);
    expect(new Set(ALFABETO).size).toBe(31);
    for (const c of "01OIL") expect(ALFABETO.includes(c)).toBe(false);
  });

  it("gera 8 caracteres do alfabeto e é determinístico com sorteio injetado", () => {
    let i = 0;
    expect(gerarCodigo((n) => i++ % n)).toBe("ABCDEFGH");
    let j = 23;
    expect(gerarCodigo(() => j++)).toBe("23456789");
    for (let k = 0; k < 200; k++) {
      const g = gerarCodigo();
      expect(g).toHaveLength(8);
      expect(codigoValido(g)).toBe(true);
    }
  });

  it("valida formato", () => {
    expect(codigoValido("ABCD2345")).toBe(true);
    expect(codigoValido("ABCD234")).toBe(false);
    expect(codigoValido("ABCD23456")).toBe(false);
    expect(codigoValido("ABCD234O")).toBe(false);
    expect(codigoValido("ABCD2341")).toBe(false);
    expect(codigoValido("abcd2345")).toBe(false);
    expect(codigoValido(null)).toBe(false);
  });

  it("normaliza caixa, espaços e hífen", () => {
    expect(normalizarCodigo(" abcd-2345 ")).toBe("ABCD2345");
    expect(normalizarCodigo(null)).toBe("");
  });

  it("estado: validade de 7 dias, uso único e precedência", () => {
    const agora = 1_000_000;
    const exp = agora + VALIDADE_CONVITE_MS;
    expect(VALIDADE_CONVITE_MS).toBe(7 * 24 * 3600 * 1000);
    expect(estadoDoConvite(false, exp, agora)).toBe("VALIDO");
    expect(estadoDoConvite(false, exp, exp - 1)).toBe("VALIDO");
    expect(estadoDoConvite(false, exp, exp)).toBe("EXPIRADO");
    expect(estadoDoConvite(true, exp, agora)).toBe("USADO");
    expect(estadoDoConvite(true, exp, exp + 5)).toBe("USADO");
    expect(estadoDoConvite(undefined, exp, agora)).toBe("INVALIDO");
    expect(estadoDoConvite(false, null, agora)).toBe("INVALIDO");
    expect(mensagemDoEstado("VALIDO")).toBeNull();
  });

  it("documento do convite", () => {
    const d = novoConviteDoc("dono1", "EDITOR", "dono1", 100);
    expect(d).toEqual({ donoUid: "dono1", papel: "EDITOR", criadoEm: 100, expiraEm: 100 + VALIDADE_CONVITE_MS, usado: false, criadoPor: "dono1" });
    expect(() => novoConviteDoc("d", "DONO" as never, "d", 1)).toThrow();
  });
});
