"use client";

import { ArrowLeftRight, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { Card, CardTitulo } from "@/components/ui/Misc";
import { descreverEstado, FONTE_BCB, MOEDAS, moedaParaReais, reaisParaMoeda, spreadValido, type CotacaoMoeda, type PontoHistorico } from "@/lib/finance/bcb";
import { useCambio } from "@/lib/cambioStore";
import { AvisoReferencia, fmtNum, lerNum } from "./util";

const nomeMoeda = (cod: string) => MOEDAS.find((m) => m.codigo === cod)?.nome ?? cod;
/** Casas decimais por moeda (iene vale centavos de real). */
const casas = (cod: string) => (cod === "JPY" ? 6 : 4);

function fmtDataHora(dh: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}:\d{2})/.exec(dh);
  return m ? `${m[3]}/${m[2]}/${m[1]} ${m[4]}` : dh;
}

function fmtDia(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
}

function Variacao({ v }: { v: number | null }) {
  if (v === null) return <span className="text-muted">—</span>;
  const tom = v > 0 ? "text-pos" : v < 0 ? "text-neg" : "text-muted";
  return (
    <span className={`tabular-nums ${tom}`}>
      {v > 0 ? "+" : ""}
      {fmtNum(v, 2)}%<span className="sr-only">{v > 0 ? " (alta)" : v < 0 ? " (queda)" : ""}</span>
    </span>
  );
}

/** Mini gráfico de linha (SVG) dos fechamentos + tabela equivalente para leitores de tela. */
function MiniGrafico({ moeda, pontos }: { moeda: string; pontos: PontoHistorico[] }) {
  const L = 600;
  const A = 160;
  const mx = 52;
  if (pontos.length < 2) return <p className="text-sm text-muted">Poucos pontos para desenhar o gráfico.</p>;
  const vs = pontos.map((p) => p.venda);
  const min = Math.min(...vs);
  const max = Math.max(...vs);
  const folga = max === min ? max * 0.01 || 1 : 0;
  const lo = min - folga;
  const hi = max + folga;
  const x = (i: number) => mx + (i / (pontos.length - 1)) * (L - mx - 10);
  const y = (v: number) => 10 + (1 - (v - lo) / (hi - lo)) * (A - 32);
  const c = casas(moeda);
  return (
    <figure>
      <svg viewBox={`0 0 ${L} ${A}`} className="w-full" role="img" aria-label={`Venda PTAX de ${nomeMoeda(moeda)} (R$) nos últimos ${pontos.length} fechamentos, de ${fmtNum(vs[0], c)} a ${fmtNum(vs[vs.length - 1], c)}`}>
        <line x1={mx} y1={A - 22} x2={L - 10} y2={A - 22} stroke="var(--line)" />
        <line x1={mx} y1={10} x2={mx} y2={A - 22} stroke="var(--line)" />
        <text x={mx - 4} y={16} textAnchor="end" fontSize={10} fill="var(--muted)">{fmtNum(max, c)}</text>
        <text x={mx - 4} y={A - 24} textAnchor="end" fontSize={10} fill="var(--muted)">{fmtNum(min, c)}</text>
        <text x={mx} y={A - 8} fontSize={10} fill="var(--muted)">{fmtDia(pontos[0].data)}</text>
        <text x={L - 10} y={A - 8} textAnchor="end" fontSize={10} fill="var(--muted)">{fmtDia(pontos[pontos.length - 1].data)}</text>
        <polyline fill="none" stroke="var(--primary)" strokeWidth={2} points={pontos.map((p, i) => `${x(i).toFixed(1)},${y(p.venda).toFixed(1)}`).join(" ")} />
      </svg>
      <details className="mt-1 text-xs text-muted">
        <summary className="cursor-pointer">Ver valores em tabela</summary>
        <table className="mt-1 text-xs">
          <caption className="sr-only">Fechamentos PTAX de {nomeMoeda(moeda)}</caption>
          <thead>
            <tr>
              <th scope="col" className="pr-3 text-left">Data</th>
              <th scope="col" className="text-right">Venda (R$)</th>
            </tr>
          </thead>
          <tbody>
            {pontos.map((p) => (
              <tr key={p.data}>
                <th scope="row" className="pr-3 text-left font-normal">{fmtDia(p.data)}</th>
                <td className="text-right tabular-nums">{fmtNum(p.venda, c)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}

function Conversor({ cotacoes }: { cotacoes: CotacaoMoeda[] }) {
  const [moeda, setMoeda] = useState("USD");
  const [sentido, setSentido] = useState<"brl" | "moeda">("brl");
  const [valor, setValor] = useState("1000");
  const [spread, setSpread] = useState("0");
  const cot = cotacoes.find((c) => c.moeda === moeda);
  const v = lerNum(valor);
  const sp = spread.trim() === "" ? 0 : lerNum(spread);
  const spOk = spreadValido(sp);
  const res = cot && spOk ? (sentido === "brl" ? reaisParaMoeda(v, cot.venda, sp) : moedaParaReais(v, cot.venda, sp)) : Number.NaN;
  const c = casas(moeda);
  return (
    <Card aria-labelledby="t-conv">
      <CardTitulo id="t-conv">Conversor</CardTitulo>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Select rotulo="Moeda" value={moeda} onChange={(e) => setMoeda(e.target.value)}>
          {MOEDAS.map((m) => (
            <option key={m.codigo} value={m.codigo}>
              {m.codigo} — {m.nome}
            </option>
          ))}
        </Select>
        <Select rotulo="Sentido" value={sentido} onChange={(e) => setSentido(e.target.value as "brl" | "moeda")}>
          <option value="brl">Reais (R$) → {moeda}</option>
          <option value="moeda">{moeda} → Reais (R$)</option>
        </Select>
        <Input rotulo={sentido === "brl" ? "Valor em R$" : `Valor em ${moeda}`} inputMode="decimal" value={valor} onChange={(e) => setValor(e.target.value)} />
        <Input
          rotulo="Spread / IOF (%) — informe sua taxa"
          inputMode="decimal"
          value={spread}
          onChange={(e) => setSpread(e.target.value)}
          dica="Padrão 0%. Acréscimo da sua compra internacional (cartão, corretora, IOF); confira a taxa com a sua instituição."
          erro={spOk ? null : "Informe um percentual entre 0 e 100."}
        />
      </div>
      <p className="mt-3 flex items-center gap-2 text-sm" aria-live="polite">
        <ArrowLeftRight size={16} aria-hidden className="shrink-0 text-muted" />
        {!cot ? (
          <span className="text-muted">Sem cotação de {moeda} ainda; atualize o câmbio.</span>
        ) : !Number.isFinite(res) ? (
          <span className="text-muted">Informe um valor válido.</span>
        ) : sentido === "brl" ? (
          <span>
            R$ {fmtNum(v, 2)} ≈ <strong>{fmtNum(res, 2)} {moeda}</strong>
          </span>
        ) : (
          <span>
            {fmtNum(v, 2)} {moeda} ≈ <strong>R$ {fmtNum(res, 2)}</strong>
          </span>
        )}
      </p>
      {cot && (
        <p className="mt-1 text-xs text-muted">
          Usa a venda PTAX de R$ {fmtNum(cot.venda, c)} por 1 {moeda}
          {sp > 0 && spOk ? `, mais ${fmtNum(sp, 2)}% informado por você` : ""}. A PTAX é referência do mercado, não a cotação que você obterá; sem impostos ou tarifas além do que você informar.
        </p>
      )}
    </Card>
  );
}

export function CambioPanel() {
  const cotacoes = useCambio((s) => s.cotacoes);
  const atualizadoEm = useCambio((s) => s.atualizadoEm);
  const carregando = useCambio((s) => s.carregando);
  const erro = useCambio((s) => s.erro);
  const offline = useCambio((s) => s.offline);
  const falhas = useCambio((s) => s.falhas);
  const historico = useCambio((s) => s.historico);
  const [moedaGraf, setMoedaGraf] = useState("USD");
  const [verGrafico, setVerGrafico] = useState(false);

  // só ao abrir a aba: carrega o cache e, se passaram 6 h, consulta o Banco Central
  useEffect(() => {
    useCambio.getState().carregar();
    void useCambio.getState().atualizar(false);
  }, []);

  // o histórico só é consultado depois que o usuário pede o gráfico (e a cada troca de moeda)
  useEffect(() => {
    if (!verGrafico) return;
    const h = useCambio.getState().historico[moedaGraf];
    if (!h || (h.pontos.length === 0 && !h.carregando && !h.erro)) void useCambio.getState().carregarHistorico(moedaGraf);
  }, [verGrafico, moedaGraf]);

  const ordenadas = useMemo(() => MOEDAS.map((m) => cotacoes.find((c) => c.moeda === m.codigo) ?? null), [cotacoes]);
  const h = historico[moedaGraf];

  return (
    <div className="space-y-4">
      <AvisoReferencia>Cotações PTAX do Banco Central (referência de mercado, não a taxa cobrada por bancos ou casas de câmbio).</AvisoReferencia>
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-surface px-3 py-2 text-sm">
        <p role="status" aria-live="polite" className="min-w-0 flex-1">
          {descreverEstado({ atualizadoEm, carregando, erro, offline })}
          {falhas.length > 0 && <span className="mt-1 block text-xs text-warn">Sem resposta para: {falhas.map((f) => f.fonte).join(", ")}. Mantidas as últimas cotações salvas, quando houver.</span>}
        </p>
        <Button tamanho="sm" icone={<RefreshCw size={14} aria-hidden className={carregando ? "animate-spin" : ""} />} onClick={() => void useCambio.getState().atualizar(true)} disabled={carregando}>
          Atualizar agora
        </Button>
      </div>

      <Card aria-labelledby="t-cambio">
        <CardTitulo id="t-cambio">Cotações (R$ por 1 unidade da moeda)</CardTitulo>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm" aria-busy={carregando}>
            <caption className="sr-only">Cotações PTAX do Banco Central em reais por unidade de moeda</caption>
            <thead>
              <tr className="text-left text-xs uppercase text-muted">
                <th scope="col" className="py-1 pr-3">Moeda</th>
                <th scope="col" className="py-1 pr-3 text-right">Compra</th>
                <th scope="col" className="py-1 pr-3 text-right">Venda</th>
                <th scope="col" className="py-1 pr-3 text-right">Variação</th>
                <th scope="col" className="py-1 pr-3">Data/hora</th>
                <th scope="col" className="py-1">Boletim</th>
              </tr>
            </thead>
            <tbody>
              {ordenadas.map((c, i) => {
                const m = MOEDAS[i];
                return (
                  <tr key={m.codigo} className="border-t border-line">
                    <th scope="row" className="py-2 pr-3 text-left font-medium">
                      {m.codigo} <span className="font-normal text-muted">{m.nome}</span>
                    </th>
                    {c ? (
                      <>
                        <td className="py-2 pr-3 text-right tabular-nums">{fmtNum(c.compra, casas(m.codigo))}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{fmtNum(c.venda, casas(m.codigo))}</td>
                        <td className="py-2 pr-3 text-right"><Variacao v={c.variacaoPct} /></td>
                        <td className="py-2 pr-3 whitespace-nowrap">{fmtDataHora(c.dataHora)}</td>
                        <td className="py-2">{c.intermediario ? "intermediário (sem fechamento ainda)" : c.tipo.toLowerCase()}</td>
                      </>
                    ) : (
                      <td colSpan={5} className="py-2 text-muted">{carregando ? "Carregando…" : "Sem cotação (atualize)."}</td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <p className="mt-2 text-xs text-muted">
          Fonte: {FONTE_BCB}. Variação = venda contra o fechamento anterior. Fora de dia útil, vale o último fechamento disponível.
        </p>
      </Card>

      <Conversor cotacoes={cotacoes} />

      <Card aria-labelledby="t-hist">
        <CardTitulo id="t-hist">Histórico (últimos ~30 fechamentos)</CardTitulo>
        <div className="flex flex-wrap items-end gap-3">
          <Select rotulo="Moeda do gráfico" value={moedaGraf} onChange={(e) => setMoedaGraf(e.target.value)} wrapperClassName="max-w-xs">
            {MOEDAS.map((m) => (
              <option key={m.codigo} value={m.codigo}>
                {m.codigo} — {m.nome}
              </option>
            ))}
          </Select>
          {!verGrafico && <Button onClick={() => setVerGrafico(true)}>Ver histórico</Button>}
        </div>
        <div className="mt-3" aria-live="polite">
          {!verGrafico ? (
            <p className="text-sm text-muted">O histórico só é consultado quando você pede (uma consulta ao Banco Central por moeda).</p>
          ) : h?.carregando ? (
            <p className="text-sm text-muted">Carregando histórico…</p>
          ) : h?.erro ? (
            <p className="text-sm text-neg">
              Não foi possível carregar o histórico: {h.erro}.{" "}
              <button type="button" className="underline" onClick={() => void useCambio.getState().carregarHistorico(moedaGraf)}>
                Tentar de novo
              </button>
            </p>
          ) : h && h.pontos.length > 0 ? (
            <MiniGrafico moeda={moedaGraf} pontos={h.pontos} />
          ) : null}
        </div>
      </Card>
    </div>
  );
}
