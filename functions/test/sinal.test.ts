import { describe, expect, it } from "vitest";
import { dispositivosParaSinal, montarMensagemSinal, origemDoSinal, TIPO_SINAL } from "../src/dominio/sinal";
import type { Dispositivo } from "../src/dominio/tipos";

function disp(p: Partial<Dispositivo> & { id: string; uid: string }): Dispositivo {
  return { token: `token-${p.id}-`.padEnd(30, "x"), plataforma: "android", prefs: {}, ...p };
}

describe("sinal de sincronização (R86)", () => {
  it("só o documento 'portal' gera sinal", () => {
    expect(origemDoSinal("portal")).toBe("portal");
    expect(origemDoSinal("app")).toBeNull();
    expect(origemDoSinal("qualquer")).toBeNull();
  });

  it("vai só para Android da conta (dono ou membro), nunca para web nem para outra conta", () => {
    const todos = [
      disp({ id: "a", uid: "dono" }),
      disp({ id: "b", uid: "membro", raizUid: "dono" }),
      disp({ id: "c", uid: "estranho", raizUid: "dono" }), // aponta para a conta de outro sem ser membro
      disp({ id: "d", uid: "dono", plataforma: "web" }),
      disp({ id: "e", uid: "outro" }),
    ];
    const alvos = dispositivosParaSinal("dono", todos, new Set(["membro"]));
    expect(alvos.map((d) => d.id).sort()).toEqual(["a", "b"]);
  });

  it("mensagem é só de dados, prioridade alta, validade curta e colapsável", () => {
    const ds = [disp({ id: "a", uid: "dono" }), disp({ id: "b", uid: "dono", token: disp({ id: "a", uid: "dono" }).token })];
    const m = montarMensagemSinal(ds, "portal");
    expect(m.tokens).toHaveLength(1); // token repetido sai uma vez
    expect(m.data).toEqual({ tipo: TIPO_SINAL, origem: "portal" });
    expect(m.android).toEqual({ priority: "high", ttl: 120_000, collapseKey: "sync" });
    expect("notification" in m).toBe(false);
  });
});
