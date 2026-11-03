import { dispositivosDaRaiz } from "./dispositivos";
import type { Dispositivo } from "./tipos";

/** R86 - "sinal" de sincronização: push silencioso (só dados) que acorda o app para puxar o que o portal gravou. */
export const TIPO_SINAL = "SYNC";

/** Validade curta: um sinal atrasado não vale nada (o app já sincroniza ao abrir). `collapseKey` junta sinais pendentes em um só. */
export const TTL_SINAL_MS = 2 * 60_000;

export interface MensagemSinalFcm {
  tokens: string[];
  data: Record<string, string>;
  android: { priority: "high"; ttl: number; collapseKey: string };
}

/** Só aparelhos Android da conta (o portal já recebe em tempo real pelo onSnapshot) e só os autorizados (dono ou membro). */
export function dispositivosParaSinal(raiz: string, todos: readonly Dispositivo[], membros: ReadonlySet<string>): Dispositivo[] {
  return dispositivosDaRaiz(raiz, todos, membros).filter((d) => d.plataforma === "android");
}

export function montarMensagemSinal(ds: readonly Dispositivo[], origem: string, ttlMs = TTL_SINAL_MS): MensagemSinalFcm {
  return {
    tokens: [...new Set(ds.map((d) => d.token))],
    data: { tipo: TIPO_SINAL, origem: origem.slice(0, 20) },
    android: { priority: "high", ttl: ttlMs, collapseKey: "sync" },
  };
}

/**
 * O sinal só nasce de `users/{raiz}/sinais/portal` (escrito pelo portal depois de gravar dados). Qualquer outro id é ignorado:
 * o app não precisa avisar o portal (onSnapshot) e ninguém deve conseguir disparar pushes por um id arbitrário.
 */
export function origemDoSinal(idDoDocumento: string): string | null {
  return idDoDocumento === "portal" ? "portal" : null;
}
