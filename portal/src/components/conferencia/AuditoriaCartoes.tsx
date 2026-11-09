"use client";

import { AlertTriangle, CheckCircle2, Info, ShieldCheck } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, InputValor } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Confirmar } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { auditarCartoes, type Achado } from "@/lib/finance/auditoriaCartao";
import { formatBRL, parseValorBR } from "@/lib/format";
import { useDataset, useNotificar } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";

const ROTULO_ACAO: Record<string, string> = {
  REGRAVAR_LIMITE: "Regravar o limite",
  MARCAR_PAGOS: "Marcar como pago",
  MARCAR_ABERTOS: "Marcar como em aberto",
  REMOVER: "Remover (vai para a lixeira)",
  AJUSTAR_CONTA: "Ajustar a conta",
  AJUSTAR_LIMITE_TOTAL: "Ajustar o limite total",
};

/** R105 - auditoria dos cartões de crédito: acha a causa da diferença, aponta os lançamentos e corrige. */
export function AuditoriaCartoes() {
  const ds = useDataset();
  const notificar = useNotificar();
  const [banco, setBanco] = useState<Record<number, string>>({});
  const [escolha, setEscolha] = useState<Record<string, boolean>>({});
  const [confirmar, setConfirmar] = useState<string[] | null>(null);

  const limiteBanco = useMemo(() => {
    const out: Record<number, number | null> = {};
    for (const [id, txt] of Object.entries(banco)) {
      const v = parseValorBR(txt);
      out[Number(id)] = Number.isFinite(v) && v >= 0 ? v : null;
    }
    return out;
  }, [banco]);
  const rel = useMemo(() => auditarCartoes(ds, limiteBanco), [ds, limiteBanco]);
  const todos = rel.grupos.flatMap((g) => g.achados);
  const marcado = (a: Achado) => a.acao !== null && (escolha[a.chave] ?? a.segura);
  const selecionados = todos.filter(marcado);
  const apagam = selecionados.some((a) => a.acao?.tipo === "REMOVER");

  function aplicar(chaves: string[]) {
    setConfirmar(null);
    const r = acoes.corrigirCartoes(chaves, limiteBanco);
    if (!r.ok) return notificar.erro(r.erro);
    setEscolha({});
    const limites = r.correcoes.filter((c) => c.tipo === "cartao").length;
    if (r.falhas.length > 0) notificar.erro(r.falhas[0]);
    else notificar.sucesso(r.aplicadas === 0 ? "Nada precisou de correção." : `${r.aplicadas} correção(ões) aplicada(s); ${limites} limite(s) regravado(s).`);
  }

  const seguras = todos.filter((a) => a.segura);

  return (
    <section aria-labelledby="t-aud-cartoes" className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="t-aud-cartoes" className="text-lg font-semibold">
          Auditoria dos cartões de crédito
        </h2>
        <Badge tom={rel.totalAchados === 0 ? "pos" : "warn"}>{rel.totalAchados === 0 ? "Tudo confere" : `${rel.totalAchados} ponto(s) a verificar`}</Badge>
      </div>
      <p className="text-sm text-muted">
        Compara o limite gravado com o calculado pelos lançamentos em aberto e procura a causa de cada diferença: itens de fatura já paga ainda em aberto, pagamento da fatura dentro do cartão, compras
        duplicadas, conta errada. Informe o limite disponível que o <strong>app do banco</strong> mostra para descobrir de onde vem a diferença. Nada é alterado até você confirmar; remoções vão para a
        lixeira.
      </p>

      {rel.grupos.length === 0 && <p className="text-sm text-muted">Nenhum cartão de crédito cadastrado.</p>}

      <div className="grid gap-4 lg:grid-cols-2">
        {rel.grupos.map((g) => (
          <Card key={g.principalId} aria-label={`Auditoria do cartão ${g.rotulo}`}>
            <CardTitulo acao={<Badge tom={g.ok ? "pos" : "neg"}>{g.ok ? "OK" : "DIVERGENTE"}</Badge>}>{g.rotulo}</CardTitulo>
            <dl className="grid grid-cols-3 gap-2 text-sm">
              <div>
                <dt className="text-xs text-muted">Limite total</dt>
                <dd className="font-semibold">
                  <Money valor={g.limiteTotal} />
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Disponível (calculado)</dt>
                <dd className="font-semibold">
                  <Money valor={g.limiteCalculado} />
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Disponível (gravado)</dt>
                <dd className="font-semibold">
                  <Money valor={g.limiteGravado} />
                </dd>
              </div>
            </dl>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <InputValor
                rotulo="Limite disponível no app do banco (opcional)"
                placeholder="0,00"
                value={banco[g.principalId] ?? ""}
                onChange={(e) => setBanco((b) => ({ ...b, [g.principalId]: e.target.value }))}
                wrapperClassName="min-w-[14rem] flex-1"
              />
              {g.diferencaBanco !== null && (
                <p role="status" className="pb-2 text-sm font-medium">
                  {g.diferencaBanco === 0 ? "Bate com o banco." : `Diferença para o banco: ${formatBRL(Math.abs(g.diferencaBanco))} (${g.diferencaBanco > 0 ? "app mostra a mais" : "app mostra a menos"})`}
                </p>
              )}
            </div>

            {g.achados.length === 0 ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-pos">
                <CheckCircle2 size={16} aria-hidden /> Nenhum problema encontrado neste cartão.
              </p>
            ) : (
              <ul className="mt-3 space-y-3">
                {g.achados.map((a) => (
                  <li key={a.chave} className="rounded-xl border border-line p-3 text-sm">
                    <div className="flex items-start gap-2">
                      {a.severidade === "erro" ? (
                        <AlertTriangle size={16} className="mt-0.5 shrink-0 text-neg" aria-label="Erro" />
                      ) : a.severidade === "aviso" ? (
                        <AlertTriangle size={16} className="mt-0.5 shrink-0 text-warn" aria-label="Aviso" />
                      ) : (
                        <Info size={16} className="mt-0.5 shrink-0 text-muted" aria-label="Informativo" />
                      )}
                      <div className="min-w-0 flex-1">
                        <p className="font-medium">{a.titulo}</p>
                        <p className="mt-0.5 text-xs text-muted">{a.detalhe}</p>
                        {a.impactoLimite !== 0 && (
                          <p className="mt-1 text-xs">
                            Efeito no limite disponível ao corrigir: <Money valor={a.impactoLimite} tom="auto" />
                          </p>
                        )}
                        {a.lancamentoIds.length > 0 && <Lancamentos ids={a.lancamentoIds} />}
                        {a.acao && (
                          <div className="mt-2">
                            <Checkbox
                              rotulo={`${ROTULO_ACAO[a.acao.tipo]}${a.segura ? " (seguro)" : ""}`}
                              checked={marcado(a)}
                              onChange={(e) => setEscolha((s) => ({ ...s, [a.chave]: e.target.checked }))}
                            />
                          </div>
                        )}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        ))}
      </div>

      {rel.grupos.length > 0 && (
        <div className="flex flex-wrap items-center gap-3">
          <Button
            variante="primary"
            icone={<ShieldCheck size={16} aria-hidden />}
            disabled={selecionados.length === 0}
            onClick={() => (apagam ? setConfirmar(selecionados.map((a) => a.chave)) : aplicar(selecionados.map((a) => a.chave)))}
          >
            Corrigir selecionados ({selecionados.length})
          </Button>
          <Button disabled={seguras.length === 0} onClick={() => aplicar(seguras.map((a) => a.chave))}>
            Corrigir só o que é seguro ({seguras.length})
          </Button>
          <p className="text-xs text-muted">Seguro = regravar limite e marcar como pago itens de fatura já quitada. Não apaga nem altera valores.</p>
        </div>
      )}

      <Confirmar
        aberto={confirmar !== null}
        titulo="Confirmar correções"
        rotuloConfirmar="Aplicar"
        perigo
        mensagem="Entre as correções há remoção de lançamentos (eles vão para a lixeira por 30 dias e podem ser restaurados). Deseja aplicar?"
        onConfirmar={() => confirmar && aplicar(confirmar)}
        onCancelar={() => setConfirmar(null)}
      />
    </section>
  );
}

/** Aponta os lançamentos envolvidos (até 6). */
function Lancamentos({ ids }: { ids: number[] }) {
  const ds = useDataset();
  const itens = ids
    .slice(0, 6)
    .map((id) => ds.despesas.find((d) => d.id === id))
    .filter((d): d is NonNullable<typeof d> => !!d);
  return (
    <ul className="mt-2 space-y-0.5 rounded-lg bg-surface-2 p-2 text-xs">
      {itens.map((d) => (
        <li key={d.id} className="flex justify-between gap-2">
          <span className="min-w-0 truncate">
            {new Date(d.data).toLocaleDateString("pt-BR")} · {d.descricao}
            {d.pago ? " (pago)" : ""}
          </span>
          <span className="shrink-0 tabular-nums">
            {d.tipo === "CREDITO" ? "−" : ""}
            {formatBRL(d.valor)}
          </span>
        </li>
      ))}
      {ids.length > itens.length && <li className="text-muted">… e mais {ids.length - itens.length}</li>}
    </ul>
  );
}
