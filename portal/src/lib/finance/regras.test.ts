import { describe, expect, it } from "vitest";
import { categoriaSugerida } from "../conciliacao/plano";
import { dataset, desp } from "./fixtures";
import {
  aplicarMudancas,
  aplicarRegras,
  desfazerMudancas,
  previaRegrasExistentes,
  sugerirRegraAprendida,
  validarRegra,
} from "./regras";
import type { RegraCategoria } from "./types";

function r(over: Partial<RegraCategoria> & Pick<RegraCategoria, "id" | "padrao" | "categoria">): RegraCategoria {
  return { tipoMatch: "CONTEM", conta: null, tipo: null, ativa: true, prioridade: 0, criadaEm: 1, ...over };
}

/** MESMOS cenários de app/src/test/.../RegrasCategoriaTest.kt */
const REGRAS: RegraCategoria[] = [
  r({ id: 1, padrao: "uber", categoria: "Transporte" }),
  r({ id: 2, padrao: "uber eats", categoria: "Restaurantes" }),
  r({ id: 3, padrao: "mercado", tipoMatch: "COMECA_COM", categoria: "Supermercado" }),
  r({ id: 4, padrao: "Padaria", tipoMatch: "IGUAL", categoria: "Padaria" }),
  r({ id: 5, padrao: "pix", categoria: "Transferências", prioridade: 5 }),
  r({ id: 6, padrao: "uber", categoria: "Lazer", prioridade: 10, ativa: false }),
  r({ id: 7, padrao: "salario", categoria: "Salário", tipo: "CREDITO" }),
  r({ id: 8, padrao: "academia", categoria: "Saúde", conta: "222" }),
];

const cat = (descricao: string, conta = "111", tipo: "DEBITO" | "CREDITO" = "DEBITO") =>
  aplicarRegras(REGRAS, { descricao, conta, tipo })?.categoria ?? null;

describe("R55 aplicarRegras", () => {
  it("contém, ignorando acento e caixa; regra inativa não conta", () => {
    expect(cat("Uber *Trip 123")).toBe("Transporte");
    expect(cat("UBER")).toBe("Transporte");
  });
  it("empate de prioridade: padrão mais longo (mais específico) vence", () => {
    expect(cat("UBER EATS pedido")).toBe("Restaurantes");
  });
  it("começa com / igual", () => {
    expect(cat("Mercado Central")).toBe("Supermercado");
    expect(cat("Super Mercado")).toBeNull();
    expect(cat("padaria")).toBe("Padaria");
    expect(cat("  PADARIA  ")).toBe("Padaria");
    expect(cat("Padaria do Zé")).toBeNull();
  });
  it("maior prioridade vence mesmo com padrão mais curto", () => {
    expect(cat("Pix uber eats")).toBe("Transferências");
  });
  it("restrição de tipo e acento na descrição", () => {
    expect(cat("SALÁRIO março", "111", "DEBITO")).toBeNull();
    expect(cat("SALÁRIO março", "111", "CREDITO")).toBe("Salário");
  });
  it("restrição de conta", () => {
    expect(cat("Academia Fit", "111")).toBeNull();
    expect(cat("Academia Fit", "222")).toBe("Saúde");
  });
  it("empate total: a regra de menor id vence; padrão vazio nunca casa", () => {
    const iguais = [r({ id: 10, padrao: "abc", categoria: "X" }), r({ id: 9, padrao: "abc", categoria: "Y" })];
    expect(aplicarRegras(iguais, { descricao: "abc" })?.categoria).toBe("Y");
    expect(aplicarRegras([r({ id: 1, padrao: "   ", categoria: "Z" })], { descricao: "qualquer" })).toBeNull();
    expect(aplicarRegras([], { descricao: "x" })).toBeNull();
  });
  it("validação", () => {
    expect(validarRegra({ padrao: " ", categoria: "A", tipoMatch: "CONTEM", prioridade: 0 })).toMatch(/texto/);
    expect(validarRegra({ padrao: "a", categoria: " ", tipoMatch: "CONTEM", prioridade: 0 })).toMatch(/categoria/);
    expect(validarRegra({ padrao: "a", categoria: "A", tipoMatch: "CONTEM", prioridade: 1.5 })).toMatch(/inteiro/);
    expect(validarRegra({ padrao: "a", categoria: "A", tipoMatch: "CONTEM", prioridade: -2 })).toBeNull();
  });
});

describe("R55 integração com a conciliação", () => {
  const historico = [desp({ id: 1, descricao: "Uber trip", categoria: "Lazer" }), desp({ id: 2, descricao: "Uber trip", categoria: "Lazer" })];
  it("a regra vence a sugestão heurística (R21)", () => {
    expect(categoriaSugerida({ despesas: historico }, "UBER *TRIP")).toBe("Lazer"); // sem regras: heurística de sempre
    expect(categoriaSugerida({ despesas: historico, regrasCategoria: REGRAS }, "UBER *TRIP", { conta: "111", tipo: "DEBITO" })).toBe("Transporte");
  });
  it("sem regra que case, mantém a heurística/Outros", () => {
    expect(categoriaSugerida({ despesas: [], regrasCategoria: REGRAS }, "Loja Desconhecida")).toBe("Outros");
  });
});

describe("R55 aplicar às existentes (prévia, aplicar, desfazer)", () => {
  const ds = dataset({
    despesas: [
      desp({ id: 1, descricao: "Uber trip", categoria: "Outros" }),
      desp({ id: 2, descricao: "Uber trip", categoria: "transporte" }), // já na categoria (sem acento/caixa): não muda
      desp({ id: 3, descricao: "Uber trip", categoria: "Outros", natureza: "TRANSFERENCIA" }), // só NORMAL
      desp({ id: 4, descricao: "Compra qualquer", categoria: "Outros" }),
      desp({ id: 5, descricao: "Salário", tipo: "CREDITO", categoria: "Outros" }),
    ],
  });

  it("prévia lista só o que mudaria e não altera nada", () => {
    const antes = JSON.stringify(ds.despesas);
    const p = previaRegrasExistentes(ds.despesas, REGRAS);
    expect(p.map((m) => [m.id, m.de, m.para])).toEqual([
      [1, "Outros", "Transporte"],
      [5, "Outros", "Salário"],
    ]);
    expect(JSON.stringify(ds.despesas)).toBe(antes);
  });

  it("aplica e desfaz restaurando as categorias anteriores", () => {
    const p = previaRegrasExistentes(ds.despesas, REGRAS);
    const ap = aplicarMudancas(ds.despesas, p);
    expect(ap.despesas.find((d) => d.id === 1)?.categoria).toBe("Transporte");
    expect(ap.despesas.find((d) => d.id === 3)?.categoria).toBe("Outros");
    expect(ap.desfazer).toEqual([
      { id: 1, categoria: "Outros" },
      { id: 5, categoria: "Outros" },
    ]);
    expect(desfazerMudancas(ap.despesas, ap.desfazer)).toEqual(ds.despesas);
  });
});

describe("R55 aprendizado leve", () => {
  const base = [
    desp({ id: 1, descricao: "Netflix", categoria: "Lazer" }),
    desp({ id: 2, descricao: "NETFLIX ", categoria: "Lazer" }),
    desp({ id: 3, descricao: "Cinema", categoria: "Lazer" }),
  ];
  it("sugere com 2+ lançamentos de mesma descrição normalizada e mesma categoria", () => {
    const s = sugerirRegraAprendida(base, [], base[1]);
    expect(s).toEqual({ padrao: "netflix", categoria: "Lazer", vezes: 2 });
  });
  it("1 só ocorrência não sugere; regra que já dá a categoria não sugere", () => {
    expect(sugerirRegraAprendida(base, [], base[2])).toBeNull();
    expect(sugerirRegraAprendida(base, [r({ id: 1, padrao: "netflix", categoria: "Lazer" })], base[1])).toBeNull();
  });
  it("ignora lançamentos que não são NORMAL", () => {
    const so = [base[0], desp({ id: 9, descricao: "Netflix", categoria: "Lazer", natureza: "AJUSTE" })];
    expect(sugerirRegraAprendida(so, [], so[0])).toBeNull();
  });
});
