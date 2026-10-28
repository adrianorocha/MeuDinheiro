import type { Dispositivo, PrefsPush, TipoPush } from "./tipos";

const CHAVE_PREF: Record<TipoPush, keyof PrefsPush> = {
  VENCIMENTOS: "vencimentos",
  ORCAMENTO: "orcamento",
  RESUMO_SEMANAL: "resumoSemanal",
  CONVITE: "convites",
};

/** Lê `users/{uid}/dispositivos/{id}`. Sem token utilizável = null. `prefs` ausente/ilegível = tudo ligado. */
export function lerDispositivo(uid: string, id: string, d: Record<string, unknown>): Dispositivo | null {
  const token = typeof d.token === "string" ? d.token.trim() : "";
  if (token.length < 20 || token.length > 4096) return null;
  const plataforma = d.plataforma === "web" ? "web" : d.plataforma === "android" ? "android" : null;
  if (!plataforma) return null;
  const p = typeof d.prefs === "object" && d.prefs !== null ? (d.prefs as Record<string, unknown>) : {};
  const prefs: PrefsPush = {};
  for (const k of ["vencimentos", "orcamento", "resumoSemanal", "convites"] as const) {
    if (typeof p[k] === "boolean") prefs[k] = p[k];
  }
  const raiz = typeof d.raizUid === "string" && d.raizUid.trim() ? d.raizUid.trim() : undefined;
  const ts = d.atualizadoEm as { toMillis?: () => number } | number | undefined;
  const atualizadoEmMs = typeof ts === "number" ? ts : typeof ts?.toMillis === "function" ? ts.toMillis() : undefined;
  return { id, uid, token, plataforma, prefs, raizUid: raiz, atualizadoEmMs };
}

/** Respeita as preferências: só `false` explícito desliga o tipo. */
export function permiteTipo(d: Dispositivo, tipo: TipoPush): boolean {
  return d.prefs[CHAVE_PREF[tipo]] !== false;
}

export function filtrarPorPrefs(ds: readonly Dispositivo[], tipo: TipoPush): Dispositivo[] {
  return ds.filter((d) => permiteTipo(d, tipo));
}

/** Conta de dados que o dispositivo diz usar (a própria, se não informar). */
export function raizDoDispositivo(d: Dispositivo): string {
  return d.raizUid ?? d.uid;
}

/**
 * SEGURANÇA: o dispositivo só recebe avisos de uma conta de dados se for a própria conta ou se o uid dele for MEMBRO dela.
 * (O campo `raizUid` é escrito pelo próprio cliente; sem esta conferência um usuário poderia apontar para a conta de outro.)
 */
export function raizAutorizada(d: Dispositivo, membrosDaRaiz: ReadonlySet<string>): boolean {
  return raizDoDispositivo(d) === d.uid || membrosDaRaiz.has(d.uid);
}

/** Dispositivos que devem ser avisados sobre a conta [raiz], já com a conferência de vínculo. */
export function dispositivosDaRaiz(raiz: string, todos: readonly Dispositivo[], membros: ReadonlySet<string>): Dispositivo[] {
  return todos.filter((d) => raizDoDispositivo(d) === raiz && raizAutorizada(d, membros));
}

const CODIGOS_TOKEN_INVALIDO = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

/** Dado o resultado do envio (um código de erro por dispositivo, na mesma ordem), devolve os que devem ser apagados. */
export function dispositivosParaRemover(ds: readonly Dispositivo[], codigos: readonly (string | undefined)[]): Dispositivo[] {
  return ds.filter((_, i) => {
    const c = codigos[i];
    return c !== undefined && CODIGOS_TOKEN_INVALIDO.has(c);
  });
}

export const VALIDADE_DISPOSITIVO_DIAS = 90;

/** Sem atualizar há mais de 90 dias = abandonado (o app regrava o token sempre que abre/renova). Sem carimbo = mantém. */
export function dispositivoObsoleto(d: Dispositivo, agoraMs: number, dias = VALIDADE_DISPOSITIVO_DIAS): boolean {
  return d.atualizadoEmMs !== undefined && agoraMs - d.atualizadoEmMs > dias * 86_400_000;
}

/** FCM aceita até 500 tokens por envio. */
export function lotes<T>(itens: readonly T[], tamanho = 500): T[][] {
  const r: T[][] = [];
  for (let i = 0; i < itens.length; i += tamanho) r.push(itens.slice(i, i + tamanho));
  return r;
}

/** Agrupa por conta de dados em uso (a própria quando não informada). */
export function agruparPorRaiz(ds: readonly Dispositivo[]): Map<string, Dispositivo[]> {
  const m = new Map<string, Dispositivo[]>();
  for (const d of ds) {
    const r = raizDoDispositivo(d);
    (m.get(r) ?? m.set(r, []).get(r)!).push(d);
  }
  return m;
}
