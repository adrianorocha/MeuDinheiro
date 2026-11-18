"use client";

import { CircleAlert, CircleCheck, Info, X } from "lucide-react";
import { useEffect } from "react";
import { useStore, type Aviso } from "@/lib/store/store";

const ESTILO: Record<Aviso["tipo"], string> = {
  info: "border-line bg-surface text-fg",
  sucesso: "border-pos/40 bg-pos-soft text-pos",
  erro: "border-neg/40 bg-neg-soft text-neg",
};

function Item({ aviso }: { aviso: Aviso }) {
  const dispensar = useStore((s) => s.dispensarAviso);
  useEffect(() => {
    const t = window.setTimeout(() => dispensar(aviso.id), aviso.tipo === "erro" ? 9000 : 5000);
    return () => window.clearTimeout(t);
  }, [aviso.id, aviso.tipo, dispensar]);
  const Icone = aviso.tipo === "erro" ? CircleAlert : aviso.tipo === "sucesso" ? CircleCheck : Info;
  return (
    <div role={aviso.tipo === "erro" ? "alert" : "status"} className={`flex items-start gap-2 rounded-xl border px-3 py-2.5 text-sm shadow-lg ${ESTILO[aviso.tipo]}`}>
      <Icone size={18} className="mt-0.5 shrink-0" aria-hidden />
      <p className="flex-1">{aviso.texto}</p>
      <button type="button" aria-label="Dispensar aviso" onClick={() => dispensar(aviso.id)} className="rounded p-0.5 opacity-70 hover:opacity-100">
        <X size={14} aria-hidden />
      </button>
    </div>
  );
}

export function Toaster() {
  const avisos = useStore((s) => s.avisos);
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4 md:bottom-24 md:items-end md:pr-6">
      <div className="pointer-events-auto flex w-full max-w-sm flex-col gap-2">
        {avisos.map((a) => (
          <Item key={a.id} aviso={a} />
        ))}
      </div>
    </div>
  );
}
