import { describe, expect, it } from "vitest";
import { dt } from "./fixtures";
import { dataParaMs, interpretarLancamento, numeroEmCentavos, resumoInterpretacao, type ContextoTexto } from "./lancamentoTexto";
import type { RegraCategoria } from "./types";

// Mesmos casos de app/src/test/java/com/meudinheiro/domain/LancamentoTextoTest.kt.
const regra = (over: Partial<RegraCategoria>): RegraCategoria => ({
  id: 1, padrao: "ifood", tipoMatch: "CONTEM", categoria: "Delivery", conta: null, tipo: null, ativa: true, prioridade: 0, criadaEm: 1, ...over,
});

// 15/03/2026 é domingo
const AGORA = dt(2026, 3, 15);
const ctx = (agora = AGORA, over: Partial<ContextoTexto> = {}): ContextoTexto => ({
  contas: [
    { id: "111", nome: "Nubank" },
    { id: "222", nome: "Itaú" },
    { id: "333", nome: "Banco do Brasil" },
  ],
  cartoes: [
    { id: 10, nome: "Itaú Click" },
    { id: 11, nome: "Inter Black" },
  ],
  categorias: ["Alimentação", "Supermercado", "Transporte", "Saúde", "Salário", "Lazer", "Combustível", "Educação", "Compras", "Casa"],
  regras: [regra({})],
  agora,
  ...over,
});

interface Esperado {
  tipo?: "DEBITO" | "CREDITO";
  valor?: number | null;
  desc?: string;
  data?: string;
  destino?: string | null;
  parcelas?: number | null;
  categoria?: string | null;
  candidatos?: number;
  /** trechos que devem aparecer nas pendências (lista vazia = sem pendências) */
  pend?: string[];
}

const iso = (i: { data: { ano: number; mes: number; dia: number } }) => `${i.data.ano}-${String(i.data.mes).padStart(2, "0")}-${String(i.data.dia).padStart(2, "0")}`;

const FRASES: [string, Esperado][] = [
  ["gastei 45 no mercado ontem no Nubank", { tipo: "DEBITO", valor: 4500, desc: "Mercado", data: "2026-03-14", destino: "CONTA:111", categoria: "Supermercado", pend: [] }],
  ["paguei 120,50 de luz dia 10", { tipo: "DEBITO", valor: 12050, desc: "Luz", data: "2026-03-10", categoria: "Casa", pend: [] }],
  ["recebi 3.500 de salário hoje", { tipo: "CREDITO", valor: 350000, desc: "Salário", data: "2026-03-15", categoria: "Salário", pend: [] }],
  ["almoço 32 reais", { tipo: "DEBITO", valor: 3200, desc: "Almoço", data: "2026-03-15", categoria: "Alimentação", pend: [] }],
  ["uber 18,90 anteontem no cartão itaú", { tipo: "DEBITO", valor: 1890, desc: "Uber", data: "2026-03-13", destino: "CARTAO:10", categoria: "Transporte", pend: [] }],
  ["parcelei 600 em 3x no cartão", { tipo: "DEBITO", valor: 60000, parcelas: 3, destino: null, candidatos: 2, desc: "", pend: ["Qual cartão", "Não entendi o que foi"] }],
  ["comprei um tênis por R$ 1.299,90 em 10x no cartão inter", { valor: 129990, parcelas: 10, destino: "CARTAO:11", desc: "tênis", categoria: "Compras", pend: [] }],
  ["ganhei 2 mil de freela ontem", { tipo: "CREDITO", valor: 200000, desc: "Freela", data: "2026-03-14", categoria: null, pend: [] }],
  ["recebi 1,5 mil de bônus", { tipo: "CREDITO", valor: 150000, desc: "Bônus", pend: [] }],
  ["gasolina 200 conto", { valor: 20000, desc: "Gasolina", categoria: "Combustível", pend: [] }],
  ["paguei 89.90 de internet dia 5", { valor: 8990, desc: "Internet", data: "2026-03-05", categoria: "Casa", pend: [] }],
  ["farmácia 35,50 dia 31", { valor: 3550, desc: "Farmácia", data: "2026-01-31", categoria: "Saúde", pend: [] }],
  ["paguei 300 de aluguel 05/03", { valor: 30000, desc: "Aluguel", data: "2026-03-05", categoria: "Casa", pend: [] }],
  ["gastei 80 no cinema 20/12", { valor: 8000, desc: "cinema", data: "2025-12-20", categoria: "Cinema", pend: [] }],
  ["gastei 50 em 25/12/2025 no ifood", { valor: 5000, desc: "ifood", data: "2025-12-25", categoria: "Delivery", pend: [] }],
  ["pizza 70 sexta", { valor: 7000, desc: "Pizza", data: "2026-03-13", categoria: "Alimentação", pend: [] }],
  ["mercado 150 segunda passada", { valor: 15000, desc: "Mercado", data: "2026-03-09", categoria: "Supermercado", pend: [] }],
  ["gastei 20 na segunda-feira", { valor: 2000, data: "2026-03-09", desc: "", pend: ["Não entendi o que foi"] }],
  ["gastei 1.234,56 no mercado", { valor: 123456, desc: "mercado", pend: [] }],
  ["gastei R$45 no mercado", { valor: 4500, desc: "mercado", pend: [] }],
  ["gastei 10 reais no mercado e 20 reais na padaria", { valor: 1000, desc: "mercado e 20 reais na padaria", pend: ["mais de um valor"] }],
  ["gastei no mercado", { valor: null, desc: "mercado", pend: ["Não entendi o valor"] }],
  ["gastei 45 no itaú", { valor: 4500, destino: null, candidatos: 2, pend: ["Qual conta ou cartão"] }],
  ["gastei 45 no débito do itaú", { valor: 4500, destino: "CONTA:222", candidatos: 0, desc: "", pend: ["Não entendi o que foi"] }],
  ["recebi 500 e paguei 200 de luz", { tipo: "CREDITO", valor: 50000, pend: ["despesa ou receita", "mais de um valor"] }],
  ["depósito 200 hoje", { tipo: "CREDITO", valor: 20000, desc: "Depósito", pend: [] }],
  ["gastei 12,5 no café", { valor: 1250, desc: "café", categoria: "Alimentação", pend: [] }],
  ["geladeira 3x de 200 no cartão inter", { valor: 60000, parcelas: 3, destino: "CARTAO:11", desc: "Geladeira", pend: [] }],
  ["gastei 45 no mercado dia 31/02", { valor: 4500, data: "2026-03-15", pend: ["Data inválida (31/02)"] }],
  ["paguei 50 de uber 15/03/26", { valor: 5000, data: "2026-03-15", desc: "Uber", pend: [] }],
  ["paguei mil reais de aluguel", { valor: 100000, desc: "aluguel", pend: [] }],
  ["gastei 2 mil no mercado", { valor: 200000, desc: "mercado", pend: [] }],
  ["gastei 100 no cartão nubank", { valor: 10000, destino: null, candidatos: 2, pend: ["Não achei um cartão"] }],
  ["gastei 45,00.", { valor: 4500, pend: ["Não entendi o que foi"] }],
  ["xyz", { valor: null, pend: ["Não entendi o valor"] }],
];

describe("R62 interpretarLancamento - frases", () => {
  it("tem pelo menos 25 frases", () => expect(FRASES.length).toBeGreaterThanOrEqual(25));
  for (const [frase, e] of FRASES) {
    it(`"${frase}"`, () => {
      const r = interpretarLancamento(frase, ctx());
      if (e.tipo) expect(r.tipo).toBe(e.tipo);
      if (e.valor !== undefined) expect(r.valorCentavos).toBe(e.valor);
      if (e.desc !== undefined) expect(r.descricao.toLowerCase()).toBe(e.desc.toLowerCase());
      if (e.data) expect(iso(r)).toBe(e.data);
      if (e.destino !== undefined) expect(r.destino ? `${r.destino.tipo}:${r.destino.id}` : null).toBe(e.destino);
      if (e.parcelas !== undefined) expect(r.parcelas).toBe(e.parcelas);
      if (e.categoria !== undefined) expect(r.categoria).toBe(e.categoria);
      if (e.candidatos !== undefined) expect(r.candidatos.length).toBe(e.candidatos);
      if (e.pend) {
        if (e.pend.length === 0) expect(r.pendencias).toEqual([]);
        for (const p of e.pend) expect(r.pendencias.some((x) => x.includes(p))).toBe(true);
      }
    });
  }
});

describe("R62 confiança e ambiguidade", () => {
  it("campos explícitos têm confiança alta; assumidos, baixa", () => {
    const r = interpretarLancamento("gastei 45 no mercado ontem no Nubank", ctx());
    expect(r.confianca).toMatchObject({ tipo: "ALTA", valor: "ALTA", data: "ALTA", descricao: "ALTA", destino: "ALTA", categoria: "MEDIA" });
    const s = interpretarLancamento("almoço 32 reais", ctx());
    expect(s.confianca.data).toBe("BAIXA");
    expect(s.confianca.tipo).toBe("BAIXA");
    expect(s.confianca.destino).toBe("BAIXA");
  });
  it("regra R55 vence o mapa de palavras (confiança alta)", () => {
    const r = interpretarLancamento("ifood 50", ctx(AGORA, { regras: [regra({ categoria: "Restaurantes" })] }));
    expect(r.categoria).toBe("Restaurantes");
    expect(r.confianca.categoria).toBe("ALTA");
  });
  it("regra restrita a conta só vale com aquela conta", () => {
    const regras = [regra({ conta: "222", categoria: "Trabalho" })];
    expect(interpretarLancamento("ifood 50 no itaú débito", ctx(AGORA, { regras })).categoria).toBe("Trabalho");
    expect(interpretarLancamento("ifood 50 no nubank", ctx(AGORA, { regras })).categoria).toBe("Alimentação");
  });
  it("mesmo nome em conta e cartão: ambíguo sem dica; com 'cartão' ou 'parcelei' escolhe o cartão", () => {
    const c = ctx(AGORA, { contas: [{ id: "111", nome: "Nubank" }], cartoes: [{ id: 5, nome: "Nubank" }] });
    const a = interpretarLancamento("gastei 30 no nubank", c);
    expect(a.destino).toBeNull();
    expect(a.candidatos.map((x) => x.tipo).sort()).toEqual(["CARTAO", "CONTA"]);
    expect(interpretarLancamento("gastei 30 no cartão nubank", c).destino).toEqual({ tipo: "CARTAO", id: "5", nome: "Nubank" });
    expect(interpretarLancamento("parcelei 90 em 3x no nubank", c).destino?.tipo).toBe("CARTAO");
    expect(interpretarLancamento("gastei 30 na conta nubank", c).destino?.tipo).toBe("CONTA");
  });
  it("só um cartão: 'no cartão' sem nome escolhe esse cartão (confiança média)", () => {
    const c = ctx(AGORA, { cartoes: [{ id: 5, nome: "Visa" }] });
    const r = interpretarLancamento("parcelei 600 em 3x no cartão", c);
    expect(r.destino?.id).toBe("5");
    expect(r.confianca.destino).toBe("MEDIA");
  });
  it("nome aproximado: sem acento, caixa, 'MercadoPago' em duas palavras", () => {
    const c = ctx(AGORA, { contas: [{ id: "9", nome: "MercadoPago" }, { id: "8", nome: "Banco do Brasil" }] });
    expect(interpretarLancamento("gastei 10 no mercado pago", c).destino?.id).toBe("9");
    expect(interpretarLancamento("gastei 10 no BRASIL", c).destino?.id).toBe("8");
    expect(interpretarLancamento("gastei 10 no ITAU", ctx()).candidatos.length).toBe(2);
  });
  it("palavra parecida com o nome (internet x Inter) não vira cartão", () => {
    expect(interpretarLancamento("paguei 100 de internet", ctx()).destino).toBeNull();
  });
  it("resumo para a prévia", () => {
    expect(resumoInterpretacao(interpretarLancamento("uber 18,90 anteontem no cartão itaú", ctx()))).toEqual([
      "Despesa", "R$ 18,90", "Uber", "13/03/2026", "Itaú Click (cartão)", "Transporte",
    ]);
    expect(dataParaMs({ ano: 2026, mes: 3, dia: 13 })).toBe(dt(2026, 3, 13));
  });
});

describe("R62 valores", () => {
  it("vírgula, ponto, milhar", () => {
    expect(numeroEmCentavos("45")).toBe(4500);
    expect(numeroEmCentavos("120,50")).toBe(12050);
    expect(numeroEmCentavos("18.90")).toBe(1890);
    expect(numeroEmCentavos("3.500")).toBe(350000);
    expect(numeroEmCentavos("1.234,56")).toBe(123456);
    expect(numeroEmCentavos("1.234.567")).toBe(123456700);
    expect(numeroEmCentavos("1,5")).toBe(150);
    expect(numeroEmCentavos("1.5")).toBe(150);
    expect(numeroEmCentavos("0,05")).toBe(5);
    expect(numeroEmCentavos("1,234")).toBeNull();
    expect(numeroEmCentavos("abc")).toBeNull();
  });
});

describe("R62 datas relativas com agora injetado", () => {
  const data = (frase: string, agora: number) => iso(interpretarLancamento(frase, ctx(agora)));
  it("ontem/anteontem em meses de 28, 29, 30 e 31 dias", () => {
    expect(data("ontem 10 reais", dt(2026, 3, 1))).toBe("2026-02-28");
    expect(data("ontem 10 reais", dt(2024, 3, 1))).toBe("2024-02-29");
    expect(data("ontem 10 reais", dt(2026, 5, 1))).toBe("2026-04-30");
    expect(data("ontem 10 reais", dt(2026, 8, 1))).toBe("2026-07-31");
    expect(data("anteontem 10 reais", dt(2026, 3, 1))).toBe("2026-02-27");
    expect(data("anteontem 10 reais", dt(2024, 3, 1))).toBe("2024-02-28");
    expect(data("antes de ontem 10 reais", dt(2026, 3, 1))).toBe("2026-02-27");
  });
  it("virada de ano", () => {
    expect(data("ontem 10 reais", dt(2026, 1, 1))).toBe("2025-12-31");
    expect(data("anteontem 10 reais", dt(2026, 1, 1))).toBe("2025-12-30");
    expect(data("dia 20 10 reais", dt(2026, 1, 5))).toBe("2025-12-20");
    expect(data("28/12 10 reais", dt(2026, 1, 1))).toBe("2025-12-28");
    expect(interpretarLancamento("28/12 10 reais", ctx(dt(2026, 1, 1))).confianca.data).toBe("MEDIA");
    expect(data("01/01 10 reais", dt(2026, 1, 1))).toBe("2026-01-01");
  });
  it("'dia N' é o dia N mais recente que não está no futuro", () => {
    expect(data("dia 15 10 reais", dt(2026, 3, 15))).toBe("2026-03-15");
    expect(data("dia 16 10 reais", dt(2026, 3, 15))).toBe("2026-02-16");
    expect(data("dia 31 10 reais", dt(2026, 3, 1))).toBe("2026-01-31"); // fevereiro não tem dia 31
    expect(data("dia 30 10 reais", dt(2026, 3, 15))).toBe("2026-01-30");
    expect(data("dia 29 10 reais", dt(2026, 3, 10))).toBe("2026-01-29");
    expect(data("dia 29 10 reais", dt(2024, 3, 10))).toBe("2024-02-29"); // ano bissexto
    expect(data("dia 31 10 reais", dt(2026, 5, 20))).toBe("2026-03-31"); // abril tem 30
  });
  it("dia da semana: 'passada' é sempre antes de hoje; sem 'passada' vale hoje se for o dia", () => {
    const seg = dt(2026, 3, 16); // segunda
    expect(data("segunda passada 10 reais", seg)).toBe("2026-03-09");
    expect(data("segunda 10 reais", seg)).toBe("2026-03-16");
    expect(data("terça 10 reais", seg)).toBe("2026-03-10");
    expect(data("sábado passado 10 reais", seg)).toBe("2026-03-14");
    expect(data("domingo 10 reais", seg)).toBe("2026-03-15");
    expect(data("quarta 10 reais", dt(2026, 1, 1))).toBe("2025-12-31"); // 1/1/2026 é quinta
    expect(data("quinta passada 10 reais", dt(2026, 1, 1))).toBe("2025-12-25");
  });
  it("dd/mm/aaaa explícito e inválidos", () => {
    expect(data("10 reais 29/02/2024", AGORA)).toBe("2024-02-29");
    expect(interpretarLancamento("10 reais 29/02/2025", ctx()).pendencias.some((p) => p.includes("Data inválida"))).toBe(true);
    expect(interpretarLancamento("10 reais dia 40", ctx()).pendencias.some((p) => p.includes("inválido"))).toBe(true);
  });
});
