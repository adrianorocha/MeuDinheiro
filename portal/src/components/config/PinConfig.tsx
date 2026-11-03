"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { pinValido } from "@/lib/pin";
import { useSession } from "@/lib/session";
import { useStore } from "@/lib/store/store";

const OPCOES_MIN = [1, 2, 5, 10, 15, 30, 60];

/** R34 - PIN opcional com bloqueio por inatividade. */
export function PinConfig() {
  const pinAtivo = useSession((s) => s.pinAtivo);
  const minutos = useSession((s) => s.inatividadeMin);
  const avisar = useStore((s) => s.avisar);
  const [pin, setPin] = useState("");
  const [confirma, setConfirma] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  async function ativar(e: FormEvent) {
    e.preventDefault();
    if (!pinValido(pin)) return setErro("O PIN deve ter de 4 a 8 dígitos.");
    if (pin !== confirma) return setErro("Os PINs não conferem.");
    await useSession.getState().ativarPin(pin);
    setPin("");
    setConfirma("");
    setErro(null);
    avisar("sucesso", "PIN ativado.");
  }

  async function desativar(e: FormEvent) {
    e.preventDefault();
    const ok = await useSession.getState().desativarPin(pin);
    if (!ok) return setErro("PIN incorreto.");
    setPin("");
    setErro(null);
    avisar("sucesso", "PIN desativado.");
  }

  const somenteDigitos = (v: string) => v.replace(/\D/g, "").slice(0, 8);

  return (
    <Card aria-labelledby="t-pin">
      <CardTitulo id="t-pin" acao={<Badge tom={pinAtivo ? "pos" : "neutro"}>{pinAtivo ? "Ativado" : "Desativado"}</Badge>}>
        PIN de bloqueio
      </CardTitulo>
      <p className="mb-3 text-sm text-muted">Protege a tela deste navegador. O PIN fica guardado apenas como hash e bloqueia o portal após um período sem uso.</p>
      {pinAtivo ? (
        <form onSubmit={desativar} className="flex flex-col gap-3" noValidate>
          <Select rotulo="Bloquear após inatividade" value={minutos} onChange={(e) => useSession.getState().definirInatividade(Number(e.target.value))}>
            {OPCOES_MIN.map((m) => (
              <option key={m} value={m}>
                {m} min
              </option>
            ))}
          </Select>
          <Input rotulo="PIN atual (para desativar)" type="password" inputMode="numeric" autoComplete="off" value={pin} onChange={(e) => setPin(somenteDigitos(e.target.value))} erro={erro} />
          <div className="flex gap-2">
            <Button type="submit" disabled={pin.length < 4}>
              Desativar PIN
            </Button>
            <Button onClick={() => useSession.getState().bloquear()}>Bloquear agora</Button>
          </div>
        </form>
      ) : (
        <form onSubmit={ativar} className="flex flex-col gap-3" noValidate>
          <div className="grid grid-cols-2 gap-3">
            <Input rotulo="Novo PIN (4–8 dígitos)" type="password" inputMode="numeric" autoComplete="new-password" value={pin} onChange={(e) => setPin(somenteDigitos(e.target.value))} />
            <Input rotulo="Confirmar PIN" type="password" inputMode="numeric" autoComplete="new-password" value={confirma} onChange={(e) => setConfirma(somenteDigitos(e.target.value))} erro={erro} />
          </div>
          <div>
            <Button type="submit" variante="primary" disabled={pin.length < 4}>
              Ativar PIN
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}
