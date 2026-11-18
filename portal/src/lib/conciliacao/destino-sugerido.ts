import type { Cartao, Conta } from "../finance/types";
import type { Destino } from "./tipos";

/**
 * R63 - identificação automática do destino (conta/cartão) de um extrato por heurística PURA.
 * Não altera `sugerirDestino` (R36): quem chama decide o que fazer com UNICO/AMBIGUO/NENHUM.
 * O espelho em Kotlin é `domain/DestinoSugerido.kt` (mesmas regras e mesmos casos de teste).
 */
export interface PistasArquivo {
  /** ACCTID do OFX */
  acctId?: string;
  /** BANKID do OFX (código COMPE) */
  bankId?: string;
  /** ORG do OFX (instituição financeira) */
  org?: string;
  /** OFX de cartão: true/false; desconhecido (CSV/PDF): undefined */
  ehCartao?: boolean;
  nomeArquivo?: string;
  /** primeiras linhas do arquivo (CSV/PDF em texto) */
  cabecalho?: string;
}

export interface CandidatoDestino {
  destino: Destino;
  score: number;
  motivo: string;
}

export type Identificacao = { estado: "UNICO" | "AMBIGUO" | "NENHUM"; destino: Destino | null; candidatos: CandidatoDestino[] };

type ContaMin = Pick<Conta, "id" | "banco" | "agencia" | "conta">;
type CartaoMin = Pick<Cartao, "id" | "nome" | "finalCartao" | "contaId" | "cartaoPrincipalId">;

/** Código COMPE -> palavras que o identificam em nomes de banco. */
const BANCOS: Record<string, string[]> = {
  "001": ["banco do brasil", "bb"],
  "033": ["santander"],
  "041": ["banrisul"],
  "070": ["brb"],
  "077": ["inter", "banco inter"],
  "104": ["caixa", "caixa economica"],
  "212": ["original"],
  "237": ["bradesco"],
  "260": ["nubank", "nu pagamentos", "nu"],
  "290": ["pagbank", "pagseguro"],
  "323": ["mercado pago", "mercadopago"],
  "336": ["c6", "c6 bank"],
  "341": ["itau", "itau unibanco"],
  "380": ["picpay"],
  "422": ["safra"],
  "655": ["votorantim", "neon"],
  "748": ["sicredi"],
  "756": ["sicoob"],
};

export function normalizar(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

const soDigitos = (s: string | undefined) => (s ?? "").replace(/\D/g, "");
const contem = (hay: string, palavra: string) => ` ${hay} `.includes(` ${palavra} `);

/** Códigos de banco cujas palavras aparecem (como palavra inteira) no texto normalizado. */
function bancosNoTexto(norm: string): Set<string> {
  const r = new Set<string>();
  for (const [cod, palavras] of Object.entries(BANCOS)) if (palavras.some((p) => contem(norm, p))) r.add(cod);
  return r;
}

/** Extrai BANKID/ORG/ACCTID/tipo de um OFX bruto (dado não confiável: só regex, sem executar nada). */
export function pistasDoOfx(texto: string): Pick<PistasArquivo, "acctId" | "bankId" | "org" | "ehCartao"> {
  const tag = (nome: string) => {
    const m = new RegExp(`<${nome}>\\s*([^<\\r\\n]{1,80})`, "i").exec(texto);
    return m ? m[1].trim() || undefined : undefined;
  };
  return { acctId: tag("ACCTID"), bankId: tag("BANKID"), org: tag("ORG"), ehCartao: /<CREDITCARDMSGSRSV1>|<CCSTMTRS>|<CCACCTFROM>/i.test(texto) };
}

/** Pistas de qualquer arquivo de texto: OFX traz as suas; CSV/PDF só nome do arquivo e cabeçalho. */
export function pistasDoArquivo(nome: string, texto: string): PistasArquivo {
  const ini = texto.slice(0, 2000);
  const ehOfx = /OFXHEADER|<OFX|<STMTTRN>/i.test(ini);
  return ehOfx ? { ...pistasDoOfx(texto), nomeArquivo: nome } : { nomeArquivo: nome, cabecalho: ini.slice(0, 600) };
}

/** Grupos de dígitos (>= 4) soltos no texto, p.ex. "fatura_4321.csv" ou "conta 12345-6". */
function numerosNoTexto(s: string): string[] {
  return (s.match(/\d[\d.-]{2,}\d/g) ?? []).map(soDigitos).filter((d) => d.length >= 4);
}

/** Heurística de conta/cartão. Nunca decide sozinha quando há empate: devolve AMBIGUO com os candidatos. */
export function identificarDestino(ctx: { contas: ContaMin[]; cartoes: CartaoMin[] }, p: PistasArquivo): Identificacao {
  const textoLivre = normalizar(`${p.nomeArquivo ?? ""} ${p.cabecalho ?? ""}`);
  const numerosLivres = numerosNoTexto(`${p.nomeArquivo ?? ""} ${p.cabecalho ?? ""}`);
  const ehCartao = p.ehCartao ?? (/\b(fatura|cartao)\b/.test(textoLivre) ? true : undefined);

  // bancos citados pelo arquivo
  const bancosArq = bancosNoTexto(normalizar(`${p.org ?? ""} ${textoLivre}`));
  const cod = soDigitos(p.bankId);
  if (cod) bancosArq.add(cod.padStart(3, "0"));
  const hayBanco = normalizar(`${p.org ?? ""} ${textoLivre}`);
  const mesmoBanco = (nomeBanco: string): boolean => {
    const n = normalizar(nomeBanco);
    if (!n) return false;
    for (const c of bancosNoTexto(n)) if (bancosArq.has(c)) return true;
    return n.length >= 3 && contem(hayBanco, n);
  };

  const acct = soDigitos(p.acctId);
  const candidatos: CandidatoDestino[] = [];

  if (ehCartao !== true) {
    for (const c of ctx.contas) {
      const num = soDigitos(c.conta);
      let score = 0;
      let motivo = "";
      if (num.length >= 3 && acct && num === acct) { score = 100; motivo = "número da conta igual ao ACCTID"; }
      else if (num.length >= 4 && acct.length >= 4 && (acct.endsWith(num) || num.endsWith(acct))) { score = 70; motivo = "número da conta parecido com o ACCTID"; }
      else if (num.length >= 4 && numerosLivres.some((n) => n === num)) { score = 60; motivo = "número da conta no nome/cabeçalho do arquivo"; }
      if (mesmoBanco(c.banco)) { score += 25; motivo = motivo ? `${motivo}; banco confere` : "banco confere"; }
      if (score > 0) candidatos.push({ destino: { tipo: "CONTA", conta: c.conta }, score, motivo });
    }
  }
  if (ehCartao !== false) {
    for (const k of ctx.cartoes) {
      if (k.cartaoPrincipalId != null) continue;
      const fim = soDigitos(k.finalCartao);
      let score = 0;
      let motivo = "";
      if (fim.length >= 4 && acct.endsWith(fim)) { score = 80; motivo = "final do cartão igual ao do ACCTID"; }
      else if (fim.length >= 4 && numerosLivres.some((n) => n === fim || n.endsWith(fim))) { score = 60; motivo = "final do cartão no nome/cabeçalho do arquivo"; }
      const conta = ctx.contas.find((c) => c.id === k.contaId);
      if (ehCartao === true && (mesmoBanco(k.nome) || (conta && mesmoBanco(conta.banco)))) { score += 25; motivo = motivo ? `${motivo}; banco confere` : "banco confere"; }
      if (score > 0) candidatos.push({ destino: { tipo: "CARTAO", cartaoId: k.id }, score, motivo });
    }
  }

  candidatos.sort((a, b) => b.score - a.score);
  if (candidatos.length === 0) return { estado: "NENHUM", destino: null, candidatos };
  const [topo, segundo] = candidatos;
  const unico = !segundo || (topo.score >= 60 && topo.score - segundo.score >= 20);
  return unico ? { estado: "UNICO", destino: topo.destino, candidatos } : { estado: "AMBIGUO", destino: null, candidatos };
}
