import { toCents } from "./money";
import { inicioDoDia } from "./dates";
import { jaccard, normalizar, tokensDescricao } from "./texto";
import { MAX_TAG_CHARS } from "./tags";
import type { Despesa, Pessoa } from "./types";

/**
 * R93 - Lançamentos via PIX (portal). Funções puras, 100% locais: nada aqui acessa rede nem registra o texto recebido.
 * Duas fontes de captura: (1) BR Code / "Pix copia e cola" (EMV-MPM do Banco Central, com CRC16) e (2) texto de comprovante ou
 * notificação de banco (leitura tolerante por regex). Dinheiro sempre em centavos neste módulo.
 */

export type DirecaoPix = "ENVIADO" | "RECEBIDO";

export const TAG_PIX = "PIX";

// ---------------------------------------------------------------------------------------------------------------------
// CRC16-CCITT (poly 0x1021, init 0xFFFF) - o do BR Code
// ---------------------------------------------------------------------------------------------------------------------

/** CRC16-CCITT-FALSE em 4 hexadecimais maiúsculos, calculado sobre os bytes UTF-8 do texto. */
export function crc16(texto: string): string {
  let crc = 0xffff;
  for (const byte of new TextEncoder().encode(texto)) {
    crc ^= byte << 8;
    for (let i = 0; i < 8; i++) crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
  }
  return crc.toString(16).toUpperCase().padStart(4, "0");
}

// ---------------------------------------------------------------------------------------------------------------------
// BR Code
// ---------------------------------------------------------------------------------------------------------------------

export interface DadosBrCode {
  /** Valor em centavos (id 54); ausente quando o QR é "aberto" (quem paga digita). */
  valorCentavos?: number;
  /** Nome do beneficiário (id 59). */
  beneficiario?: string;
  /** Cidade (id 60). */
  cidade?: string;
  /** Chave Pix (26 > 01). Só existe em QR estático; em dinâmico vem a URL. */
  chave?: string;
  /** Mensagem ao pagador (26 > 02). */
  mensagem?: string;
  /** Identificador da transação (62 > 05); "***" (não informado) vira ausente. */
  txid?: string;
  /** URL do payload dinâmico (26 > 25). Apenas exibida: o sistema NUNCA a acessa. */
  urlPayload?: string;
  dinamico: boolean;
}

export type ResultadoBrCode = { ok: true; dados: DadosBrCode } | { ok: false; erro: string };

type Tlv = Map<string, string>;

/** Lê uma sequência TLV (2 dígitos de id, 2 de tamanho). Devolve null se estiver malformada. Preserva o primeiro valor de cada id. */
function lerTlv(s: string, parar63 = false): Tlv | null {
  const m: Tlv = new Map();
  let i = 0;
  while (i < s.length) {
    if (i + 4 > s.length) return null;
    const id = s.slice(i, i + 2);
    const tam = s.slice(i + 2, i + 4);
    if (!/^\d{2}$/.test(id) || !/^\d{2}$/.test(tam)) return null;
    const n = Number(tam);
    const fim = i + 4 + n;
    if (fim > s.length) return null;
    if (!m.has(id)) m.set(id, s.slice(i + 4, fim));
    i = fim;
    if (parar63 && id === "63") break;
  }
  return m;
}

/** "25.90" / "1234.5" -> centavos; formato do BR Code usa ponto e até 2 casas. Inválido -> undefined. */
function valorBrCode(v: string): number | undefined {
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(v)) return undefined;
  const [int, frac = ""] = v.split(".");
  return Number(int) * 100 + Number(frac.padEnd(2, "0"));
}

const SEM_TXID = "***";

/** Lê e valida (CRC16 incluído) um Pix copia e cola / conteúdo de QR Code. Nunca lança; erros vêm em texto claro. */
export function lerBrCode(texto: string): ResultadoBrCode {
  const bruto = (texto ?? "").trim();
  const ini = bruto.indexOf("000201");
  if (ini < 0) return { ok: false, erro: "Não parece um Pix copia e cola (o código começa com 000201)." };
  // o código vai até o fim do campo 63 (CRC); o que vier depois (quebra de linha, texto extra) é ignorado
  const corpo = bruto.slice(ini);
  const topo = lerTlv(corpo, true);
  const pos63 = topo ? corpo.lastIndexOf("6304") : -1;
  if (!topo || !topo.has("63") || pos63 < 0) return { ok: false, erro: "Código incompleto ou cortado: falta o final (CRC)." };
  const crcInformado = topo.get("63")!;
  const codigo = corpo.slice(0, pos63 + 4);
  if (!/^[0-9A-Fa-f]{4}$/.test(crcInformado) || crc16(codigo) !== crcInformado.toUpperCase()) {
    return { ok: false, erro: "Código Pix inválido: o CRC não confere. Copie o código inteiro de novo, sem alterar nada." };
  }
  if (topo.get("00") !== "01") return { ok: false, erro: "Formato de código não reconhecido (versão do payload diferente de 01)." };

  // conta do Pix: qualquer id 26-51 cujo subcampo 00 seja a GUI br.gov.bcb.pix
  let conta: Tlv | null = null;
  for (let id = 26; id <= 51 && !conta; id++) {
    const v = topo.get(String(id));
    const sub = v ? lerTlv(v) : null;
    if (sub && (sub.get("00") ?? "").toLowerCase() === "br.gov.bcb.pix") conta = sub;
  }
  if (!conta) return { ok: false, erro: "Este código não é de um Pix (não tem o identificador br.gov.bcb.pix)." };

  const adicional = topo.get("62") ? lerTlv(topo.get("62")!) : null;
  const txidBruto = adicional?.get("05")?.trim();
  const url = conta.get("25")?.trim();
  const dados: DadosBrCode = {
    dinamico: !!url,
    ...(topo.get("54") !== undefined && valorBrCode(topo.get("54")!) !== undefined ? { valorCentavos: valorBrCode(topo.get("54")!) } : {}),
    ...(topo.get("59")?.trim() ? { beneficiario: topo.get("59")!.trim() } : {}),
    ...(topo.get("60")?.trim() ? { cidade: topo.get("60")!.trim() } : {}),
    ...(conta.get("01")?.trim() ? { chave: conta.get("01")!.trim() } : {}),
    ...(conta.get("02")?.trim() ? { mensagem: conta.get("02")!.trim() } : {}),
    ...(txidBruto && txidBruto !== SEM_TXID ? { txid: txidBruto } : {}),
    ...(url ? { urlPayload: url } : {}),
  };
  return { ok: true, dados };
}

// ---------------------------------------------------------------------------------------------------------------------
// Chave Pix: máscara
// ---------------------------------------------------------------------------------------------------------------------

function cpfValido(d: string): boolean {
  if (!/^\d{11}$/.test(d) || /^(\d)\1{10}$/.test(d)) return false;
  for (const t of [9, 10]) {
    let soma = 0;
    for (let i = 0; i < t; i++) soma += Number(d[i]) * (t + 1 - i);
    const dv = ((soma * 10) % 11) % 10;
    if (dv !== Number(d[t])) return false;
  }
  return true;
}

/** Máscara parcial para exibir a chave na tela. Nunca devolve a chave inteira (exceto se já vier mascarada). */
export function mascararChave(chave: string): string {
  const c = chave.trim();
  if (!c) return "";
  if (c.includes("*")) return c;
  if (c.includes("@")) {
    const [u, ...resto] = c.split("@");
    return `${u.slice(0, Math.min(2, Math.max(1, u.length - 1)))}***@${resto.join("@")}`;
  }
  const digitos = c.replace(/\D/g, "");
  const formatado = /[.\-/]/.test(c) && !c.startsWith("+");
  if (!c.startsWith("+") && (digitos.length === 14 || (formatado && digitos.length === 11) || (digitos.length === 11 && cpfValido(digitos)))) {
    // CPF: ***.123.456-**   CNPJ: **.***.123/0001-**
    if (digitos.length === 11) return `***.${digitos.slice(3, 6)}.${digitos.slice(6, 9)}-**`;
    return `**.***.${digitos.slice(5, 8)}/${digitos.slice(8, 12)}-**`;
  }
  if (c.startsWith("+") || (digitos.length >= 10 && digitos.length <= 13 && !/[a-z]/i.test(c))) {
    return `+${digitos.startsWith("55") && c.startsWith("+") ? "55 " : ""}*****${digitos.slice(-4)}`;
  }
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(c)) return `${c.slice(0, 4)}****-****-****-****-********${c.slice(-4)}`;
  return c.length <= 6 ? "***" : `${c.slice(0, 2)}***${c.slice(-2)}`;
}

// ---------------------------------------------------------------------------------------------------------------------
// Comprovante / notificação de banco
// ---------------------------------------------------------------------------------------------------------------------

export interface DadosComprovante {
  direcao?: DirecaoPix;
  valorCentavos?: number;
  /** Nome de quem recebeu (ENVIADO) ou de quem enviou (RECEBIDO). */
  contraparte?: string;
  banco?: string;
  /** epoch ms (12:00 local do dia lido). */
  data?: number;
  /** ID E2E (E + 31 caracteres alfanuméricos). */
  idTransacao?: string;
  chave?: string;
  mensagem?: string;
}

const MESES: Record<string, number> = {
  jan: 1, janeiro: 1, fev: 2, fevereiro: 2, mar: 3, marco: 3, abr: 4, abril: 4, mai: 5, maio: 5, jun: 6, junho: 6,
  jul: 7, julho: 7, ago: 8, agosto: 8, set: 9, setembro: 9, out: 10, outubro: 10, nov: 11, novembro: 11, dez: 12, dezembro: 12,
};

/** "1.234,56" / "1234,56" / "1234.56" / "12" -> centavos. Inválido -> undefined. */
export function centavosDeTexto(t: string): number | undefined {
  let s = t.trim().replace(/[R$\s]/g, "");
  if (!s) return undefined;
  if (s.includes(",")) s = s.replace(/\./g, "").replace(",", ".");
  else if ((s.match(/\./g) ?? []).length > 1) s = s.replace(/\./g, "");
  else if (/^\d{1,3}\.\d{3}$/.test(s)) s = s.replace(".", ""); // "1.234" = mil duzentos e trinta e quatro
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return undefined;
  const c = toCents(Number(s));
  return c > 0 && c < 1e13 ? c : undefined;
}

function dataValida(a: number, m: number, d: number): number | undefined {
  if (a < 2000 || a > 2100 || m < 1 || m > 12 || d < 1 || d > 31) return undefined;
  const dt = new Date(a, m - 1, d, 12, 0, 0, 0);
  return dt.getMonth() === m - 1 && dt.getDate() === d ? dt.getTime() : undefined;
}

function lerData(texto: string): number | undefined {
  let m = /\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})\b/.exec(texto);
  if (m) {
    const a = m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3]);
    const r = dataValida(a, Number(m[2]), Number(m[1]));
    if (r !== undefined) return r;
  }
  m = /\b(\d{4})-(\d{2})-(\d{2})\b/.exec(texto);
  if (m) {
    const r = dataValida(Number(m[1]), Number(m[2]), Number(m[3]));
    if (r !== undefined) return r;
  }
  const sem = normalizar(texto);
  m = /\b(\d{1,2})\s*(?:de\s+)?([a-z]{3,9})\.?\s*(?:de\s+)?(\d{4})\b/.exec(sem);
  if (m && MESES[m[2]]) return dataValida(Number(m[3]), MESES[m[2]], Number(m[1]));
  return undefined;
}

const ROTULOS_ENVIADO = ["para", "destinatario", "favorecido", "recebedor", "quem recebeu", "nome do recebedor", "nome do favorecido", "beneficiario", "destino", "nome"];
const ROTULOS_RECEBIDO = ["de", "pagador", "remetente", "quem pagou", "quem enviou", "nome do pagador", "nome do remetente", "origem", "nome"];

const RE_CPF_MASC = /[*\d]{3}\.[*\d]{3}\.[*\d]{3}-[*\d]{2}|[*\d]{2}\.[*\d]{3}\.[*\d]{3}\/[*\d]{4}-[*\d]{2}/g;

/** Limpa o nome da contraparte: remove CPF/CNPJ, banco depois de " - ", ruído, e põe em formato "Nome Próprio" se vier em CAIXA ALTA. */
export function normalizarContraparte(bruto: string): string {
  let s = (bruto ?? "").normalize("NFC").replace(RE_CPF_MASC, " ").replace(/\s+/g, " ").trim();
  s = s.replace(/\s+(?:-|–|\|)\s+.*$/, "").replace(/\s+(?:cpf|cnpj|ag[eê]ncia|conta|banco|institui[cç][aã]o)\b.*$/i, "");
  s = s.replace(/^[\s:.,;\-–*]+|[\s:.,;\-–*]+$/g, "").trim();
  if (!s) return "";
  const letras = s.replace(/[^\p{L}]/gu, "");
  if (letras && letras === letras.toUpperCase()) {
    s = s
      .toLowerCase()
      .replace(/(^|[\s\-'/(])(\p{L})/gu, (_m, a: string, b: string) => a + b.toUpperCase())
      .replace(/\s(Da|De|Do|Das|Dos|E)(?=\s)/g, (w) => w.toLowerCase());
  }
  return s.length > 60 ? s.slice(0, 60).trim() : s;
}

/** Texto até o fim da linha, cortado antes de valores/rótulos que costumam vir colados. */
function nomeDe(resto: string): string {
  return normalizarContraparte(resto.replace(/\s+(?:r\$|valor|data|em\s+\d|no\s+valor|chave|banco|institui).*$/i, ""));
}

/** Valor do campo "rótulo: valor" (ou valor na linha seguinte). */
function campoRotulado(linhas: string[], rotulos: readonly string[]): string | undefined {
  for (let i = 0; i < linhas.length; i++) {
    const m = /^([^:]{1,32}?)\s*:\s*(.*)$/.exec(linhas[i]);
    const rot = normalizar(m ? m[1] : linhas[i]);
    if (!rotulos.includes(rot)) continue;
    const v = m ? m[2].trim() : "";
    if (v) return v;
    const prox = linhas.slice(i + 1).find((l) => l.trim());
    if (prox && !/:\s*$/.test(prox) && normalizar(prox).length > 0) return prox.trim();
  }
  return undefined;
}

const RE_ENVIADO = /pix\s+(?:enviado|pago|realizado|efetuado)|voce\s+(?:enviou|pagou|transferiu)|(?:enviou|pagou|transferiu)\s+(?:um\s+)?(?:pix|r\$)|transferencia\s+(?:realizada|enviada|efetuada)|pagamento\s+(?:realizado|efetuado)|comprovante\s+de\s+(?:pagamento|transferencia)|pix\s+para\b/;
const RE_RECEBIDO = /pix\s+recebido|voce\s+recebeu|recebeu\s+(?:um\s+)?(?:pix|r\$|uma\s+transferencia)|transferencia\s+recebida|pagamento\s+recebido|recebimento\s+(?:de\s+)?pix|credito\s+(?:de\s+)?pix|pix\s+de\b/;

function lerDirecao(sem: string): DirecaoPix | undefined {
  const e = RE_ENVIADO.exec(sem);
  const r = RE_RECEBIDO.exec(sem);
  if (e && r) return r.index <= e.index ? "RECEBIDO" : "ENVIADO";
  return e ? "ENVIADO" : r ? "RECEBIDO" : undefined;
}

/** Leitura tolerante de comprovante/notificação. Devolve só o que encontrou; `{}` se nada foi reconhecido. Nunca lança. */
export function lerComprovantePix(texto: string): DadosComprovante {
  const t = (texto ?? "").normalize("NFC").replace(/\r/g, "").replace(/ /g, " ");
  const linhas = t.split("\n").map((l) => l.trim()).filter(Boolean);
  const sem = normalizar(t);
  const out: DadosComprovante = {};

  const direcao = lerDirecao(sem);
  if (direcao) out.direcao = direcao;

  // valor: campo "Valor" primeiro, depois o primeiro "R$ ..."
  const vLab = /\bvalor(?:\s+(?:do\s+pix|pago|enviado|recebido|da\s+transfer[eê]ncia|total))?\s*[:\-]?\s*(?:r\$)?\s*(\d[\d.]*(?:,\d{1,2})?)/i.exec(t);
  const vRs = /R\$\s*(\d[\d.]*(?:,\d{1,2})?)/i.exec(t);
  const valor = centavosDeTexto(vLab?.[1] ?? "") ?? centavosDeTexto(vRs?.[1] ?? "");
  if (valor !== undefined) out.valorCentavos = valor;

  const e2e = /\bE\d{20}[A-Za-z0-9]{11}\b/.exec(t) ?? /\bE(?=[A-Za-z0-9]*\d)[A-Za-z0-9]{31}\b/.exec(t);
  if (e2e) out.idTransacao = e2e[0];

  const data = lerData(t);
  if (data !== undefined) out.data = data;

  // contraparte: rótulos, depois frases ("Pix enviado para X", "Você recebeu ... de X")
  const rotulos = direcao === "RECEBIDO" ? ROTULOS_RECEBIDO : direcao === "ENVIADO" ? ROTULOS_ENVIADO : [...ROTULOS_ENVIADO, ...ROTULOS_RECEBIDO];
  let nome = nomeDe(campoRotulado(linhas, rotulos.filter((r) => r !== "nome")) ?? "");
  if (!nome) {
    const re =
      direcao === "RECEBIDO"
        ? /(?:pix\s+recebido|recebeu(?:\s+(?:um\s+)?(?:pix|transfer[eê]ncia))?)(?:\s+(?:de\s+)?r\$\s*[\d.,]+)?\s+de\s+([^\n]+)/i
        : /(?:pix\s+(?:enviado|pago|realizado)|enviou|transferiu|pagou)(?:\s+(?:um\s+)?pix)?(?:\s+(?:de\s+)?r\$\s*[\d.,]+)?\s+para\s+([^\n]+)/i;
    const m = re.exec(t) ?? (direcao ? undefined : /\b(?:para|de)\s+([A-ZÀ-Ú][^\n]+)/.exec(t));
    if (m) nome = nomeDe(m[1]);
  }
  if (!nome) nome = nomeDe(campoRotulado(linhas, ["nome"]) ?? "");
  if (nome) out.contraparte = nome;

  const banco = campoRotulado(linhas, ["instituicao", "banco", "instituicao financeira", "banco destino", "banco de destino", "banco origem", "banco de origem"]);
  if (banco) out.banco = banco.replace(/\s+/g, " ").slice(0, 60);

  const chave = campoRotulado(linhas, ["chave", "chave pix", "chave de destino", "chave do recebedor", "chave utilizada"]);
  if (chave) out.chave = chave.replace(/\s+/g, "").slice(0, 80);

  const msg = campoRotulado(linhas, ["mensagem", "descricao", "mensagem ao destinatario", "mensagem opcional"]);
  if (msg) out.mensagem = msg.slice(0, 140);

  return out;
}

// ---------------------------------------------------------------------------------------------------------------------
// Rascunho de lançamento
// ---------------------------------------------------------------------------------------------------------------------

export interface RascunhoPix {
  descricao: string;
  /** Ausente quando a fonte não trouxe valor (o usuário digita). */
  valorCentavos?: number;
  tipo: "DEBITO" | "CREDITO";
  /** epoch ms; ausente quando a fonte não trouxe data (o formulário mantém a data atual). */
  data?: number;
  tags: string[];
  /** ID E2E/txid completo (não é gravado inteiro: só os 8 últimos caracteres vão na descrição, para detectar duplicata). */
  idTransacao?: string;
  /** Nome normalizado da contraparte (para sugerir Pessoa). */
  contraparte?: string;
}

export type EntradaPix =
  | { fonte: "BRCODE"; dados: DadosBrCode }
  | { fonte: "COMPROVANTE"; dados: DadosComprovante; /** usada se o texto não indicar a direção; padrão ENVIADO. */ direcaoPadrao?: DirecaoPix };

const MAX_DESCRICAO = 120;

/** Sufixo curto do ID (últimos 8 caracteres) usado na descrição como "(ID XXXXXXXX)". */
export function idCurto(id: string): string {
  return id.replace(/[^A-Za-z0-9]/g, "").slice(-8);
}

/** Descrição padrão: "Pix para X"/"Pix de X" + " (ID 8chars)" quando houver identificador. */
export function descricaoPix(direcao: DirecaoPix, contraparte?: string, id?: string): string {
  const sufixo = id && idCurto(id).length >= 6 ? ` (ID ${idCurto(id)})` : "";
  const base = contraparte ? `Pix ${direcao === "ENVIADO" ? "para" : "de"} ${contraparte}` : direcao === "ENVIADO" ? "Pix enviado" : "Pix recebido";
  return base.slice(0, MAX_DESCRICAO - sufixo.length).trim() + sufixo;
}

/** Extrai "(ID XXXXXXXX)" da descrição de um lançamento. */
export function idCurtoDaDescricao(descricao: string): string | null {
  return /\(ID ([A-Za-z0-9]{6,})\)/.exec(descricao)?.[1] ?? null;
}

/** Monta o rascunho a partir do que foi lido. A direção do BR Code é sempre ENVIADO (é um código para pagar). */
export function sugerirLancamentoPix(entrada: EntradaPix): RascunhoPix {
  if (entrada.fonte === "BRCODE") {
    const d = entrada.dados;
    const nome = d.beneficiario ? normalizarContraparte(d.beneficiario) : undefined;
    return {
      descricao: descricaoPix("ENVIADO", nome, d.txid),
      ...(d.valorCentavos !== undefined ? { valorCentavos: d.valorCentavos } : {}),
      tipo: "DEBITO",
      tags: [TAG_PIX],
      ...(d.txid ? { idTransacao: d.txid } : {}),
      ...(nome ? { contraparte: nome } : {}),
    };
  }
  const d = entrada.dados;
  const direcao = d.direcao ?? entrada.direcaoPadrao ?? "ENVIADO";
  const nome = d.contraparte ? normalizarContraparte(d.contraparte) : undefined;
  return {
    descricao: descricaoPix(direcao, nome, d.idTransacao),
    ...(d.valorCentavos !== undefined ? { valorCentavos: d.valorCentavos } : {}),
    tipo: direcao === "ENVIADO" ? "DEBITO" : "CREDITO",
    ...(d.data !== undefined ? { data: d.data } : {}),
    tags: [TAG_PIX],
    ...(d.idTransacao ? { idTransacao: d.idTransacao } : {}),
    ...(nome ? { contraparte: nome } : {}),
  };
}

/** Texto colado -> BR Code se começar como um; senão comprovante. `ok:false` quando não deu para entender nada. */
export type LeituraPix =
  | { ok: true; origem: "BRCODE"; brCode: DadosBrCode; rascunho: RascunhoPix }
  | { ok: true; origem: "COMPROVANTE"; comprovante: DadosComprovante; rascunho: RascunhoPix }
  | { ok: false; erro: string };

export function lerTextoPix(texto: string, direcaoPadrao?: DirecaoPix): LeituraPix {
  const t = (texto ?? "").trim();
  if (!t) return { ok: false, erro: "Cole o Pix copia e cola ou o texto do comprovante." };
  if (t.includes("000201") && /br\.gov\.bcb\.pix/i.test(t)) {
    const r = lerBrCode(t);
    if (!r.ok) return r;
    return { ok: true, origem: "BRCODE", brCode: r.dados, rascunho: sugerirLancamentoPix({ fonte: "BRCODE", dados: r.dados }) };
  }
  if (/^000201\d{2}/.test(t) && !/\s/.test(t) && t.length > 40) {
    const r = lerBrCode(t);
    if (!r.ok) return r;
  }
  const c = lerComprovantePix(t);
  if (c.valorCentavos === undefined && !c.contraparte && !c.idTransacao && !c.direcao) {
    return { ok: false, erro: "Não encontrei dados de Pix nesse texto. Cole o código copia e cola ou o texto do comprovante." };
  }
  return { ok: true, origem: "COMPROVANTE", comprovante: c, rascunho: sugerirLancamentoPix({ fonte: "COMPROVANTE", dados: c, direcaoPadrao }) };
}

/** Tags do rascunho respeitando o limite de tamanho (a tag PIX é curta; nunca entra nome, chave ou ID). */
export function tagsPix(extras: readonly string[] = []): string[] {
  return [TAG_PIX, ...extras.filter((x) => x.trim() && x.trim().length <= MAX_TAG_CHARS)];
}

// ---------------------------------------------------------------------------------------------------------------------
// Pessoa e duplicata
// ---------------------------------------------------------------------------------------------------------------------

/** Pessoa cadastrada cujo nome casa com a contraparte (igual, ou todos os tokens do nome menor contidos no maior, mínimo 2). Só sugestão. */
export function sugerirPessoa(contraparte: string | undefined, pessoas: readonly Pessoa[]): Pessoa | null {
  if (!contraparte) return null;
  const a = normalizar(contraparte);
  if (!a) return null;
  const ta = a.split(" ").filter((x) => x.length >= 2);
  for (const p of pessoas) {
    const b = normalizar(p.nome);
    if (!b) continue;
    if (a === b) return p;
    const tb = b.split(" ").filter((x) => x.length >= 2);
    const [menor, maior] = ta.length <= tb.length ? [ta, tb] : [tb, ta];
    if (menor.length >= 2 && menor.every((x) => maior.includes(x))) return p;
  }
  return null;
}

const PALAVRAS_COMUNS = new Set(["pix", "para", "enviado", "recebido", "transferencia", "pagamento", "ted", "doc"]);

function tokensNome(s: string): string[] {
  return tokensDescricao(s.replace(/\(ID [^)]*\)/gi, " ")).filter((t) => !PALAVRAS_COMUNS.has(t));
}

export interface EntradaDuplicata {
  /** Número da conta (contas.conta). */
  conta: string;
  tipo: "DEBITO" | "CREDITO";
  valorCentavos: number;
  /** epoch ms */
  data: number;
  descricao: string;
  /** Lançamento que está sendo editado (ignorado). */
  ignorarId?: number | null;
}

/**
 * Procura lançamento provavelmente já existente: mesma conta (sem cartão), mesmo tipo e valor, data com no máximo 1 dia de diferença e
 * descrição parecida (compartilha o nome/token) ou mesmo ID curto "(ID ...)". O mesmo ID na mesma conta já basta (mesmo com outro valor/data).
 */
export function acharDuplicataPix(despesas: readonly Despesa[], e: EntradaDuplicata): Despesa | null {
  const id = idCurtoDaDescricao(e.descricao);
  const meus = tokensNome(e.descricao);
  const dia = inicioDoDia(e.data);
  let achada: Despesa | null = null;
  for (const d of despesas) {
    if (d.id === e.ignorarId || d.conta !== e.conta || d.cartaoId !== null || d.natureza !== "NORMAL") continue;
    if (id && idCurtoDaDescricao(d.descricao) === id) return d;
    if (d.tipo !== e.tipo || toCents(d.valor) !== e.valorCentavos) continue;
    if (Math.abs(Math.round((inicioDoDia(d.data) - dia) / 86_400_000)) > 1) continue;
    const dele = tokensNome(d.descricao);
    const parecida = (meus.length === 0 && dele.length === 0) || meus.some((t) => dele.includes(t)) || jaccard(meus, dele) >= 0.5;
    if (parecida && (!achada || d.id > achada.id)) achada = d;
  }
  return achada;
}

// ---------------------------------------------------------------------------------------------------------------------
// Apoio ao formulário (puro)
// ---------------------------------------------------------------------------------------------------------------------

export interface LinhaResumoPix {
  rotulo: string;
  valor: string;
}

function brl(c: number): string {
  return `R$ ${(c / 100).toFixed(2).replace(".", ",").replace(/\B(?=(\d{3})+(?!\d),)/g, ".")}`;
}

function brData(ms: number): string {
  const d = new Date(ms);
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}/${d.getFullYear()}`;
}

/** Linhas para mostrar o que foi lido antes de confirmar. A chave sai mascarada; o ID aparece (é identificador de transação, não segredo). */
export function resumoPix(l: Extract<LeituraPix, { ok: true }>): LinhaResumoPix[] {
  const out: LinhaResumoPix[] = [];
  const add = (rotulo: string, valor: string | undefined) => valor && out.push({ rotulo, valor });
  if (l.origem === "BRCODE") {
    const b = l.brCode;
    add("Origem", b.dinamico ? "Pix copia e cola (QR dinâmico)" : "Pix copia e cola (QR estático)");
    add("Beneficiário", b.beneficiario);
    add("Valor", b.valorCentavos !== undefined ? brl(b.valorCentavos) : "não informado no código (digite)");
    add("Chave", b.chave ? mascararChave(b.chave) : undefined);
    add("Cidade", b.cidade);
    add("Mensagem", b.mensagem);
    add("Identificador", b.txid);
    add("Endereço do QR", b.urlPayload ? `${b.urlPayload} (só exibido; não é acessado)` : undefined);
  } else {
    const c = l.comprovante;
    add("Origem", "Texto de comprovante");
    add("Direção", c.direcao === "RECEBIDO" ? "Pix recebido (crédito)" : c.direcao === "ENVIADO" ? "Pix enviado (débito)" : "não identificada (assumido pela escolha do formulário)");
    add(c.direcao === "RECEBIDO" ? "Quem enviou" : "Quem recebeu", c.contraparte);
    add("Valor", c.valorCentavos !== undefined ? brl(c.valorCentavos) : undefined);
    add("Data", c.data !== undefined ? brData(c.data) : undefined);
    add("Banco", c.banco);
    add("Chave", c.chave ? mascararChave(c.chave) : undefined);
    add("Mensagem", c.mensagem);
    add("Identificador", c.idTransacao);
  }
  return out;
}

export interface CamposAtuais {
  descricao: string;
  valorCentavos: number | null;
  /** epoch ms ou null se o campo está vazio/inválido. */
  data: number | null;
  /** Data que o formulário traz sozinha ao abrir (hoje): trocar por outra não conta como sobrescrita. */
  dataPadrao?: number | null;
}

/** Campos já preenchidos pelo usuário que a leitura mudaria (para avisar antes de aplicar). */
export function camposSobrescritos(r: RascunhoPix, atual: CamposAtuais): string[] {
  const out: string[] = [];
  if (atual.descricao.trim() && atual.descricao.trim() !== r.descricao) out.push("descrição");
  if (r.valorCentavos !== undefined && atual.valorCentavos && atual.valorCentavos !== r.valorCentavos) out.push("valor");
  const mesmoDia = (a: number, b: number) => inicioDoDia(a) === inicioDoDia(b);
  if (r.data !== undefined && atual.data !== null && !mesmoDia(atual.data, r.data) && !(atual.dataPadrao != null && mesmoDia(atual.data, atual.dataPadrao))) {
    out.push("data");
  }
  return out;
}
