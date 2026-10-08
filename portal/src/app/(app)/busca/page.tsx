"use client";

import { Search } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo } from "react";
import { ListaLancamentos } from "@/components/lancamentos/ListaLancamentos";
import { Card, CardTitulo, EmptyState, PageHeader, Spinner } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { buscaGlobal } from "@/lib/finance/analises";
import { useDataset } from "@/lib/hooks";

const LIMITE = 100;

function Resultados() {
  const ds = useDataset();
  const q = useSearchParams().get("q") ?? "";
  const r = useMemo(() => buscaGlobal(ds, q), [ds, q]);

  return (
    <>
      <PageHeader titulo="Busca" descricao={q ? `Resultados para "${q}" (${r.total})` : "Digite um termo no campo de busca"} />
      {q && r.total === 0 && <EmptyState titulo="Nada encontrado" descricao="Tente parte da descrição, categoria, banco, um valor (123,45) ou uma data (dd/MM/aaaa)." icone={<Search size={32} />} />}

      {r.contas.length > 0 && (
        <Card className="mb-4" aria-labelledby="b-contas">
          <CardTitulo id="b-contas">Contas · {r.contas.length}</CardTitulo>
          <ul className="divide-y divide-line">
            {r.contas.map((c) => (
              <li key={c.id} className="flex items-center justify-between py-2 text-sm">
                <Link href="/contas/" className="font-medium text-primary hover:underline">
                  {c.banco} · {c.conta}
                </Link>
                <Money valor={c.saldo} tom="auto" />
              </li>
            ))}
          </ul>
        </Card>
      )}
      {r.cartoes.length > 0 && (
        <Card className="mb-4" aria-labelledby="b-cartoes">
          <CardTitulo id="b-cartoes">Cartões · {r.cartoes.length}</CardTitulo>
          <ul className="divide-y divide-line">
            {r.cartoes.map((c) => (
              <li key={c.id} className="flex items-center justify-between py-2 text-sm">
                <Link href="/cartoes/" className="font-medium text-primary hover:underline">
                  {c.nome}
                </Link>
                <span className="text-muted">
                  disponível <Money valor={c.limiteDisponivel} />
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}
      {r.metas.length > 0 && (
        <Card className="mb-4" aria-labelledby="b-metas">
          <CardTitulo id="b-metas">Metas · {r.metas.length}</CardTitulo>
          <ul className="divide-y divide-line">
            {r.metas.map((m) => (
              <li key={m.id} className="flex items-center justify-between py-2 text-sm">
                <Link href="/metas/" className="font-medium text-primary hover:underline">
                  {m.nome}
                </Link>
                <Money valor={m.valorGuardado} />
              </li>
            ))}
          </ul>
        </Card>
      )}
      {r.lancamentos.length > 0 && (
        <Card aria-labelledby="b-lanc">
          <CardTitulo id="b-lanc">Lançamentos · {r.lancamentos.length}</CardTitulo>
          <ListaLancamentos itens={r.lancamentos.slice(0, LIMITE)} />
          {r.lancamentos.length > LIMITE && <p className="mt-2 text-xs text-muted">Mostrando os {LIMITE} mais recentes; refine a busca.</p>}
        </Card>
      )}
    </>
  );
}

export default function BuscaPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <Resultados />
    </Suspense>
  );
}
