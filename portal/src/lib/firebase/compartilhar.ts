import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, writeBatch } from "firebase/firestore";
import { getDb } from "./client";

/** R32 - conta compartilhada: perfis, convites e membros. */

export interface Membro {
  uid: string;
  email: string;
  criadoEm: number;
}

export interface Convite {
  donoUid: string;
  donoEmail: string;
  criadoEm: number;
}

export function emailMinusculo(email: string): string {
  return email.trim().toLowerCase();
}

/** Grava `perfis/{email}` ao logar para que outros possam convidar este usuário. */
export async function gravarPerfil(uid: string, email: string): Promise<void> {
  const e = emailMinusculo(email);
  await setDoc(doc(getDb(), "perfis", e), { uid, email: e });
}

export async function listarMembros(donoUid: string): Promise<Membro[]> {
  const snap = await getDocs(collection(getDb(), "users", donoUid, "membros"));
  return snap.docs.map((d) => d.data() as Membro).sort((a, b) => a.criadoEm - b.criadoEm);
}

export async function convidar(donoUid: string, donoEmail: string, emailConvidado: string): Promise<void> {
  const email = emailMinusculo(emailConvidado);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new Error("Informe um e-mail válido.");
  if (email === emailMinusculo(donoEmail)) throw new Error("Você já é o dono destes dados.");
  const db = getDb();
  const perfil = await getDoc(doc(db, "perfis", email));
  if (!perfil.exists()) throw new Error("Esse e-mail ainda não entrou no Meu Dinheiro. Peça para a pessoa fazer login uma vez e tente de novo.");
  const membroUid = String((perfil.data() as { uid: string }).uid);
  const agora = Date.now();
  const batch = writeBatch(db);
  batch.set(doc(db, "users", donoUid, "membros", membroUid), { uid: membroUid, email, criadoEm: agora });
  batch.set(doc(db, "perfis", email, "convites", donoUid), { donoUid, donoEmail: emailMinusculo(donoEmail), criadoEm: agora });
  await batch.commit();
}

export async function removerMembro(donoUid: string, membro: Membro): Promise<void> {
  const db = getDb();
  await deleteDoc(doc(db, "users", donoUid, "membros", membro.uid));
  await deleteDoc(doc(db, "perfis", emailMinusculo(membro.email), "convites", donoUid));
}

export async function listarConvites(meuEmail: string): Promise<Convite[]> {
  const snap = await getDocs(collection(getDb(), "perfis", emailMinusculo(meuEmail), "convites"));
  return snap.docs.map((d) => d.data() as Convite).sort((a, b) => a.criadoEm - b.criadoEm);
}
