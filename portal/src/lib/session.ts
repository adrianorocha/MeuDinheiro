import { create } from "zustand";
import { firebaseConfigurado } from "./firebase/client";
import { criarPin, INATIVIDADE_PADRAO_MIN, lerPinGuardado, verificarPin } from "./pin";
import type { StorageMode } from "./store/adapter";

export type TemaPref = "system" | "light" | "dark";

export interface Usuario {
  uid: string;
  email: string | null;
}

const CHAVE_MODO = "meudinheiro:modo";
const CHAVE_TEMA = "meudinheiro:tema";
const CHAVE_PRIVADO = "meudinheiro:privado";
const CHAVE_PIN = "meudinheiro:pin";
const CHAVE_PIN_MIN = "meudinheiro:pin:min";
const chaveRaiz = (uid: string) => `meudinheiro:raiz:${uid}`;

export function ler(chave: string): string | null {
  try {
    return window.localStorage.getItem(chave);
  } catch {
    return null;
  }
}

export function gravar(chave: string, valor: string | null): void {
  try {
    if (valor === null) window.localStorage.removeItem(chave);
    else window.localStorage.setItem(chave, valor);
  } catch {
    // armazenamento indisponível: preferência vale só na sessão
  }
}

export function aplicarTema(pref: TemaPref): void {
  const raiz = document.documentElement;
  if (pref === "system") delete raiz.dataset.theme;
  else raiz.dataset.theme = pref;
}

interface Sessao {
  hidratado: boolean;
  /** Modo escolhido pelo usuário (null = ainda não escolheu). */
  modo: StorageMode | null;
  firebaseDisponivel: boolean;
  user: Usuario | null;
  authPronto: boolean;
  /** R32: dono dos dados em uso (null = os próprios dados do usuário). */
  raizUid: string | null;
  tema: TemaPref;
  privado: boolean;
  /** R34 */
  pinAtivo: boolean;
  bloqueado: boolean;
  inatividadeMin: number;
  hidratar: () => void;
  definirModo: (modo: StorageMode | null) => void;
  definirUsuario: (user: Usuario | null) => void;
  marcarAuthPronto: () => void;
  definirRaiz: (donoUid: string | null) => void;
  definirTema: (t: TemaPref) => void;
  alternarPrivado: () => void;
  ativarPin: (pin: string) => Promise<void>;
  desativarPin: (pin: string) => Promise<boolean>;
  desbloquear: (pin: string) => Promise<boolean>;
  bloquear: () => void;
  definirInatividade: (min: number) => void;
}

export const useSession = create<Sessao>((set, get) => ({
  hidratado: false,
  modo: null,
  firebaseDisponivel: false,
  user: null,
  authPronto: false,
  raizUid: null,
  tema: "system",
  privado: false,
  pinAtivo: false,
  bloqueado: false,
  inatividadeMin: INATIVIDADE_PADRAO_MIN,

  hidratar: () => {
    const disponivel = firebaseConfigurado();
    const salvo = ler(CHAVE_MODO);
    const modo: StorageMode | null = salvo === "local" || (salvo === "firebase" && disponivel) ? salvo : null;
    const t = ler(CHAVE_TEMA);
    const tema: TemaPref = t === "light" || t === "dark" ? t : "system";
    const pinAtivo = lerPinGuardado(ler(CHAVE_PIN)) !== null;
    const min = Number(ler(CHAVE_PIN_MIN));
    set({
      hidratado: true,
      modo,
      firebaseDisponivel: disponivel,
      tema,
      privado: ler(CHAVE_PRIVADO) === "1",
      pinAtivo,
      bloqueado: pinAtivo,
      inatividadeMin: Number.isFinite(min) && ler(CHAVE_PIN_MIN) !== null ? min : INATIVIDADE_PADRAO_MIN,
    });
  },

  definirModo: (modo) => {
    gravar(CHAVE_MODO, modo);
    set({ modo, ...(modo !== "firebase" ? { user: null, authPronto: modo === "local", raizUid: null } : { authPronto: false }) });
  },

  definirUsuario: (user) => {
    // Restaura a raiz compartilhada escolhida por este usuário (R32).
    const raiz = user ? ler(chaveRaiz(user.uid)) : null;
    set({ user, raizUid: raiz && raiz !== user?.uid ? raiz : null });
  },
  marcarAuthPronto: () => set({ authPronto: true }),

  definirRaiz: (donoUid) => {
    const user = get().user;
    if (user) gravar(chaveRaiz(user.uid), donoUid && donoUid !== user.uid ? donoUid : null);
    set({ raizUid: donoUid && donoUid !== user?.uid ? donoUid : null });
  },

  definirTema: (tema) => {
    gravar(CHAVE_TEMA, tema === "system" ? null : tema);
    aplicarTema(tema);
    set({ tema });
  },

  alternarPrivado: () => {
    const privado = !get().privado;
    gravar(CHAVE_PRIVADO, privado ? "1" : null);
    set({ privado });
  },

  ativarPin: async (pin) => {
    gravar(CHAVE_PIN, JSON.stringify(await criarPin(pin)));
    set({ pinAtivo: true, bloqueado: false });
  },

  desativarPin: async (pin) => {
    const guardado = lerPinGuardado(ler(CHAVE_PIN));
    if (!guardado || !(await verificarPin(pin, guardado))) return false;
    gravar(CHAVE_PIN, null);
    set({ pinAtivo: false, bloqueado: false });
    return true;
  },

  desbloquear: async (pin) => {
    const guardado = lerPinGuardado(ler(CHAVE_PIN));
    if (!guardado) {
      set({ bloqueado: false });
      return true;
    }
    const ok = await verificarPin(pin, guardado);
    if (ok) set({ bloqueado: false });
    return ok;
  },

  bloquear: () => {
    if (get().pinAtivo) set({ bloqueado: true });
  },

  definirInatividade: (min) => {
    gravar(CHAVE_PIN_MIN, String(min));
    set({ inatividadeMin: min });
  },
}));
