"use client";

import { X } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { InputValor, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { fluxoVazio, projetarFluxo } from "@/lib/finance/fluxoCaixa";
import { acumuladoDosUltimos, buscarIndice, pctParaFracao, valorMensalPct } from "@/lib/finance/indices";
import { gerarInsights } from "@/lib/finance/insightsEconomia";
import { formatMesAno } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { useIndices } from "@/lib/indicesStore";
import { useInsightsDispensados } from "@/lib/insightsStore";
import { CortesPanel } from "./CortesPanel";
import { GraficoLinhas } from "./Grafico";
import { AvisoReferencia, fmtPctFracao, lerNum, paraCampo, Resultado } from "./util";

export function FluxoPanel() {
  const ds = useDataset();
  const agora = useAgora();
  const conjunto = useIndices((s) => s.conjunto);
  const dispensados = useInsightsDispensados((s) => s.dispensados);
  const dispensar = useInsightsDispensados((s) => s.dispensar);
  const restaurar = useInsightsDispensados((s) => s.restaurar);

  const ipca = buscarIndice(conjunto, "ipca");
  const acum = acumuladoDosUltimos(ipca.serie, ipca.serie.length);
  // padrão: média mensal (geométrica) do IPCA dos meses disponíveis; evita extrapolar um único mês atípico
  const inflPadrao = acum.mesesUsados > 0 ? (Math.pow(1 + acum.acumulado, 1 / acum.mesesUsados) - 1) * 100 : valorMensalPct(ipca);
  const [meses, setMeses] = useState("12");
  const [infl, setInfl] = useState<string | null>(null);
  const inflTxt = infl ?? paraCampo(Number(inflPadrao.toFixed(4)));
  const inflFracao = pctParaFracao(lerNum(inflTxt));
  const inflOk = Number.isFinite(inflFracao) && inflFracao > -0.5 && inflFracao < 1;

  const cdi = pctParaFracao(valorMensalPct(buscarIndice(conjunto, "cdi")));
  const r = inflOk ? projetarFluxo(ds, agora, Number(meses), inflFracao) : null;
  const insights = inflOk ? gerarInsights(ds, agora, { inflacaoMensal: inflFracao, cdiMensal: cdi, pctCdi: 1 }) : [];
  const visiveis = insights.filter((i) => !dispensados.has(i.id));
  const ocultos = insights.length - visiveis.length;

  const semDados = ds.contas.length === 0 && ds.despesas.length === 0;
  const fmtMes = (m: number, a: number) => formatMesAno(m, a);

  return (
    <div className="space-y-4">
      <AvisoReferencia>Projeção e insights calculados só no seu navegador, com base nos seus lançamentos e em premissas editáveis. Estimativas, não garantias.</AvisoReferencia>
      {semDados && <EmptyState titulo="Sem dados ainda" descricao="Cadastre contas, lançamentos e recorrências para ver a projeção do seu fluxo de caixa e sugestões de economia." />}

      <Card aria-labelledby="t-fluxo">
        <CardTitulo id="t-fluxo">Projeção de fluxo de caixa</CardTitulo>
        <div className="grid gap-3 sm:grid-cols-3">
          <Select rotulo="Horizonte" value={meses} onChange={(e) => setMeses(e.target.value)}>
            {[6, 7, 8, 9, 10, 11, 12].map((n) => (
              <option key={n} value={n}>{n} meses</option>
            ))}
          </Select>
          <InputValor rotulo="Inflação mensal (%)" value={inflTxt} onChange={(e) => setInfl(e.target.value)} dica="Padrão: média mensal do IPCA dos meses da tela de índices. Corrige a despesa variável." erro={inflOk ? null : "Informe um percentual válido."} />
        </div>

        {r && (
          <div className="mt-4 space-y-4" aria-live="polite">
            {fluxoVazio(r) ? (
              <p className="text-sm text-muted">Ainda não há saldo, recorrências ou histórico de gastos para projetar.</p>
            ) : (
              <>
                <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                  <Resultado rotulo="Saldo atual das contas"><Money valor={r.saldoInicial / 100} tom="auto" /></Resultado>
                  <Resultado rotulo={`Despesa variável estimada (média 3 meses, em valores de hoje)`}><Money valor={r.variavelBase / 100} /></Resultado>
                  <Resultado rotulo={`Saldo em ${fmtMes(r.meses[r.meses.length - 1].mes, r.meses[r.meses.length - 1].ano)}`}><Money valor={r.meses[r.meses.length - 1].saldoFinal / 100} tom="auto" /></Resultado>
                  <Resultado rotulo="Meses com saldo negativo" destaque={r.primeiroNegativo !== null}>{r.meses.filter((m) => m.negativo).length}</Resultado>
                </div>

                {r.primeiroNegativo !== null ? (
                  <p className="rounded-xl border border-neg/40 bg-neg-soft px-3 py-2 text-sm text-neg" role="status">
                    O saldo previsto fica negativo a partir de {fmtMes(r.meses[r.primeiroNegativo].mes, r.meses[r.primeiroNegativo].ano)}. Para fechar o déficit seria preciso cortar ou poupar cerca de <strong><Money valor={r.cortePorMes / 100} /></strong> por mês, a partir deste mês (menor valor constante que mantém todos os saldos mensais em zero ou mais).
                  </p>
                ) : (
                  <p className="rounded-xl border border-pos/40 bg-pos-soft px-3 py-2 text-sm text-pos" role="status">
                    Nenhum mês com saldo negativo nesta projeção.
                  </p>
                )}
                {r.mesesComDados < 3 && <p className="text-sm text-warn">Há menos de 3 meses completos de histórico de gastos variáveis; a estimativa pode estar subavaliada.</p>}

                <div className="overflow-x-auto">
                  <table className="w-full min-w-[640px] text-sm">
                    <caption className="sr-only">Projeção mês a mês</caption>
                    <thead>
                      <tr className="text-left text-xs uppercase text-muted">
                        <th scope="col" className="py-1 pr-3">Mês</th>
                        <th scope="col" className="py-1 pr-3 text-right">Entradas</th>
                        <th scope="col" className="py-1 pr-3 text-right">Saídas conhecidas</th>
                        <th scope="col" className="py-1 pr-3 text-right">Variável estimada</th>
                        <th scope="col" className="py-1 pr-3 text-right">Resultado</th>
                        <th scope="col" className="py-1 text-right">Saldo final</th>
                      </tr>
                    </thead>
                    <tbody>
                      {r.meses.map((m) => (
                        <tr key={m.indice} className={`border-t border-line ${m.negativo ? "bg-neg-soft/60" : ""}`}>
                          <th scope="row" className="py-2 pr-3 text-left font-medium">
                            {fmtMes(m.mes, m.ano)} {m.negativo && <Badge tom="neg">saldo negativo</Badge>}
                          </th>
                          <td className="py-2 pr-3 text-right tabular-nums"><Money valor={m.entradas / 100} /></td>
                          <td className="py-2 pr-3 text-right tabular-nums"><Money valor={m.saidas / 100} /></td>
                          <td className="py-2 pr-3 text-right tabular-nums"><Money valor={m.variavel / 100} /></td>
                          <td className="py-2 pr-3 text-right tabular-nums"><Money valor={m.resultado / 100} tom="auto" /></td>
                          <td className="py-2 text-right tabular-nums"><Money valor={m.saldoFinal / 100} tom="auto" /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <GraficoLinhas rotulo="Saldo projetado ao fim de cada mês, a partir do saldo atual" series={[{ nome: "Saldo projetado", cor: "var(--c1)", valores: [r.saldoInicial, ...r.meses.map((m) => m.saldoFinal)] }]} />

                <details className="text-sm text-muted">
                  <summary className="cursor-pointer text-primary">Como a projeção é calculada</summary>
                  <ul className="mt-2 list-disc space-y-1 pl-5">
                    <li>Saldo inicial: saldo atual das contas.</li>
                    <li>Entradas e saídas conhecidas: pendências (inclui parcelas futuras), faturas de cartão já lançadas (no vencimento) e recorrências ainda não lançadas.</li>
                    <li>Variável: média dos 3 últimos meses completos de gastos sem recorrência nem parcela, corrigida pela inflação informada; no mês atual conta só o que ainda falta.</li>
                    <li>Não inclui receita variável nem imprevistos; o pagamento de compras novas no cartão é contado no mês da compra.</li>
                  </ul>
                </details>
              </>
            )}
          </div>
        )}
      </Card>

      <CortesPanel fluxo={r} />

      <Card aria-labelledby="t-ins">
        <CardTitulo
          id="t-ins"
          acao={
            ocultos > 0 ? (
              <Button tamanho="sm" onClick={restaurar}>
                Mostrar {ocultos} dispensado(s)
              </Button>
            ) : undefined
          }
        >
          Insights de economia
        </CardTitulo>
        {visiveis.length === 0 ? (
          <p className="py-4 text-center text-sm text-muted">
            {insights.length === 0 ? "Nenhuma sugestão por enquanto. Com mais lançamentos, metas com prazo e recorrências, as regras abaixo passam a mostrar oportunidades." : "Você dispensou todos os insights atuais."}
          </p>
        ) : (
          <ul className="space-y-3">
            {visiveis.map((i) => (
              <li key={i.id} className="rounded-xl border border-line p-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-medium">{i.titulo}</p>
                    <p className="text-sm">
                      Estimativa: <Money valor={i.valorMensal / 100} className="font-semibold" />/mês
                    </p>
                  </div>
                  <Button tamanho="sm" variante="ghost" icone={<X size={14} aria-hidden />} onClick={() => dispensar(i.id)} aria-label={`Dispensar: ${i.titulo}`}>
                    Dispensar
                  </Button>
                </div>
                <details className="mt-1 text-sm">
                  <summary className="cursor-pointer text-primary">Ver regra e cálculo</summary>
                  <p className="mt-1 text-muted">Regra: {i.regra}</p>
                  <ul className="mt-1 list-disc pl-5">
                    {i.calculo.map((l) => (
                      <li key={l}>{l}</li>
                    ))}
                  </ul>
                </details>
              </li>
            ))}
          </ul>
        )}
        <p className="mt-3 text-xs text-muted">Sugestões por regras simples sobre os seus lançamentos; não consideram sua situação completa nem constituem recomendação personalizada. Metas usam o rendimento líquido de {fmtPctFracao(cdi)} a.m. (CDI da tela de índices, 100%, IR regressivo).</p>
      </Card>
    </div>
  );
}
