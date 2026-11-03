"use client";

import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { CambioPanel } from "@/components/ferramentas/CambioPanel";
import { DividasPanel } from "@/components/ferramentas/DividasPanel";
import { FluxoPanel } from "@/components/ferramentas/FluxoPanel";
import { IndicesPanel } from "@/components/ferramentas/IndicesPanel";
import { ParceladoPanel } from "@/components/ferramentas/ParceladoPanel";
import { ReajustePanel } from "@/components/ferramentas/ReajustePanel";
import { RendimentoPanel } from "@/components/ferramentas/RendimentoPanel";
import { PageHeader } from "@/components/ui/Misc";
import { useIndices } from "@/lib/indicesStore";
import { useInsightsDispensados } from "@/lib/insightsStore";

const ABAS = [
  { id: "indices", rotulo: "Índices e taxas", Painel: IndicesPanel },
  { id: "cambio", rotulo: "Câmbio", Painel: CambioPanel },
  { id: "rendimento", rotulo: "Onde rende mais", Painel: RendimentoPanel },
  { id: "parcelado", rotulo: "À vista × parcelado", Painel: ParceladoPanel },
  { id: "dividas", rotulo: "Dívidas", Painel: DividasPanel },
  { id: "fluxo", rotulo: "Fluxo e economia", Painel: FluxoPanel },
  { id: "reajuste", rotulo: "Reajustes", Painel: ReajustePanel },
] as const;

export default function FerramentasPage() {
  const [ativa, setAtiva] = useState<(typeof ABAS)[number]["id"]>("indices");
  const botoes = useRef<Record<string, HTMLButtonElement | null>>({});
  const carregado = useIndices((s) => s.carregado);

  useEffect(() => {
    useIndices.getState().carregar();
    useInsightsDispensados.getState().carregar();
  }, []);

  function teclado(e: KeyboardEvent, i: number) {
    let alvo = -1;
    if (e.key === "ArrowRight") alvo = (i + 1) % ABAS.length;
    else if (e.key === "ArrowLeft") alvo = (i - 1 + ABAS.length) % ABAS.length;
    else if (e.key === "Home") alvo = 0;
    else if (e.key === "End") alvo = ABAS.length - 1;
    if (alvo < 0) return;
    e.preventDefault();
    setAtiva(ABAS[alvo].id);
    botoes.current[ABAS[alvo].id]?.focus();
  }

  const atual = ABAS.find((a) => a.id === ativa) ?? ABAS[0];
  const Painel = atual.Painel;

  return (
    <>
      <PageHeader titulo="Ferramentas" descricao="Índices e câmbio do Banco Central, simulações de rendimento, parcelamento e dívidas, projeção de caixa e reajustes" />
      <div role="tablist" aria-label="Ferramentas de planejamento" className="mb-4 flex gap-1 overflow-x-auto rounded-xl border border-line bg-surface-2 p-1">
        {ABAS.map((a, i) => (
          <button
            key={a.id}
            ref={(el) => {
              botoes.current[a.id] = el;
            }}
            id={`aba-${a.id}`}
            type="button"
            role="tab"
            aria-selected={ativa === a.id}
            aria-controls={`painel-${a.id}`}
            tabIndex={ativa === a.id ? 0 : -1}
            onClick={() => setAtiva(a.id)}
            onKeyDown={(e) => teclado(e, i)}
            className={`h-9 shrink-0 rounded-lg px-3 text-sm font-medium transition-colors ${ativa === a.id ? "bg-surface text-fg shadow-sm" : "text-muted hover:text-fg"}`}
          >
            {a.rotulo}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`painel-${atual.id}`} aria-labelledby={`aba-${atual.id}`}>
        {carregado ? <Painel /> : null}
      </div>
    </>
  );
}
