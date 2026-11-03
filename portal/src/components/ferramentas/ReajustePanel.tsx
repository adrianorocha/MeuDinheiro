"use client";

import { useState } from "react";
import { Input, InputValor } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { buscarIndice } from "@/lib/finance/indices";
import { calcularReajustes, type IndiceReajuste } from "@/lib/finance/reajuste";
import { useIndices } from "@/lib/indicesStore";
import { AvisoReferencia, fmtPctFracao, lerNum } from "./util";

const IDS = ["igpm", "ipca", "inpc"] as const;

export function ReajustePanel() {
  const conjunto = useIndices((s) => s.conjunto);
  const [valor, setValor] = useState("");
  const [meses, setMeses] = useState("12");
  const [informados, setInformados] = useState<Record<string, string>>({});
  const [manual, setManual] = useState("");

  const indices: IndiceReajuste[] = IDS.map((id) => {
    const it = buscarIndice(conjunto, id);
    const txt = informados[id];
    const v = txt?.trim() ? lerNum(txt) : null;
    return { id, nome: it.nome, serie: it.serie, acumuladoInformadoPct: v !== null && Number.isFinite(v) ? v : null };
  });
  if (manual.trim() && Number.isFinite(lerNum(manual))) indices.push({ id: "manual", nome: "Percentual informado", serie: [], acumuladoInformadoPct: lerNum(manual) });
  const linhas = valor.trim() ? calcularReajustes(lerNum(valor), Number(meses), indices) : null;
  const incompleta = linhas?.some((l) => l.incompleto) ?? false;

  return (
    <div className="space-y-4">
      <AvisoReferencia>Reajuste calculado com os índices de referência editáveis; confira o índice e o período previstos no seu contrato.</AvisoReferencia>
      <Card aria-labelledby="t-reaj">
        <CardTitulo id="t-reaj">Reajuste de aluguel e mensalidades</CardTitulo>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <InputValor rotulo="Valor atual (R$)" value={valor} onChange={(e) => setValor(e.target.value)} />
          <Input rotulo="Meses de acumulado" type="number" min={1} max={120} value={meses} onChange={(e) => setMeses(e.target.value)} dica="Composição dos últimos N meses disponíveis de cada índice" />
          <InputValor rotulo="Outro percentual acumulado (%)" value={manual} onChange={(e) => setManual(e.target.value)} dica="Opcional; ex.: 4,5 para um reajuste combinado" />
        </div>
        <fieldset className="mt-3">
          <legend className="mb-1 text-sm font-medium">Acumulado oficial de {meses || "N"} meses (opcional; substitui a composição)</legend>
          <div className="grid gap-3 sm:grid-cols-3">
            {IDS.map((id) => (
              <InputValor key={id} rotulo={`${buscarIndice(conjunto, id).nome} acumulado (%)`} value={informados[id] ?? ""} onChange={(e) => setInformados((p) => ({ ...p, [id]: e.target.value }))} />
            ))}
          </div>
        </fieldset>

        <div className="mt-4" aria-live="polite">
          {!valor.trim() ? (
            <p className="text-sm text-muted">Informe o valor atual do aluguel ou da mensalidade para ver o reajuste por cada índice.</p>
          ) : !linhas ? (
            <p className="text-sm text-neg" role="alert">Informe um valor maior que zero e de 1 a 120 meses.</p>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[520px] text-sm">
                  <caption className="sr-only">Reajuste por índice</caption>
                  <thead>
                    <tr className="text-left text-xs uppercase text-muted">
                      <th scope="col" className="py-1 pr-3">Índice</th>
                      <th scope="col" className="py-1 pr-3 text-right">Acumulado</th>
                      <th scope="col" className="py-1 pr-3 text-right">Novo valor</th>
                      <th scope="col" className="py-1 text-right">Diferença</th>
                    </tr>
                  </thead>
                  <tbody>
                    {linhas.map((l) => (
                      <tr key={l.id} className="border-t border-line">
                        <th scope="row" className="py-2 pr-3 text-left font-medium">
                          {l.nome}{" "}
                          {l.informado ? <Badge tom="primary">informado</Badge> : l.incompleto ? <Badge tom="warn">{l.mesesUsados} de {meses} meses</Badge> : <Badge>{l.mesesUsados} meses</Badge>}
                        </th>
                        <td className="py-2 pr-3 text-right tabular-nums">{fmtPctFracao(l.acumulado, 4)}</td>
                        <td className="py-2 pr-3 text-right tabular-nums"><Money valor={l.novoValor / 100} className="font-semibold" /></td>
                        <td className="py-2 text-right tabular-nums"><Money valor={l.diferenca / 100} tom="auto" /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {incompleta && (
                <p className="mt-2 text-sm text-warn" role="status">
                  Só há alguns meses de cada índice na tela de índices; o cálculo usa os meses disponíveis. Para o reajuste anual, informe o acumulado oficial de 12 meses acima ou edite a série de meses em Índices.
                </p>
              )}
              <p className="mt-2 text-xs text-muted">Índice negativo reduz o valor; verifique se o contrato prevê piso. Cálculo por composição (1 + i1) × (1 + i2) × ... − 1, valor arredondado ao centavo.</p>
            </>
          )}
        </div>
      </Card>
    </div>
  );
}
