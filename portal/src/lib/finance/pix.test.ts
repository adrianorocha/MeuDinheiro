import { describe, expect, it } from "vitest";
import { dt, desp } from "./fixtures";
import {
  acharDuplicataPix,
  centavosDeTexto,
  crc16,
  descricaoPix,
  idCurtoDaDescricao,
  lerBrCode,
  lerComprovantePix,
  lerTextoPix,
  mascararChave,
  normalizarContraparte,
  resumoPix,
  camposSobrescritos,
  sugerirLancamentoPix,
  sugerirPessoa,
} from "./pix";

// Exemplo do manual do BR Code (BCB): CRC 1D3D
const OFICIAL =
  "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000" + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D";
const ESTATICO =
  "00020101021226630014br.gov.bcb.pix0120maria@exemplo.com.br0217Almoco de domingo520400005303986540525.905802BR5911Maria Souza6009SAO PAULO62130509PEDIDO123630432F4";
const DINAMICO =
  "00020101021226790014br.gov.bcb.pix2557pix.exemplo.com.br/qr/v2/9d36b84fc70b478fb95c12729b90ca255204000053039865802BR5917Loja Exemplo LTDA6008CURITIBA62070503***630438AB";

describe("crc16", () => {
  it("vetor padrão CCITT-FALSE", () => expect(crc16("123456789")).toBe("29B1"));
  it("exemplo do manual", () => expect(crc16(OFICIAL.slice(0, -4))).toBe("1D3D"));
});

describe("lerBrCode", () => {
  it("exemplo oficial", () => {
    const r = lerBrCode(OFICIAL);
    expect(r).toEqual({
      ok: true,
      dados: { dinamico: false, beneficiario: "Fulano de Tal", cidade: "BRASILIA", chave: "123e4567-e12b-12d1-a456-426655440000" },
    });
  });
  it("estático com valor, mensagem e txid; aceita minúsculas no CRC e espaços ao redor", () => {
    const r = lerBrCode(`  ${ESTATICO.slice(0, -4)}${ESTATICO.slice(-4).toLowerCase()}\n`);
    expect(r.ok && r.dados).toMatchObject({
      valorCentavos: 2590, beneficiario: "Maria Souza", cidade: "SAO PAULO", chave: "maria@exemplo.com.br", mensagem: "Almoco de domingo", txid: "PEDIDO123", dinamico: false,
    });
  });
  it("dinâmico: devolve a URL só para exibição", () => {
    const r = lerBrCode(DINAMICO);
    expect(r.ok && r.dados).toMatchObject({ dinamico: true, urlPayload: "pix.exemplo.com.br/qr/v2/9d36b84fc70b478fb95c12729b90ca25", beneficiario: "Loja Exemplo LTDA" });
    expect(r.ok && r.dados.chave).toBeUndefined();
    expect(r.ok && r.dados.txid).toBeUndefined();
  });
  it("ignora texto depois do código", () => {
    expect(lerBrCode(`Copie: ${OFICIAL}\nobrigado`).ok).toBe(true);
  });
  it("CRC errado", () => {
    const r = lerBrCode(OFICIAL.slice(0, -4) + "0000");
    expect(r).toMatchObject({ ok: false });
    expect(!r.ok && r.erro).toMatch(/CRC/);
  });
  it("conteúdo adulterado (valor trocado) reprova no CRC", () => {
    expect(lerBrCode(ESTATICO.replace("25.90", "99.90")).ok).toBe(false);
  });
  it("cortado, vazio e não-Pix", () => {
    expect(lerBrCode(OFICIAL.slice(0, 50)).ok).toBe(false);
    expect(lerBrCode("").ok).toBe(false);
    expect(lerBrCode("qualquer coisa").ok).toBe(false);
  });
  it("BR Code que não é Pix (sem GUI) é recusado mesmo com CRC certo", () => {
    const corpo = "00020101021226250010outro.guid0107abcdefg5204000053039865802BR5903Eu 6003Rio6304";
    const r = lerBrCode(corpo + crc16(corpo));
    expect(!r.ok && r.erro).toMatch(/br\.gov\.bcb\.pix/);
  });
});

describe("centavosDeTexto", () => {
  it.each([
    ["1.234,56", 123456], ["1234,56", 123456], ["R$ 12,5", 1250], ["12", 1200], ["1234.56", 123456], ["1.234", 123400], ["0,99", 99], ["1.234.567,89", 123456789],
  ])("%s", (t, c) => expect(centavosDeTexto(t)).toBe(c));
  it("inválidos", () => {
    expect(centavosDeTexto("abc")).toBeUndefined();
    expect(centavosDeTexto("0,00")).toBeUndefined();
    expect(centavosDeTexto("")).toBeUndefined();
  });
});

const COMPROVANTE_ENVIADO = `
Comprovante de transferência
Pix enviado
Valor: R$ 1.234,56
Para
JOÃO DA SILVA SANTOS
CPF ***.123.456-**
Instituição: Banco Exemplo S.A.
Chave Pix: joao.silva@email.com
Data: 05/03/2025 14:32:10
ID da transação
E1234567820250305143200abcDEF123
`;

describe("lerComprovantePix", () => {
  it("comprovante enviado com rótulos em linhas separadas", () => {
    const c = lerComprovantePix(COMPROVANTE_ENVIADO);
    expect(c).toMatchObject({
      direcao: "ENVIADO",
      valorCentavos: 123456,
      contraparte: "João da Silva Santos",
      banco: "Banco Exemplo S.A.",
      chave: "joao.silva@email.com",
      idTransacao: "E1234567820250305143200abcDEF123",
    });
    expect(c.data).toBe(dt(2025, 3, 5));
  });
  it("notificação recebida numa linha só, valor com vírgula", () => {
    const c = lerComprovantePix("Você recebeu um Pix de MARIA APARECIDA no valor de R$ 50,00 em 10/02/2025");
    expect(c).toMatchObject({ direcao: "RECEBIDO", valorCentavos: 5000, contraparte: "Maria Aparecida" });
    expect(c.data).toBe(dt(2025, 2, 10));
  });
  it("'Pix recebido' com 'De:' e valor com ponto decimal", () => {
    const c = lerComprovantePix("Pix recebido\nValor: R$ 1234.50\nDe: Carlos Eduardo - Banco X\n12 de março de 2025");
    expect(c).toMatchObject({ direcao: "RECEBIDO", valorCentavos: 123450, contraparte: "Carlos Eduardo" });
    expect(c.data).toBe(dt(2025, 3, 12));
  });
  it("frase 'enviou ... para' e acentos", () => {
    const c = lerComprovantePix("Você enviou R$ 20,00 para José Conceição\nTransferência realizada");
    expect(c).toMatchObject({ direcao: "ENVIADO", valorCentavos: 2000, contraparte: "José Conceição" });
  });
  it("texto sujo: ruído, espaços e id E2E no meio", () => {
    const c = lerComprovantePix("  ***  PIX  ENVIADO  ***   \n\n\tR$    7,00 \nDestinatário:   LOJA   DO   ZE  \nE00360305202501011200ab12cd34ef5 ok");
    expect(c.direcao).toBe("ENVIADO");
    expect(c.valorCentavos).toBe(700);
    expect(c.contraparte).toBe("Loja do Ze");
    expect(c.idTransacao).toBe("E00360305202501011200ab12cd34ef5");
  });
  it("sem nada reconhecível", () => {
    expect(lerComprovantePix("olá, tudo bem?")).toEqual({});
    expect(lerComprovantePix("")).toEqual({});
  });
  it("data inválida (31/02) é ignorada", () => {
    expect(lerComprovantePix("Pix enviado R$ 1,00 em 31/02/2025").data).toBeUndefined();
  });
});

describe("normalizarContraparte", () => {
  it("remove CPF/CNPJ, banco e ajusta caixa", () => {
    expect(normalizarContraparte("  ANA  PAULA DE SOUZA  CPF ***.111.222-** ")).toBe("Ana Paula de Souza");
    expect(normalizarContraparte("Padaria Central - Banco Inter")).toBe("Padaria Central");
    expect(normalizarContraparte("Maria da Silva")).toBe("Maria da Silva");
    expect(normalizarContraparte("***")).toBe("");
  });
  it("limita o tamanho", () => expect(normalizarContraparte("a".repeat(200)).length).toBeLessThanOrEqual(60));
});

describe("mascararChave", () => {
  it.each([
    ["maria@exemplo.com.br", "ma***@exemplo.com.br"],
    ["529.982.247-25", "***.982.247-**"],
    ["52998224725", "***.982.247-**"],
    ["12.345.678/0001-95", "**.***.678/0001-**"],
    ["+5511987654321", "+55 *****4321"],
    ["11987654321", "+*****4321"],
    ["123e4567-e12b-12d1-a456-426655440000", "123e****-****-****-****-********0000"],
    ["***.982.247-**", "***.982.247-**"],
  ])("%s", (c, m) => expect(mascararChave(c)).toBe(m));
  it("nunca devolve a chave inteira", () => {
    for (const c of ["maria@exemplo.com.br", "52998224725", "+5511987654321", "123e4567-e12b-12d1-a456-426655440000", "abcdefghij"]) {
      expect(mascararChave(c)).not.toBe(c);
    }
  });
});

describe("sugerirLancamentoPix e lerTextoPix", () => {
  it("BR Code: débito 'Pix para', tag PIX, sem chave nas tags", () => {
    const r = lerTextoPix(ESTATICO);
    expect(r.ok && r.origem).toBe("BRCODE");
    const rascunho = r.ok ? r.rascunho : null;
    expect(rascunho).toMatchObject({ descricao: "Pix para Maria Souza (ID EDIDO123)", valorCentavos: 2590, tipo: "DEBITO", tags: ["PIX"] });
    expect(JSON.stringify(rascunho?.tags)).not.toContain("maria@");
  });
  it("BR Code aberto sem valor: valorCentavos ausente", () => {
    const r = lerTextoPix(OFICIAL);
    expect(r.ok && r.rascunho.valorCentavos).toBeUndefined();
    expect(r.ok && r.rascunho.descricao).toBe("Pix para Fulano de Tal");
  });
  it("comprovante recebido vira crédito 'Pix de'", () => {
    const r = lerTextoPix("Pix recebido\nR$ 15,00\nDe: PEDRO LIMA\n03/04/2025");
    expect(r.ok && r.rascunho).toMatchObject({ descricao: "Pix de Pedro Lima", tipo: "CREDITO", valorCentavos: 1500, data: dt(2025, 4, 3), tags: ["PIX"] });
  });
  it("direção padrão quando o texto não diz", () => {
    const r = lerTextoPix("R$ 9,90 Fulano", "RECEBIDO");
    expect(r.ok && r.rascunho.tipo).toBe("CREDITO");
  });
  it("erros claros", () => {
    expect(lerTextoPix("   ")).toMatchObject({ ok: false });
    expect(lerTextoPix("bom dia")).toMatchObject({ ok: false });
    expect(lerTextoPix(OFICIAL.slice(0, -4) + "FFFF")).toMatchObject({ ok: false, erro: expect.stringMatching(/CRC/) });
  });
  it("descricaoPix com ID curto e truncamento", () => {
    expect(descricaoPix("RECEBIDO", "Ana", "E1234567820250305143200abcDEF123")).toBe("Pix de Ana (ID bcDEF123)");
    expect(descricaoPix("ENVIADO", "x".repeat(200), "E1234567820250305143200abcDEF123").length).toBeLessThanOrEqual(120);
    expect(descricaoPix("ENVIADO")).toBe("Pix enviado");
    expect(idCurtoDaDescricao("Pix de Ana (ID bcDEF123)")).toBe("bcDEF123");
    expect(idCurtoDaDescricao("Pix de Ana")).toBeNull();
  });
  it("sugerirLancamentoPix devolve idTransacao completo", () => {
    const r = sugerirLancamentoPix({ fonte: "COMPROVANTE", dados: { direcao: "ENVIADO", idTransacao: "E1234567820250305143200abcDEF123", contraparte: "Ana" } });
    expect(r.idTransacao).toBe("E1234567820250305143200abcDEF123");
  });
});

describe("sugerirPessoa", () => {
  const pessoas = [{ id: 1, nome: "Maria Souza" }, { id: 2, nome: "João" }];
  it("casa por nome igual ou contido (2+ tokens)", () => {
    expect(sugerirPessoa("Maria Souza", pessoas)?.id).toBe(1);
    expect(sugerirPessoa("MARIA APARECIDA SOUZA", pessoas)?.id).toBe(1);
    expect(sugerirPessoa("João", pessoas)?.id).toBe(2);
  });
  it("não sugere por primeiro nome apenas", () => {
    expect(sugerirPessoa("João Pedro Alves", pessoas)).toBeNull();
    expect(sugerirPessoa(undefined, pessoas)).toBeNull();
  });
});

describe("acharDuplicataPix", () => {
  const base = desp({ id: 900, descricao: "Pix para Maria Souza", valor: 25.9, data: dt(2025, 3, 10), conta: "111" });
  const e = { conta: "111", tipo: "DEBITO" as const, valorCentavos: 2590, data: dt(2025, 3, 11), descricao: "Pix para Maria Souza" };
  it("mesmo valor, ±1 dia, nome parecido", () => expect(acharDuplicataPix([base], e)?.id).toBe(900));
  it("2 dias de diferença não é duplicata", () => expect(acharDuplicataPix([base], { ...e, data: dt(2025, 3, 12) })).toBeNull());
  it("valor, conta ou tipo diferentes não são", () => {
    expect(acharDuplicataPix([base], { ...e, valorCentavos: 2591 })).toBeNull();
    expect(acharDuplicataPix([base], { ...e, conta: "222" })).toBeNull();
    expect(acharDuplicataPix([base], { ...e, tipo: "CREDITO" })).toBeNull();
  });
  it("nome diferente não é", () => expect(acharDuplicataPix([base], { ...e, descricao: "Pix para Carlos Lima" })).toBeNull());
  it("compra no cartão e o próprio lançamento editado são ignorados", () => {
    expect(acharDuplicataPix([desp({ ...base, id: 901, cartaoId: 1 })], e)).toBeNull();
    expect(acharDuplicataPix([base], { ...e, ignorarId: 900 })).toBeNull();
  });
  it("mesmo ID curto basta, mesmo com valor e data diferentes", () => {
    const comId = desp({ id: 902, descricao: "Pix de Ana (ID bcDEF123)", valor: 10, tipo: "CREDITO", data: dt(2025, 1, 1), conta: "111" });
    expect(acharDuplicataPix([comId], { ...e, descricao: "Pix de Ana (ID bcDEF123)", tipo: "CREDITO", valorCentavos: 9999 })?.id).toBe(902);
  });
  it("descrição de banco ('PIX ENVIADO MARIA SOUZA') também casa pelo nome", () => {
    expect(acharDuplicataPix([desp({ id: 903, descricao: "PIX ENVIADO MARIA SOUZA", valor: 25.9, data: dt(2025, 3, 10) })], e)?.id).toBe(903);
  });
});

describe("resumoPix e camposSobrescritos", () => {
  it("resumo mascara a chave e não mostra a chave inteira", () => {
    const r = lerTextoPix(ESTATICO);
    const linhas = r.ok ? resumoPix(r) : [];
    const texto = JSON.stringify(linhas);
    expect(texto).toContain("ma***@exemplo.com.br");
    expect(texto).not.toContain("maria@exemplo");
    expect(linhas.find((l) => l.rotulo === "Valor")?.valor).toBe("R$ 25,90");
  });
  it("QR dinâmico: mostra a URL como texto, sem acessá-la", () => {
    const r = lerTextoPix(DINAMICO);
    expect(r.ok && resumoPix(r).some((l) => l.rotulo === "Endereço do QR" && l.valor.includes("não é acessado"))).toBe(true);
  });
  it("sobrescritas: só o que o usuário já preencheu e difere", () => {
    const rasc = { descricao: "Pix para Ana", valorCentavos: 1000, tipo: "DEBITO" as const, data: dt(2025, 3, 5), tags: ["PIX"] };
    expect(camposSobrescritos(rasc, { descricao: "", valorCentavos: null, data: dt(2025, 3, 5) })).toEqual([]);
    expect(camposSobrescritos(rasc, { descricao: "Padaria", valorCentavos: 500, data: dt(2025, 3, 6) })).toEqual(["descrição", "valor", "data"]);
    expect(camposSobrescritos(rasc, { descricao: "Pix para Ana", valorCentavos: 1000, data: dt(2025, 3, 6), dataPadrao: dt(2025, 3, 6) })).toEqual([]);
  });
});
