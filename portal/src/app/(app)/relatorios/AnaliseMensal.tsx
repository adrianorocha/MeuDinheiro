"use client";

import { useMemo, useState } from "react";
import { GraficoBarrasMensais } from "@/components/charts/Graficos";
import { Segmentado } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, ProgressBar } from "@/components/ui/Misc";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { Money } from "@/components/ui/Money";
import { kpisDoMes, saudeFinanceira, type StatusSaude } from "@/lib/finance/calc";
import { fimDoMes, inicioDoMes, mesAnoDe, normalizaMes } from "@/lib/finance/dates";
import { formatPercentual } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { despesasPorCategoria, serieMensal } from "@/lib/selectors";
import { useSession } from "@/lib/session";

const SAUDE: Record<StatusSaude, { texto: string; tom: "pos" | "warn" | "neg" }> = {
  SAUDAVEL: { texto: "Saudável", tom: "pos" },
  ALERTA: { texto: "Alerta", tom: "warn" },
  PERIGO: { texto: "Perigo", tom: "neg" },
};

export function AnaliseMensal() {
  const ds = useDataset();
  const agora = useAgora();
  const privado = useSession((s) => s.privado);
  const hoje = mesAnoDe(agora);
  const [janela, setJanela] = useState<"6" | "12">("6");
  const [ref, setRef] = useState(hoje);

  const serie = useMemo(() => serieMensal(ds.despesas, Number(janela), agora), [ds.despesas, janela, agora]);
  const top = useMemo(() => despesasPorCategoria(ds.despesas, inicioDoMes(ref.mes, ref.ano), fimDoMes(ref.mes, ref.ano)).slice(0, 10), [ds.despesas, ref]);
  const totalTop = top.reduce((a, c) => a + c.valor, 0);

  const saude = useMemo(() => {
    const ant = normalizaMes(ref.mes - 1, ref.ano);
    const atual = kpisDoMes(ds.despesas, ref.mes, ref.ano);
    const anterior = kpisDoMes(ds.despesas, ant.mes, ant.ano);
    return { ...saudeFinanceira(atual.receitasRealizadas, atual.despesasTotal, anterior.despesasTotal), atual, anterior };
  }, [ds.despesas, ref]);
  const info = SAUDE[saude.status];

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">Análise mensal</h2>
        <MonthPicker mes={ref.mes} ano={ref.ano} onChange={setRef} rotulo="Mês de referência" />
      </div>

      <Card className="mb-4" aria-labelledby="t-comp">
        <CardTitulo id="t-comp">Comparativo mensal</CardTitulo>
        <div className="mb-3 w-48">
          <Segmentado rotulo="Janela" valor={janela} onChange={setJanela} opcoes={[{ valor: "6", rotulo: "6 meses" }, { valor: "12", rotulo: "12 meses" }]} />
        </div>
        <GraficoBarrasMensais
          dados={serie.map((p) => ({ rotulo: p.rotulo, receitas: p.receitasRealizadas, despesas: p.despesasTotal }))}
          rotulo="Receitas e despesas por mês"
        />
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[32rem] text-sm">
            <caption className="sr-only">Receitas, despesas e resultado por mês</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs text-muted">
                <th scope="col" className="py-2 pr-3 font-medium">Mês</th>
                <th scope="col" className="py-2 pr-3 text-right font-medium">Receitas</th>
                <th scope="col" className="py-2 pr-3 text-right font-medium">Despesas</th>
                <th scope="col" className="py-2 pr-3 text-right font-medium">Resultado</th>
                <th scope="col" className="py-2 text-right font-medium">Poupança</th>
              </tr>
            </thead>
            <tbody>
              {[...serie].reverse().map((p) => (
                <tr key={p.rotulo} className="border-b border-line last:border-0">
                  <th scope="row" className="py-2 pr-3 text-left font-medium">{p.rotulo}</th>
                  <td className="py-2 pr-3 text-right"><Money valor={p.receitasRealizadas} /></td>
                  <td className="py-2 pr-3 text-right"><Money valor={p.despesasTotal} /></td>
                  <td className="py-2 pr-3 text-right"><Money valor={p.resultado} tom="auto" /></td>
                  <td className="tabular py-2 text-right">{privado ? "••••" : formatPercentual(p.taxaPoupanca)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card aria-labelledby="t-top">
          <CardTitulo id="t-top">Top categorias do mês</CardTitulo>
          {top.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Sem despesas neste mês.</p>
          ) : (
            <ol className="space-y-3">
              {top.map((c, i) => (
                <li key={c.categoria}>
                  <div className="mb-1 flex items-center justify-between text-sm">
                    <span className="font-medium">
                      {i + 1}. {c.categoria}
                    </span>
                    <span className="text-muted">
                      <Money valor={c.valor} /> · {totalTop > 0 ? formatPercentual(c.valor / totalTop) : ""}
                    </span>
                  </div>
                  <ProgressBar razao={totalTop > 0 ? c.valor / totalTop : 0} rotulo={`Participação de ${c.categoria}`} />
                </li>
              ))}
            </ol>
          )}
        </Card>

        <Card aria-labelledby="t-saude">
          <CardTitulo id="t-saude" acao={<Badge tom={info.tom}>{info.texto}</Badge>}>
            Saúde financeira
          </CardTitulo>
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-xs text-muted">Receitas do mês</dt>
              <dd className="font-semibold"><Money valor={saude.atual.receitasRealizadas} /></dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Despesas do mês</dt>
              <dd className="font-semibold"><Money valor={saude.atual.despesasTotal} /></dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Despesas do mês anterior</dt>
              <dd className="font-semibold"><Money valor={saude.anterior.despesasTotal} /></dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Variação dos gastos</dt>
              <dd className="tabular font-semibold">{privado ? "••••" : `${saude.variacaoGastos >= 0 ? "+" : ""}${saude.variacaoGastos.toFixed(1).replace(".", ",")}%`}</dd>
            </div>
          </dl>
          <p className="mt-4 text-sm text-muted">
            Consumo da receita: <strong className="tabular text-fg">{privado ? "••••" : formatPercentual(saude.consumo)}</strong>
          </p>
          <div className="mt-2">
            <ProgressBar razao={saude.consumo} tom={info.tom} rotulo="Consumo da receita" />
          </div>
          <p className="mt-2 text-xs text-muted">Saudável abaixo de 70%, alerta a partir de 70% e perigo a partir de 90% da receita.</p>
        </Card>
      </div>
    </>
  );
}
