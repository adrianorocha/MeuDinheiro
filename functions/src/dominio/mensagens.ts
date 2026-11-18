import type { Dispositivo, MensagemPush } from "./tipos";

/** Mensagem FCM de dados (SEM bloco `notification`: o app/SW decide canal e privacidade). Estrutura compatível com MulticastMessage. */
export interface MensagemFcm {
  tokens: string[];
  data: Record<string, string>;
  android: { priority: "high" | "normal"; ttl: number };
  webpush: { headers: { Urgency: string; TTL: string } };
}

const TTL_PADRAO_MS = 12 * 3_600_000;

/** Corpo FCM. `data` só aceita strings; `titulo`/`corpo` são cortados para o limite seguro (4 KB no total do FCM). */
export function montarMensagemFcm(m: MensagemPush, ds: readonly Dispositivo[], ttlMs = TTL_PADRAO_MS): MensagemFcm {
  const data: Record<string, string> = {};
  for (const [k, v] of Object.entries(m.extra)) data[k] = String(v).slice(0, 200);
  data.tipo = m.tipo;
  data.titulo = m.titulo.slice(0, 80);
  data.corpo = m.corpo.slice(0, 200);
  return {
    tokens: [...new Set(ds.map((d) => d.token))],
    data,
    android: { priority: "high", ttl: ttlMs },
    webpush: { headers: { Urgency: "high", TTL: String(Math.floor(ttlMs / 1000)) } },
  };
}
