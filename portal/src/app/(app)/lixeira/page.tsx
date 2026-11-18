"use client";

import { RotateCcw, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Card, EmptyState, PageHeader } from "@/components/ui/Misc";
import { Confirmar } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { DIAS_LIXEIRA } from "@/lib/finance/operations";
import { formatData } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

export default function LixeiraPage() {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const [esvaziar, setEsvaziar] = useState(false);
  const itens = useMemo(() => [...ds.lixeira].sort((a, b) => b.excluidoEm - a.excluidoEm), [ds.lixeira]);

  const diasRestantes = (excluidoEm: number) => Math.max(0, DIAS_LIXEIRA - Math.floor((agora - excluidoEm) / 86_400_000));

  function restaurar(id: number) {
    const r = acoes.restaurarLixeira(id);
    avisar(r.ok ? "sucesso" : "erro", r.ok ? `${r.restaurados.length > 1 ? "Transferência restaurada" : "Lançamento restaurado"}.` : r.erro);
  }

  return (
    <>
      <PageHeader
        titulo="Lixeira"
        descricao={`Lançamentos excluídos ficam aqui por ${DIAS_LIXEIRA} dias`}
        acoes={
          <Button variante="danger" icone={<Trash2 size={16} aria-hidden />} disabled={itens.length === 0} onClick={() => setEsvaziar(true)}>
            Esvaziar lixeira
          </Button>
        }
      />
      {itens.length === 0 ? (
        <EmptyState titulo="Lixeira vazia" descricao="Quando você excluir um lançamento ele poderá ser restaurado daqui." icone={<Trash2 size={32} />} />
      ) : (
        <Card>
          <ul className="divide-y divide-line">
            {itens.map((l) => (
              <li key={l.id} className="flex items-center gap-3 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{l.descricao}</p>
                  <p className="text-xs text-muted">
                    Excluído em {formatData(l.excluidoEm)} · some em {diasRestantes(l.excluidoEm)} dia(s)
                  </p>
                </div>
                <Money valor={l.valor} className="text-sm font-semibold" />
                <IconButton rotulo={`Restaurar ${l.descricao}`} onClick={() => restaurar(l.id)}>
                  <RotateCcw size={16} aria-hidden />
                </IconButton>
              </li>
            ))}
          </ul>
        </Card>
      )}
      <Confirmar
        aberto={esvaziar}
        perigo
        titulo="Esvaziar lixeira"
        rotuloConfirmar="Esvaziar"
        mensagem={`Excluir definitivamente ${itens.length} item(ns)? Não pode ser desfeito.`}
        onConfirmar={() => {
          acoes.esvaziarLixeira();
          avisar("sucesso", "Lixeira esvaziada.");
          setEsvaziar(false);
        }}
        onCancelar={() => setEsvaziar(false)}
      />
    </>
  );
}
