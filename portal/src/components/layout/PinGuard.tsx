"use client";

import { Lock } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { deveBloquear } from "@/lib/pin";
import { useSession } from "@/lib/session";

const EVENTOS = ["pointerdown", "keydown", "scroll", "touchstart"] as const;

/** R34 - bloqueia a UI (sem renderizar o conteúdo) enquanto o PIN não for informado. */
export function PinGuard({ children }: { children: ReactNode }) {
  const pinAtivo = useSession((s) => s.pinAtivo);
  const bloqueado = useSession((s) => s.bloqueado);
  const minutos = useSession((s) => s.inatividadeMin);
  const ultima = useRef(0);
  const [pin, setPin] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!pinAtivo || bloqueado) return;
    ultima.current = Date.now();
    const marcar = () => {
      ultima.current = Date.now();
    };
    for (const e of EVENTOS) window.addEventListener(e, marcar, { passive: true });
    const timer = window.setInterval(() => {
      if (deveBloquear(ultima.current, Date.now(), minutos)) useSession.getState().bloquear();
    }, 5_000);
    return () => {
      for (const e of EVENTOS) window.removeEventListener(e, marcar);
      window.clearInterval(timer);
    };
  }, [pinAtivo, bloqueado, minutos]);

  async function enviar(e: FormEvent) {
    e.preventDefault();
    const ok = await useSession.getState().desbloquear(pin);
    setPin("");
    setErro(ok ? null : "PIN incorreto.");
  }

  if (pinAtivo && bloqueado) {
    return (
      <main className="mx-auto flex min-h-dvh max-w-xs flex-col justify-center gap-4 px-4">
        <div className="flex flex-col items-center gap-2 text-center">
          <span className="flex size-12 items-center justify-center rounded-2xl bg-primary text-primary-fg">
            <Lock size={24} aria-hidden />
          </span>
          <h1 className="text-xl font-semibold">Meu Dinheiro bloqueado</h1>
          <p className="text-sm text-muted">Digite seu PIN para continuar.</p>
        </div>
        <form onSubmit={enviar} className="flex flex-col gap-3">
          <Input rotulo="PIN" type="password" inputMode="numeric" autoComplete="off" autoFocus value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 8))} erro={erro} />
          <Button type="submit" variante="primary" disabled={pin.length < 4}>
            Desbloquear
          </Button>
        </form>
      </main>
    );
  }
  return <>{children}</>;
}
