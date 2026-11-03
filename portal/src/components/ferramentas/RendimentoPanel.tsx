"use client";

import { useState } from "react";
import { Input, InputValor } from "@/components/ui/Field";
import { Card, CardTitulo } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { anualDeMensal, buscarIndice, pctParaFracao, valorMensalPct } from "@/lib/finance/indices";
import { aliquotaIR, compararRendimento, poupancaPelaRegra, validarRendimento, type EntradaRendimento } from "@/lib/finance/rendimento";
import { useIndices } from "@/lib/indicesStore";
import { GraficoLinhas } from "./Grafico";
import { AvisoReferencia, fmtPctFracao, lerNum, paraCampo, Resultado } from "./util";

export function RendimentoPanel() {
  const conjunto = useIndices((s) => s.conjunto);
  const cdiPadrao = valorMensalPct(buscarIndice(conjunto, "cdi"));
  const poupPadrao = valorMensalPct(buscarIndice(conjunto, "poupanca"));
  const ipcaPadrao = valorMensalPct(buscarIndice(conjunto, "ipca"));
  const trPadrao = valorMensalPct(buscarIndice(conjunto, "tr"));
  const selicPadrao = valorMensalPct(buscarIndice(conjunto, "selic"));

  const [inicial, setInicial] = useState("10.000,00");
  const [aporte, setAporte] = useState("0");
  const [meses, setMeses] = useState("12");
  const [pctCdi, setPctCdi] = useState("100");
  const [cdi, setCdi] = useState<string | null>(null);
  const [poup, setPoup] = useState<string | null>(null);
  const [infl, setInfl] = useState<string | null>(null);

  const cdiTxt = cdi ?? paraCampo(cdiPadrao);
  const poupTxt = poup ?? paraCampo(poupPadrao);
  const inflTxt = infl ?? paraCampo(ipcaPadrao);

  const entrada: EntradaRendimento = {
    valorInicial: inicial.trim() ? lerNum(inicial) : 0,
    aporteMensal: aporte.trim() ? lerNum(aporte) : 0,
    meses: Number(meses),
    cdiMensal: pctParaFracao(lerNum(cdiTxt)),
    pctCdi: lerNum(pctCdi) / 100,
    poupancaMensal: pctParaFracao(lerNum(poupTxt)),
    inflacaoMensal: pctParaFracao(lerNum(inflTxt)),
  };
  const erro = validarRendimento(entrada);
  const r = erro === null ? compararRendimento(entrada) : null;

  const selicAnual = conjunto.extras.selicMetaAnual !== null ? conjunto.extras.selicMetaAnual / 100 : anualDeMensal(pctParaFracao(selicPadrao));
  const poupRegra = poupancaPelaRegra(selicAnual, pctParaFracao(trPadrao));

  const real = (c: number) => <Money valor={c / 100} tom="auto" />;

  return (
    <div className="space-y-4">
      <AvisoReferencia />
      <Card aria-labelledby="t-rend">
        <CardTitulo id="t-rend">Onde o dinheiro rende mais? (simulação)</CardTitulo>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <InputValor rotulo="Valor inicial (R$)" value={inicial} onChange={(e) => setInicial(e.target.value)} />
          <InputValor rotulo="Aporte mensal (R$)" value={aporte} onChange={(e) => setAporte(e.target.value)} />
          <Input rotulo="Prazo (meses)" type="number" min={1} max={600} value={meses} onChange={(e) => setMeses(e.target.value)} />
          <InputValor rotulo="% do CDI do produto" value={pctCdi} onChange={(e) => setPctCdi(e.target.value)} dica="100 = rende o mesmo que o CDI" />
          <InputValor rotulo="CDI (% a.m.)" value={cdiTxt} onChange={(e) => setCdi(e.target.value)} dica="Padrão: valor da tela de índices" />
          <InputValor rotulo="Poupança (% a.m.)" value={poupTxt} onChange={(e) => setPoup(e.target.value)} dica={`Pela regra com Selic de ${fmtPctFracao(selicAnual)} a.a.: ${fmtPctFracao(poupRegra, 4)} a.m.`} />
          <InputValor rotulo="Inflação (% a.m.)" value={inflTxt} onChange={(e) => setInfl(e.target.value)} dica="Padrão: IPCA mais recente" />
        </div>

        <div className="mt-4" aria-live="polite">
          {erro || !r ? (
            <p className="text-sm text-muted">{erro}</p>
          ) : (
            <>
              <p className="mb-2 text-sm font-medium">Resultado da simulação em {entrada.meses} meses (total aplicado: <Money valor={r.totalAplicado / 100} />)</p>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] text-sm">
                  <caption className="sr-only">Resultado da simulação por aplicação</caption>
                  <thead>
                    <tr className="text-left text-xs uppercase text-muted">
                      <th scope="col" className="py-1 pr-3">Aplicação</th>
                      <th scope="col" className="py-1 pr-3 text-right">Saldo final líquido</th>
                      <th scope="col" className="py-1 pr-3 text-right">Ganho líquido</th>
                      <th scope="col" className="py-1 pr-3 text-right">IR no resgate</th>
                      <th scope="col" className="py-1 text-right">Ganho real (descontada a inflação)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(
                      [
                        ["Poupança (isenta de IR)", r.poupanca],
                        [`Produto a ${fmtPctFracao(entrada.pctCdi, 0)} do CDI (IR ${fmtPctFracao(aliquotaIR(30 * entrada.meses), 1)} no prazo)`, r.cdi],
                        ["Dinheiro parado (sem rendimento)", r.parado],
                      ] as const
                    ).map(([nome, p]) => (
                      <tr key={nome} className="border-t border-line">
                        <th scope="row" className="py-2 pr-3 text-left font-medium">{nome}</th>
                        <td className="py-2 pr-3 text-right tabular-nums"><Money valor={p.saldoFinal / 100} /></td>
                        <td className="py-2 pr-3 text-right tabular-nums">{real(p.ganhoLiquido)}</td>
                        <td className="py-2 pr-3 text-right tabular-nums"><Money valor={p.ir / 100} /></td>
                        <td className="py-2 text-right tabular-nums">{real(p.ganhoReal)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="mt-3 grid gap-2 sm:grid-cols-3">
                <Resultado rotulo="Poupança − produto CDI (saldo líquido)"><Money valor={(r.poupanca.saldoFinal - r.cdi.saldoFinal) / 100} tom="auto" /></Resultado>
                <Resultado rotulo="Aplicado corrigido pela inflação"><Money valor={r.aplicadoCorrigido / 100} /></Resultado>
                <Resultado rotulo="Taxa anual equivalente do CDI informado">{fmtPctFracao(anualDeMensal(entrada.cdiMensal))}</Resultado>
              </div>
              <div className="mt-4">
                <GraficoLinhas
                  rotulo="Evolução do saldo líquido mês a mês: poupança, produto atrelado ao CDI, dinheiro parado e valor aplicado corrigido pela inflação"
                  passoTabela={Math.max(1, Math.ceil(entrada.meses / 12))}
                  series={[
                    { nome: "Poupança", cor: "var(--c3)", valores: r.serie.map((p) => p.poupanca) },
                    { nome: "Produto CDI (líquido)", cor: "var(--c1)", traco: "8 3", valores: r.serie.map((p) => p.cdiLiquido) },
                    { nome: "Parado", cor: "var(--c8)", traco: "2 4", valores: r.serie.map((p) => p.parado) },
                    { nome: "Aplicado corrigido pela inflação", cor: "var(--c4)", traco: "4 2", valores: r.serie.map((p) => p.corrigidoInflacao) },
                  ]}
                />
              </div>
            </>
          )}
        </div>
        <details className="mt-4 text-sm text-muted">
          <summary className="cursor-pointer text-primary">Premissas e limitações</summary>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            <li>Aporte no fim de cada mês; o do último mês não rende. Mês de 30 dias para o prazo do IR.</li>
            <li>Produto atrelado ao CDI: taxa mensal = % do CDI × CDI mensal; IR regressivo sobre o rendimento de cada aporte (22,5% até 180 dias, 20% de 181 a 360, 17,5% de 361 a 720, 15% acima). Sem come-cotas, IOF, taxas ou garantias.</li>
            <li>Poupança isenta de IR, rendendo todo mês (sem data de aniversário). Regra: Selic acima de 8,5% a.a. = 0,5% a.m. + TR; senão 70% da Selic + TR.</li>
            <li>Ganho real = saldo final − valor aplicado corrigido pela inflação informada. Resultados são projeções e não garantem rentabilidade futura.</li>
          </ul>
        </details>
      </Card>
    </div>
  );
}
