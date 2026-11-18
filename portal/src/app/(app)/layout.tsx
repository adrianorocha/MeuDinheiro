"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import type { ReactNode } from "react";
import { PinGuard } from "@/components/layout/PinGuard";
import { Shell } from "@/components/layout/Shell";
import { Button } from "@/components/ui/Button";
import { ErroBox, Spinner } from "@/components/ui/Misc";
import { useSession } from "@/lib/session";
import { useStore } from "@/lib/store/store";

/** Rotas autenticadas: exige modo escolhido (e login no modo Firebase). */
export default function AppLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const hidratado = useSession((s) => s.hidratado);
  const modo = useSession((s) => s.modo);
  const user = useSession((s) => s.user);
  const authPronto = useSession((s) => s.authPronto);
  const raizUid = useSession((s) => s.raizUid);
  const status = useStore((s) => s.status);
  const erro = useStore((s) => s.erro);

  const precisaLogin = hidratado && (modo === null || (modo === "firebase" && authPronto && !user));

  useEffect(() => {
    if (precisaLogin) router.replace("/login/");
  }, [precisaLogin, router]);

  if (!hidratado || precisaLogin) return <Spinner />;

  if (status === "erro") {
    return (
      <div className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-4 p-6">
        <ErroBox>Não foi possível carregar seus dados: {erro}</ErroBox>
        <p className="text-sm text-muted">Verifique sua conexão e as regras do Firestore (veja o README) e tente novamente.</p>
        <div className="flex gap-2">
          <Button variante="primary" onClick={() => window.location.reload()}>
            Tentar novamente
          </Button>
          {raizUid && (
            <Button onClick={() => useSession.getState().definirRaiz(null)}>Usar meus dados</Button>
          )}
          <Button
            onClick={() => {
              useSession.getState().definirModo(null);
              router.replace("/login/");
            }}
          >
            Voltar ao login
          </Button>
        </div>
      </div>
    );
  }

  if (status !== "pronto") return <Spinner rotulo="Carregando seus dados" />;

  return (
    <PinGuard>
      <Shell>{children}</Shell>
    </PinGuard>
  );
}
