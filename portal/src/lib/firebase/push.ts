import { deleteDoc, doc, serverTimestamp, setDoc } from "firebase/firestore";
import { ler, gravar } from "../session";
import {
  idEstavelWeb,
  lerPrefs,
  montarDocDispositivoWeb,
  type PermissaoNavegador,
  type PrefsPush,
} from "../push";
import { getDb, getFirebaseApp } from "./client";

/**
 * R65 - web push (FCM) opcional. Fica OCULTO sem `NEXT_PUBLIC_FIREBASE_VAPID_KEY` (chave pública de "Web Push certificates"
 * no console Firebase > Cloud Messaging). O token vai a `users/<uid>/dispositivos/<id>` (uid do usuário logado, plataforma
 * "web"). O service worker `public/firebase-messaging-sw.js` só mostra o aviso; nada é guardado em cache.
 */
const VAPID = process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY ?? "";
const SW_URL = "/firebase-messaging-sw.js";
const VERSAO = "portal";

const CHAVE_ID = "meudinheiro:push:id";
const CHAVE_PREFS = "meudinheiro:push:prefs";
const chaveLigado = (uid: string) => `meudinheiro:push:ligado:${uid}`;

export const vapidConfigurada = (): boolean => VAPID.length > 0;

/** Suporte básico do navegador (service worker + Push + Notification). Só no cliente. */
export function navegadorSuportaPush(): boolean {
  return typeof window !== "undefined" && "Notification" in window && "serviceWorker" in navigator && "PushManager" in window;
}

export function permissaoAtual(): PermissaoNavegador {
  return typeof Notification === "undefined" ? "default" : Notification.permission;
}

export const pushLigado = (uid: string): boolean => ler(chaveLigado(uid)) === "1";

export function prefsLocais(): PrefsPush {
  try {
    return lerPrefs(JSON.parse(ler(CHAVE_PREFS) ?? "{}"));
  } catch {
    return lerPrefs({});
  }
}

export function salvarPrefsLocais(p: PrefsPush): void {
  gravar(CHAVE_PREFS, JSON.stringify(p));
}

function idDoNavegador(): string {
  const atual = ler(CHAVE_ID);
  const id = idEstavelWeb(atual, () => crypto.randomUUID());
  if (id !== atual) gravar(CHAVE_ID, id);
  return id;
}

const refDispositivo = (uid: string) => doc(getDb(), "users", uid, "dispositivos", idDoNavegador());

async function obterToken(): Promise<string | null> {
  const { getMessaging, getToken, isSupported } = await import("firebase/messaging");
  if (!(await isSupported())) return null;
  const registro = await navigator.serviceWorker.register(SW_URL);
  await navigator.serviceWorker.ready;
  const token = await getToken(getMessaging(getFirebaseApp()), { vapidKey: VAPID, serviceWorkerRegistration: registro });
  return token || null;
}

/** Grava/atualiza o documento do dispositivo (token + prefs + raiz de dados em uso). Devolve false se o FCM não estiver disponível. */
async function registrar(uid: string, raizUid: string | null, prefs: PrefsPush): Promise<boolean> {
  const token = await obterToken();
  if (!token) return false;
  await setDoc(refDispositivo(uid), { ...montarDocDispositivoWeb(token, VERSAO, prefs, raizUid, uid), atualizadoEm: serverTimestamp() });
  return true;
}

export type ResultadoAtivar = "ok" | "negado" | "indisponivel";

/** Botão "Ativar notificações neste navegador": pede permissão (só por gesto do usuário), registra e liga o recurso. */
export async function ativarPush(uid: string, raizUid: string | null, prefs: PrefsPush): Promise<ResultadoAtivar> {
  if (!vapidConfigurada() || !navegadorSuportaPush()) return "indisponivel";
  const perm = permissaoAtual() === "granted" ? "granted" : await Notification.requestPermission();
  if (perm !== "granted") return "negado";
  try {
    if (!(await registrar(uid, raizUid, prefs))) return "indisponivel";
  } catch {
    return "indisponivel";
  }
  gravar(chaveLigado(uid), "1");
  return "ok";
}

/** Desliga: apaga o documento e invalida o token. */
export async function desativarPush(uid: string): Promise<void> {
  gravar(chaveLigado(uid), null);
  await removerRegistro(uid);
}

async function removerRegistro(uid: string): Promise<void> {
  await deleteDoc(refDispositivo(uid)).catch(() => undefined);
  try {
    const { getMessaging, deleteToken, isSupported } = await import("firebase/messaging");
    if (await isSupported()) await deleteToken(getMessaging(getFirebaseApp()));
  } catch {
    // sem FCM/ sem token: nada a invalidar
  }
}

/** Logout: só mexe no Firestore se este navegador tinha o recurso ligado para o uid. */
export async function removerPushAoSair(uid: string): Promise<void> {
  if (!pushLigado(uid)) return;
  gravar(chaveLigado(uid), null);
  await Promise.race([removerRegistro(uid), new Promise<void>((r) => setTimeout(r, 4000))]);
}

/** Mantém o token/prefs atualizados (o servidor descarta aparelhos sem renovação há 90 dias). Silencioso. */
export async function sincronizarPush(uid: string, raizUid: string | null): Promise<void> {
  if (!vapidConfigurada() || !navegadorSuportaPush() || !pushLigado(uid) || permissaoAtual() !== "granted") return;
  try {
    await registrar(uid, raizUid, prefsLocais());
  } catch {
    // offline/FCM indisponível: tenta de novo na próxima abertura
  }
}
