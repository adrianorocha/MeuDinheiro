"use client";

import { TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useEffect } from "react";
import { Button } from "@/components/ui/Button";
import { Money } from "@/components/ui/Money";
import { useAlertas } from "@/lib/alertas";
import { alertasOrcamento } from "@/lib/finance/analises";
import { formatPercentual } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";

/** R22 - banner com avisos de orçamento (80% e 100%), exibidos uma única vez por mês/categoria. */
export function AlertasOrcamento() {
  const ds = useDataset();
  const agora = useAgora();
  const avisados = useAlertas((s) => s.avisados);
  useEffect(() => useAlertas.getState().carregar(), []);
  const { alertas, marcar } = alertasOrcamento(ds, agora, avisados);
  if (alertas.length === 0) return null;
  return (
    <div role="alert" className="mb-5 flex flex-col gap-2 rounded-2xl border border-warn/40 bg-warn-soft p-4 text-sm text-warn">
      <div className="flex items-center gap-2 font-semibold">
        <TriangleAlert size={18} aria-hidden /> Alertas de orçamento
      </div>
      <ul className="space-y-1">
        {alertas.map((a) => (
          <li key={a.chave}>
            <strong>{a.categoria}</strong>: {a.limiar === 100 ? "orçamento estourado" : "80% do orçamento utilizado"} ({formatPercentual(a.pct)}) ·{" "}
            <Money valor={a.gasto} /> de <Money valor={a.limite} />
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <Button tamanho="sm" onClick={() => useAlertas.getState().marcar(marcar)}>
          Entendi
        </Button>
        <Link href="/orcamentos/" className="inline-flex h-8 items-center rounded-lg px-3 text-sm font-medium underline">
          Ver orçamentos
        </Link>
      </div>
    </div>
  );
}
