"use client";

import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/Misc";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { analiseFaturasDoCartao, reprocessarSaldos } from "@/lib/finance/reprocessar";
import { useAgora, useDataset, useNotificar } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";

/**
 * R77 - "Reprocessar lançamentos do cartão": reaplica o ciclo atual do cartão (dia de fechamento e "fechamento = melhor dia")
 * a TODAS as faturas. A fatura de cada compra é sempre calculada pela data, então mudar a opção já reclassifica; o que precisa
 * de reprocessamento é o estado pago/em aberto e o limite. A tabela mostra cada fatura antes de aplicar.
 */
export function ReprocessarCartaoModal({ aberto, onFechar, cartaoId }: { aberto: boolean; onFechar: () => void; cartaoId: number }) {
  const ds = useDataset();
  const agora = useAgora();
  const notificar = useNotificar();
  const [corrigir, setCorrigir] = useState(true);

  const cartao = ds.cartoes.find((c) => c.id === cartaoId);
  const linhas = useMemo(() => (aberto ? analiseFaturasDoCartao(ds, cartaoId) : []), [aberto, ds, cartaoId]);
  const sim = useMemo(() => (aberto ? reprocessarSaldos(ds, { corrigirFaturas: corrigir, fatura: { cartaoId }, agora }) : null), [aberto, ds, cartaoId, corrigir, agora]);
  const afetadas = linhas.reduce((s, l) => s + l.noDiaDoFechamento, 0);

  function aplicar() {
    const r = acoes.reprocessarCartao(cartaoId, corrigir);
    onFechar();
    if (!r.ok) return notificar.erro(r.erro);
    notificar.sucesso(r.itensMarcados === 0 ? "Faturas conferidas: nada precisou de correção." : `Faturas reprocessadas: ${r.itensMarcados} item(ns) marcados como pagos.`);
  }

  return (
    <Modal aberto={aberto} onFechar={onFechar} titulo={`Reprocessar lançamentos: ${cartao?.nome ?? ""}`}>
      {cartao && sim && (
        <div className="space-y-3 text-sm">
          <p className="text-muted">
            Ciclo atual: fecha dia {cartao.diaFechamento}
            {cartao.fechamentoMelhorDia ? ", e o próprio dia do fechamento já é o melhor dia (compra nesse dia vai para a próxima fatura)" : ", e compras feitas no dia do fechamento ainda entram nessa fatura"}. Cada compra é
            classificada pela sua data; aqui você confere cada fatura e corrige o que ficou em aberto numa fatura já paga. Não altera valores, datas nem o saldo da conta.
          </p>
          {afetadas > 0 && (
            <p className="rounded-xl border border-line bg-surface-2 p-2 text-xs">
              {afetadas} compra(s) foram feitas no dia do fechamento: são as que mudam de fatura quando a opção &quot;fechamento = melhor dia&quot; é alterada.
            </p>
          )}
          <div className="max-h-64 overflow-auto rounded-lg border border-line">
            <table className="w-full text-xs">
              <caption className="sr-only">Faturas do cartão com o ciclo atual</caption>
              <thead className="sticky top-0 bg-surface-2 text-left uppercase text-muted">
                <tr>
                  <th className="px-2 py-1">Fatura</th>
                  <th className="px-2 py-1 text-right">Itens</th>
                  <th className="px-2 py-1 text-right">Total</th>
                  <th className="px-2 py-1 text-right">Em aberto</th>
                  <th className="px-2 py-1">Situação</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map((l) => (
                  <tr key={`${l.ano}-${l.mes}`} className="border-t border-line">
                    <td className="px-2 py-1">{String(l.mes).padStart(2, "0")}/{l.ano}</td>
                    <td className="px-2 py-1 text-right tabular-nums">
                      {l.itens}
                      {l.noDiaDoFechamento > 0 && <span className="text-muted" title="Compras no dia do fechamento"> ({l.noDiaDoFechamento}*)</span>}
                    </td>
                    <td className="px-2 py-1 text-right"><Money valor={l.total} /></td>
                    <td className="px-2 py-1 text-right"><Money valor={l.emAberto} /></td>
                    <td className="px-2 py-1">
                      {l.paga ? <Badge tom="pos">Paga</Badge> : l.emAberto > 0 ? <Badge tom="warn">Em aberto</Badge> : <Badge>—</Badge>}
                      {l.pendentesEmFaturaPaga > 0 && <span className="ml-1 text-warn">{l.pendentesEmFaturaPaga} a corrigir</span>}
                      {l.pagosSemPagamento > 0 && <span className="ml-1 text-muted" title="Itens já pagos numa fatura sem pagamento completo registrado">{l.pagosSemPagamento} já pago(s)</span>}
                    </td>
                  </tr>
                ))}
                {linhas.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-2 py-3 text-muted">Este cartão não tem compras.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <p className="text-xs text-muted">* compras feitas no dia do fechamento.</p>
          <Checkbox rotulo="Marcar como pagos os itens que ficaram em aberto numa fatura já paga" checked={corrigir} onChange={(e) => setCorrigir(e.target.checked)} />
          {corrigir && sim.itensMarcados > 0 && <p className="ml-6 text-xs text-muted">{sim.itensMarcados} item(ns) serão marcados como pagos.</p>}
          <div className="flex justify-end gap-2 pt-1">
            <Button onClick={onFechar}>Cancelar</Button>
            <Button variante="primary" onClick={aplicar}>
              {sim.itensMarcados > 0 || sim.correcoes.length > 0 ? "Reprocessar" : "Conferir"}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
