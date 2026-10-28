/**
 * R65 - regras PURAS do web push (sem Firebase/DOM): estado do recurso, preferências e documento do dispositivo.
 * O documento é `users/<uid>/dispositivos/<id>` (uid SEMPRE o do usuário logado); `atualizadoEm` (serverTimestamp) é
 * acrescentado por quem grava. Mesmo formato do app Android (ver docs/CONTRATO_DADOS.md, R65).
 */

export interface PrefsPush {
  vencimentos: boolean;
  orcamento: boolean;
  resumoSemanal: boolean;
  convites: boolean;
}

export const PREFS_PADRAO: PrefsPush = { vencimentos: true, orcamento: true, resumoSemanal: true, convites: true };

export const ROTULO_PREFS: readonly { chave: keyof PrefsPush; rotulo: string }[] = [
  { chave: "vencimentos", rotulo: "Vencimentos (contas a pagar amanhã ou em atraso)" },
  { chave: "orcamento", rotulo: "Orçamento (categoria em 80% ou estourada)" },
  { chave: "resumoSemanal", rotulo: "Resumo semanal (domingo, 18h)" },
  { chave: "convites", rotulo: "Convites para compartilhar uma conta" },
];

/** Leitura tolerante (JSON do localStorage): ausente/inválido = padrão (ligado). */
export function lerPrefs(bruto: unknown): PrefsPush {
  const o = typeof bruto === "object" && bruto !== null ? (bruto as Record<string, unknown>) : {};
  const b = (k: keyof PrefsPush) => (typeof o[k] === "boolean" ? (o[k] as boolean) : true);
  return { vencimentos: b("vencimentos"), orcamento: b("orcamento"), resumoSemanal: b("resumoSemanal"), convites: b("convites") };
}

export type PermissaoNavegador = "granted" | "denied" | "default";

export type EstadoPush =
  /** sem VAPID, navegador sem suporte, sem login/nuvem: o recurso fica OCULTO */
  | "oculto"
  /** ainda não pediu permissão ("Ativar notificações neste navegador") */
  | "sem-permissao"
  /** o usuário bloqueou no navegador */
  | "bloqueado"
  /** permissão concedida, mas este navegador não está registrado (ou foi desativado) */
  | "inativo"
  | "ativo";

export interface EntradaEstado {
  vapidConfigurada: boolean;
  suportado: boolean;
  nuvemEUsuario: boolean;
  permissao: PermissaoNavegador;
  /** o usuário ligou o recurso neste navegador (flag local) */
  ligado: boolean;
}

export function estadoPush(e: EntradaEstado): EstadoPush {
  if (!e.vapidConfigurada || !e.suportado || !e.nuvemEUsuario) return "oculto";
  if (e.permissao === "denied") return "bloqueado";
  if (e.permissao === "default") return "sem-permissao";
  return e.ligado ? "ativo" : "inativo";
}

/** Registrar o token só com: recurso ligado + permissão concedida + login na nuvem (opt-in, padrão desligado). */
export function deveRegistrarWeb(e: EntradaEstado): boolean {
  return estadoPush(e) === "ativo";
}

const ID_VALIDO = /^[A-Za-z0-9_-]{8,64}$/;

/** ID estável do navegador (nome do documento); reaproveita o salvo se válido. */
export function idEstavelWeb(salvo: string | null, gerarUuid: () => string): string {
  const s = (salvo ?? "").trim();
  return ID_VALIDO.test(s) ? s : `web-${gerarUuid().replace(/-/g, "").slice(0, 32)}`;
}

/** Documento do dispositivo web (sem `atualizadoEm`). Dados mínimos: nada de user-agent nem e-mail. */
export function montarDocDispositivoWeb(token: string, versao: string, prefs: PrefsPush, raizUid: string | null, uid: string): Record<string, unknown> {
  if (!token.trim()) throw new Error("Token vazio");
  const doc: Record<string, unknown> = {
    token,
    plataforma: "web",
    modelo: "Navegador",
    appVersao: versao.slice(0, 40),
    prefs: { ...prefs },
  };
  if (raizUid && raizUid !== uid) doc.raizUid = raizUid;
  return doc;
}

/** Mensagem do estado para a UI. */
export function textoDoEstado(s: EstadoPush): string {
  switch (s) {
    case "bloqueado":
      return "As notificações estão bloqueadas neste navegador. Libere nas configurações do site (cadeado ao lado do endereço) e volte aqui.";
    case "sem-permissao":
      return "Receba avisos de vencimentos, orçamento e resumo semanal neste navegador. O navegador vai pedir permissão.";
    case "inativo":
      return "A permissão existe, mas este navegador não está registrado para receber avisos.";
    case "ativo":
      return "Ativo neste navegador. Os avisos não mostram valores.";
    default:
      return "";
  }
}

/**
 * O que o service worker mostra a partir do payload de dados do FCM (mesma lógica do `public/firebase-messaging-sw.js`, que
 * não pode importar módulos). Sempre genérico: sem valores. Tipo desconhecido/sem título = null (ignora).
 */
export function textoDaNotificacaoWeb(data: Record<string, string> | undefined): { titulo: string; corpo: string; tag: string } | null {
  if (!data) return null;
  const tipos = ["VENCIMENTOS", "ORCAMENTO", "RESUMO_SEMANAL", "CONVITE"];
  if (!tipos.includes(data.tipo ?? "")) return null;
  const titulo = (data.titulo ?? "").trim().slice(0, 80);
  if (!titulo) return null;
  return { titulo, corpo: (data.corpo ?? "").trim().slice(0, 200), tag: `meudinheiro-${data.tipo}` };
}
