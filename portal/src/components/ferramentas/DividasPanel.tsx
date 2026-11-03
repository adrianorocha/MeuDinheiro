"use client";

import Link from "next/link";
import { useState } from "react";
import { Input, InputValor } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { custoRotativo } from "@/lib/finance/custoRotativo";
import { brlCentavos } from "@/lib/finance/centavos";
import { dataQuitacao, paraSim, ROTULO_TIPO_DIVIDA, type ResultadoSim } from "@/lib/finance/dividas";
import { compararComAjustes, type AjusteDivida } from "@/lib/finance/dividasSimulacao";
import { fromCents } from "@/lib/finance/money";
import { formatMesAno } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { useIndices } from "@/lib/indicesStore";
import { AvisoReferencia, fmtPctFracao, lerNum, paraCampo } from "./util";

function Rotativo() {
  const extras = useIndices((s) => s.conjunto.extras);
  const [saldo, setSaldo] = useState("");
  const [tipo, setTipo] = useState<"ROTATIVO" | "CHEQUE">("ROTATIVO");
  const [taxa, setTaxa] = useState<string | null>(null);
  const padrao = tipo === "ROTATIVO" ? extras.taxaRotativoMensal : extras.taxaChequeEspecialMensal;
  const taxaTxt = taxa ?? paraCampo(padrao);
  const r = saldo.trim() ? custoRotativo(lerNum(saldo), lerNum(taxaTxt) / 100) : null;
  return (
    <Card aria-labelledby="t-rot">
      <CardTitulo id="t-rot">Quanto custa ficar no rotativo ou no cheque especial?</CardTitulo>
      <div className="grid gap-3 sm:grid-cols-3">
        <InputValor rotulo="Saldo devedor (R$)" value={saldo} onChange={(e) => setSaldo(e.target.value)} />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="rot-tipo" className="text-sm font-medium">Tipo</label>
          <select
            id="rot-tipo"
            className="h-10 w-full rounded-lg border border-line bg-surface px-3 text-sm"
            value={tipo}
            onChange={(e) => {
              setTipo(e.target.value as "ROTATIVO" | "CHEQUE");
              setTaxa(null);
            }}
          >
            <option value="ROTATIVO">Rotativo do cartão</option>
            <option value="CHEQUE">Cheque especial</option>
          </select>
        </div>
        <InputValor rotulo="Taxa (% a.m.)" value={taxaTxt} onChange={(e) => setTaxa(e.target.value)} dica="Estimativa de referência: edite com a taxa do seu contrato" />
      </div>
      <div className="mt-4" aria-live="polite">
        {!r ? (
          <p className="text-sm text-muted">Informe o saldo devedor e a taxa para ver quanto a dívida cresce em 1 e em 3 meses sem pagamento.</p>
        ) : (
          <div className="grid gap-2 text-sm sm:grid-cols-3">
            <div className="rounded-xl bg-surface-2 px-3 py-2">
              <p className="text-xs text-muted">1 mês de atraso</p>
              <p className="font-semibold"><Money valor={r.m1.juros} /> de juros</p>
              <p className="text-xs text-muted">dívida: <Money valor={r.m1.total} /></p>
            </div>
            <div className="rounded-xl bg-surface-2 px-3 py-2">
              <p className="text-xs text-muted">3 meses de atraso</p>
              <p className="font-semibold"><Money valor={r.m3.juros} /> de juros</p>
              <p className="text-xs text-muted">dívida: <Money valor={r.m3.total} /></p>
            </div>
            <div className="rounded-xl bg-surface-2 px-3 py-2">
              <p className="text-xs text-muted">Taxa anual equivalente</p>
              <p className="font-semibold">{fmtPctFracao(r.taxaAnual, 1)}</p>
            </div>
          </div>
        )}
      </div>
      <p className="mt-3 text-xs text-muted">Juros compostos mensais sobre o saldo, sem multa, IOF ou outros encargos; a regra do seu contrato pode limitar ou acrescentar custos.</p>
    </Card>
  );
}

const ROTULO: Record<string, string> = { MINIMOS: "Só os mínimos", BOLA_DE_NEVE: "Bola de neve (menor saldo primeiro)", AVALANCHE: "Avalanche (maior juro primeiro)" };

export function DividasPanel() {
  const ds = useDataset();
  const agora = useAgora();
  const [extraTxt, setExtraTxt] = useState("0");
  const [ajustes, setAjustes] = useState<Record<number, { taxa?: string; minimo?: string }>>({});
  const ativas = ds.dividas.filter((d) => paraSim(d) !== null);

  const ajustesNum: Record<number, AjusteDivida> = {};
  for (const [id, a] of Object.entries(ajustes)) {
    const t = a.taxa?.trim() ? lerNum(a.taxa) : undefined;
    const m = a.minimo?.trim() ? lerNum(a.minimo) : undefined;
    ajustesNum[Number(id)] = { taxaPct: t !== undefined && Number.isFinite(t) ? t : undefined, minimo: m !== undefined && Number.isFinite(m) ? m : undefined };
  }
  const extra = lerNum(extraTxt);
  const comp = compararComAjustes(ativas, ajustesNum, Number.isFinite(extra) ? extra : 0);
  const ref = { ano: new Date(agora).getFullYear(), mes: new Date(agora).getMonth() + 1 };
  const linhas: ResultadoSim[] = [comp.minimos, comp.bolaDeNeve, comp.avalanche];
  const fmtMes = (r: ResultadoSim) => {
    const d = dataQuitacao(ref, r.meses);
    return d ? formatMesAno(d.mes, d.ano) : "—";
  };
  const eco = comp.economia;

  return (
    <div className="space-y-4">
      <AvisoReferencia>Simulação sobre as dívidas cadastradas; os ajustes de taxa e mínimo valem só aqui e não alteram o cadastro.</AvisoReferencia>
      <Card aria-labelledby="t-quit">
        <CardTitulo id="t-quit">Simulador de quitação: avalanche × bola de neve</CardTitulo>
        {ativas.length === 0 ? (
          <EmptyState
            titulo="Nenhuma dívida ativa cadastrada"
            descricao="Cadastre suas dívidas para comparar as formas de quitação e ver quanto de juros dá para economizar."
            acao={<Link href="/dividas/" className="inline-flex h-10 items-center rounded-lg bg-primary px-4 text-sm font-medium text-primary-fg">Ir para Dívidas</Link>}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px] text-sm">
                <caption className="sr-only">Dívidas ativas e ajustes do simulador</caption>
                <thead>
                  <tr className="text-left text-xs uppercase text-muted">
                    <th scope="col" className="py-1 pr-3">Dívida</th>
                    <th scope="col" className="py-1 pr-3 text-right">Saldo</th>
                    <th scope="col" className="py-1 pr-3">Juros (% a.m.)</th>
                    <th scope="col" className="py-1">Mínimo mensal (R$)</th>
                  </tr>
                </thead>
                <tbody>
                  {ativas.map((d) => (
                    <tr key={d.id} className="border-t border-line align-top">
                      <th scope="row" className="py-2 pr-3 text-left font-medium">
                        {d.nome} <span className="block text-xs font-normal text-muted">{ROTULO_TIPO_DIVIDA[d.tipo]}</span>
                        {!(d.taxaJurosMensal && d.taxaJurosMensal > 0) && !ajustes[d.id]?.taxa?.trim() && <Badge tom="warn">sem taxa</Badge>}
                      </th>
                      <td className="py-2 pr-3 text-right tabular-nums"><Money valor={d.saldoDevedor} /></td>
                      <td className="py-2 pr-3">
                        <Input rotulo={`Juros de ${d.nome} (% a.m.)`} className="max-w-28" inputMode="decimal" value={ajustes[d.id]?.taxa ?? paraCampo(d.taxaJurosMensal ?? 0)} onChange={(e) => setAjustes((p) => ({ ...p, [d.id]: { ...p[d.id], taxa: e.target.value } }))} />
                      </td>
                      <td className="py-2">
                        <Input rotulo={`Mínimo de ${d.nome} (R$)`} className="max-w-32" inputMode="decimal" value={ajustes[d.id]?.minimo ?? paraCampo(d.parcelaMinima ?? 0, 2)} onChange={(e) => setAjustes((p) => ({ ...p, [d.id]: { ...p[d.id], minimo: e.target.value } }))} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-3 max-w-xs">
              <InputValor rotulo="Valor extra por mês (além dos mínimos)" value={extraTxt} onChange={(e) => setExtraTxt(e.target.value)} />
            </div>
            {comp.avisos.length > 0 && (
              <ul className="mt-3 space-y-1 text-sm text-warn" aria-label="Avisos do simulador">
                {comp.avisos.map((a) => (
                  <li key={a}>Atenção: {a}</li>
                ))}
              </ul>
            )}
            <div className="mt-4 overflow-x-auto" aria-live="polite">
              <table className="w-full min-w-[520px] text-sm">
                <caption className="sr-only">Resultado da simulação por estratégia</caption>
                <thead>
                  <tr className="text-left text-xs uppercase text-muted">
                    <th scope="col" className="py-1 pr-3">Estratégia</th>
                    <th scope="col" className="py-1 pr-3">Meses até quitar</th>
                    <th scope="col" className="py-1 pr-3">Quita em</th>
                    <th scope="col" className="py-1 pr-3 text-right">Juros totais</th>
                    <th scope="col" className="py-1 text-right">Total pago</th>
                  </tr>
                </thead>
                <tbody>
                  {linhas.map((l) => (
                    <tr key={l.estrategia} className="border-t border-line">
                      <th scope="row" className="py-2 pr-3 text-left font-medium">{ROTULO[l.estrategia]}</th>
                      <td className="py-2 pr-3">{l.nuncaQuita ? <Badge tom="neg">não quita em 600 meses</Badge> : l.meses}</td>
                      <td className="py-2 pr-3">{fmtMes(l)}</td>
                      <td className="py-2 pr-3 text-right tabular-nums">{l.nuncaQuita ? "—" : <Money valor={fromCents(l.jurosTotal)} />}</td>
                      <td className="py-2 text-right tabular-nums">{l.nuncaQuita ? "—" : <Money valor={fromCents(l.pagoTotal)} />}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-3 space-y-0.5 text-sm">
              {eco.avalancheVsBola !== null && <p>Diferença de juros entre avalanche e bola de neve: <strong>{brlCentavos(eco.avalancheVsBola)}</strong> (positivo = avalanche paga menos juros).</p>}
              {eco.avalancheVsMinimos !== null && <p>Avalanche versus pagar só o mínimo: <strong>{brlCentavos(eco.avalancheVsMinimos)}</strong> a menos em juros.</p>}
              {eco.bolaVsMinimos !== null && <p>Bola de neve versus pagar só o mínimo: <strong>{brlCentavos(eco.bolaVsMinimos)}</strong> a menos em juros.</p>}
            </div>
            <p className="mt-3 text-xs text-muted">Juros compostos mensais em centavos; o mínimo de uma dívida quitada é repassado às demais. A bola de neve pode ter apelo de motivação (quita dívidas pequenas cedo) mesmo pagando mais juros. O cronograma completo está na tela Dívidas.</p>
          </>
        )}
      </Card>
      <Rotativo />
    </div>
  );
}
