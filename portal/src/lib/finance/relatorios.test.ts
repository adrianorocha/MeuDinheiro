import { describe, expect, it } from "vitest";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import {
  csvDeDocumento,
  documentoDeResultado,
  documentoFatura,
  documentoPatrimonio,
  filtroPadrao,
  gerarRelatorio,
  MODELOS,
  periodoRapido,
} from "./relatorios";

const ds = dataset({
  contas: [conta(), conta({ id: 2, conta: "222", banco: "B2" })],
  cartoes: [cartao({ id: 10 }), cartao({ id: 11, nome: "Virtual", cartaoPrincipalId: 10 }), cartao({ id: 12, nome: "Outro" })],
  despesas: [
    desp({ id: 1, descricao: "Gasolina posto", categoria: "Combustível", valor: 200, cartaoId: 10, pago: false, data: dt(2025, 9, 5) }),
    desp({ id: 2, descricao: "Gasolina virtual", categoria: "Combustível", valor: 100, cartaoId: 11, pago: false, data: dt(2025, 9, 20) }),
    desp({ id: 3, descricao: "Gasolina outro cartão", categoria: "Combustível", valor: 50, cartaoId: 12, pago: false, data: dt(2025, 9, 21) }),
    desp({ id: 4, descricao: "Gasolina débito", categoria: "Combustível", valor: 80, data: dt(2025, 9, 10) }),
    desp({ id: 5, descricao: "Estorno", categoria: "Combustível", valor: 30, tipo: "CREDITO", cartaoId: 10, pago: false, data: dt(2025, 9, 22) }),
    desp({ id: 6, descricao: "Salário", categoria: "Salário", valor: 5000, tipo: "CREDITO", data: dt(2025, 9, 5) }),
    desp({ id: 7, descricao: "Gasolina agosto", categoria: "Combustível", valor: 120, cartaoId: 10, data: dt(2025, 8, 10) }),
    desp({ id: 8, descricao: "Transf", categoria: "Transferência", valor: 999, natureza: "TRANSFERENCIA", data: dt(2025, 9, 11) }),
    desp({ id: 9, descricao: "Remédio", categoria: "Saúde", valor: 60, conta: "222", data: dt(2025, 9, 12) }),
  ],
});
const SET = { inicio: dt(2025, 9, 1, 0), fim: dt(2025, 9, 30, 23) };

describe("R19 gerarRelatorio", () => {
  it("Combustível + cartão físico (inclui virtual) em setembro", () => {
    const r = gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), categorias: ["combustivel"], cartoes: [10] });
    expect(r.itens.map((d) => d.id).sort()).toEqual([1, 2, 5]);
    expect(r.total).toBe(270); // 200 + 100 - 30
    expect(r.quantidade).toBe(3);
    expect(r.media).toBe(90);
    expect(r.maior).toBe(200);
    expect(r.porCategoria).toEqual([{ nome: "Combustível", total: 270, percentual: 1 }]);
    expect(r.porMes).toEqual([{ mes: "2025-09", total: 270 }]);
  });
  it("escolher só o virtual filtra apenas ele", () => {
    const r = gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), cartoes: [11] });
    expect(r.itens.map((d) => d.id)).toEqual([2]);
  });
  it("tipo RECEITA ignora estorno de cartão; TODOS = receitas - despesas", () => {
    const rec = gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), tipo: "RECEITA" });
    expect(rec.total).toBe(5000);
    const todos = gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), tipo: "TODOS" });
    expect(todos.total).toBe(5000 - (200 + 100 + 50 + 80 + 60 - 30));
  });
  it("só NORMAL salvo incluirInternos; filtro de conta, situação e texto", () => {
    expect(gerarRelatorio(ds, filtroPadrao(SET.inicio, SET.fim)).itens.some((d) => d.id === 8)).toBe(false);
    expect(gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), incluirInternos: true, tipo: "TODOS" }).itens.some((d) => d.id === 8)).toBe(true);
    expect(gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), contas: ["222"] }).itens.map((d) => d.id)).toEqual([9]);
    expect(gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), pago: false }).itens).toHaveLength(4);
    expect(gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), texto: "REMEDIO" }).itens.map((d) => d.id)).toEqual([9]);
    expect(gerarRelatorio(ds, { ...filtroPadrao(SET.inicio, SET.fim), texto: "saude" }).itens.map((d) => d.id)).toEqual([9]);
  });
  it("variação com anterior > 0 e null sem anterior", () => {
    const f = { ...filtroPadrao(dt(2025, 9, 1, 0), dt(2025, 9, 30, 23)), categorias: ["Combustível"] };
    const r = gerarRelatorio(ds, f);
    expect(r.anterior.total).toBe(120);
    expect(r.variacaoPercentual).toBeCloseTo(((r.total - 120) / 120) * 100, 5);
    const vazio = gerarRelatorio(dataset(), f);
    expect(vazio.variacaoPercentual).toBeNull();
    expect(vazio.media).toBe(0);
    expect(vazio.quantidade).toBe(0);
  });
  it("itens em ordem de data desc", () => {
    const r = gerarRelatorio(ds, filtroPadrao(SET.inicio, SET.fim));
    const datas = r.itens.map((d) => d.data);
    expect(datas).toEqual([...datas].sort((a, b) => b - a));
  });
});

describe("R20 modelos e exportação", () => {
  it("períodos rápidos", () => {
    const agora = dt(2025, 1, 15);
    expect(new Date(periodoRapido("anterior", agora).inicio).getFullYear()).toBe(2024);
    const ano = periodoRapido("ano", agora);
    expect([new Date(ano.inicio).getMonth(), new Date(ano.fim).getMonth()]).toEqual([0, 11]);
  });
  it("anual IR usa Saúde e Educação do ano inteiro", () => {
    const f = MODELOS.anualIR(2025);
    expect(f.categorias).toEqual(["Saúde", "Educação"]);
    expect(gerarRelatorio(ds, f).itens.map((d) => d.id)).toEqual([9]);
  });
  it("extrato da conta inclui internos e ambos os tipos", () => {
    const r = gerarRelatorio(ds, MODELOS.extratoConta("111", SET.inicio, SET.fim));
    expect(r.itens.some((d) => d.natureza === "TRANSFERENCIA")).toBe(true);
  });
  it("documento + CSV: separador ;, BOM, vírgula decimal e escape", () => {
    const f = { ...filtroPadrao(SET.inicio, SET.fim), categorias: ["Combustível"], cartoes: [10] };
    const doc = documentoDeResultado("Combustível; cartão", f, gerarRelatorio(ds, f), ds);
    expect(doc.filtros.join("|")).toContain("Cartões: Cartão A");
    expect(doc.grafico?.dados[0].rotulo).toBe("Combustível");
    const csv = csvDeDocumento(doc);
    expect(csv.charCodeAt(0)).toBe(0xfeff);
    expect(csv.slice(1).split("\r\n")[0]).toBe("Data;Descrição;Categoria;Conta/Cartão;Situação;Valor");
    expect(csv).toContain(";-200,00");
    expect(csv).toContain(";30,00"); // estorno positivo
    const c2 = csvDeDocumento({ ...doc, linhas: [["01/01/2025", 'a;"b"', "x", "y", "z", 1234.5]] });
    expect(c2).toContain('"a;""b"""');
    expect(c2).toContain(";1234,50");
  });
  it("fatura do grupo e patrimônio", () => {
    const d = documentoFatura(ds, 11, 9, 2025);
    expect(d?.linhas.length).toBe(3); // itens 1, 2 (virtual) e 5; outro cartão e agosto ficam de fora
    expect(documentoFatura(ds, 999, 9, 2025)).toBeNull();
    const p = documentoPatrimonio({ patrimonio: [{ id: 1, dataMillis: 2, valorTotal: 20, mesReferencia: "B" }, { id: 2, dataMillis: 1, valorTotal: 10, mesReferencia: "A" }] });
    expect(p.linhas[0][0]).toBe("A");
    expect(p.totais.find((t) => t.rotulo === "Variação")?.valor).toContain("10,00");
    expect(documentoPatrimonio({ patrimonio: [] }).grafico).toBeNull();
  });
});
