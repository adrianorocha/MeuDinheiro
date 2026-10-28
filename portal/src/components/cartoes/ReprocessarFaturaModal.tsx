"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { useAgora, useDataset, useNotificar } from "@/lib/hooks";
import { creditosDaFatura, pareceCreditoDePagamento, reprocessarFatura, type AlvoFatura } from "@/lib/finance/reprocessar";
import { formatData } from "@/lib/format";
import { acoes } from "@/lib/store/actions";

interface Props {
  aberto: boolean;
  onFechar: () => void;
  alvo: AlvoFatura;
}

/** R71 - "Reprocessar fatura": corrige os itens e o limite de UMA fatura, com prévia antes×depois. */
export function ReprocessarFaturaModal({ aberto, onFechar, alvo }: Props) {
  const ds = useDataset();
  const agora = useAgora();
  const notificar = useNotificar();
  const [corrigir, setCorrigir] = useState(true);
  const [escolha, setEscolha] = useState<Record<number, boolean>>({});

  const creditos = useMemo(() => (aberto ? creditosDaFatura(ds, alvo) : []), [aberto, ds, alvo]);
  const marcado = (d: { id: number; descricao: string }) => escolha[d.id] ?? pareceCreditoDePagamento(d);
  const removerIds = useMemo(() => creditos.filter((d) => escolha[d.id] ?? pareceCreditoDePagamento(d)).map((d) => d.id), [creditos, escolha]);
  const sim = useMemo(() => (aberto ? reprocessarFatura(ds, alvo, { corrigirFaturas: corrigir, removerPagamentosNoCartao: false, removerIds, agora }) : null), [aberto, ds, alvo, corrigir, removerIds, agora]);
  const nada = !sim || (sim.itensMarcados === 0 && sim.pagamentosRemovidos === 0 && sim.correcoes.length === 0);

  function aplicar() {
    const r = acoes.reprocessarFatura(alvo, corrigir, false, removerIds);
    onFechar();
    if (!r.ok) return notificar.erro(r.erro);
    notificar.sucesso(
      r.itensMarcados === 0 && r.pagamentosRemovidos === 0 && r.correcoes.length === 0
        ? "Fatura conferida: nada precisou de correção."
        : `Fatura reprocessada: ${r.pagamentosRemovidos} pagamento(s) duplicado(s) removido(s), ${r.itensMarcados} item(ns) marcados como pagos.`,
    );
  }

  return (
    <Modal aberto={aberto} onFechar={onFechar} titulo={`Reprocessar fatura ${String(alvo.mes).padStart(2, "0")}/${alvo.ano}`}>
      {sim && (
        <div className="space-y-3 text-sm">
          <p className="text-muted">Revisa só os itens desta fatura e recalcula o limite do cartão. Não cria lançamentos nem altera valores ou datas; o saldo da conta não muda.</p>
          {creditos.length > 0 && (
            <div>
              <p className="font-medium">Créditos desta fatura</p>
              <p className="text-xs text-muted">
                Crédito reduz o total da fatura. Se o total ficou negativo, geralmente é o pagamento da fatura anterior que veio no extrato do cartão: esse valor já está na conta e abate em dobro. Marque os que devem sair
                (vão para a lixeira, restaurável); estornos de compra devem continuar.
              </p>
              <ul className="mt-1 max-h-40 space-y-1 overflow-y-auto">
                {creditos.map((d) => (
                  <li key={d.id} className="flex items-center justify-between gap-2 text-xs">
                    <Checkbox rotulo={`${formatData(d.data)} · ${d.descricao}`} checked={marcado(d)} onChange={(e) => setEscolha((o) => ({ ...o, [d.id]: e.target.checked }))} />
                    <Money valor={d.valor} />
                  </li>
                ))}
              </ul>
            </div>
          )}
          <Checkbox rotulo="Marcar como pagos os itens que sobraram em aberto numa fatura já quitada" checked={corrigir} onChange={(e) => setCorrigir(e.target.checked)} />
          {sim.itensMarcados > 0 && <p className="ml-6 text-xs text-muted">{sim.itensMarcados} item(ns) serão marcados como pagos.</p>}
          <dl className="grid grid-cols-2 gap-2 rounded-xl border border-line p-3 text-xs">
            <dt className="text-muted">Total da fatura</dt>
            <dd className="text-right">
              <Money valor={sim.resumo.totalAntes} /> → <Money valor={sim.resumo.totalDepois} className="font-semibold" />
            </dd>
            <dt className="text-muted">Em aberto</dt>
            <dd className="text-right">
              <Money valor={sim.resumo.emAbertoAntes} /> → <Money valor={sim.resumo.emAbertoDepois} className="font-semibold" />
            </dd>
            {sim.correcoes.map((c) => (
              <div key={`${c.tipo}-${c.id}`} className="col-span-2 flex justify-between gap-2">
                <span className="text-muted">{c.tipo === "conta" ? "Saldo" : "Limite"} · {c.rotulo}</span>
                <span>
                  <Money valor={c.antes} /> → <Money valor={c.depois} className="font-semibold" />
                </span>
              </div>
            ))}
          </dl>
          {nada && <p className="text-muted">Nada a corrigir nesta fatura.</p>}
          <div className="flex justify-end gap-2">
            <Button onClick={onFechar}>Cancelar</Button>
            <Button variante="primary" onClick={aplicar} disabled={nada}>
              Reprocessar
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
