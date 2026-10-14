import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where, writeBatch } from "firebase/firestore";
import { getDb } from "./client";
import {
  codigoValido,
  estadoDoConvite,
  gerarCodigo,
  mensagemDoEstado,
  normalizarCodigo,
  novoConviteDoc,
  type ConviteDoc,
} from "../finance/convite";
import { papeisConcediveis, papelDoDocumento, podeGerenciarMembro, type Papel, type PapelMembro } from "../finance/permissoes";

/**
 * R32/R67 - conta compartilhada: perfis, convites por e-mail, convites por CÓDIGO e membros.
 * Convite por código: `convites/<codigo>` (uso único, 7 dias, papel fixado no convite). Quem entra grava, no MESMO batch, o próprio
 * `users/<dono>/membros/<uid>` (papel = o do convite) e `convites/<codigo>.usado = true`; as regras conferem as duas escritas.
 */

export interface Membro {
  uid: string;
  email: string;
  /** R67: ausente em membros do R32 = EDITOR. */
  papel: PapelMembro;
  /** Quando entrou (R67 `entrouEm`; R32 `criadoEm`). */
  entrouEm: number;
  nome: string;
}

/** Convite por e-mail (R32): a pessoa já tem acesso e escolhe "usar os dados de <dono>". */
export interface Convite {
  donoUid: string;
  donoEmail: string;
  criadoEm: number;
}

export interface ConviteCodigo extends ConviteDoc {
  codigo: string;
}

export function emailMinusculo(email: string): string {
  return email.trim().toLowerCase();
}

/** Grava `perfis/{email}` ao logar para que outros possam convidar este usuário. */
export async function gravarPerfil(uid: string, email: string): Promise<void> {
  const e = emailMinusculo(email);
  await setDoc(doc(getDb(), "perfis", e), { uid, email: e });
}

function lerMembro(d: Record<string, unknown>): Membro {
  return {
    uid: String(d.uid ?? ""),
    email: typeof d.email === "string" ? d.email : "",
    papel: papelDoDocumento(d.papel),
    entrouEm: Number(d.entrouEm ?? d.criadoEm ?? 0) || 0,
    nome: typeof d.nome === "string" ? d.nome : "",
  };
}

export async function listarMembros(donoUid: string): Promise<Membro[]> {
  const snap = await getDocs(collection(getDb(), "users", donoUid, "membros"));
  return snap.docs
    .map((d) => lerMembro(d.data()))
    .filter((m) => m.uid)
    .sort((a, b) => a.entrouEm - b.entrouEm);
}

/** Meu papel na conta de `donoUid`: DONO se for a minha; sem documento de membro = null (sem acesso). */
export async function meuPapelEm(donoUid: string, meuUid: string): Promise<Papel | null> {
  if (donoUid === meuUid) return "DONO";
  const snap = await getDoc(doc(getDb(), "users", donoUid, "membros", meuUid));
  return snap.exists() ? papelDoDocumento(snap.data().papel) : null;
}

/** Convite por e-mail (R32), agora com papel (padrão EDITOR). */
export async function convidar(donoUid: string, donoEmail: string, emailConvidado: string, papel: PapelMembro = "EDITOR", meuPapel: Papel = "DONO"): Promise<void> {
  const email = emailMinusculo(emailConvidado);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new Error("Informe um e-mail válido.");
  if (email === emailMinusculo(donoEmail)) throw new Error("Você já é o dono destes dados.");
  if (!papeisConcediveis(meuPapel).includes(papel)) throw new Error("Você não pode conceder esse papel.");
  const db = getDb();
  const perfil = await getDoc(doc(db, "perfis", email));
  if (!perfil.exists()) throw new Error("Esse e-mail ainda não entrou no Meu Dinheiro. Peça para a pessoa fazer login uma vez e tente de novo.");
  const membroUid = String((perfil.data() as { uid: string }).uid);
  if (membroUid === donoUid) throw new Error("Essa pessoa já é a dona desta conta.");
  const agora = Date.now();
  const batch = writeBatch(db);
  batch.set(doc(db, "users", donoUid, "membros", membroUid), { uid: membroUid, email, criadoEm: agora, entrouEm: agora, papel });
  if (meuPapel === "DONO") {
    batch.set(doc(db, "perfis", email, "convites", donoUid), { donoUid, donoEmail: emailMinusculo(donoEmail), criadoEm: agora });
  }
  await batch.commit();
}

export async function alterarPapelMembro(donoUid: string, membro: Membro, novo: PapelMembro, meuPapel: Papel): Promise<void> {
  if (!podeGerenciarMembro(meuPapel, membro.papel) || !papeisConcediveis(meuPapel).includes(novo)) throw new Error("Você não pode alterar esse papel.");
  await updateDoc(doc(getDb(), "users", donoUid, "membros", membro.uid), { papel: novo });
}

export async function removerMembro(donoUid: string, membro: Membro, meuPapel: Papel = "DONO"): Promise<void> {
  if (!podeGerenciarMembro(meuPapel, membro.papel)) throw new Error("Você não pode remover este membro.");
  const db = getDb();
  await deleteDoc(doc(db, "users", donoUid, "membros", membro.uid));
  if (meuPapel === "DONO" && membro.email) await deleteDoc(doc(db, "perfis", emailMinusculo(membro.email), "convites", donoUid)).catch(() => undefined);
}

/** O membro sai da conta compartilhada (apaga o próprio documento). O dono não sai. */
export async function sairDaConta(donoUid: string, meuUid: string): Promise<void> {
  if (donoUid === meuUid) throw new Error("Você é o dono desta conta; não é possível sair dela.");
  await deleteDoc(doc(getDb(), "users", donoUid, "membros", meuUid));
}

export async function listarConvites(meuEmail: string): Promise<Convite[]> {
  const snap = await getDocs(collection(getDb(), "perfis", emailMinusculo(meuEmail), "convites"));
  return snap.docs.map((d) => d.data() as Convite).sort((a, b) => a.criadoEm - b.criadoEm);
}

// ------------------------------------------------------------------ R67: convite por código

/** Gera um convite (uso único, 7 dias) com o papel pré-definido e devolve o código. */
export async function gerarConvite(
  donoUid: string,
  papel: PapelMembro,
  meuUid: string,
  meuPapel: Papel,
  exibicao: { donoEmail?: string; donoNome?: string } = {},
): Promise<ConviteCodigo> {
  if (!papeisConcediveis(meuPapel).includes(papel)) throw new Error("Você não pode gerar convite com esse papel.");
  const db = getDb();
  const agora = Date.now();
  let ultimo: unknown = null;
  for (let t = 0; t < 3; t++) {
    const codigo = gerarCodigo();
    const dados = novoConviteDoc(donoUid, papel, meuUid, agora, exibicao);
    try {
      // criar sobre documento existente é negado pelas regras (só `usado` pode ser atualizado)
      await setDoc(doc(db, "convites", codigo), dados);
      return { codigo, ...dados };
    } catch (e) {
      ultimo = e;
    }
  }
  throw new Error(`Não foi possível gerar o convite (${ultimo instanceof Error ? ultimo.message : "erro"}). Verifique se as regras do Firestore foram publicadas.`);
}

function lerConvite(codigo: string, d: Record<string, unknown>): ConviteCodigo | null {
  if (typeof d.donoUid !== "string") return null;
  const papel = papelDoDocumento(d.papel);
  return {
    codigo,
    donoUid: d.donoUid,
    papel,
    criadoEm: Number(d.criadoEm ?? 0) || 0,
    expiraEm: Number(d.expiraEm ?? 0) || 0,
    usado: d.usado !== false,
    criadoPor: typeof d.criadoPor === "string" ? d.criadoPor : "",
    ...(typeof d.donoEmail === "string" ? { donoEmail: d.donoEmail } : {}),
    ...(typeof d.donoNome === "string" ? { donoNome: d.donoNome } : {}),
  };
}

/** Convites pendentes (não usados e não expirados) da conta; depende da regra de `list` para dono/ADMIN. */
export async function convitesPendentes(donoUid: string, agora = Date.now()): Promise<ConviteCodigo[]> {
  const snap = await getDocs(query(collection(getDb(), "convites"), where("donoUid", "==", donoUid)));
  return snap.docs
    .map((d) => lerConvite(d.id, d.data()))
    .filter((c): c is ConviteCodigo => c !== null && estadoDoConvite(c.usado, c.expiraEm, agora) === "VALIDO")
    .sort((a, b) => b.criadoEm - a.criadoEm);
}

export async function revogarConvite(codigo: string): Promise<void> {
  await deleteDoc(doc(getDb(), "convites", codigo));
}

/** Lê o convite para mostrar de quem é a conta ANTES de confirmar. Lança com mensagem pronta (inválido/expirado/usado). */
export async function consultarConvite(digitado: string, meuUid: string, agora = Date.now()): Promise<ConviteCodigo> {
  const codigo = normalizarCodigo(digitado);
  if (!codigoValido(codigo)) throw new Error(mensagemDoEstado("INVALIDO") ?? "Código inválido.");
  let dados: Record<string, unknown> | undefined;
  try {
    const snap = await getDoc(doc(getDb(), "convites", codigo));
    dados = snap.exists() ? snap.data() : undefined;
  } catch {
    throw new Error("Não foi possível consultar o convite. Verifique a conexão e tente de novo.");
  }
  const convite = dados ? lerConvite(codigo, dados) : null;
  if (!convite) throw new Error(mensagemDoEstado("INVALIDO") ?? "Código inválido.");
  const msg = mensagemDoEstado(estadoDoConvite(convite.usado, convite.expiraEm, agora));
  if (msg) throw new Error(msg);
  if (convite.donoUid === meuUid) throw new Error("Este convite é da sua própria conta.");
  return convite;
}

/** Entra na conta: UM batch com o meu documento de membro (papel do convite) e o convite marcado como usado. */
export async function aceitarConvite(convite: ConviteCodigo, meuUid: string, meuEmail: string | null, nome: string): Promise<void> {
  if (convite.donoUid === meuUid) throw new Error("Este convite é da sua própria conta.");
  const db = getDb();
  const agora = Date.now();
  const batch = writeBatch(db);
  batch.set(doc(db, "users", convite.donoUid, "membros", meuUid), {
    uid: meuUid,
    papel: convite.papel,
    entrouEm: agora,
    criadoEm: agora,
    nome: (nome.trim() || (meuEmail ?? "").split("@")[0] || "Membro").slice(0, 80),
    ...(meuEmail ? { email: emailMinusculo(meuEmail) } : {}),
    convite: convite.codigo,
  });
  batch.update(doc(db, "convites", convite.codigo), { usado: true, usadoPor: meuUid, usadoEm: agora });
  try {
    await batch.commit();
  } catch {
    throw new Error("Não foi possível entrar na conta. O convite pode ter sido usado ou ter expirado; peça um código novo.");
  }
}
