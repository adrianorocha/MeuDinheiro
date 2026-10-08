"use client";

import { CreditCard, Receipt } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { GraficoPatrimonio, GraficoPizza } from "@/components/charts/Graficos";
import { AlertasOrcamento } from "@/components/layout/AlertasOrcamento";
import { Segmentado } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, PageHeader, ProgressBar } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import {
  kpisDoMes,
  kpisPeriodo,
  patrimonioLiquido,
  previsaoMes,
  progressoOrcamento,
  saldoTotalContas,
  saudeFinanceira,
  type StatusSaude,
} from "@/lib/finance/calc";
import { normalizaMes, mesAnoDe } from "@/lib/finance/dates";
import { formatData, formatPercentual } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { contasAPagar, despesasPorCategoria, faturasEmAberto, periodoDe, ultimosSnapshots, type PeriodoId } from "@/lib/selectors";
import { useSession } from "@/lib/session";

const ROTULO_SAUDE: Record<StatusSaude, { texto: string; tom: "pos" | "warn" | "neg" }> = {
  SAUDAVEL: { texto: "Saudável", tom: "pos" },
  ALERTA: { texto: "Alerta", tom: "warn" },
  PERIGO: { texto: "Perigo", tom: "neg" },
};

function Kpi({ rotulo, children, destaque, legenda }: { rotulo: string; children: React.ReactNode; destaque?: boolean; legenda?: React.ReactNode }) {
  return (
    <Card className={destaque ? "border-primary/40 bg-primary-soft" : ""}>
      <p className="text-xs font-medium text-muted">{rotulo}</p>
      <p className="mt-1 text-xl font-semibold tracking-tight sm:text-2xl">{children}</p>
      {legenda && <p className="mt-1 text-xs text-muted">{legenda}</p>}
    </Card>
  );
}

export default function DashboardPage() {
  const ds = useDataset();
  const agora = useAgora();
  const privado = useSession((s) => s.privado);
  const [periodoId, setPeriodoId] = useState<PeriodoId>("mes");

  const periodo = useMemo(() => periodoDe(periodoId, agora), [periodoId, agora]);
  const kpis = useMemo(() => kpisPeriodo(ds.despesas, periodo.inicio, periodo.fim), [ds.despesas, periodo]);
  const patrimonio = useMemo(() => patrimonioLiquido(ds), [ds]);
  const saldoContas = useMemo(() => saldoTotalContas(ds), [ds]);
  const previsao = useMemo(() => previsaoMes(ds, agora), [ds, agora]);
  const { mes, ano } = mesAnoDe(agora);

  const saude = useMemo(() => {
    const ant = normalizaMes(mes - 1, ano);
    const atual = kpisDoMes(ds.despesas, mes, ano);
    const anterior = kpisDoMes(ds.despesas, ant.mes, ant.ano);
    return saudeFinanceira(atual.receitasRealizadas, atual.despesasTotal, anterior.despesasTotal);
  }, [ds.despesas, mes, ano]);

  const pizza = useMemo(
    () => despesasPorCategoria(ds.despesas, periodo.inicio, periodo.fim).map((c) => ({ nome: c.categoria, valor: c.valor })),
    [ds.despesas, periodo],
  );
  const evolucao = useMemo(() => ultimosSnapshots(ds, 12).map((p) => ({ rotulo: p.mesReferencia, valor: p.valorTotal })), [ds]);

  const proximos = useMemo(() => {
    const itens = [
      ...contasAPagar(ds.despesas, agora).map((c) => ({
        chave: `d${c.despesa.id}`,
        titulo: c.despesa.descricao,
        data: c.despesa.data,
        valor: c.despesa.valor,
        atrasada: c.atrasada,
        fatura: false,
      })),
      ...faturasEmAberto(ds).map((f) => ({
        chave: `f${f.cartaoId}-${f.ano}-${f.mes}`,
        titulo: `Fatura ${ds.cartoes.find((c) => c.id === f.cartaoId)?.nome ?? "cartão"} ${String(f.mes).padStart(2, "0")}/${f.ano}`,
        data: f.vencimento,
        valor: f.total,
        atrasada: f.vencimento < agora - 86_400_000,
        fatura: true,
      })),
    ];
    return itens.sort((a, b) => a.data - b.data).slice(0, 6);
  }, [ds, agora]);

  const orcamentos = useMemo(
    () => ds.orcamentos.map((o) => progressoOrcamento(o, ds.despesas, mes, ano)).sort((a, b) => b.pct - a.pct).slice(0, 5),
    [ds.orcamentos, ds.despesas, mes, ano],
  );

  const vazio = ds.contas.length === 0 && ds.despesas.length === 0;
  const tomPrevisao = previsao.status === "SEGURO" ? "pos" : previsao.status === "ATENCAO" ? "warn" : "neg";
  const saudeInfo = ROTULO_SAUDE[saude.status];

  return (
    <>
      <PageHeader titulo="Visão geral" descricao="Resumo das suas finanças" />
      <AlertasOrcamento />
      {vazio && (
        <div className="mb-5">
          <EmptyState
            titulo="Nenhum dado ainda"
            descricao="Cadastre uma conta para começar, importe um backup do app ou carregue dados de exemplo em Configurações."
            acao={
              <Link href="/contas/" className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-fg">
                Cadastrar conta
              </Link>
            }
          />
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <Kpi rotulo="Patrimônio líquido" destaque>
          <Money valor={patrimonio} />
        </Kpi>
        <Kpi rotulo="Saldo total das contas">
          <Money valor={saldoContas} />
        </Kpi>
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">Período</h2>
        <div className="w-full sm:w-80">
          <Segmentado
            rotulo="Período"
            valor={periodoId}
            onChange={setPeriodoId}
            opcoes={[
              { valor: "mes", rotulo: "Este mês" },
              { valor: "anterior", rotulo: "Mês passado" },
              { valor: "total", rotulo: "Total" },
            ]}
          />
        </div>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi rotulo="Receitas">
          <Money valor={kpis.receitasRealizadas} />
        </Kpi>
        <Kpi
          rotulo="Despesas"
          legenda={
            <>
              pagas <Money valor={kpis.despesasPagas} /> · a pagar <Money valor={kpis.despesasPendentes} />
            </>
          }
        >
          <Money valor={kpis.despesasTotal} />
        </Kpi>
        <Kpi rotulo="Resultado">
          <Money valor={kpis.resultado} tom="auto" />
        </Kpi>
        <Kpi rotulo="Taxa de poupança">
          <span className="tabular">{privado ? "••••" : formatPercentual(kpis.taxaPoupanca)}</span>
        </Kpi>
      </div>
      {(kpis.receitasPrevistas > 0 || kpis.despesasPendentes > 0) && (
        <p className="mt-2 text-xs text-muted">
          No período: <Money valor={kpis.receitasPrevistas} /> de receitas previstas e <Money valor={kpis.despesasPendentes} /> de despesas pendentes.
        </p>
      )}
      <p className="mt-1 text-xs text-muted">
        Receitas só contam o que já foi recebido e Despesas incluem o pendente, então Receitas − Despesas não é o saldo.{" "}
        <Link href="/conferencia/" className="font-medium text-primary hover:underline">
          Conferir saldos
        </Link>
      </p>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <Card aria-labelledby="t-previsao">
          <CardTitulo id="t-previsao" acao={<Badge tom={tomPrevisao}>{previsao.rotulo}</Badge>}>
            Previsão do mês
          </CardTitulo>
          <p className="text-2xl font-semibold">
            <Money valor={previsao.saldoLivrePrevisto} tom="auto" />
          </p>
          <p className="mt-1 text-sm text-muted">
            Saldo livre previsto até o fim do mês, considerando pendências e faturas a vencer. Margem sobre o saldo atual:{" "}
            <strong className="tabular">{privado ? "••••" : formatPercentual(previsao.margem)}</strong>.
          </p>
        </Card>

        <Card aria-labelledby="t-saude">
          <CardTitulo id="t-saude" acao={<Badge tom={saudeInfo.tom}>{saudeInfo.texto}</Badge>}>
            Saúde financeira
          </CardTitulo>
          <p className="text-sm">
            Você comprometeu <strong className="tabular">{privado ? "••••" : formatPercentual(saude.consumo)}</strong> das receitas do mês com despesas.
          </p>
          <div className="mt-3">
            <ProgressBar razao={saude.consumo} tom={saudeInfo.tom} rotulo="Consumo da receita no mês" />
          </div>
          <p className="mt-2 text-xs text-muted">
            Gastos {saude.variacaoGastos >= 0 ? "acima" : "abaixo"} do mês passado em {Math.abs(saude.variacaoGastos).toFixed(1).replace(".", ",")}%.
          </p>
        </Card>

        <Card aria-labelledby="t-pizza">
          <CardTitulo id="t-pizza">Despesas por categoria · {periodo.rotulo}</CardTitulo>
          {pizza.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted">Sem despesas no período.</p>
          ) : (
            <GraficoPizza dados={pizza.slice(0, 8)} rotulo="Gráfico de pizza das despesas por categoria" />
          )}
        </Card>

        <Card aria-labelledby="t-evolucao">
          <CardTitulo id="t-evolucao">Evolução patrimonial</CardTitulo>
          {evolucao.length < 2 ? (
            <p className="py-8 text-center text-sm text-muted">O histórico aparece conforme os meses passam.</p>
          ) : (
            <GraficoPatrimonio dados={evolucao} rotulo="Evolução do patrimônio nos últimos 12 meses" />
          )}
        </Card>

        <Card aria-labelledby="t-pagar">
          <CardTitulo id="t-pagar" acao={<Link href="/pendencias/" className="text-sm font-medium text-primary hover:underline">Ver todas</Link>}>
            Contas a pagar
          </CardTitulo>
          {proximos.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Nada pendente.</p>
          ) : (
            <ul className="divide-y divide-line">
              {proximos.map((p) => (
                <li key={p.chave} className="flex items-center gap-3 py-2.5">
                  <span className="text-muted" aria-hidden>
                    {p.fatura ? <CreditCard size={18} /> : <Receipt size={18} />}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{p.titulo}</p>
                    <p className="text-xs text-muted">{formatData(p.data)}</p>
                  </div>
                  {p.atrasada && <Badge tom="neg">Atrasada</Badge>}
                  <Money valor={p.valor} className="text-sm font-semibold" />
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card aria-labelledby="t-orc">
          <CardTitulo id="t-orc" acao={<Link href="/orcamentos/" className="text-sm font-medium text-primary hover:underline">Gerenciar</Link>}>
            Orçamentos do mês
          </CardTitulo>
          {orcamentos.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Nenhum orçamento definido.</p>
          ) : (
            <ul className="space-y-3">
              {orcamentos.map((o) => (
                <li key={o.orcamento.id}>
                  <div className="mb-1 flex items-center justify-between text-sm">
                    <span className="font-medium">{o.orcamento.categoria}</span>
                    <span className="tabular text-xs text-muted">
                      <Money valor={o.gasto} /> / <Money valor={o.limite} /> · {formatPercentual(o.pct)}
                    </span>
                  </div>
                  <ProgressBar razao={o.pct} tom={o.status === "ESTOURADO" ? "neg" : o.status === "ATENCAO" ? "warn" : "primary"} rotulo={`Orçamento de ${o.orcamento.categoria}`} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
