"use client";

import { AlertTriangle, CheckCircle2, Info, RefreshCw } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Confirmar } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { auditarSaldos } from "@/lib/finance/conferencia";
import { fromCents } from "@/lib/finance/money";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";

/** R45 - painel "Conferência de saldos". */
export function ConferenciaSaldos() {
  const ds = useDataset();
  const agora = useAgora();
  const rel = useMemo(() => auditarSaldos(ds, agora), [ds, agora]);
  const [confirmar, setConfirmar] = useState(false);
  const [resultado, setResultado] = useState<string | null>(null);

  function recalcular() {
    setConfirmar(false);
    const r = acoes.recalcularSaldos();
    if (!r.ok) return setResultado(r.erro);
    const contas = r.correcoes.filter((c) => c.tipo === "conta").length;
    const limites = r.correcoes.length - contas;
    setResultado(
      r.correcoes.length === 0
        ? "Tudo conferido: nenhum saldo ou limite precisou de correção."
        : `${contas} saldo(s) e ${limites} limite(s) corrigidos.`,
    );
  }

  const t = rel.totais;
  const erros = rel.inconsistencias.filter((i) => i.severidade === "erro");
  const infos = rel.inconsistencias.filter((i) => i.severidade === "info");

  return (
    <div className="grid gap-4">
      <Card aria-labelledby="t-conf-acao">
        <CardTitulo
          id="t-conf-acao"
          acao={<Badge tom={rel.divergencias === 0 ? "pos" : "warn"}>{rel.divergencias === 0 ? "Tudo confere" : `${rel.divergencias} divergência(s)`}</Badge>}
        >
          Recalcular
        </CardTitulo>
        <p className="text-sm text-muted">
          O saldo de cada conta é a soma dos lançamentos pagos (sem cartão) e o limite do cartão é o limite total menos o que está em aberto. Recalcular regrava esses valores gravados a partir do
          extrato; nenhum lançamento é alterado.
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <Button variante="primary" icone={<RefreshCw size={16} aria-hidden />} onClick={() => setConfirmar(true)}>
            Recalcular saldos agora
          </Button>
          {resultado && (
            <p role="status" className="text-sm font-medium">
              {resultado}
            </p>
          )}
        </div>
      </Card>

      <section aria-labelledby="t-conf-contas">
        <h2 id="t-conf-contas" className="mb-2 text-lg font-semibold">
          Contas
        </h2>
        {rel.contas.length === 0 ? (
          <p className="text-sm text-muted">Nenhuma conta cadastrada.</p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {rel.contas.map((c) => (
              <Card key={c.contaId} aria-label={`Conta ${c.rotulo}`}>
                <div className="mb-3 flex items-center justify-between gap-2">
                  <h3 className="text-sm font-semibold">{c.rotulo}</h3>
                  <Badge tom={c.ok ? "pos" : "neg"}>
                    {c.ok ? <CheckCircle2 size={12} className="mr-1 inline" aria-hidden /> : <AlertTriangle size={12} className="mr-1 inline" aria-hidden />}
                    {c.ok ? "OK" : "DIVERGENTE"}
                  </Badge>
                </div>
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <dt className="text-xs text-muted">Saldo gravado</dt>
                    <dd className="font-semibold">
                      <Money valor={c.saldoGravado} />
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">Saldo calculado (extrato)</dt>
                    <dd className="font-semibold">
                      <Money valor={c.saldoCalculado} />
                    </dd>
                  </div>
                  {!c.ok && (
                    <div className="col-span-2">
                      <dt className="text-xs text-muted">Diferença (gravado − calculado)</dt>
                      <dd className="font-semibold">
                        <Money valor={c.diferenca} tom="auto" />
                      </dd>
                    </div>
                  )}
                </dl>
                <table className="mt-3 w-full text-sm">
                  <caption className="sr-only">Composição do saldo calculado</caption>
                  <thead className="text-xs text-muted">
                    <tr>
                      <th scope="col" className="text-left font-medium">
                        Composição (pagos, sem cartão)
                      </th>
                      <th scope="col" className="text-right font-medium">
                        Valor
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {c.decomposicao.length === 0 && (
                      <tr>
                        <td colSpan={2} className="py-2 text-muted">
                          Sem lançamentos pagos.
                        </td>
                      </tr>
                    )}
                    {c.decomposicao.map((l) => (
                      <tr key={l.chave}>
                        <td className="py-1.5">
                          {l.rotulo} <span className="text-xs text-muted">({l.qtd})</span>
                        </td>
                        <td className="py-1.5 text-right">
                          <Money valor={fromCents(l.centavos)} tom="auto" />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t border-line font-semibold">
                      <th scope="row" className="py-1.5 text-left">
                        Total = saldo calculado
                      </th>
                      <td className="py-1.5 text-right">
                        <Money valor={fromCents(c.totalDecomposicaoCentavos)} />
                      </td>
                    </tr>
                  </tfoot>
                </table>
                <p className="mt-3 text-xs text-muted">
                  Ainda não no saldo: receitas previstas <Money valor={c.pendencias.receitasPrevistas} />; despesas atrasadas <Money valor={c.pendencias.despesasAtrasadas} />, a vencer em 30 dias{" "}
                  <Money valor={c.pendencias.despesasAVencer30d} />, futuras <Money valor={c.pendencias.despesasFuturas} />; compras de cartão em aberto{" "}
                  <Money valor={c.pendencias.comprasCartaoEmAberto} />.
                </p>
              </Card>
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="t-conf-cartoes">
        <h2 id="t-conf-cartoes" className="mb-2 text-lg font-semibold">
          Limites de cartão
        </h2>
        {rel.cartoes.length === 0 ? (
          <p className="text-sm text-muted">Nenhum cartão cadastrado.</p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {rel.cartoes.map((g) => (
              <Card key={g.principalId} aria-label={`Cartão ${g.rotulo}`}>
                <div className="mb-3 flex items-center justify-between gap-2">
                  <h3 className="text-sm font-semibold">{g.rotulo}</h3>
                  <Badge tom={g.ok ? "pos" : "neg"}>{g.ok ? "OK" : "DIVERGENTE"}</Badge>
                </div>
                <p className="text-sm">
                  Limite disponível calculado:{" "}
                  <strong>
                    <Money valor={g.limiteCalculado} />
                  </strong>{" "}
                  de <Money valor={g.limiteTotal} />
                </p>
                <ul className="mt-2 space-y-1 text-sm">
                  {g.cartoes.map((c) => (
                    <li key={c.cartaoId} className="flex items-center justify-between gap-2">
                      <span>
                        {c.rotulo}
                        {c.virtual ? " (virtual)" : ""}: gravado <Money valor={c.limiteGravado} />
                      </span>
                      <Badge tom={c.ok ? "pos" : "neg"}>{c.ok ? "OK" : "DIVERGENTE"}</Badge>
                    </li>
                  ))}
                </ul>
                <p className="mt-3 text-xs text-muted">
                  Em aberto <Money valor={g.emAbertoTotal} />: fatura atual <Money valor={g.faturaAtual} />, anteriores <Money valor={g.anteriores} />, parcelas futuras{" "}
                  <Money valor={g.parcelasFuturas} />.
                </p>
              </Card>
            ))}
          </div>
        )}
      </section>

      <Card aria-labelledby="t-conf-totais">
        <CardTitulo id="t-conf-totais">Entradas e saídas (todo o histórico)</CardTitulo>
        <dl className="grid grid-cols-2 gap-3 text-sm lg:grid-cols-3">
          <div>
            <dt className="text-xs text-muted">Entradas realizadas</dt>
            <dd className="font-semibold">
              <Money valor={t.entradasRealizadas} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Entradas previstas</dt>
            <dd className="font-semibold">
              <Money valor={t.entradasPrevistas} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Saídas pagas</dt>
            <dd className="font-semibold">
              <Money valor={t.saidasPagas} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Saídas pendentes / futuras</dt>
            <dd className="font-semibold">
              <Money valor={t.saidasPendentes} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Saídas total</dt>
            <dd className="font-semibold">
              <Money valor={t.saidasTotal} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Saldo das contas</dt>
            <dd className="font-semibold">
              <Money valor={t.saldoContas} />
            </dd>
          </div>
        </dl>
        <p className="mt-3 flex gap-2 text-sm text-muted">
          <Info size={16} className="mt-0.5 shrink-0" aria-hidden />
          <span>{t.explicacao}</span>
        </p>
      </Card>

      <Card aria-labelledby="t-conf-inc">
        <CardTitulo id="t-conf-inc" acao={<Badge tom={erros.length === 0 ? "pos" : "neg"}>{erros.length === 0 ? "Sem erros" : `${erros.length} erro(s)`}</Badge>}>
          Inconsistências nos dados
        </CardTitulo>
        {rel.inconsistencias.length === 0 ? (
          <p className="text-sm text-muted">Nenhuma inconsistência encontrada.</p>
        ) : (
          <ul className="space-y-1.5 text-sm">
            {[...erros, ...infos].map((i, n) => (
              <li key={n} className="flex gap-2">
                {i.severidade === "erro" ? (
                  <AlertTriangle size={16} className="mt-0.5 shrink-0 text-neg" aria-label="Erro" />
                ) : (
                  <Info size={16} className="mt-0.5 shrink-0 text-muted" aria-label="Informativo" />
                )}
                <span>{i.mensagem}</span>
              </li>
            ))}
          </ul>
        )}
        <p className="mt-2 text-xs text-muted">Nada é apagado automaticamente: corrija os itens acima manualmente.</p>
      </Card>

      <Confirmar
        aberto={confirmar}
        titulo="Recalcular saldos"
        rotuloConfirmar="Recalcular"
        mensagem="Os saldos das contas e os limites dos cartões serão regravados a partir dos lançamentos. Nenhum lançamento será alterado."
        onConfirmar={recalcular}
        onCancelar={() => setConfirmar(false)}
      />
    </div>
  );
}
