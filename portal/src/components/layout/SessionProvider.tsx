"use client";

import { onAuthStateChanged } from "firebase/auth";
import { useEffect } from "react";
import type { ReactNode } from "react";
import { gravarPerfil } from "@/lib/firebase/compartilhar";
import { getFirebaseAuth } from "@/lib/firebase/client";
import { useSession } from "@/lib/session";
import { FirebaseAdapter } from "@/lib/store/firebase-adapter";
import { LocalAdapter } from "@/lib/store/local-adapter";
import { useStore } from "@/lib/store/store";

/** Hidrata preferências, observa o login e liga o adaptador de persistência do modo escolhido. */
export function SessionProvider({ children }: { children: ReactNode }) {
  const modo = useSession((s) => s.modo);
  const hidratado = useSession((s) => s.hidratado);
  const user = useSession((s) => s.user);
  const raizUid = useSession((s) => s.raizUid);

  useEffect(() => {
    useSession.getState().hidratar();
  }, []);

  // Login (modo nuvem): mantém o usuário e publica o perfil para convites (R32).
  useEffect(() => {
    if (!hidratado || modo !== "firebase") return;
    const sessao = useSession.getState();
    return onAuthStateChanged(getFirebaseAuth(), (u) => {
      sessao.definirUsuario(u ? { uid: u.uid, email: u.email } : null);
      sessao.marcarAuthPronto();
      if (u?.email) void gravarPerfil(u.uid, u.email).catch(() => undefined);
    });
  }, [modo, hidratado]);

  // Persistência: local, ou Firestore na raiz do dono (próprio uid ou conta compartilhada).
  const uidUsuario = user?.uid ?? null;
  useEffect(() => {
    if (!hidratado) return;
    const { iniciar, parar } = useStore.getState();
    if (modo === "local") {
      iniciar(new LocalAdapter());
      return () => parar();
    }
    if (modo === "firebase" && uidUsuario) {
      iniciar(new FirebaseAdapter(raizUid ?? uidUsuario));
      return () => parar();
    }
    parar();
    return undefined;
  }, [modo, hidratado, uidUsuario, raizUid]);

  return <>{children}</>;
}
