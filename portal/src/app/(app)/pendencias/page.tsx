"use client";

import { CircleCheck, CreditCard } from "lucide-react";
import Link from "next/link";
import { useMemo } from "react";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitulo, EmptyState, PageHeader } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { PicBadge } from "@/components/ui/PicIcon";
import { cartaoIdDe } from "@/lib/finance/calc";
import { inicioDoDia } from "@/lib/finance/dates";
import type { Despesa } from "@/lib/finance/types";
import { formatData } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { faturasEmAberto } from "@/lib/selectors";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const JANELA_DIAS = 30;

function Linha({ d, atrasada }: { d: Despesa; atrasada: boolean }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const entrada = d.tipo === "CREDITO";
  const conta = ds.contas.find((c) => c.conta === d.conta);
  return (
    <li className="flex items-center gap-3 py-3">
      <PicBadge pic={ds.categorias.find((c) => c.nome.trim().toLowerCase() === d.categoria.trim().toLowerCase())?.pic || d.pic} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{d.descricao}</p>
        <p className="truncate text-xs text-muted">
          {formatData(d.data)} · {d.categoria} · {conta?.banco ?? d.conta}
        </p>
      </div>
      {atrasada && <Badge tom="neg">Atrasada</Badge>}
      {entrada && <Badge tom="pos">A receber</Badge>}
      <Money valor={entrada ? d.valor : -d.valor} tom="auto" className="text-sm font-semibold" />
      <Button
        tamanho="sm"
        icone={<CircleCheck size={14} aria-hidden />}
        onClick={() => {
          const r = acoes.darBaixa(d.id);
          avisar(r.ok ? "sucesso" : "erro", r.ok ? `Baixa em "${d.descricao}".` : r.erro);
        }}
        aria-label={`Dar baixa em ${d.descricao}`}
      >
        Dar baixa
      </Button>
    </li>
  );
}

export default function PendenciasPage() {
  const ds = useDataset();
  const agora = useAgora();

  const { atrasadas, aVencer, futuras } = useMemo(() => {
    const hoje = inicioDoDia(agora);
    const limite = hoje + JANELA_DIAS * 86_400_000;
    const pend = ds.despesas.filter((d) => d.natureza === "NORMAL" && !d.pago && cartaoIdDe(d) === null).sort((a, b) => a.data - b.data);
    return {
      atrasadas: pend.filter((d) => d.data < hoje),
      aVencer: pend.filter((d) => d.data >= hoje && d.data < limite),
      futuras: pend.filter((d) => d.data >= limite),
    };
  }, [ds.despesas, agora]);
  const faturas = useMemo(() => faturasEmAberto(ds), [ds]);

  const total = atrasadas.length + aVencer.length + futuras.length + faturas.length;

  return (
    <>
      <PageHeader titulo="Pendências" descricao="Lançamentos pendentes de baixa e faturas em aberto" />
      {total === 0 && <EmptyState titulo="Tudo em dia" descricao="Não há pendências de contas nem faturas em aberto." icone={<CircleCheck size={32} />} />}

      {[
        { titulo: "Atrasadas", lista: atrasadas, atrasada: true },
        { titulo: `A vencer (próximos ${JANELA_DIAS} dias)`, lista: aVencer, atrasada: false },
        { titulo: "Futuras", lista: futuras, atrasada: false },
      ]
        .filter((g) => g.lista.length > 0)
        .map((g) => (
          <Card key={g.titulo} className="mb-4">
            <CardTitulo>
              {g.titulo} · {g.lista.length}
            </CardTitulo>
            <ul className="divide-y divide-line">
              {g.lista.map((d) => (
                <Linha key={d.id} d={d} atrasada={g.atrasada} />
              ))}
            </ul>
          </Card>
        ))}

      {faturas.length > 0 && (
        <Card>
          <CardTitulo>Faturas de cartão em aberto · {faturas.length}</CardTitulo>
          <ul className="divide-y divide-line">
            {faturas.map((f) => {
              const c = ds.cartoes.find((x) => x.id === f.cartaoId);
              const atrasada = f.vencimento < inicioDoDia(agora);
              return (
                <li key={`${f.cartaoId}-${f.ano}-${f.mes}`} className="flex items-center gap-3 py-3">
                  <span className="flex size-9 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden>
                    <CreditCard size={18} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">
                      {c?.nome ?? "Cartão"} · {String(f.mes).padStart(2, "0")}/{f.ano}
                    </p>
                    <p className="text-xs text-muted">Vence em {formatData(f.vencimento)}</p>
                  </div>
                  {atrasada && <Badge tom="neg">Vencida</Badge>}
                  <Money valor={f.total} className="text-sm font-semibold" />
                  <Link href="/cartoes/" className="text-sm font-medium text-primary hover:underline">
                    Pagar
                  </Link>
                </li>
              );
            })}
          </ul>
        </Card>
      )}
    </>
  );
}
