import { describe, expect, it } from "vitest";
import { papeisConcediveis, papelDoDocumento, podeExecutar, podeGerenciarMembro, TODAS_ACOES, type Papel } from "./permissoes";

// Colunas: LEITOR, EDITOR, ADMIN, DONO (1 = pode). O MESMO quadro está em PermissoesTest.kt (app).
const MATRIZ: Record<string, string> = {
  VER: "1111",
  LANCAMENTO_CRIAR: "0111",
  LANCAMENTO_EDITAR: "0111",
  LANCAMENTO_EXCLUIR: "0111",
  ANEXO_VER: "1111",
  ANEXO_ADICIONAR: "0111",
  ANEXO_EXCLUIR: "0011",
  ROTINA_EDITAR: "0111",
  ESTRUTURA_EDITAR: "0011",
  LIXEIRA_RESTAURAR: "0011",
  LIXEIRA_ESVAZIAR: "0011",
  MEMBROS_VER: "1111",
  MEMBROS_GERENCIAR: "0011",
  CONVITE_GERAR: "0011",
  ADMIN_CONCEDER: "0001",
  CONTA_SAIR: "1110",
  PROPRIEDADE_TRANSFERIR: "0000",
};

describe("R67 permissões", () => {
  it("matriz completa papel x ação", () => {
    expect(new Set(Object.keys(MATRIZ))).toEqual(new Set(TODAS_ACOES));
    const papeis: Papel[] = ["LEITOR", "EDITOR", "ADMIN", "DONO"];
    for (const acao of TODAS_ACOES) {
      papeis.forEach((p, i) => expect(podeExecutar(p, acao), `${p} x ${acao}`).toBe(MATRIZ[acao][i] === "1"));
    }
  });

  it("sem papel nega tudo", () => {
    for (const a of TODAS_ACOES) {
      expect(podeExecutar(null, a)).toBe(false);
      expect(podeExecutar(undefined, a)).toBe(false);
    }
  });

  it("papel do documento: ausente = EDITOR, desconhecido = LEITOR, DONO nunca vem de documento", () => {
    expect(papelDoDocumento(undefined)).toBe("EDITOR");
    expect(papelDoDocumento(" ")).toBe("EDITOR");
    expect(papelDoDocumento("admin")).toBe("ADMIN");
    expect(papelDoDocumento("SUPERUSUARIO")).toBe("LEITOR");
    expect(papelDoDocumento("DONO")).toBe("LEITOR");
    expect(papelDoDocumento(42)).toBe("EDITOR");
  });

  it("papéis concedíveis: ADMIN não cria outro ADMIN", () => {
    expect(papeisConcediveis("DONO")).toEqual(["LEITOR", "EDITOR", "ADMIN"]);
    expect(papeisConcediveis("ADMIN")).toEqual(["LEITOR", "EDITOR"]);
    expect(papeisConcediveis("EDITOR")).toEqual([]);
    expect(papeisConcediveis(null)).toEqual([]);
  });

  it("gerenciar membro: só o dono mexe em ADMIN e ninguém mexe no dono", () => {
    expect(podeGerenciarMembro("DONO", "ADMIN")).toBe(true);
    expect(podeGerenciarMembro("DONO", "DONO")).toBe(false);
    expect(podeGerenciarMembro("ADMIN", "EDITOR")).toBe(true);
    expect(podeGerenciarMembro("ADMIN", "ADMIN")).toBe(false);
    expect(podeGerenciarMembro("EDITOR", "LEITOR")).toBe(false);
    expect(podeGerenciarMembro(null, "LEITOR")).toBe(false);
  });
});
