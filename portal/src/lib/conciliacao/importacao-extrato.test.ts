import { describe, expect, it } from "vitest";
import { recalcularTudo } from "../finance/calc";
import { cartao, conta, dataset, desp, dt } from "../finance/fixtures";
import type { Dataset } from "../finance/types";
import { identificarDestino, pistasDoArquivo, pistasDoOfx } from "./destino-sugerido";
import {
  arquivosNovos,
  carimboArquivo,
  gravarHashes,
  hashArquivo,
  jaProcessado,
  lerHashes,
  registrarHash,
  validarArquivoExtrato,
  type ArquivoDePasta,
  type MapaHashes,
} from "./importacao-arquivos";
import { executarLote, type ArquivoParaLote } from "./lote-multiplo";
import { aplicarConciliacao, type Plano } from "./plano";
import type { Destino, TransacaoBanco } from "./tipos";

const enc = (s: string) => new TextEncoder().encode(s);

describe("R63 sugestão de destino", () => {
  const contas = [
    { id: 1, banco: "Itaú", agencia: "0001", conta: "12345-6" },
    { id: 2, banco: "Nubank", agencia: "0001", conta: "99887-1" },
    { id: 3, banco: "Itaú", agencia: "0002", conta: "55555-0" },
  ];
  const cartoes = [
    { id: 10, nome: "Nubank Platinum", finalCartao: "4321", contaId: 2, cartaoPrincipalId: null },
    { id: 11, nome: "Virtual", finalCartao: "4321", contaId: 2, cartaoPrincipalId: 10 },
  ];
  const ofx = (acct: string, extra = "", ehCartao = false) =>
    ehCartao
      ? `OFXHEADER:100\n<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CCACCTFROM>${extra}<ACCTID>${acct}</CCACCTFROM>`
      : `OFXHEADER:100\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKACCTFROM>${extra}<ACCTID>${acct}</BANKACCTFROM>`;

  it("OFX com ACCTID igual: destino único", () => {
    const r = identificarDestino({ contas, cartoes }, pistasDoArquivo("extrato.ofx", ofx("123456", "<BANKID>341")));
    expect(r.estado).toBe("UNICO");
    expect(r.destino).toEqual({ tipo: "CONTA", conta: "12345-6" });
  });

  it("OFX de cartão casa pelo final do cartão (virtual é ignorado)", () => {
    const r = identificarDestino({ contas, cartoes }, pistasDoArquivo("fatura.ofx", ofx("5555444433334321", "", true)));
    expect(r.estado).toBe("UNICO");
    expect(r.destino).toEqual({ tipo: "CARTAO", cartaoId: 10 });
  });

  it("pistas do OFX: BANKID, ORG, ACCTID e tipo", () => {
    expect(pistasDoOfx(ofx("777", "<BANKID>260<ORG>NU PAGAMENTOS", true))).toMatchObject({ acctId: "777", bankId: "260", ehCartao: true });
  });

  it("CSV sem dados: nenhum candidato", () => {
    const r = identificarDestino({ contas, cartoes }, pistasDoArquivo("export.csv", "Data;Descricao;Valor\n01/03/2025;Mercado;-10,00"));
    expect(r).toEqual({ estado: "NENHUM", destino: null, candidatos: [] });
  });

  it("CSV com nome do banco no arquivo: único quando só uma conta daquele banco", () => {
    const r = identificarDestino({ contas, cartoes }, pistasDoArquivo("nubank_marco.csv", "Data;Descricao;Valor"));
    expect(r.estado).toBe("UNICO");
    expect(r.destino).toEqual({ tipo: "CONTA", conta: "99887-1" });
  });

  it("ambíguo: duas contas do mesmo banco, sem número -> o usuário escolhe", () => {
    const r = identificarDestino({ contas, cartoes }, pistasDoArquivo("itau.csv", "Data;Descricao;Valor"));
    expect(r.estado).toBe("AMBIGUO");
    expect(r.destino).toBeNull();
    expect(r.candidatos.map((c) => c.destino)).toEqual([
      { tipo: "CONTA", conta: "12345-6" },
      { tipo: "CONTA", conta: "55555-0" },
    ]);
  });

  it("CSV de fatura com final do cartão no nome do arquivo", () => {
    const r = identificarDestino({ contas, cartoes }, pistasDoArquivo("fatura_4321.csv", "Data;Descricao;Valor"));
    expect(r.destino).toEqual({ tipo: "CARTAO", cartaoId: 10 });
  });

  it("texto estranho no cabeçalho não quebra nada", () => {
    expect(() => identificarDestino({ contas, cartoes }, { cabecalho: "<script>alert(1)</script>".repeat(100), acctId: "x; DROP" })).not.toThrow();
  });
});

describe("R63 validação de arquivo", () => {
  it("aceita OFX/CSV e rejeita PDF, vazio, grande e extensão estranha", () => {
    expect(validarArquivoExtrato({ nome: "a.OFX", tamanho: 100 }).ok).toBe(true);
    expect(validarArquivoExtrato({ nome: "a.csv", tamanho: 100, tipo: "text/csv" }).ok).toBe(true);
    expect(validarArquivoExtrato({ nome: "a.pdf", tamanho: 100 }).ok).toBe(false);
    expect(validarArquivoExtrato({ nome: "a.exe", tamanho: 100 }).ok).toBe(false);
    expect(validarArquivoExtrato({ nome: "a.ofx", tamanho: 0 }).ok).toBe(false);
    expect(validarArquivoExtrato({ nome: "a.ofx", tamanho: 6 * 1024 * 1024 }, 5 * 1024 * 1024).ok).toBe(false);
    expect(validarArquivoExtrato({ nome: "a.csv", tamanho: 10, tipo: "image/png" }).ok).toBe(false);
  });
});

describe("R63 hash e arquivos já processados", () => {
  it("mesmo conteúdo, mesmo hash; conteúdo diferente, hash diferente", async () => {
    const a = await hashArquivo(enc("OFX conteudo"));
    expect(a).toMatch(/^[0-9a-f]{64}$/);
    expect(await hashArquivo(enc("OFX conteudo"))).toBe(a);
    expect(await hashArquivo(enc("OFX conteudo 2"))).not.toBe(a);
  });

  it("registro: detecta, limita a 500 e persiste/valida o JSON", async () => {
    const h = await hashArquivo(enc("x"));
    let mapa: MapaHashes = {};
    expect(jaProcessado(mapa, h)).toBeNull();
    mapa = registrarHash(mapa, h, "x.ofx", 1000);
    expect(jaProcessado(mapa, h)).toEqual({ nome: "x.ofx", em: 1000 });
    let grande: MapaHashes = {};
    for (let i = 0; i < 520; i++) grande = registrarHash(grande, i.toString(16).padStart(64, "0"), `f${i}`, i);
    expect(Object.keys(grande)).toHaveLength(500);
    expect(grande["0".repeat(64)]).toBeUndefined();
    let guardado: string | null = null;
    gravarHashes((_k, v) => {
      guardado = v;
    }, mapa);
    expect(lerHashes(() => guardado)).toEqual(mapa);
    expect(lerHashes(() => "lixo{")).toEqual({});
    expect(lerHashes(() => JSON.stringify({ nao_hash: { nome: "a", em: 1 } }))).toEqual({});
  });
});

describe("R63 arquivos novos da pasta (nome + tamanho + lastModified)", () => {
  const f = (nome: string, tamanho: number, modificado: number): ArquivoDePasta => ({ nome, tamanho, modificado });
  it("filtra extensão, ignora já vistos e detecta o mesmo nome modificado", () => {
    const a = f("jan.ofx", 100, 10);
    const lista = [a, f("fev.csv", 50, 20), f("nota.pdf", 5, 5), f("foto.png", 5, 1), f("vazio.ofx", 0, 3)];
    const vistos = new Set([carimboArquivo(a)]);
    expect(arquivosNovos(lista, vistos).map((x) => x.nome)).toEqual(["fev.csv"]);
    expect(arquivosNovos([f("jan.ofx", 100, 11)], vistos)).toHaveLength(1);
    expect(arquivosNovos([f("jan.ofx", 101, 10)], vistos)).toHaveLength(1);
    expect(arquivosNovos(lista, new Set(lista.map(carimboArquivo)))).toEqual([]);
  });
});

describe("R63 arquivo repetido não duplica lançamentos", () => {
  const DEST: Destino = { tipo: "CONTA", conta: "111" };
  const AGORA = dt(2025, 4, 1);
  const T = (fitid: string, data: number, valor: number, descricao: string): TransacaoBanco => ({ fitid, data, valor, descricao });
  const base = (): Dataset =>
    recalcularTudo(
      dataset({
        contas: [conta({ id: 1, conta: "111" })],
        cartoes: [cartao({ id: 10, contaId: 1 })],
        despesas: [
          desp({ id: 1, tipo: "CREDITO", valor: 5000, natureza: "SALDO_INICIAL", data: dt(2025, 2, 1), descricao: "Saldo inicial" }),
          desp({ id: 2, descricao: "Mercado Alfa", valor: 100, data: dt(2025, 3, 10), pago: false }),
        ],
      }),
    );
  const arq = (): ArquivoParaLote => ({
    id: "a",
    nome: "a.ofx",
    destino: DEST,
    rotuloDestino: "Banco A · 111",
    extrato: { formato: "OFX", periodo: null, avisos: [], transacoes: [T("a1", dt(2025, 3, 10), -100, "MERCADO ALFA"), T("x1", dt(2025, 3, 15), -12.5, "CAFE DA ESQUINA")] },
  });

  it("reimportar o mesmo extrato (mesmos fitids) não cria nem altera nada", async () => {
    let atual = base();
    const aplicar = (plano: Plano) => {
      const r = aplicarConciliacao(atual, plano, AGORA);
      if (r.ok) atual = r.ds;
      return r;
    };
    const opcoes = { sugeridos: false, criar: true };
    const ceder = async () => {};
    const r1 = await executarLote({ arquivos: [arq()], opcoes, getDs: () => atual, aplicar, ceder });
    expect(r1.contadores).toMatchObject({ conciliados: 1, criados: 1 });
    const total = atual.despesas.length;
    const r2 = await executarLote({ arquivos: [arq()], opcoes, getDs: () => atual, aplicar, ceder });
    expect(r2.contadores).toMatchObject({ conciliados: 0, criados: 0, duplicados: 2 });
    expect(atual.despesas).toHaveLength(total);
    for (const f of ["a1", "x1"]) expect(atual.despesas.filter((d) => d.fitid === f)).toHaveLength(1);
  });
});
