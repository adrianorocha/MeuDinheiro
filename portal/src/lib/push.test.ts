import { describe, expect, it } from "vitest";
import {
  deveRegistrarWeb,
  estadoPush,
  idEstavelWeb,
  lerPrefs,
  montarDocDispositivoWeb,
  PREFS_PADRAO,
  textoDaNotificacaoWeb,
  textoDoEstado,
  type EntradaEstado,
} from "./push";

const base: EntradaEstado = { vapidConfigurada: true, suportado: true, nuvemEUsuario: true, permissao: "default", ligado: false };

describe("estado do web push", () => {
  it("fica oculto sem VAPID, sem suporte ou sem nuvem/login", () => {
    expect(estadoPush({ ...base, vapidConfigurada: false })).toBe("oculto");
    expect(estadoPush({ ...base, suportado: false })).toBe("oculto");
    expect(estadoPush({ ...base, nuvemEUsuario: false })).toBe("oculto");
  });
  it("sem permissão, bloqueado, inativo e ativo", () => {
    expect(estadoPush(base)).toBe("sem-permissao");
    expect(estadoPush({ ...base, permissao: "denied" })).toBe("bloqueado");
    expect(estadoPush({ ...base, permissao: "denied", ligado: true })).toBe("bloqueado");
    expect(estadoPush({ ...base, permissao: "granted" })).toBe("inativo");
    expect(estadoPush({ ...base, permissao: "granted", ligado: true })).toBe("ativo");
  });
  it("só registra o token no estado ativo (opt-in)", () => {
    expect(deveRegistrarWeb({ ...base, permissao: "granted", ligado: true })).toBe(true);
    expect(deveRegistrarWeb({ ...base, permissao: "granted" })).toBe(false);
    expect(deveRegistrarWeb({ ...base, ligado: true })).toBe(false);
    expect(textoDoEstado("oculto")).toBe("");
    expect(textoDoEstado("bloqueado")).toContain("bloqueadas");
  });
});

describe("prefs e documento do dispositivo", () => {
  it("leitura tolerante: padrão ligado", () => {
    expect(lerPrefs(null)).toEqual(PREFS_PADRAO);
    expect(lerPrefs({ vencimentos: false, orcamento: "x" })).toEqual({ ...PREFS_PADRAO, vencimentos: false });
  });
  it("id estável do navegador", () => {
    const gerar = () => "123e4567-e89b-12d3-a456-426614174000";
    const id = idEstavelWeb(null, gerar);
    expect(id).toBe("web-123e4567e89b12d3a456426614174000");
    expect(idEstavelWeb(id, () => "outro")).toBe(id);
    expect(idEstavelWeb("a/b", gerar)).toBe(id);
  });
  it("documento só com o mínimo e mesmo formato do app", () => {
    const d = montarDocDispositivoWeb("tok", "portal", { ...PREFS_PADRAO, resumoSemanal: false }, "dono", "eu");
    expect(Object.keys(d).sort()).toEqual(["appVersao", "modelo", "plataforma", "prefs", "raizUid", "token"]);
    expect(d.plataforma).toBe("web");
    expect(d.prefs).toEqual({ vencimentos: true, orcamento: true, resumoSemanal: false, convites: true });
    expect(montarDocDispositivoWeb("tok", "portal", PREFS_PADRAO, "eu", "eu")).not.toHaveProperty("raizUid");
    expect(montarDocDispositivoWeb("tok", "portal", PREFS_PADRAO, null, "eu")).not.toHaveProperty("raizUid");
    expect(() => montarDocDispositivoWeb(" ", "portal", PREFS_PADRAO, null, "eu")).toThrow();
  });
});

describe("texto da notificação web", () => {
  it("usa só tipo/título/corpo genéricos e ignora o inválido", () => {
    expect(textoDaNotificacaoWeb({ tipo: "VENCIMENTOS", titulo: "2 contas vencem amanhã", corpo: "Toque para ver." })).toEqual({
      titulo: "2 contas vencem amanhã",
      corpo: "Toque para ver.",
      tag: "meudinheiro-VENCIMENTOS",
    });
    expect(textoDaNotificacaoWeb(undefined)).toBeNull();
    expect(textoDaNotificacaoWeb({ tipo: "QUALQUER", titulo: "x" })).toBeNull();
    expect(textoDaNotificacaoWeb({ tipo: "ORCAMENTO", titulo: "  " })).toBeNull();
  });
  it("não repassa valores monetários do resumo semanal", () => {
    const t = textoDaNotificacaoWeb({ tipo: "RESUMO_SEMANAL", titulo: "Resumo da sua semana", corpo: "Toque para ver", entradasCentavos: "100000", saidasCentavos: "5000" })!;
    expect(JSON.stringify(t)).not.toMatch(/100000|5000/);
  });
});
