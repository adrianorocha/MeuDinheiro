"use client";

import { Trash2, UserPlus } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, ErroBox } from "@/components/ui/Misc";
import { convidar, listarConvites, listarMembros, removerMembro, type Convite, type Membro } from "@/lib/firebase/compartilhar";
import { useSession } from "@/lib/session";
import { useStore } from "@/lib/store/store";

/** R32 - conta compartilhada (somente modo nuvem). */
export function Compartilhar() {
  const user = useSession((s) => s.user);
  const raizUid = useSession((s) => s.raizUid);
  const avisar = useStore((s) => s.avisar);
  const [membros, setMembros] = useState<Membro[]>([]);
  const [convites, setConvites] = useState<Convite[]>([]);
  const [email, setEmail] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);

  const carregar = useCallback(async () => {
    if (!user) return;
    setCarregando(true);
    try {
      const [m, c] = await Promise.all([listarMembros(user.uid), user.email ? listarConvites(user.email) : Promise.resolve([])]);
      setMembros(m);
      setConvites(c);
      setErro(null);
    } catch {
      setErro("Não foi possível carregar o compartilhamento. Verifique se as regras do Firestore (firestore.rules) foram publicadas.");
    } finally {
      setCarregando(false);
    }
  }, [user]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- carga inicial assíncrona
    void carregar();
  }, [carregar]);

  if (!user) return null;
  const meuUid = user.uid;
  const donoAtual = convites.find((c) => c.donoUid === raizUid);

  async function enviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    try {
      await convidar(meuUid, user?.email ?? "", email);
      setEmail("");
      avisar("sucesso", "Convite enviado. A pessoa já pode usar seus dados.");
      await carregar();
    } catch (err) {
      setErro(err instanceof Error ? err.message : "Não foi possível convidar.");
    }
  }

  async function remover(m: Membro) {
    try {
      await removerMembro(meuUid, m);
      avisar("sucesso", `${m.email} removido.`);
      await carregar();
    } catch {
      avisar("erro", "Não foi possível remover o membro.");
    }
  }

  return (
    <Card aria-labelledby="t-compartilhar">
      <CardTitulo id="t-compartilhar">Compartilhar</CardTitulo>
      <p className="mb-3 text-sm text-muted">Convide outra pessoa (que já tenha entrado no Meu Dinheiro) para ver e lançar nos seus dados. Quem for membro não altera a lista de membros.</p>

      <form onSubmit={enviar} className="flex flex-wrap items-end gap-2" noValidate>
        <div className="min-w-56 flex-1">
          <Input rotulo="E-mail do convidado" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />
        </div>
        <Button type="submit" variante="primary" icone={<UserPlus size={16} aria-hidden />} disabled={!email.trim()}>
          Convidar
        </Button>
      </form>
      {erro && (
        <div className="mt-3">
          <ErroBox>{erro}</ErroBox>
        </div>
      )}

      <h3 className="mt-5 text-sm font-semibold">Membros</h3>
      {carregando ? (
        <p className="py-2 text-sm text-muted">Carregando...</p>
      ) : membros.length === 0 ? (
        <p className="py-2 text-sm text-muted">Ninguém tem acesso aos seus dados.</p>
      ) : (
        <ul className="divide-y divide-line">
          {membros.map((m) => (
            <li key={m.uid} className="flex items-center gap-2 py-2 text-sm">
              <span className="min-w-0 flex-1 truncate">{m.email}</span>
              <IconButton rotulo={`Remover ${m.email}`} onClick={() => void remover(m)}>
                <Trash2 size={16} aria-hidden />
              </IconButton>
            </li>
          ))}
        </ul>
      )}

      <h3 className="mt-5 text-sm font-semibold">Convites recebidos</h3>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-sm">
        <Badge tom={raizUid ? "warn" : "pos"}>{raizUid ? `Usando dados de ${donoAtual?.donoEmail ?? "outro usuário"}` : "Usando meus dados"}</Badge>
        {raizUid && (
          <Button tamanho="sm" onClick={() => useSession.getState().definirRaiz(null)}>
            Usar meus dados
          </Button>
        )}
      </div>
      {convites.length === 0 ? (
        <p className="py-2 text-sm text-muted">Nenhum convite recebido.</p>
      ) : (
        <ul className="divide-y divide-line">
          {convites.map((c) => (
            <li key={c.donoUid} className="flex items-center gap-2 py-2 text-sm">
              <span className="min-w-0 flex-1 truncate">{c.donoEmail}</span>
              <Button tamanho="sm" disabled={raizUid === c.donoUid} onClick={() => useSession.getState().definirRaiz(c.donoUid)}>
                Usar dados de {c.donoEmail.split("@")[0]}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
