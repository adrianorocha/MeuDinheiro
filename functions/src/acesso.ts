/**
 * Acesso ao Firestore/FCM (firebase-admin). Mantido FINO de propósito: toda decisão fica em `dominio/` (puro e testado).
 * O Admin SDK ignora as regras de segurança; por isso o vínculo dispositivo×conta é conferido em `dominio/dispositivos`.
 */
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";
import { dispositivosParaRemover, lerDispositivo, lotes } from "./dominio/dispositivos";
import { montarMensagemFcm } from "./dominio/mensagens";
import { montarMensagemSinal } from "./dominio/sinal";
import { lerDespesa, type DespesaDoc, type Dispositivo, type MensagemPush } from "./dominio/tipos";

function db(): Firestore {
  if (getApps().length === 0) initializeApp();
  return getFirestore();
}

export { db };

/** `users/{uid}/dispositivos/*` de um usuário. */
export async function dispositivosDoUsuario(uid: string): Promise<Dispositivo[]> {
  const snap = await db().collection("users").doc(uid).collection("dispositivos").get();
  return snap.docs.map((d) => lerDispositivo(uid, d.id, d.data())).filter((d): d is Dispositivo => d !== null);
}

/** Todos os dispositivos de todos os usuários (collection group). Uso pessoal: poucas dezenas de documentos. */
export async function todosOsDispositivos(): Promise<Dispositivo[]> {
  const snap = await db().collectionGroup("dispositivos").get();
  const r: Dispositivo[] = [];
  for (const d of snap.docs) {
    const uid = d.ref.parent.parent?.id;
    if (!uid || d.ref.parent.parent?.parent.id !== "users") continue;
    const lido = lerDispositivo(uid, d.id, d.data());
    if (lido) r.push(lido);
  }
  return r;
}

/** uids dos membros de `users/{raiz}/membros` (o id do documento é o uid). */
export async function membrosDe(raiz: string): Promise<Set<string>> {
  const snap = await db().collection("users").doc(raiz).collection("membros").get();
  return new Set(snap.docs.map((d) => d.id));
}

/** Dispositivos do dono da conta e dos membros (candidatos a receber avisos desta conta; o filtro final é de `dispositivosDaRaiz`). */
export async function candidatosDaRaiz(raiz: string): Promise<{ dispositivos: Dispositivo[]; membros: Set<string> }> {
  const membros = await membrosDe(raiz);
  const lista = await Promise.all([raiz, ...membros].map(dispositivosDoUsuario));
  return { dispositivos: lista.flat(), membros };
}

function paraDespesas(docs: FirebaseFirestore.QueryDocumentSnapshot[]): DespesaDoc[] {
  return docs.map((d) => lerDespesa(d.id, d.data())).filter((d): d is DespesaDoc => d !== null);
}

export async function despesasNaoPagas(raiz: string): Promise<DespesaDoc[]> {
  const snap = await db().collection("users").doc(raiz).collection("despesas").where("pago", "==", false).get();
  return paraDespesas(snap.docs);
}

/** Intervalo por `data` (epoch ms); um único campo: não exige índice composto. */
export async function despesasEntre(raiz: string, inicioMs: number, fimExclusivoMs?: number): Promise<DespesaDoc[]> {
  let q = db().collection("users").doc(raiz).collection("despesas").where("data", ">=", inicioMs);
  if (fimExclusivoMs !== undefined) q = q.where("data", "<", fimExclusivoMs);
  return paraDespesas((await q.get()).docs);
}

export interface ResultadoEnvio {
  enviados: number;
  falhas: number;
  removidos: number;
}

/**
 * Envia [mensagem] a [ds] (em lotes de 500) e APAGA os dispositivos cujo token o FCM recusou como inexistente/inválido.
 * Nunca lança por falha de envio: registra e segue (um aparelho ruim não pode derrubar os demais).
 */
export async function enviar(ds: readonly Dispositivo[], mensagem: MensagemPush): Promise<ResultadoEnvio> {
  const r: ResultadoEnvio = { enviados: 0, falhas: 0, removidos: 0 };
  const unicos = [...new Map(ds.map((d) => [d.token, d])).values()];
  for (const grupo of lotes(unicos)) {
    try {
      const msg = montarMensagemFcm(mensagem, grupo);
      const resp = await getMessaging().sendEachForMulticast(msg);
      r.enviados += resp.successCount;
      r.falhas += resp.failureCount;
      const codigos = resp.responses.map((x) => x.error?.code);
      const apagar = dispositivosParaRemover(grupo, codigos);
      if (apagar.length > 0) {
        const batch = db().batch();
        for (const d of apagar) batch.delete(db().collection("users").doc(d.uid).collection("dispositivos").doc(d.id));
        await batch.commit();
        r.removidos += apagar.length;
      }
    } catch (e) {
      r.falhas += grupo.length;
      logger.error("Falha ao enviar push", { tipo: mensagem.tipo, erro: e instanceof Error ? e.message : String(e) });
    }
  }
  return r;
}

/** R86 - envia o sinal de sincronização (push silencioso). Mesma limpeza de tokens inválidos; nunca lança. */
export async function enviarSinal(ds: readonly Dispositivo[], origem: string): Promise<ResultadoEnvio> {
  const r: ResultadoEnvio = { enviados: 0, falhas: 0, removidos: 0 };
  const unicos = [...new Map(ds.map((d) => [d.token, d])).values()];
  for (const grupo of lotes(unicos)) {
    try {
      const resp = await getMessaging().sendEachForMulticast(montarMensagemSinal(grupo, origem));
      r.enviados += resp.successCount;
      r.falhas += resp.failureCount;
      const apagar = dispositivosParaRemover(grupo, resp.responses.map((x) => x.error?.code));
      if (apagar.length > 0) {
        const batch = db().batch();
        for (const d of apagar) batch.delete(db().collection("users").doc(d.uid).collection("dispositivos").doc(d.id));
        await batch.commit();
        r.removidos += apagar.length;
      }
    } catch (e) {
      r.falhas += grupo.length;
      logger.error("Falha ao enviar sinal de sincronização", { erro: e instanceof Error ? e.message : String(e) });
    }
  }
  return r;
}

export async function apagarDispositivos(ds: readonly Dispositivo[]): Promise<void> {
  for (const grupo of lotes(ds, 400)) {
    const batch = db().batch();
    for (const d of grupo) batch.delete(db().collection("users").doc(d.uid).collection("dispositivos").doc(d.id));
    await batch.commit();
  }
}
