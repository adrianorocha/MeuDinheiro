"use client";

import { useMemo, useState } from "react";
import { Input, InputValor } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, PageHeader, ProgressBar } from "@/components/ui/Misc";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { Money } from "@/components/ui/Money";
import {
  detectarAssinaturas,
  regra503020,
  reservaEmergencia,
  simularParcelamento,
  totaisAssinaturas,
  MESES_META_RESERVA,
  type GrupoRegra,
} from "@/lib/finance/analises";
import { mesAnoDe } from "@/lib/finance/dates";
import { formatData, formatPercentual, parseValorBR } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { useSession } from "@/lib/session";

function Reserva() {
  const ds = useDataset();
  const agora = useAgora();
  const privado = useSession((s) => s.privado);
  const r = useMemo(() => reservaEmergencia(ds, agora), [ds, agora]);
  const tom = r.status === "OK" ? "pos" : r.status === "ATENCAO" ? "warn" : "neg";
  return (
    <Card aria-labelledby="t-reserva">
      <CardTitulo id="t-reserva" acao={r.status && <Badge tom={tom}>{r.status === "OK" ? "Reserva ok" : r.status === "ATENCAO" ? "Atenção" : "Crítico"}</Badge>}>
        Reserva de emergência
      </CardTitulo>
      {r.mediaDespesas3m === null ? (
        <p className="text-sm text-muted">Sem lançamentos nos 3 meses anteriores para estimar sua despesa mensal.</p>
      ) : (
        <>
          <p className="text-sm">
            Você cobre <strong className="tabular">{r.meses === null ? "—" : privado ? "••" : r.meses.toFixed(1).replace(".", ",")} meses</strong> de despesas (meta: {MESES_META_RESERVA}).
          </p>
          <div className="mt-2">
            <ProgressBar razao={r.meses === null ? 1 : r.meses / MESES_META_RESERVA} tom={tom} rotulo="Cobertura da reserva em meses" />
          </div>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            <div>
              <dt className="text-xs text-muted">Despesa média (3 meses)</dt>
              <dd className="font-semibold"><Money valor={r.mediaDespesas3m} /></dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Liquidez (contas + renda fixa)</dt>
              <dd className="font-semibold"><Money valor={r.liquidez} /></dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Meta ({MESES_META_RESERVA} meses)</dt>
              <dd className="font-semibold">{r.meta !== null && <Money valor={r.meta} />}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Faltante</dt>
              <dd className="font-semibold">{r.faltante !== null && <Money valor={r.faltante} />}</dd>
            </div>
          </dl>
        </>
      )}
    </Card>
  );
}

function Assinaturas() {
  const ds = useDataset();
  const agora = useAgora();
  const lista = useMemo(() => detectarAssinaturas(ds, agora), [ds, agora]);
  const total = totaisAssinaturas(lista);
  return (
    <Card aria-labelledby="t-assin">
      <CardTitulo id="t-assin">Assinaturas e gastos recorrentes</CardTitulo>
      {lista.length === 0 ? (
        <p className="py-4 text-center text-sm text-muted">Nenhuma assinatura encontrada. Recorrências cadastradas e gastos que se repetem todo mês aparecem aqui.</p>
      ) : (
        <>
          <div className="mb-3 grid grid-cols-2 gap-3 text-sm">
            <div className="rounded-xl bg-surface-2 px-3 py-2">
              <p className="text-xs text-muted">Total mensal</p>
              <p className="font-semibold"><Money valor={total.mensal} /></p>
            </div>
            <div className="rounded-xl bg-surface-2 px-3 py-2">
              <p className="text-xs text-muted">Total anual</p>
              <p className="font-semibold"><Money valor={total.anual} /></p>
            </div>
          </div>
          <ul className="divide-y divide-line">
            {lista.map((a) => (
              <li key={`${a.origem}-${a.nome}`} className="flex items-center gap-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{a.nome}</p>
                  <p className="text-xs text-muted">
                    {a.categoria} · {a.ultimaData ? `último em ${formatData(a.ultimaData)}` : "ainda não lançada"}
                  </p>
                </div>
                <Badge tom={a.origem === "FIXA" ? "primary" : "neutro"}>{a.origem === "FIXA" ? "Fixa" : "Detectada"}</Badge>
                <div className="text-right text-sm">
                  <Money valor={a.totalMensal} className="font-semibold" />
                  <p className="text-xs text-muted">
                    <Money valor={a.totalAnual} />/ano
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </Card>
  );
}

function Simulador() {
  const [aVista, setAVista] = useState("");
  const [n, setN] = useState("10");
  const [parcela, setParcela] = useState("");
  const [taxa, setTaxa] = useState("1,0");
  const [entrada, setEntrada] = useState("");
  const privado = useSession((s) => s.privado);

  const r = useMemo(() => {
    const v = parseValorBR(aVista);
    const p = parseValorBR(parcela);
    const num = Number(n);
    const t = parseValorBR(taxa);
    const e = entrada.trim() ? parseValorBR(entrada) : 0;
    if (!(v > 0) || !(p > 0) || !Number.isInteger(num) || num < 1 || Number.isNaN(t) || t < 0 || Number.isNaN(e) || e < 0) return null;
    return simularParcelamento({ valorAVista: v, n: num, valorParcela: p, taxaMensal: t / 100, entrada: e });
  }, [aVista, n, parcela, taxa, entrada]);

  return (
    <Card aria-labelledby="t-sim">
      <CardTitulo id="t-sim">Simulador: parcelar × à vista</CardTitulo>
      <div className="grid gap-3 sm:grid-cols-3">
        <InputValor rotulo="Valor à vista (R$)" value={aVista} onChange={(e) => setAVista(e.target.value)} />
        <Input rotulo="Nº de parcelas" type="number" min={1} value={n} onChange={(e) => setN(e.target.value)} />
        <InputValor rotulo="Valor da parcela (R$)" value={parcela} onChange={(e) => setParcela(e.target.value)} />
        <InputValor rotulo="Entrada (R$)" value={entrada} onChange={(e) => setEntrada(e.target.value)} />
        <InputValor rotulo="Rendimento (% a.m.)" value={taxa} onChange={(e) => setTaxa(e.target.value)} placeholder="1,0" />
      </div>
      <div className="mt-4" aria-live="polite">
        {r === null ? (
          <p className="text-sm text-muted">Preencha o valor à vista, as parcelas e o valor da parcela.</p>
        ) : (
          <div className="space-y-1 text-sm">
            <p>
              <Badge tom={r.parcelarVale ? "pos" : "warn"}>{r.parcelarVale ? "Parcelar vale a pena" : "Pagar à vista compensa"}</Badge>
            </p>
            <p>
              Valor presente do parcelamento: <Money valor={r.valorPresente} className="font-semibold" />
            </p>
            <p>
              {r.diferenca >= 0 ? "Economia em valor presente" : "Custo extra em valor presente"}: <Money valor={Math.abs(r.diferenca)} className="font-semibold" />
            </p>
            <p>
              Juros implícitos: <strong className="tabular">{privado ? "••••" : `${(r.jurosImplicitosMensais * 100).toFixed(2).replace(".", ",")}% a.m.`}</strong>
            </p>
          </div>
        )}
      </div>
    </Card>
  );
}

function LinhaRegra({ nome, g, alvoRotulo }: { nome: string; g: GrupoRegra; alvoRotulo: string }) {
  const tom = g.status === "OK" ? "pos" : "warn";
  return (
    <li>
      <div className="mb-1 flex items-center justify-between gap-2 text-sm">
        <span className="font-medium">
          {nome} <span className="text-xs text-muted">({alvoRotulo})</span>
        </span>
        <span className="flex items-center gap-2">
          <Money valor={g.valor} /> · {formatPercentual(g.percentual)}
          <Badge tom={tom}>{g.status === "OK" ? "OK" : g.status === "ACIMA" ? "Acima" : "Abaixo"}</Badge>
        </span>
      </div>
      <ProgressBar razao={g.percentual} tom={tom === "pos" ? "primary" : "warn"} rotulo={`${nome}: ${formatPercentual(g.percentual)} da receita`} />
    </li>
  );
}

function Painel503020() {
  const ds = useDataset();
  const agora = useAgora();
  const [ref, setRef] = useState(() => mesAnoDe(agora));
  const r = useMemo(() => regra503020(ds.despesas, ref.mes, ref.ano), [ds.despesas, ref]);
  return (
    <Card aria-labelledby="t-503020">
      <CardTitulo id="t-503020" acao={<MonthPicker mes={ref.mes} ano={ref.ano} onChange={setRef} />}>
        Regra 50/30/20
      </CardTitulo>
      {r.receitas <= 0 ? (
        <p className="py-4 text-center text-sm text-muted">Sem receitas realizadas neste mês para calcular os percentuais.</p>
      ) : (
        <p className="mb-3 text-sm text-muted">
          Sobre receitas de <Money valor={r.receitas} />.
        </p>
      )}
      <ul className="space-y-4">
        <LinhaRegra nome="Necessidades" g={r.necessidades} alvoRotulo="até 50%" />
        <LinhaRegra nome="Desejos" g={r.desejos} alvoRotulo="até 30%" />
        <LinhaRegra nome="Poupança" g={r.poupanca} alvoRotulo="pelo menos 20%" />
      </ul>
    </Card>
  );
}

export default function PlanejamentoPage() {
  const ds = useDataset();
  return (
    <>
      <PageHeader titulo="Planejamento" descricao="Reserva, assinaturas, simulador e regra 50/30/20" />
      {ds.contas.length === 0 && ds.despesas.length === 0 && (
        <div className="mb-4">
          <EmptyState titulo="Sem dados ainda" descricao="Cadastre contas e lançamentos para ver as análises. O simulador funciona já." />
        </div>
      )}
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="lg:col-span-2">
          <Reserva />
        </div>
        <Assinaturas />
        <Painel503020 />
        <div className="lg:col-span-2">
          <Simulador />
        </div>
      </div>
    </>
  );
}
