import { describe, expect, it } from "vitest";
import { exportarBackup, importarBackup } from "../store/backup";
import { autoresDistintos, estamparNovos, filtrarQuemLancou, nomeCurto, rotuloAutor } from "./autoria";
import { conta, dataset, desp, dt } from "./fixtures";
import { adicionarLancamento, duplicarLancamento, repetirLancamento } from "./operations";

const ana = { uid: "uid-ana", nome: "ana@x.com" };
const ctx = { agora: dt(2025, 3, 15) };

describe("R67 autoria", () => {
  const base = () => dataset({ contas: [conta()] });

  it("preencher: carimba só o que está vazio e mantém Importação", () => {
    const prev = base();
    const r = adicionarLancamento(prev, { descricao: "x", valor: 10, data: ctx.agora, categoria: "A", conta: "111", tipo: "DEBITO", pic: "" }, ctx);
    if (!r.ok) throw new Error(r.erro);
    const novo = estamparNovos(prev, r.ds, ana, "preencher").despesas[0];
    expect(novo.autorUid).toBe("uid-ana");
    expect(novo.autor).toBe("ana@x.com");
    const imp = { ...r.ds, despesas: [{ ...r.ds.despesas[0], autor: "Importação" }] };
    const c = estamparNovos(prev, imp, ana, "preencher").despesas[0];
    expect(c.autor).toBe("Importação");
    expect(c.autorUid).toBe("uid-ana");
    // modo local: nada muda
    expect(estamparNovos(prev, r.ds, null, "preencher")).toBe(r.ds);
  });

  it("parcelar: todas as parcelas ficam com o autor de quem executa", () => {
    const prev = base();
    const r = adicionarLancamento(prev, { descricao: "TV", valor: 90, data: ctx.agora, categoria: "A", conta: "111", tipo: "DEBITO", pic: "", parcelas: 3, autor: ana.nome }, ctx);
    if (!r.ok) throw new Error(r.erro);
    const ds = estamparNovos(prev, r.ds, ana, "preencher");
    expect(ds.despesas).toHaveLength(3);
    expect(new Set(ds.despesas.map((d) => d.autorUid))).toEqual(new Set(["uid-ana"]));
  });

  it("duplicar e repetir: o autor é quem executa, não o do original", () => {
    const original = desp({ id: 5, autor: "bia@x.com", autorUid: "uid-bia", natureza: "NORMAL", conta: "111" });
    const prev = dataset({ contas: [conta()], despesas: [original] });
    const d = duplicarLancamento(prev, 5, ctx);
    if (!d.ok) throw new Error(d.erro);
    const dup = estamparNovos(prev, d.ds, ana, "executor").despesas.find((x) => x.id !== 5)!;
    expect(dup.autorUid).toBe("uid-ana");
    expect(dup.autor).toBe("ana@x.com");
    const rep = repetirLancamento(prev, 5, { n: 2, intervalo: 1, unidade: "MESES" }, ctx);
    if (!rep.ok) throw new Error(rep.erro);
    const novos = estamparNovos(prev, rep.ds, ana, "executor").despesas.filter((x) => x.id !== 5);
    expect(novos).toHaveLength(2);
    expect(new Set(novos.map((x) => x.autorUid))).toEqual(new Set(["uid-ana"]));
    // original intacto
    expect(estamparNovos(prev, rep.ds, ana, "executor").despesas.find((x) => x.id === 5)!.autorUid).toBe("uid-bia");
    // modo local: executor limpa o autor copiado
    const local = estamparNovos(prev, d.ds, null, "executor").despesas.find((x) => x.id !== 5)!;
    expect(local.autorUid).toBeUndefined();
    expect(local.autor).toBeNull();
  });

  it("filtro quem lançou: conjunta, minhas e membro", () => {
    const l = [
      desp({ id: 1, autorUid: "a", autor: "ana@x.com" }),
      desp({ id: 2, autorUid: "b", autor: "bia@x.com" }),
      desp({ id: 3 }),
    ];
    expect(filtrarQuemLancou(l, { tipo: "TODOS" }, "a")).toHaveLength(3);
    expect(filtrarQuemLancou(l, { tipo: "EU" }, "a").map((d) => d.id)).toEqual([1]);
    expect(filtrarQuemLancou(l, { tipo: "MEMBRO", uid: "b" }, "a").map((d) => d.id)).toEqual([2]);
    expect(filtrarQuemLancou(l, { tipo: "EU" }, null)).toEqual([]);
    expect(autoresDistintos(l, "a")).toEqual([{ uid: "b", nome: "bia@x.com" }]);
  });

  it("rótulo por <nome> só com membros", () => {
    expect(rotuloAutor("ana@x.com", false)).toBeNull();
    expect(rotuloAutor("ana@x.com", true)).toBe("por ana");
    expect(rotuloAutor("Ana", true)).toBe("por Ana");
    expect(rotuloAutor(null, true)).toBeNull();
    expect(nomeCurto("bia@x.com")).toBe("bia");
  });

  it("backup antigo sem autorUid restaura sem o campo; ida e volta preserva", () => {
    const antigo = importarBackup(JSON.stringify({ despesas: [{ id: 1, descricao: "x", valor: 5, data: dt(2025, 3, 1), categoria: "A", conta: "111", pic: "p", tipo: "DEBITO", autor: "ana@x.com" }], contas: [conta()] }));
    if (!antigo.ok) throw new Error(antigo.erro);
    expect("autorUid" in antigo.dataset.despesas[0]).toBe(false);
    const ds = dataset({ contas: [conta()], despesas: [desp({ id: 9, autor: "Ana", autorUid: "uid-ana" })] });
    const volta = importarBackup(JSON.stringify(exportarBackup(ds)));
    if (!volta.ok) throw new Error(volta.erro);
    expect(volta.dataset.despesas[0].autorUid).toBe("uid-ana");
    const vazio = importarBackup(JSON.stringify({ despesas: [{ id: 1, descricao: "x", valor: 5, data: dt(2025, 3, 1), categoria: "A", conta: "111", pic: "p", tipo: "DEBITO", autorUid: "  " }], contas: [conta()] }));
    if (!vazio.ok) throw new Error(vazio.erro);
    expect("autorUid" in vazio.dataset.despesas[0]).toBe(false);
  });
});
