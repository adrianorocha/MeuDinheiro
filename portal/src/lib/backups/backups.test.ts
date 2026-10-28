import { describe, expect, it } from "vitest";
import { criarDatasetDemo } from "../demo";
import { dt } from "../finance/fixtures";
import { emptyDataset, type Dataset, type Despesa } from "../finance/types";
import { criarHistoricoStore, criarTrilhaStore, type KV } from "./armazenamento";
import { campoCsv, csvsDoDataset, dataBr, montarCsv, zipDoDataset } from "./exportar";
import { compararContagens, contagens, limitarHistorico, precisaLembrete, type RegistroBackup } from "./historico";
import { aplicarLimite, entradasDoDiff, filtrarTrilha, LIMITE_TRILHA, type EntradaTrilha } from "./trilha";
import { crc32, criarZip, lerZip } from "./zip";

const agora = dt(2025, 6, 15);

function desp(id: number, p: Partial<Despesa> = {}): Despesa {
  return {
    id, descricao: `D${id}`, valor: 10, data: agora, categoria: "Cat", conta: "1", pic: "p", tipo: "DEBITO", mes: 6, ano: 2025, cartaoId: null,
    valorOriginal: 10, moedaOriginal: "BRL", cotacaoNaData: 1, pago: false, natureza: "NORMAL", grupoId: null, autor: null, fitid: null, conciliadoEm: null, ...p,
  };
}
const dsCom = (...d: Despesa[]): Dataset => ({ ...emptyDataset(), despesas: d });

function kvMemoria(): KV & { dados: Map<string, unknown> } {
  const dados = new Map<string, unknown>();
  return { dados, get: async (k) => dados.get(k), set: async (k, v) => void dados.set(k, structuredClone(v)) };
}

describe("ZIP sem compressão", () => {
  it("crc32 conhecido", () => {
    expect(crc32(new TextEncoder().encode("123456789"))).toBe(0xcbf43926);
  });
  it("gera ZIP legível pelo diretório central, com nomes UTF-8 e CRC corretos", () => {
    const enc = new TextEncoder();
    const bytes = criarZip([
      { nome: "a.csv", dados: enc.encode("olá;mundo\r\n") },
      { nome: "áçã.csv", dados: enc.encode("") },
    ]);
    const itens = lerZip(bytes);
    expect(itens.map((i) => i.nome)).toEqual(["a.csv", "áçã.csv"]);
    expect(new TextDecoder().decode(itens[0].dados)).toBe("olá;mundo\r\n");
    expect(itens[0].crc).toBe(crc32(enc.encode("olá;mundo\r\n")));
    expect(itens[1].tamanho).toBe(0);
    expect(() => lerZip(new Uint8Array(10))).toThrow();
  });
});

describe("CSV por coleção", () => {
  it("escapa ;, aspas e quebras de linha; vírgula decimal; booleano em português", () => {
    expect(campoCsv("a;b")).toBe('"a;b"');
    expect(campoCsv('diz "oi"')).toBe('"diz ""oi"""');
    expect(campoCsv("l1\nl2")).toBe('"l1\nl2"');
    expect(campoCsv("x\r\ny")).toBe('"x\r\ny"');
    expect(campoCsv(1234.5)).toBe("1234,50");
    expect(campoCsv(7)).toBe("7");
    expect(campoCsv(true)).toBe("sim");
    expect(campoCsv(null)).toBe("");
  });
  it("tem BOM, separador ; e data dd/mm/aaaa", () => {
    const csv = montarCsv(["data", "valor"], [[dataBr(new Date(2026, 2, 5, 12).getTime()), 10.5]]);
    expect(csv.startsWith("﻿data;valor\r\n")).toBe(true);
    expect(csv).toContain("05/03/2026;10,50\r\n");
  });
  it("ZIP do dataset: um CSV por coleção, todos com BOM", () => {
    const ds = criarDatasetDemo(agora);
    ds.despesas[0] = { ...ds.despesas[0], descricao: 'Linha1\nLinha2; "x"' };
    const itens = lerZip(zipDoDataset(ds));
    const nomes = itens.map((i) => i.nome);
    for (const n of ["despesas.csv", "contas.csv", "cartoes.csv", "categorias.csv", "orcamentos.csv", "metas.csv", "investimentos.csv", "recorrencias.csv", "dividas.csv", "pessoas.csv", "acertos.csv", "projetos.csv", "regras.csv"]) {
      expect(nomes).toContain(n);
    }
    const textos = Object.fromEntries(itens.map((i) => [i.nome, new TextDecoder().decode(i.dados)]));
    expect(itens.every((i) => i.dados[0] === 0xef && i.dados[1] === 0xbb && i.dados[2] === 0xbf)).toBe(true); // BOM UTF-8
    expect(textos["despesas.csv"]).toContain('"Linha1\nLinha2; ""x"""');
    expect(Object.keys(csvsDoDataset(ds))).toEqual(nomes);
  });
});

describe("contagens, comparação e lembrete", () => {
  it("compara atual x arquivo por coleção", () => {
    const a = contagens(dsCom(desp(1), desp(2)));
    const b = contagens(dsCom(desp(1)));
    const l = compararContagens(a, b).find((x) => x.chave === "lancamentos")!;
    expect([l.atual, l.arquivo, l.diferenca]).toEqual([2, 1, -1]);
    expect(compararContagens(a, b)).toHaveLength(14);
  });
  it("lembrete após 7 dias ou se nunca houve backup", () => {
    expect(precisaLembrete(null, agora)).toBe(true);
    expect(precisaLembrete(agora - 6 * 86_400_000, agora)).toBe(false);
    expect(precisaLembrete(agora - 8 * 86_400_000, agora)).toBe(true);
  });
  it("histórico limitado aos 7 mais recentes", () => {
    const lista = Array.from({ length: 9 }, (_, i) => ({ criadoEm: i }));
    const { manter, descartar } = limitarHistorico(lista);
    expect(manter.map((x) => x.criadoEm)).toEqual([8, 7, 6, 5, 4, 3, 2]);
    expect(descartar.map((x) => x.criadoEm)).toEqual([1, 0]);
  });
  it("histórico guardado no KV (últimos 7, excluir)", async () => {
    const h = criarHistoricoStore(kvMemoria(), () => "local");
    for (let i = 0; i < 9; i++) {
      const r: RegistroBackup = { id: `i${i}`, criadoEm: i, tipo: "MANUAL", contagens: {}, tamanho: 1, json: "{}" };
      expect(await h.adicionar(r)).toBe(true);
    }
    expect((await h.listar()).map((x) => x.id)).toEqual(["i8", "i7", "i6", "i5", "i4", "i3", "i2"]);
    await h.excluir("i8");
    expect((await h.listar())[0].id).toBe("i7");
  });
});

describe("trilha de alterações", () => {
  it("diff: criar, editar (campos antigo→novo), pagar, excluir", () => {
    const antes = dsCom(desp(1), desp(2), desp(3));
    const depois = dsCom(desp(1, { valor: 20, descricao: "Novo" }), desp(2, { pago: true }), desp(4));
    const e = entradasDoDiff(antes, depois, 5);
    const por = (a: string) => e.filter((x) => x.acao === a);
    expect(por("CRIAR").map((x) => x.id)).toEqual([4]);
    expect(por("EXCLUIR").map((x) => x.id)).toEqual([3]);
    expect(por("PAGAR").map((x) => x.id)).toEqual([2]);
    const ed = por("EDITAR")[0];
    expect(ed.alteracoes.find((m) => m.campo === "valor")).toEqual({ campo: "valor", antigo: "10", novo: "20" });
    expect(entradasDoDiff(antes, antes, 5)).toEqual([]);
  });
  it("restaurar da lixeira usa a dica", () => {
    const e = entradasDoDiff(dsCom(), dsCom(desp(9)), 5, "RESTAURAR_LIXEIRA");
    expect(e.map((x) => x.acao)).toEqual(["RESTAURAR_LIXEIRA"]);
  });
  it("operação em lote vira no máximo 51 entradas", () => {
    const e = entradasDoDiff(dsCom(), dsCom(...Array.from({ length: 120 }, (_, i) => desp(i + 1))), 5);
    expect(e).toHaveLength(51);
    expect(e[50].descricao).toContain("70");
  });
  it("limite de 2000 descarta as mais antigas", () => {
    const mk = (q: number): EntradaTrilha => ({ quando: q, acao: "CRIAR", id: q, descricao: "x", valor: 1, alteracoes: [] });
    const base = Array.from({ length: LIMITE_TRILHA }, (_, i) => mk(i + 1));
    const r = aplicarLimite(base, [mk(5001), mk(5002)]);
    expect(r).toHaveLength(LIMITE_TRILHA);
    expect(r[0].quando).toBe(3);
    expect(r[r.length - 1].quando).toBe(5002);
  });
  it("filtro por período, ação e busca", () => {
    const l = entradasDoDiff(dsCom(desp(1)), dsCom(desp(1, { descricao: "Pizza", valor: 12 }), desp(2, { descricao: "Luz" })), 100);
    expect(filtrarTrilha(l, { acao: "CRIAR" })).toHaveLength(1);
    expect(filtrarTrilha(l, { de: 101 })).toHaveLength(0);
    expect(filtrarTrilha(l, { busca: "pizz" })).toHaveLength(1);
    expect(filtrarTrilha(l, { busca: "valor" })).toHaveLength(1);
  });
  it("grava no KV com limite e lê mais recentes primeiro", async () => {
    const t = criarTrilhaStore(kvMemoria(), () => "local");
    t.registrarDiff(dsCom(), dsCom(desp(1)), 1);
    t.registrarDiff(dsCom(desp(1)), dsCom(desp(1), desp(2)), 2);
    await new Promise((r) => setTimeout(r, 10));
    expect((await t.ler()).map((x) => x.id)).toEqual([2, 1]);
  });
  it("falha do armazenamento nunca propaga", async () => {
    const quebrado: KV = { get: async () => { throw new Error("sem IndexedDB"); }, set: async () => { throw new Error("cheio"); } };
    const t = criarTrilhaStore(quebrado, () => "local");
    expect(() => t.registrarDiff(dsCom(), dsCom(desp(1)), 1)).not.toThrow();
    expect(await t.registrar([{ quando: 1, acao: "CRIAR", id: 1, descricao: "x", valor: 1, alteracoes: [] }])).toBe(false);
    expect(await t.ler()).toEqual([]);
    const h = criarHistoricoStore(quebrado, () => "local");
    expect(await h.adicionar({ id: "a", criadoEm: 1, tipo: "MANUAL", contagens: {}, tamanho: 1, json: "{}" })).toBe(false);
  });
});
