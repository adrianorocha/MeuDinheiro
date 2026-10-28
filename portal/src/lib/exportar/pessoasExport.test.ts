import { describe, expect, it } from "vitest";
import { dt } from "../finance/fixtures";
import { montarRelatorioPessoas, type OpcoesRelatorioPessoas } from "../finance/relatorioPessoas";
import type { Acerto, Pessoa } from "../finance/types";
import { desenharIcone, type NomeIcone } from "./pessoasIcones";
import { MAX_ALTURA, planejarImagens, type Medidor } from "./pessoasImagem";
import { gerarPdfPessoas, textoPdf } from "./pessoasPdf";

// jsdom/canvas não existem neste ambiente (vitest em node): PNG/JPEG são cobertos pelo plano de layout (puro) com um medidor
// falso; o PDF (jsPDF) roda em node de verdade.
const medidor: Medidor = {
  font: "",
  measureText(t: string) {
    const tam = Number(/(\d+)px/.exec(this.font)?.[1] ?? 16);
    return { width: t.length * tam * 0.5 } as TextMetrics;
  },
};

const agora = dt(2026, 9, 30, 15);
const pessoas: Pessoa[] = Array.from({ length: 12 }, (_, i) => ({ id: i + 1, nome: `Pessoa ${i + 1} com um nome razoavelmente comprido`, contato: i % 2 ? "11 99999-0000" : null }));
function acertos(n: number): Acerto[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    pessoaId: (i % pessoas.length) + 1,
    tipo: i % 3 === 0 ? "A_PAGAR" : "A_RECEBER",
    valor: 10 + (i % 17) * 1.37,
    descricao: `Acerto número ${i + 1} com descrição longa para forçar a quebra de linha na imagem e no PDF do relatório`,
    data: dt(2026, 1 + (i % 9), 1 + (i % 27)),
    despesaId: null,
    quitado: i % 4 === 0,
    quitadoEm: i % 4 === 0 ? dt(2026, 9, 2) : null,
    grupoRateio: null,
  }));
}
const base: OpcoesRelatorioPessoas = { pessoaIds: [], periodo: null, situacao: "TODOS", incluirDetalhes: true, agrupar: "POR_PESSOA", agora };
const rel = (n: number, o: Partial<OpcoesRelatorioPessoas> = {}) => montarRelatorioPessoas(pessoas, acertos(n), [], { ...base, ...o });

describe("R69 - plano de imagens (altura dinâmica e divisão em partes)", () => {
  it("poucos acertos: uma só imagem, com altura = cabeçalho + blocos + rodapé", () => {
    const plano = planejarImagens(rel(6), medidor);
    expect(plano.paginas).toHaveLength(1);
    const p = plano.paginas[0];
    expect(p.altura).toBe(p.hCabecalho + p.blocos.reduce((s, b) => s + b.h, 0) + 64);
    expect(p.altura).toBeLessThan(MAX_ALTURA);
    expect(p.indice).toBe(1);
    expect(p.total).toBe(1);
  });

  it("lista longa: partes de no máximo 6000 px, nenhum acerto perdido ou repetido, cabeçalho de pessoa nunca isolado", () => {
    const r = rel(400);
    const plano = planejarImagens(r, medidor);
    expect(plano.paginas.length).toBeGreaterThan(2);
    for (const p of plano.paginas) {
      expect(p.altura).toBeLessThanOrEqual(MAX_ALTURA);
      expect(p.total).toBe(plano.paginas.length);
      const ult = p.blocos[p.blocos.length - 1];
      expect(ult.tipo === "linha" || ult.tipo === "rateio" || ult.tipo === "rateioTotais").toBe(true);
      // parte que começa no meio da lista de uma pessoa repete o nome dela
      if (p.indice > 1 && p.blocos[0].tipo !== "pessoa" && p.blocos[0].tipo !== "titulo") expect(p.blocos[0].tipo).toBe("cont");
    }
    const ids = plano.paginas.flatMap((p) => p.blocos.flatMap((b) => (b.tipo === "linha" ? [b.linha.acertoId] : [])));
    expect(ids).toHaveLength(400);
    expect(new Set(ids).size).toBe(400);
    expect(plano.paginas.flatMap((p) => p.blocos).filter((b) => b.tipo === "pessoa")).toHaveLength(r.pessoas.length);
  });

  it("textos longos são quebrados em mais linhas (altura da linha cresce), nunca cortados", () => {
    const curto = planejarImagens(rel(1), medidor).paginas[0].blocos.find((b) => b.tipo === "linha");
    const r = montarRelatorioPessoas([{ id: 1, nome: "A" }], [{ ...acertos(1)[0], pessoaId: 1, descricao: "palavra ".repeat(80) }], [], base);
    const longo = planejarImagens(r, medidor).paginas[0].blocos.find((b) => b.tipo === "linha");
    expect(curto?.tipo === "linha" && longo?.tipo === "linha" && longo.desc.length > 1 && longo.h > curto.h).toBe(true);
    if (longo?.tipo === "linha") expect(longo.desc.join(" ")).toBe("palavra ".repeat(80).trim());
  });

  it("modo cronológico inclui saldo por pessoa + linhas por data; rateios entram como bloco opcional", () => {
    const plano = planejarImagens(rel(30, { agrupar: "CRONOLOGICO", incluirRateios: true }), medidor);
    const blocos = plano.paginas.flatMap((p) => p.blocos);
    expect(blocos.filter((b) => b.tipo === "titulo").map((b) => b.tipo === "titulo" && b.texto).slice(0, 2)).toEqual(["Saldo por pessoa", "Acertos em ordem cronológica"]);
    const datas = blocos.flatMap((b) => (b.tipo === "linha" ? [b.linha.data] : []));
    expect(datas).toEqual([...datas].sort((a, b) => a - b));
  });
});

describe("R69 - PDF e ícones", () => {
  it("gera um PDF A4 não vazio (Blob application/pdf), multipágina e sem erro com todos os modos", async () => {
    const um = await gerarPdfPessoas(rel(6));
    expect(um.type).toBe("application/pdf");
    expect(um.size).toBeGreaterThan(2000);
    const longo = await gerarPdfPessoas(rel(120, { incluirRateios: true }));
    expect(longo.size).toBeGreaterThan(um.size);
    const buf = Buffer.from(await longo.arrayBuffer()).toString("latin1");
    expect(buf.startsWith("%PDF-")).toBe(true);
    expect((buf.match(/\/Type\s*\/Page[^s]/g) ?? []).length).toBeGreaterThan(2);
    const cron = await gerarPdfPessoas(rel(40, { agrupar: "CRONOLOGICO" }));
    const semDet = await gerarPdfPessoas(rel(40, { incluirDetalhes: false }));
    expect(cron.size).toBeGreaterThan(2000);
    expect(semDet.size).toBeGreaterThan(2000);
    expect(semDet.size).toBeLessThan(cron.size);
  });

  it("textoPdf troca caracteres fora do WinAnsi por equivalentes seguros", () => {
    expect(textoPdf("a — b − c → d … e")).toBe("a - b - c -> d ... e");
    expect(textoPdf("você · cota")).toBe("você · cota");
  });

  it("cada ícone desenha ao menos um traço", () => {
    for (const n of ["entrada", "saida", "relogio", "check", "recibo", "carteira", "balanca"] as NomeIcone[]) {
      let tracos = 0;
      desenharIcone({ segmento: () => tracos++, circulo: () => tracos++ }, n, 0, 0, 24);
      expect(tracos).toBeGreaterThan(0);
    }
  });
});
