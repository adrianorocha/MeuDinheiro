import { describe, expect, it } from "vitest";
import { exportarBackup, importarBackup } from "../store/backup";
import { conta, dataset, desp, dt } from "./fixtures";
import {
  anexosDoPayload,
  caminhoAnexoValido,
  caminhoStorage,
  formatarTamanho,
  MAX_BYTES_ANEXO,
  nomeAnexoSeguro,
  normalizarAnexos,
  validarNovoAnexo,
  type Anexo,
} from "./anexos";
import { duplicarLancamento, editarLancamento, excluirLancamento, repetirLancamento, restaurarDaLixeira, type Ctx } from "./operations";

const ok: Anexo = { id: "a1", nome: "nota.jpg", tipo: "image/jpeg", tamanho: 1000, caminho: "usuarios/u1/anexos/55/a1.jpg", criadoEm: 1 };
const ctx = (agora: number): Ctx => ({ agora, uuid: () => "u1", rng: (() => { let s = 0.1; return () => (s = (s + 0.137) % 1); })() });

describe("R60 - validação (mesmos cenários de AnexosTest)", () => {
  it("aceita jpeg, png e pdf até 5 MB", () => {
    expect(validarNovoAnexo("image/jpeg", 1, 0)).toBeNull();
    expect(validarNovoAnexo("image/png", 1000, 4)).toBeNull();
    expect(validarNovoAnexo("application/pdf", MAX_BYTES_ANEXO, 0)).toBeNull();
  });
  it("recusa tipo fora da lista", () => {
    const m = "Tipo de arquivo não permitido. Use JPEG, PNG ou PDF.";
    expect(validarNovoAnexo("image/gif", 10, 0)).toBe(m);
    expect(validarNovoAnexo("", 10, 0)).toBe(m);
    expect(validarNovoAnexo("text/html", 10, 0)).toBe(m);
  });
  it("recusa vazio e acima de 5 MB", () => {
    expect(validarNovoAnexo("image/png", 0, 0)).toBe("O arquivo está vazio.");
    expect(validarNovoAnexo("application/pdf", MAX_BYTES_ANEXO + 100_000, 0)).toBe("Arquivo muito grande (5,1 MB). O limite é 5 MB.");
  });
  it("recusa o sexto anexo", () => {
    expect(validarNovoAnexo("image/png", 10, 5)).toBe("Limite de 5 anexos por lançamento.");
    expect(validarNovoAnexo("image/png", 10, 7)).toBe("Limite de 5 anexos por lançamento.");
  });
  it("formata tamanho", () => {
    expect(formatarTamanho(512)).toBe("512 B");
    expect(formatarTamanho(340 * 1024)).toBe("340 KB");
    expect(formatarTamanho(Math.round(1.2 * 1024 * 1024))).toBe("1,2 MB");
  });
});

describe("R60 - caminhos", () => {
  it("segue usuarios/<uid>/anexos/<despesaId>/<id>.<ext>", () => {
    expect(caminhoStorage("u1", 123, "abc", "image/jpeg")).toBe("usuarios/u1/anexos/123/abc.jpg");
    expect(caminhoStorage("u1", 123, "abc", "image/png")).toBe("usuarios/u1/anexos/123/abc.png");
    expect(caminhoStorage("u1", 123, "abc", "application/pdf")).toBe("usuarios/u1/anexos/123/abc.pdf");
    expect(caminhoStorage("u1", 123, "abc", "image/gif")).toBeNull();
    expect(caminhoStorage("", 123, "abc", "image/png")).toBeNull();
    expect(caminhoStorage("u/1", 123, "abc", "image/png")).toBeNull();
    expect(caminhoStorage("u1", 123, "../x", "image/png")).toBeNull();
  });
  it("valida caminhos de storage e locais", () => {
    expect(caminhoAnexoValido("usuarios/u1/anexos/9/abc.pdf")).toBe(true);
    expect(caminhoAnexoValido("local:anexos/9/abc.pdf")).toBe(true);
    expect(caminhoAnexoValido("usuarios/u1/outros/9/abc.pdf")).toBe(false);
    expect(caminhoAnexoValido("../../etc/passwd")).toBe(false);
  });
});

describe("R60 - normalização", () => {
  it("descarta inválidos, repetidos e passa de 5", () => {
    const lixo = [ok, { ...ok, nome: "repetido" }, { ...ok, id: "" }, { ...ok, id: "b", tipo: "image/gif" }, { ...ok, id: "c", caminho: "http://evil/x.jpg" }, null, "x"];
    expect(normalizarAnexos(lixo).map((a) => a.id)).toEqual(["a1"]);
    const muitos = Array.from({ length: 8 }, (_, i) => ({ ...ok, id: `i${i}`, caminho: `usuarios/u1/anexos/55/i${i}.jpg` }));
    expect(normalizarAnexos(muitos)).toHaveLength(5);
    expect(normalizarAnexos(undefined)).toEqual([]);
    expect(nomeAnexoSeguro("   ")).toBe("anexo");
    expect(nomeAnexoSeguro("a/b\\c")).toBe("a_b_c");
    expect(nomeAnexoSeguro("x".repeat(500))).toHaveLength(120);
  });
  it("lê anexos do payload da lixeira", () => {
    expect(anexosDoPayload(JSON.stringify({ id: 1, anexos: [ok] }))).toEqual([ok]);
    expect(anexosDoPayload("não é json")).toEqual([]);
    expect(anexosDoPayload(JSON.stringify({ id: 1 }))).toEqual([]);
  });
});

describe("R60 - operações e backup", () => {
  const base = () =>
    dataset({ contas: [conta()], despesas: [{ ...desp({ id: 55, valor: 10, conta: "111", data: dt(2026, 10, 1) }), anexos: [ok] }] });

  it("editar mantém os anexos; patch.anexos os substitui (vazio remove o campo)", () => {
    const r = editarLancamento(base(), 55, { descricao: "Novo nome" });
    expect(r.ok && r.ds.despesas[0].anexos).toEqual([ok]);
    const r2 = editarLancamento(base(), 55, { anexos: [] });
    expect(r2.ok && "anexos" in r2.ds.despesas[0]).toBe(false);
    const r3 = editarLancamento(base(), 55, { anexos: [{ ...ok, id: "z", caminho: "usuarios/u1/anexos/55/z.jpg" }, { ...ok, tipo: "image/gif" }] });
    expect(r3.ok && r3.ds.despesas[0].anexos?.map((a) => a.id)).toEqual(["z"]);
  });

  it("duplicar e repetir NÃO copiam anexos", () => {
    const d = duplicarLancamento(base(), 55, ctx(dt(2026, 10, 10)));
    expect(d.ok && d.criado.anexos).toBeUndefined();
    const r = repetirLancamento(base(), 55, { n: 2, intervalo: 1, unidade: "MESES" }, ctx(dt(2026, 10, 10)));
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.ds.despesas.filter((x) => x.id !== 55).every((x) => x.anexos === undefined)).toBe(true);
  });

  it("excluir vai para a lixeira com os metadados e restaurar mantém os anexos", () => {
    const e = excluirLancamento(base(), 55, {}, ctx(dt(2026, 10, 10)));
    expect(e.ok).toBe(true);
    if (!e.ok) return;
    expect(e.ds.despesas).toHaveLength(0);
    expect(anexosDoPayload(e.ds.lixeira[0].payload)).toEqual([ok]);
    const r = restaurarDaLixeira(e.ds, e.ds.lixeira[0].id, ctx(dt(2026, 10, 11)));
    expect(r.ok && r.ds.despesas[0].anexos).toEqual([ok]);
  });

  it("backup ida e volta preserva anexos; backup antigo sem o campo segue válido", () => {
    const json = JSON.stringify(exportarBackup(base()));
    const volta = importarBackup(json, 1);
    expect(volta.ok && volta.dataset.despesas[0].anexos).toEqual([ok]);
    const contaBackup = { id: 1, saldo: 0, banco: "B", pic: "", agencia: "1", conta: "111", titular: "" };
    const lancamento = { id: 1, descricao: "x", valor: 1, data: 1760000000000, categoria: "c", conta: "111", pic: "p", tipo: "DEBITO" };
    const antigo = importarBackup(JSON.stringify({ contas: [contaBackup], despesas: [lancamento] }), 1);
    expect(antigo.ok && antigo.dataset.despesas[0].anexos).toBeUndefined();
    const sujo = importarBackup(
      JSON.stringify({ contas: [contaBackup], despesas: [{ ...lancamento, anexos: [ok, { id: "r", tipo: "text/html", caminho: "x" }] }] }),
      1,
    );
    expect(sujo.ok && sujo.dataset.despesas[0].anexos?.map((a) => a.id)).toEqual(["a1"]);
  });
});
