import type { PapelMembro } from "./permissoes";
import { PAPEIS_ATRIBUIVEIS } from "./permissoes";

/**
 * R67 - código de convite (8 caracteres, uso único, validade de 7 dias). Funções puras (o sorteio é injetável).
 * Espelho de `app/.../domain/ConviteCodigo.kt`; o formato também é validado em `portal/firestore.rules`.
 */

/** Sem 0/O/1/I/L (ambíguos): 23 letras + 8 dígitos (2..9) = 31 símbolos. */
export const ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export const TAMANHO_CODIGO = 8;
export const VALIDADE_CONVITE_MS = 7 * 24 * 60 * 60 * 1000;

export type EstadoConvite = "VALIDO" | "EXPIRADO" | "USADO" | "INVALIDO";

/** `sorteio(n)` devolve um inteiro em [0, n). Padrão: crypto.getRandomValues com rejeição (sem viés). */
export function gerarCodigo(sorteio: (n: number) => number = sorteioSeguro): string {
  let s = "";
  for (let i = 0; i < TAMANHO_CODIGO; i++) s += ALFABETO[sorteio(ALFABETO.length)];
  return s;
}

function sorteioSeguro(n: number): number {
  const limite = Math.floor(256 / n) * n; // descarta o resto para não enviesar
  const buf = new Uint8Array(1);
  for (;;) {
    crypto.getRandomValues(buf);
    if (buf[0] < limite) return buf[0] % n;
  }
}

/** Aceita minúsculas, espaços e hífen ("abcd-2345" -> "ABCD2345"). */
export function normalizarCodigo(texto: string | null | undefined): string {
  return (texto ?? "").toUpperCase().replace(/[\s-]/g, "");
}

export function codigoValido(codigo: string | null | undefined): boolean {
  return typeof codigo === "string" && codigo.length === TAMANHO_CODIGO && [...codigo].every((c) => ALFABETO.includes(c));
}

export function conviteExpirado(expiraEm: number, agora: number): boolean {
  return agora >= expiraEm;
}

/** Situação de um convite lido do Firestore. Usado tem precedência sobre expirado. */
export function estadoDoConvite(usado: boolean | null | undefined, expiraEm: number | null | undefined, agora: number): EstadoConvite {
  if (typeof usado !== "boolean" || typeof expiraEm !== "number") return "INVALIDO";
  if (usado) return "USADO";
  if (conviteExpirado(expiraEm, agora)) return "EXPIRADO";
  return "VALIDO";
}

export function mensagemDoEstado(e: EstadoConvite): string | null {
  switch (e) {
    case "VALIDO":
      return null;
    case "EXPIRADO":
      return "Este convite expirou. Peça um código novo a quem convidou.";
    case "USADO":
      return "Este convite já foi usado. Peça um código novo a quem convidou.";
    case "INVALIDO":
      return "Código de convite inválido. Confira os 8 caracteres.";
  }
}

/** Documento `convites/<codigo>` (campos do contrato R67); `donoEmail`/`donoNome` são só para exibição. */
export interface ConviteDoc {
  donoUid: string;
  papel: PapelMembro;
  criadoEm: number;
  expiraEm: number;
  usado: boolean;
  criadoPor: string;
  donoEmail?: string;
  donoNome?: string;
  usadoPor?: string;
  usadoEm?: number;
}

export function novoConviteDoc(donoUid: string, papel: PapelMembro, criadoPor: string, agora: number, exibicao: { donoEmail?: string; donoNome?: string } = {}): ConviteDoc {
  if (!PAPEIS_ATRIBUIVEIS.includes(papel)) throw new Error("Papel inválido para convite.");
  return {
    donoUid,
    papel,
    criadoEm: agora,
    expiraEm: agora + VALIDADE_CONVITE_MS,
    usado: false,
    criadoPor,
    ...(exibicao.donoEmail ? { donoEmail: exibicao.donoEmail.trim().toLowerCase() } : {}),
    ...(exibicao.donoNome?.trim() ? { donoNome: exibicao.donoNome.trim().slice(0, 80) } : {}),
  };
}
