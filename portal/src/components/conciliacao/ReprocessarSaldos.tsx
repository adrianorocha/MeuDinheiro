"use client";

import { RefreshCw } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { auditarSaldos } from "@/lib/finance/conferencia";
import { faturasPagasComPendencias, pagamentosNoCartao, reprocessarSaldos } from "@/lib/finance/reprocessar";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";

/** R70 - "Reprocessar saldos": corrige itens de fatura já paga e recalcula saldos/limites (só portal). */
export function ReprocessarSaldos() {
  const ds = useDataset();
  const agora = useAgora();
  const [aberto, setAberto] = useState(false);
  const [corrigirFaturas, setCorrigirFaturas] = useState(true);
  const [removerPgtos, setRemoverPgtos] = useState(true);
  const [resultado, setResultado] = useState<string | null>(null);

  const divergencias = useMemo(() => auditarSaldos(ds, agora).divergencias, [ds, agora]);
  const faturas = useMemo(() => faturasPagasComPendencias(ds), [ds]);
  const pgtosNoCartao = useMemo(() => pagamentosNoCartao(ds), [ds]);
  const simulacao = useMemo(() => (aberto ? reprocessarSaldos(ds, { corrigirFaturas, removerPagamentosNoCartao: removerPgtos, agora }) : null), [aberto, ds, corrigirFaturas, removerPgtos, agora]);
  const pendente = divergencias > 0 || faturas.length > 0 || pgtosNoCartao.length > 0;

  function aplicar() {
    const r = acoes.reprocessarSaldos(corrigirFaturas, removerPgtos);
    setAberto(false);
    if (!r.ok) return setResultado(r.erro);
    const contas = r.correcoes.filter((c) => c.tipo === "conta").length;
    const limites = r.correcoes.length - contas;
    setResultado(
      r.itensMarcados === 0 && r.pagamentosRemovidos === 0 && r.correcoes.length === 0
        ? "Tudo conferido: nada precisou de correção."
        : `${r.pagamentosRemovidos} pagamento(s) duplicado(s) removido(s) do cartão · ${r.itensMarcados} item(ns) de fatura paga marcados como pagos · ${contas} saldo(s) e ${limites} limite(s) corrigidos.`,
    );
  }

  return (
    <Card aria-labelledby="t-reproc">
      <CardTitulo id="t-reproc" acao={<Badge tom={pendente ? "warn" : "pos"}>{pendente ? `${divergencias} divergência(s) · ${faturas.length} fatura(s) paga(s) com itens em aberto · ${pgtosNoCartao.length} pagamento(s) no cartão` : "Tudo confere"}</Badge>}>
        Reprocessar saldos
      </CardTitulo>
      <p className="text-sm text-muted">
        Corrige diferenças entre o que está gravado e o que os lançamentos mostram: recalcula o saldo das contas e o limite dos cartões e marca como pagos os itens de faturas que já foram quitadas e
        ficaram em aberto (por exemplo, importados depois). Nenhum lançamento é criado, e valores e datas não mudam.
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <Button icone={<RefreshCw size={16} aria-hidden />} onClick={() => setAberto(true)}>
          Reprocessar saldos
        </Button>
        {resultado && (
          <p role="status" className="text-sm font-medium">
            {resultado}
          </p>
        )}
      </div>

      <Modal aberto={aberto} onFechar={() => setAberto(false)} titulo="Reprocessar saldos">
        {simulacao && (
          <div className="space-y-3 text-sm">
            <Checkbox rotulo="Marcar como pagos os itens de faturas já quitadas" checked={corrigirFaturas} onChange={(e) => setCorrigirFaturas(e.target.checked)} />
            {pgtosNoCartao.length > 0 && (
              <div>
                <Checkbox rotulo={`Remover ${pgtosNoCartao.length} crédito(s) de "pagamento de fatura" importado(s) para dentro do cartão`} checked={removerPgtos} onChange={(e) => setRemoverPgtos(e.target.checked)} />
                <p className="ml-6 text-xs text-muted">O extrato do cartão traz o pagamento da fatura anterior; esse valor já está na conta e, dentro do cartão, abate a fatura em dobro (total negativo). Vão para a lixeira (restaurável).</p>
                <ul className="ml-6 mt-1 max-h-28 overflow-y-auto text-xs">
                  {pgtosNoCartao.slice(0, 20).map((d) => (
                    <li key={d.id} className="flex justify-between gap-2">
                      <span>{d.descricao}</span>
                      <Money valor={d.valor} />
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {corrigirFaturas && simulacao.faturas.length > 0 && (
              <ul className="max-h-40 space-y-1 overflow-y-auto rounded-xl border border-line p-2 text-xs">
                {simulacao.faturas.map((f) => (
                  <li key={`${f.cartaoId}-${f.ano}-${f.mes}`} className="flex justify-between gap-2">
                    <span>
                      {f.rotulo} · fatura {String(f.mes).padStart(2, "0")}/{f.ano} · {f.itens.length} item(ns)
                    </span>
                    <Money valor={f.liquido} />
                  </li>
                ))}
              </ul>
            )}
            {simulacao.correcoes.length > 0 ? (
              <ul className="space-y-1 text-xs">
                {simulacao.correcoes.map((c) => (
                  <li key={`${c.tipo}-${c.id}`} className="flex justify-between gap-2">
                    <span>{c.tipo === "conta" ? "Saldo" : "Limite"} · {c.rotulo}</span>
                    <span>
                      <Money valor={c.antes} /> → <Money valor={c.depois} className="font-semibold" />
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-muted">Nenhum saldo ou limite precisa mudar.</p>
            )}
            <div className="flex justify-end gap-2">
              <Button onClick={() => setAberto(false)}>Cancelar</Button>
              <Button variante="primary" onClick={aplicar} disabled={simulacao.itensMarcados === 0 && simulacao.pagamentosRemovidos === 0 && simulacao.correcoes.length === 0}>
                Reprocessar
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </Card>
  );
}
