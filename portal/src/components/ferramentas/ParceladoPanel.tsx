"use client";

import { useState } from "react";
import { Input, InputValor } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { compararParcelado, validarParcelado, type EntradaParcelado } from "@/lib/finance/avistaParcelado";
import { buscarIndice, pctParaFracao, valorMensalPct } from "@/lib/finance/indices";
import { useIndices } from "@/lib/indicesStore";
import { AvisoReferencia, fmtPctFracao, lerNum, paraCampo, Resultado } from "./util";

export function ParceladoPanel() {
  const conjunto = useIndices((s) => s.conjunto);
  const cdiPadrao = valorMensalPct(buscarIndice(conjunto, "cdi"));
  const [aVista, setAVista] = useState("");
  const [n, setN] = useState("10");
  const [parcela, setParcela] = useState("");
  const [entrada, setEntrada] = useState("");
  const [pctCdi, setPctCdi] = useState("100");
  const [cdi, setCdi] = useState<string | null>(null);
  const cdiTxt = cdi ?? paraCampo(cdiPadrao);

  const e: EntradaParcelado = { aVista: lerNum(aVista), n: Number(n), parcela: lerNum(parcela), entrada: entrada.trim() ? lerNum(entrada) : 0 };
  const preenchido = aVista.trim() !== "" && parcela.trim() !== "";
  const erro = preenchido ? validarParcelado(e) : null;
  const r = preenchido && !erro ? compararParcelado(e, pctParaFracao(lerNum(cdiTxt)), lerNum(pctCdi) / 100) : null;

  return (
    <div className="space-y-4">
      <AvisoReferencia />
      <Card aria-labelledby="t-parc">
        <CardTitulo id="t-parc">À vista × parcelado</CardTitulo>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <InputValor rotulo="Preço à vista (R$)" value={aVista} onChange={(ev) => setAVista(ev.target.value)} />
          <Input rotulo="Número de parcelas" type="number" min={1} value={n} onChange={(ev) => setN(ev.target.value)} />
          <InputValor rotulo="Valor da parcela (R$)" value={parcela} onChange={(ev) => setParcela(ev.target.value)} />
          <InputValor rotulo="Entrada (R$, opcional)" value={entrada} onChange={(ev) => setEntrada(ev.target.value)} />
          <InputValor rotulo="CDI (% a.m.)" value={cdiTxt} onChange={(ev) => setCdi(ev.target.value)} dica="Padrão: tela de índices" />
          <InputValor rotulo="% do CDI da aplicação" value={pctCdi} onChange={(ev) => setPctCdi(ev.target.value)} />
        </div>
        <div className="mt-4" aria-live="polite">
          {erro ? (
            <p className="text-sm text-neg" role="alert">{erro}</p>
          ) : !r ? (
            <p className="text-sm text-muted">Preencha o preço à vista, o número de parcelas e o valor da parcela para ver a taxa embutida e a comparação.</p>
          ) : (
            <div className="space-y-3 text-sm">
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <Resultado rotulo="Taxa implícita">{fmtPctFracao(r.taxaMensal, 3)} a.m.</Resultado>
                <Resultado rotulo="Taxa anual equivalente">{fmtPctFracao(r.taxaAnual, 2)} a.a.</Resultado>
                <Resultado rotulo="Total pago parcelado"><Money valor={r.totalPago} /></Resultado>
                <Resultado rotulo="Juros embutidos"><Money valor={r.juros} tom="auto" /></Resultado>
              </div>
              <div className="rounded-xl border border-line p-3">
                <p className="mb-1 flex items-center gap-2 font-medium">
                  Resultado da simulação{" "}
                  <Badge tom={r.menorCusto === "EMPATE" ? "neutro" : "primary"}>
                    {r.menorCusto === "AVISTA" ? "à vista custa menos em valor presente" : r.menorCusto === "PARCELADO" ? "parcelado custa menos em valor presente" : "empate em valor presente"}
                  </Badge>
                </p>
                <p>
                  Valor presente das parcelas (dinheiro que você precisaria aplicar hoje, líquido de IR, para pagá-las): <Money valor={r.valorPresente} className="font-semibold" /> contra <Money valor={e.aVista} className="font-semibold" /> à vista.
                </p>
                <p>
                  Diferença: <Money valor={r.diferenca} tom="auto" /> ({r.diferenca > 0 ? "parcelar custa mais" : r.diferenca < 0 ? "parcelar custa menos" : "iguais"}, em valor presente).
                </p>
                <p className="mt-1">
                  Desconto à vista oferecido sobre o total parcelado: <strong>{fmtPctFracao(r.descontoOferecido, 2)}</strong>. Ponto de equilíbrio: a partir de <strong>{fmtPctFracao(Math.max(0, r.descontoBreakEven), 2)}</strong> de desconto, pagar à vista passa a custar menos que parcelar (preço à vista de equilíbrio: <Money valor={r.valorPresente} />).
                </p>
              </div>
              <p className="text-xs text-muted">
                Considera parcelas mensais (a primeira 30 dias após a compra), rendimento do dinheiro a {fmtPctFracao(lerNum(pctCdi) / 100, 0)} do CDI com IR regressivo, sem custos adicionais. Não considera valor de revenda, risco de crédito nem outras vantagens (frete, cashback). É uma simulação, não uma recomendação.
              </p>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
