import {
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import { getFirebaseAuth } from "./client";

const MENSAGENS: Record<string, string> = {
  "auth/invalid-email": "E-mail inválido.",
  "auth/user-not-found": "Usuário não encontrado.",
  "auth/wrong-password": "E-mail ou senha incorretos.",
  "auth/invalid-credential": "E-mail ou senha incorretos.",
  "auth/email-already-in-use": "Este e-mail já está cadastrado.",
  "auth/weak-password": "Senha fraca: use ao menos 6 caracteres.",
  "auth/too-many-requests": "Muitas tentativas. Aguarde um pouco e tente novamente.",
  "auth/network-request-failed": "Sem conexão com a internet.",
  "auth/operation-not-allowed": "Login por e-mail/senha não está habilitado no projeto Firebase.",
  "auth/user-disabled": "Esta conta foi desativada.",
};

export function traduzirErroAuth(e: unknown): string {
  const code = typeof e === "object" && e !== null && "code" in e ? String((e as { code: unknown }).code) : "";
  return MENSAGENS[code] ?? "Não foi possível concluir a operação. Tente novamente.";
}

export async function entrar(email: string, senha: string): Promise<void> {
  await signInWithEmailAndPassword(getFirebaseAuth(), email.trim(), senha);
}

export async function cadastrar(email: string, senha: string): Promise<void> {
  await createUserWithEmailAndPassword(getFirebaseAuth(), email.trim(), senha);
}

export async function recuperarSenha(email: string): Promise<void> {
  await sendPasswordResetEmail(getFirebaseAuth(), email.trim());
}

export async function sair(): Promise<void> {
  await signOut(getFirebaseAuth());
}
