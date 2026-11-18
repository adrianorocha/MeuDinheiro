"use client";

import { X } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { InputValor } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { aplicarCorteNoFluxo, calcularCortes, PCT_ESSENCIAL_PADRAO, PCT_FLEXIVEL_PADRAO, type TipoCorte } from "@/lib/finance/cortes";
import type { ResultadoFluxo } from "@/lib/finance/fluxoCaixa";
import { formatMesAno } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { useInsightsDispensados } from "@/lib/insightsStore";
import { lerNum, paraCampo, Resultado } from "./util";

const ROTULO: Record<TipoCorte, { texto: string; tom: "neg" | "warn" | "primary" | "neutro" | "pos" }> = {
  EVITAVEL: { texto: "Evitável", tom: "neg" },
  ASSINATURA: { texto: "Assinatura", tom: "warn" },
  FREQUENTE: { texto: "Compras frequentes", tom: "warn" },
  FLEXIVEL: { texto: "Gasto flexível", tom: "primary" },
  ESSENCIAL: { texto: "Essencial", tom: "neutro" },
};

/** Percentual digitado (0-100) -> fração; inválido -> null. */
function lerPct(t: string): number | null {
  const n = lerNum(t);
  return Number.isFinite(n) && n >= 0 && n <= 100 ? n / 100 : null;
}

/** R103 - "Onde cortar": cortes sugeridos por tipo, plano para fechar o déficit do fluxo e impacto no saldo projetado. */
export function CortesPanel({ fluxo }: { fluxo: ResultadoFluxo | null }) {
  const ds = useDataset();
  const agora = useAgora();
  const dispensados = useInsightsDispensados((s) => s.dispensados);
  const dispensar = useInsightsDispensados((s) => s.dispensar);
  const restaurar = useInsightsDispensados((s) => s.restaurar);

  const [flex, setFlex] = useState(paraCampo(PCT_FLEXIVEL_PADRAO * 100));
  const [ess, setEss] = useState(paraCampo(PCT_ESSENCIAL_PADRAO * 100));
  const [alvoTxt, setAlvoTxt] = useState<string | null>(null);
  const pf = lerPct(flex);
  const pe = lerPct(ess);

  const deficit = fluxo ? fluxo.cortePorMes : 0;
  const alvoCentavos = alvoTxt === null ? deficit : Number.isFinite(lerNum(alvoTxt)) ? Math.max(0, Math.round(lerNum(alvoTxt) * 100)) : 0;

  const r = useMemo(
    () => (pf !== null && pe !== null ? calcularCortes(ds, agora, { pctFlexivel: pf, pctEssencial: pe, alvoMensal: alvoCentavos, dispensados: new Set([...dispensados].filter((x) => x.startsWith("corte:"))) }) : null),
    [ds, agora, pf, pe, alvoCentavos, dispensados],
  );
  const ocultos = [...dispensados].filter((x) => x.startsWith("corte:")).length;
  const impacto = fluxo && r?.plano ? aplicarCorteNoFluxo(fluxo, r.plano.economia) : null;
  const ultimo = fluxo && fluxo.meses.length > 0 ? fluxo.meses[fluxo.meses.length - 1] : null;
  const selecionados = new Set(r?.plano?.selecionados.map((c) => c.id) ?? []);

  return (
    <Card aria-labelledby="t-cortes">
      <CardTitulo
        id="t-cortes"
        acao={
          ocultos > 0 ? (
            <Button tamanho="sm" onClick={restaurar}>
              Mostrar dispensados
            </Button>
          ) : undefined
        }
      >
        Onde cortar para economizar
      </CardTitulo>
      <p className="mb-3 text-sm text-muted">Calculado sobre a média dos 3 últimos meses completos. Cada lançamento aparece em um só bloco, do corte menos doloroso (juros, assinaturas) ao mais difícil (essenciais).</p>

      <div className="grid gap-3 sm:grid-cols-3">
        <InputValor rotulo="Reduzir gastos flexíveis (%)" value={flex} onChange={(e) => setFlex(e.target.value)} erro={pf === null ? "Use de 0 a 100." : null} />
        <InputValor rotulo="Reduzir essenciais (%)" value={ess} onChange={(e) => setEss(e.target.value)} erro={pe === null ? "Use de 0 a 100." : null} dica="Renegociar, pesquisar preço, evitar desperdício." />
        <InputValor
          rotulo="Meta de economia por mês (R$)"
          value={alvoTxt ?? paraCampo(deficit / 100, 2)}
          onChange={(e) => setAlvoTxt(e.target.value)}
          dica={fluxo && fluxo.cortePorMes > 0 ? "Padrão: o corte necessário para fechar o déficit da projeção." : "Digite quanto quer economizar por mês."}
        />
      </div>

      {r && (
        <div className="mt-4 space-y-4" aria-live="polite">
          {r.mesesComDados < 3 && <p className="text-sm text-warn">Há menos de 3 meses completos de gastos variáveis; as médias podem estar subavaliadas.</p>}
          {r.cortes.length === 0 ? (
            <p className="py-2 text-center text-sm text-muted">Nenhum corte sugerido por enquanto. Com mais lançamentos nos últimos 3 meses, esta lista passa a mostrar onde economizar.</p>
          ) : (
            <>
              <div className="grid gap-2 sm:grid-cols-3">
                <Resultado rotulo="Economia possível por mês" destaque>
                  <Money valor={r.totalMensal / 100} />
                </Resultado>
                <Resultado rotulo="Por ano">
                  <Money valor={r.totalAnual / 100} />
                </Resultado>
                <Resultado rotulo="Cortes sugeridos">{r.cortes.length}</Resultado>
              </div>

              {r.plano && (
                <div className={`rounded-xl border px-3 py-2 text-sm ${r.plano.atinge ? "border-pos/40 bg-pos-soft" : "border-warn/40 bg-warn-soft"}`} role="status">
                  <p className="font-medium">
                    Plano para economizar <Money valor={r.plano.alvo / 100} />/mês: {r.plano.selecionados.length} corte(s), <Money valor={r.plano.economia / 100} />/mês
                    {r.plano.atinge ? " — meta atingida." : <> — faltam <Money valor={r.plano.falta / 100} />/mês (os cortes sugeridos não bastam; veja receitas ou prazos).</>}
                  </p>
                  {impacto && fluxo && ultimo && (
                    <p className="mt-1 text-muted">
                      No fluxo de caixa: saldo em {formatMesAno(ultimo.mes, ultimo.ano)} de <Money valor={ultimo.saldoFinal / 100} tom="auto" /> para <Money valor={impacto.saldoFinalDepois / 100} tom="auto" className="font-semibold" />; meses negativos: {impacto.mesesNegativosAntes} → {impacto.mesesNegativosDepois}.
                    </p>
                  )}
                </div>
              )}

              <ul className="space-y-3">
                {r.cortes.map((c) => (
                  <li key={c.id} className={`rounded-xl border p-3 ${selecionados.has(c.id) ? "border-primary/50" : "border-line"}`}>
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="flex flex-wrap items-center gap-2 font-medium">
                          {c.titulo} <Badge tom={ROTULO[c.tipo].tom}>{ROTULO[c.tipo].texto}</Badge>
                          {selecionados.has(c.id) && <Badge tom="pos">No plano</Badge>}
                        </p>
                        <p className="text-sm">
                          Hoje <Money valor={c.mensalAtual / 100} />/mês · economia estimada <Money valor={c.economiaMensal / 100} className="font-semibold" />/mês
                        </p>
                      </div>
                      <Button tamanho="sm" variante="ghost" icone={<X size={14} aria-hidden />} onClick={() => dispensar(c.id)} aria-label={`Dispensar: ${c.titulo}`}>
                        Dispensar
                      </Button>
                    </div>
                    <details className="mt-1 text-sm">
                      <summary className="cursor-pointer text-primary">Ver cálculo</summary>
                      <ul className="mt-1 list-disc pl-5 text-muted">
                        {c.calculo.map((l) => (
                          <li key={l}>{l}</li>
                        ))}
                      </ul>
                    </details>
                  </li>
                ))}
              </ul>
            </>
          )}

          {r.maioresGastos.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[420px] text-sm">
                <caption className="mb-1 text-left text-xs uppercase text-muted">Maiores gastos variáveis (média mensal)</caption>
                <thead>
                  <tr className="text-left text-xs uppercase text-muted">
                    <th scope="col" className="py-1 pr-3">Gasto</th>
                    <th scope="col" className="py-1 pr-3">Categoria</th>
                    <th scope="col" className="py-1 pr-3 text-right">Compras</th>
                    <th scope="col" className="py-1 text-right">Por mês</th>
                  </tr>
                </thead>
                <tbody>
                  {r.maioresGastos.map((g) => (
                    <tr key={`${g.nome}|${g.categoria}`} className="border-t border-line">
                      <th scope="row" className="py-1.5 pr-3 text-left font-medium">{g.nome}</th>
                      <td className="py-1.5 pr-3">{g.categoria}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{g.compras}</td>
                      <td className="py-1.5 text-right tabular-nums"><Money valor={g.mensal / 100} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
      <p className="mt-3 text-xs text-muted">Estimativas por regras simples sobre os seus lançamentos (percentuais editáveis); não consideram sua situação completa nem são recomendação personalizada.</p>
    </Card>
  );
}
