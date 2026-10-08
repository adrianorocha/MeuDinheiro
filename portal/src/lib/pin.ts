/** R34 - PIN de 4–8 dígitos guardado como hash PBKDF2-SHA256 (nunca em texto). */

export interface PinGuardado {
  salt: string;
  hash: string;
  iteracoes: number;
}

export const ITERACOES_PIN = 120_000;
export const INATIVIDADE_PADRAO_MIN = 5;

export function pinValido(pin: string): boolean {
  return /^\d{4,8}$/.test(pin);
}

function paraB64(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s);
}

function deB64(b64: string): Uint8Array<ArrayBuffer> {
  const s = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(s.length));
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

async function derivar(pin: string, salt: Uint8Array<ArrayBuffer>, iteracoes: number): Promise<string> {
  const chave = await crypto.subtle.importKey("raw", new TextEncoder().encode(pin), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: iteracoes }, chave, 256);
  return paraB64(new Uint8Array(bits));
}

export async function criarPin(pin: string, iteracoes: number = ITERACOES_PIN): Promise<PinGuardado> {
  if (!pinValido(pin)) throw new Error("O PIN deve ter de 4 a 8 dígitos.");
  const salt = crypto.getRandomValues(new Uint8Array(new ArrayBuffer(16)));
  return { salt: paraB64(salt), hash: await derivar(pin, salt, iteracoes), iteracoes };
}

export async function verificarPin(pin: string, guardado: PinGuardado): Promise<boolean> {
  if (!pinValido(pin)) return false;
  const h = await derivar(pin, deB64(guardado.salt), guardado.iteracoes);
  // comparação em tempo constante
  if (h.length !== guardado.hash.length) return false;
  let diff = 0;
  for (let i = 0; i < h.length; i++) diff |= h.charCodeAt(i) ^ guardado.hash.charCodeAt(i);
  return diff === 0;
}

export function lerPinGuardado(texto: string | null): PinGuardado | null {
  if (!texto) return null;
  try {
    const o = JSON.parse(texto) as Partial<PinGuardado>;
    return typeof o.salt === "string" && typeof o.hash === "string" && typeof o.iteracoes === "number" ? (o as PinGuardado) : null;
  } catch {
    return null;
  }
}

/** Bloqueia quando passou `minutos` sem atividade (0 desativa o bloqueio por inatividade). */
export function deveBloquear(ultimaAtividade: number, agora: number, minutos: number): boolean {
  return minutos > 0 && agora - ultimaAtividade >= minutos * 60_000;
}
