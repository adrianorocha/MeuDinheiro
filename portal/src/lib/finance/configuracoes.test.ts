import { describe, expect, it } from "vitest";
import { alterarConfig, decidirConfig, mesclarConfig, normalizarGeral } from "./configuracoes";
import { CONFIG_PADRAO } from "./painel503020";

/** MESMOS cenários de app/src/test/.../ConfiguracaoGeralTest.kt */
const painelA = { ...CONFIG_PADRAO, mesesReserva: 3 };
const painelB = { ...CONFIG_PADRAO, mesesReserva: 9 };

describe("R57 mesclagem de configurações (mais novo vence)", () => {
  it("nuvem mais nova substitui o local", () => {
    const r = mesclarConfig({ painel503020: painelA, atualizadoEm: 100 }, { painel503020: painelB, limiteDiarioAteReceita: true, atualizadoEm: 200 });
    expect(r.acao).toBe("APLICAR_NUVEM");
    expect(r.config.painel503020?.mesesReserva).toBe(9);
    expect(r.config.limiteDiarioAteReceita).toBe(true);
  });
  it("local mais novo é mantido e deve ser enviado", () => {
    const r = mesclarConfig({ painel503020: painelA, atualizadoEm: 300 }, { painel503020: painelB, atualizadoEm: 200 });
    expect(r.acao).toBe("ENVIAR_LOCAL");
    expect(r.config.painel503020?.mesesReserva).toBe(3);
  });
  it("empate: a nuvem não sobrescreve o local", () => {
    const r = mesclarConfig({ painel503020: painelA, atualizadoEm: 200 }, { painel503020: painelB, atualizadoEm: 200 });
    expect(r.acao).toBe("NADA");
    expect(r.config.painel503020?.mesesReserva).toBe(3);
  });
  it("nuvem sem data (nula/0/ausente) não sobrescreve; local sem data cede a uma nuvem datada", () => {
    expect(decidirConfig({ painel503020: painelA, atualizadoEm: 5 }, { painel503020: painelB })).toBe("ENVIAR_LOCAL");
    expect(decidirConfig({ painel503020: painelA, atualizadoEm: 5 }, { painel503020: painelB, atualizadoEm: null })).toBe("ENVIAR_LOCAL");
    expect(decidirConfig({ painel503020: painelA }, { painel503020: painelB })).toBe("NADA");
    expect(decidirConfig({ painel503020: painelA }, { painel503020: painelB, atualizadoEm: 1 })).toBe("APLICAR_NUVEM");
  });
  it("sem nuvem: nada a aplicar; local datado manda enviar", () => {
    expect(decidirConfig({ atualizadoEm: 1 }, null)).toBe("ENVIAR_LOCAL");
    expect(decidirConfig({}, null)).toBe("NADA");
  });
  it("normaliza lixo e alterar sempre avança o carimbo", () => {
    expect(normalizarGeral({ painel503020: { metas: { necessidades: 999 } }, limiteDiarioAteReceita: "x", atualizadoEm: "ontem" })).toEqual({
      painel503020: CONFIG_PADRAO,
    });
    const a = alterarConfig({ atualizadoEm: 1000 }, { limiteDiarioAteReceita: true }, 500); // relógio atrasado
    expect(a.atualizadoEm).toBe(1001);
    expect(a.limiteDiarioAteReceita).toBe(true);
  });
});
