import { describe, expect, it } from "vitest";
import { analisarCsv, detectarDecimal, parseCsv, parseDataCsv, parseValorCsv } from "./csv";
import { dataOfx, parseOfx, pareceOfx } from "./ofx";
import { atribuirFitids, decodificarTexto, detectarParcela, fitidSintetico, limparDescricaoBanco, normalizarDescricaoBanco } from "./texto-banco";

const dia = (ms: number) => {
  const d = new Date(ms);
  return [d.getFullYear(), d.getMonth() + 1, d.getDate(), d.getHours()];
};

const OFX_ITAU = `OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

<OFX>
<SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>20250331120000[-3:BRT]<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>
<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>
<STMTRS><CURDEF>BRL
<BANKACCTFROM><BANKID>0341<BRANCHID>1234<ACCTID>98765-4<ACCTTYPE>CHECKING</BANKACCTFROM>
<BANKTRANLIST><DTSTART>20250301<DTEND>20250331
<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250310120000[-3:BRT]<TRNAMT>-123.45<FITID>2025031001<CHECKNUM>0001<MEMO>COMPRA NO DEBITO 10/03 SUPERMERCADO SÃO JOÃO
</STMTTRN>
<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20250315<TRNAMT>5000,00<FITID>2025031502<NAME>SALARIO<MEMO>SALARIO EMPRESA XYZ
</STMTTRN>
<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250320235959[-3:BRT]<TRNAMT>-45.90<FITID>2025032003<NAME>PIX ENVIADO<MEMO>PIX ENVIADO FULANO
</STMTTRN>
</BANKTRANLIST>
<LEDGERBAL><BALAMT>4830.65<DTASOF>20250331120000[-3:BRT]</LEDGERBAL>
</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>`;

describe("OFX v1 (SGML, sem tags de fechamento)", () => {
  const a = parseOfx(OFX_ITAU);
  it("reconhece o formato e lê conta, saldo e período", () => {
    expect(pareceOfx(OFX_ITAU)).toBe(true);
    expect(a.formato).toBe("OFX");
    expect(a.acctId).toBe("98765-4");
    expect(a.ehCartao).toBe(false);
    expect(a.saldoFinal?.valor).toBe(4830.65);
    expect(dia(a.saldoFinal!.data)).toEqual([2025, 3, 31, 12]);
    expect(dia(a.periodo!.inicio)).toEqual([2025, 3, 10, 12]);
  });
  it("lê transações com sinal, fitid, acentos, NAME/MEMO e vírgula decimal", () => {
    expect(a.transacoes).toHaveLength(3);
    expect(a.transacoes.map((t) => t.valor)).toEqual([-123.45, 5000, -45.9]);
    expect(a.transacoes.map((t) => t.fitid)).toEqual(["2025031001", "2025031502", "2025032003"]);
    expect(a.transacoes[0].descricao).toContain("SÃO JOÃO");
    expect(a.transacoes[1].descricao).toBe("SALARIO EMPRESA XYZ"); // MEMO contém NAME
    expect(a.transacoes[2].descricao).toBe("PIX ENVIADO FULANO"); // MEMO contém NAME: sem repetir
  });
  it("o fuso nunca desloca o dia", () => {
    expect(dia(dataOfx("20250310235959[-3:BRT]")!)).toEqual([2025, 3, 10, 12]);
    expect(dia(dataOfx("20250310000000[+9:JST]")!)).toEqual([2025, 3, 10, 12]);
    expect(dia(dataOfx("20250320235959.000[-3:BRT]")!)).toEqual([2025, 3, 20, 12]);
    expect(dataOfx("20250230")).toBeNull();
    expect(dataOfx("abc")).toBeNull();
  });
});

describe("OFX v2 (XML) e cartão", () => {
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL</CURDEF>
<CCACCTFROM><ACCTID>5555123412341234</ACCTID></CCACCTFROM>
<BANKTRANLIST>
<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20250305120000</DTPOSTED><TRNAMT>-89.90</TRNAMT><FITID>abc-1</FITID><MEMO>Netflix &amp; Co</MEMO></STMTTRN>
<STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20250306</DTPOSTED><TRNAMT>20.00</TRNAMT><FITID>abc-2</FITID><MEMO>Estorno</MEMO></STMTTRN>
</BANKTRANLIST>
<LEDGERBAL><BALAMT>-69.90</BALAMT><DTASOF>20250310</DTASOF></LEDGERBAL>
</CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>`;
  it("lê XML com fechamento, entidades e marca cartão", () => {
    const a = parseOfx(xml);
    expect(a.ehCartao).toBe(true);
    expect(a.acctId).toBe("5555123412341234");
    expect(a.transacoes.map((t) => [t.fitid, t.valor, t.descricao])).toEqual([
      ["abc-1", -89.9, "Netflix & Co"],
      ["abc-2", 20, "Estorno"],
    ]);
    expect(a.saldoFinal?.valor).toBe(-69.9);
  });
  it("transação inválida vira aviso, FITID repetido recebe sufixo", () => {
    const a = parseOfx(`<OFX><STMTTRN><DTPOSTED>x<TRNAMT>1</STMTTRN><STMTTRN><DTPOSTED>20250101<TRNAMT>1<FITID>Z</STMTTRN><STMTTRN><DTPOSTED>20250102<TRNAMT>2<FITID>Z</STMTTRN></OFX>`);
    expect(a.avisos).toHaveLength(1);
    expect(a.transacoes.map((t) => t.fitid)).toEqual(["Z", "Z#2"]);
  });
  it("sem FITID gera sintético estável", () => {
    const o = `<OFX><STMTTRN><DTPOSTED>20250101<TRNAMT>-5.00<MEMO>Café</STMTTRN><STMTTRN><DTPOSTED>20250101<TRNAMT>-5.00<MEMO>Café</STMTTRN></OFX>`;
    const a = parseOfx(o);
    const b = parseOfx(o);
    expect(a.transacoes[0].fitid.startsWith("h:")).toBe(true);
    expect(a.transacoes[0].fitid).not.toBe(a.transacoes[1].fitid);
    expect(b.transacoes.map((t) => t.fitid)).toEqual(a.transacoes.map((t) => t.fitid));
  });
});

describe("decodificação", () => {
  it("UTF-8 permanece; Windows-1252 é relido quando há U+FFFD", () => {
    expect(decodificarTexto(new TextEncoder().encode("Ação São João"))).toBe("Ação São João");
    const latin = Uint8Array.from(Buffer.from("Ação São João", "latin1"));
    expect(decodificarTexto(latin)).toBe("Ação São João");
    const comBom = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode("ok")]);
    expect(decodificarTexto(comBom)).toBe("ok");
  });
  it("OFX em Windows-1252 mantém acentos", () => {
    const texto = decodificarTexto(Uint8Array.from(Buffer.from(OFX_ITAU.replace("\\u00c3", "Ã"), "latin1")));
    expect(parseOfx(texto).transacoes[0].descricao).toContain("SÃO");
  });
});

describe("fitid e descrição bancária", () => {
  it("fitid sintético é estável e depende de data, valor, descrição e n", () => {
    const d = new Date(2025, 0, 5, 12).getTime();
    const base = fitidSintetico(d, -10, "Padaria 123", 1);
    expect(fitidSintetico(d, -10, "PADARIA 999", 1)).toBe(base); // dígitos ignorados
    expect(fitidSintetico(d, -10, "Padaria", 2)).not.toBe(base);
    expect(fitidSintetico(d, -10.01, "Padaria", 1)).not.toBe(base);
    expect(fitidSintetico(d + 86_400_000, -10, "Padaria", 1)).not.toBe(base);
  });
  it("duas linhas idênticas no mesmo dia continuam distintas", () => {
    const d = new Date(2025, 0, 5, 12).getTime();
    const r = atribuirFitids([
      { data: d, valor: -5, descricao: "Café" },
      { data: d, valor: -5, descricao: "Café" },
      { data: d, valor: -5, descricao: "Café" },
    ]);
    expect(new Set(r.map((t) => t.fitid)).size).toBe(3);
  });
  it("limpa ruído bancário", () => {
    expect(limparDescricaoBanco("COMPRA NO DEBITO 10/03 SUPERMERCADO EXTRA 123456")).toBe("Supermercado Extra");
    expect(limparDescricaoBanco("PIX ENVIADO JOAO DA SILVA")).toBe("Joao da Silva");
    expect(limparDescricaoBanco("PIX RECEBIDO - MARIA")).toBe("Maria");
    expect(limparDescricaoBanco("NETFLIX.COM 12/03")).toBe("Netflix.com");
    expect(limparDescricaoBanco("COMPRA NO DEBITO")).toBe("Compra No Debito"); // nada sobra: mantém o texto
    expect(limparDescricaoBanco("Padaria São João")).toBe("Padaria São João");
    expect(normalizarDescricaoBanco("COMPRA NO DEBITO 10/03 Padaria São João 998877")).toBe("padaria sao joao");
  });
  it("reconhece parcela no texto", () => {
    expect(detectarParcela("LOJA X (2/5)")).toEqual({ i: 2, n: 5 });
    expect(detectarParcela("PARC 03/10 LOJA")).toEqual({ i: 3, n: 10 });
    expect(detectarParcela("LOJA X")).toBeNull();
  });
});

describe("CSV", () => {
  const csvBR = `Data;Histórico;Valor;Saldo
10/03/2025;"COMPRA NO DEBITO; MERCADO";-1.234,56;5.000,00
15/03/2025;SALARIO;5.000,00;10.000,00
Saldo anterior;;;
20/03/25;PIX;(45,90);9.954,10`;
  it("detecta ; vírgula decimal, cabeçalho e mapeia colunas", () => {
    const an = analisarCsv(csvBR);
    expect(an.config).toMatchObject({ delimitador: ";", decimal: ",", temCabecalho: true, colData: 0, colDescricao: 1, colValor: 2, colSaldo: 3, inverter: false });
    const a = parseCsv(csvBR, an.config);
    expect(a.transacoes.map((t) => t.valor)).toEqual([-1234.56, 5000, -45.9]);
    expect(a.transacoes[0].descricao).toBe("COMPRA NO DEBITO; MERCADO");
    expect(dia(a.transacoes[2].data)).toEqual([2025, 3, 20, 12]);
    expect(a.saldoFinal?.valor).toBe(9954.1);
    expect(a.avisos[0]).toContain("1 linha");
  });
  it("vírgula como delimitador com ponto decimal e datas ISO", () => {
    const t = `date,description,amount\n2025-03-10,Coffee,-3.50\n2025-03-11,Refund,1,200.00`;
    const an = analisarCsv(t);
    expect(an.config.delimitador).toBe(",");
    const a = parseCsv(`date,description,amount\n2025-03-10,Coffee,-3.50\n2025-03-11,"Refund","1,200.00"`, an.config);
    expect(an.config.decimal).toBe(".");
    expect(a.transacoes.map((x) => x.valor)).toEqual([-3.5, 1200]);
  });
  it("tab, débito+crédito e inverter sinal", () => {
    const t = "Data\tDescrição\tDébito\tCrédito\n01/04/2025\tLoja\t50,00\t\n02/04/2025\tDepósito\t\t100,00";
    const an = analisarCsv(t);
    expect(an.config.delimitador).toBe("\t");
    expect(an.config.colDebito).toBe(2);
    expect(an.config.colCredito).toBe(3);
    expect(an.config.colValor).toBeNull();
    expect(parseCsv(t, an.config).transacoes.map((x) => x.valor)).toEqual([-50, 100]);
    expect(parseCsv(t, { ...an.config, inverter: true }).transacoes.map((x) => x.valor)).toEqual([50, -100]);
  });
  it("CSV de fatura (compra positiva) com inverter vira saída", () => {
    const t = "Data;Estabelecimento;Valor\n05/05/2025;LOJA A;89,90\n06/05/2025;ESTORNO LOJA;-20,00";
    const an = analisarCsv(t);
    const a = parseCsv(t, { ...an.config, inverter: true });
    expect(a.transacoes.map((x) => x.valor)).toEqual([-89.9, 20]);
  });
  it("sem cabeçalho e com preâmbulo do banco", () => {
    const t = "Extrato Conta Corrente\nPeríodo: março\n\n10/03/2025;Mercado;-10,00;100,00\n11/03/2025;Salário;50,00;150,00";
    const an = analisarCsv(t);
    expect(an.config.temCabecalho).toBe(false);
    expect(an.config).toMatchObject({ colData: 0, colDescricao: 1, colValor: 2, colSaldo: 3 });
    expect(parseCsv(t, an.config).transacoes).toHaveLength(2);
  });
  it("saldo final usa a linha mais recente mesmo em ordem decrescente", () => {
    const t = "Data;Descrição;Valor;Saldo\n12/03/2025;B;-10,00;80,00\n11/03/2025;A;-20,00;90,00";
    const a = parseCsv(t, analisarCsv(t).config);
    expect(a.saldoFinal?.valor).toBe(80);
  });
  it("utilitários de data, valor e decimal", () => {
    expect(dia(parseDataCsv("31/12/24")!)).toEqual([2024, 12, 31, 12]);
    expect(parseDataCsv("31/02/2025")).toBeNull();
    expect(parseDataCsv("2025-3-5")).not.toBeNull();
    expect(parseValorCsv("R$ 1.234,56", ",")).toBe(1234.56);
    expect(parseValorCsv("1,234.56", ".")).toBe(1234.56);
    expect(parseValorCsv("12,50-", ",")).toBe(-12.5);
    expect(parseValorCsv("abc", ",")).toBeNull();
    expect(detectarDecimal(["1.234,56", "10,00"])).toBe(",");
    expect(detectarDecimal(["1,234.56", "10.00"])).toBe(".");
  });
  it("reimportar o mesmo CSV gera os mesmos fitids", () => {
    const t = "Data;Descrição;Valor\n05/05/2025;Café;-5,00\n05/05/2025;Café;-5,00";
    const cfg = analisarCsv(t).config;
    expect(parseCsv(t, cfg).transacoes.map((x) => x.fitid)).toEqual(parseCsv(t, cfg).transacoes.map((x) => x.fitid));
  });
});
