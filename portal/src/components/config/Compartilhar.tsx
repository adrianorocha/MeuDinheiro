"use client";

import { UserCog } from "lucide-react";
import Link from "next/link";
import { Card, CardTitulo } from "@/components/ui/Misc";
import { usePapel } from "@/lib/compartilhamento";
import { ROTULO_PAPEL } from "@/lib/finance/permissoes";

/** R32/R67 - a gestão de membros, papéis e convites (por código) fica em /membros. Aqui só o atalho. */
export function Compartilhar() {
  const papel = usePapel();
  return (
    <Card aria-labelledby="t-compartilhar">
      <CardTitulo id="t-compartilhar">Compartilhar</CardTitulo>
      <p className="mb-3 text-sm text-muted">
        Convide outra pessoa (casal/família) por código, defina o papel de cada uma (leitor, editor, administrador) e veja quem tem acesso.
        Seu papel na conta em uso: <strong>{ROTULO_PAPEL[papel]}</strong>.
      </p>
      <Link
        href="/membros/"
        className="inline-flex items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm font-medium hover:bg-surface-2"
      >
        <UserCog size={16} aria-hidden />
        Gerenciar membros e convites
      </Link>
    </Card>
  );
}
