import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { GRUPOS_PICS, NOME_PIC, PICS_CATEGORIA } from "./PicIcon";
import { ICONES_CATALOGO } from "./picsCatalogo";

const chaves = GRUPOS_PICS.flatMap((g) => g.itens.map((i) => i.chave));

describe("catálogo de ícones de categoria (R50)", () => {
  it("toda chave tem ícone mapeado e rótulo", () => {
    for (const g of GRUPOS_PICS) {
      expect(g.rotulo.length).toBeGreaterThan(0);
      for (const i of g.itens) {
        expect(ICONES_CATALOGO[i.chave], i.chave).toBeDefined();
        expect(i.rotulo.trim(), i.chave).not.toBe("");
        expect(NOME_PIC[i.chave]).toBe(i.rotulo);
      }
    }
  });

  it("não há chaves duplicadas, formato snake_case e nada fora dos grupos", () => {
    expect(new Set(chaves).size).toBe(chaves.length);
    expect(chaves.every((c) => /^[a-z][a-z0-9_]*$/.test(c))).toBe(true);
    expect(Object.keys(ICONES_CATALOGO).sort()).toEqual([...chaves].sort());
    expect(chaves.length).toBeGreaterThanOrEqual(60);
  });

  it("os 15 originais continuam e não colidem com o catálogo", () => {
    expect(PICS_CATEGORIA).toHaveLength(15);
    for (const p of PICS_CATEGORIA) {
      expect(chaves).not.toContain(p);
      expect(NOME_PIC[p]).toBeTruthy();
    }
  });

  it("paridade de chaves com o app (domain/IconesCategoria.kt)", () => {
    const kt = readFileSync(resolve(__dirname, "../../../../app/src/main/java/com/meudinheiro/domain/IconesCategoria.kt"), "utf8");
    const doApp = [...kt.matchAll(/IconeCategoria\("([a-z0-9_]+)",\s*"([^"]+)"/g)].map((m) => [m[1], m[2]]);
    expect(doApp.map((x) => x[0])).toEqual(chaves);
    expect(doApp.map((x) => x[1])).toEqual(GRUPOS_PICS.flatMap((g) => g.itens.map((i) => i.rotulo)));
  });
});
