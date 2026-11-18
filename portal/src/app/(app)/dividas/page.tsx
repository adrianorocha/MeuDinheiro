"use client";

import { CircleCheck, Link2, Link2Off, Pencil, Plus, Trash2, TrendingDown } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Checkbox, Input, InputValor, Segmentado, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader, ProgressBar } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { brlCentavos } from "@/lib/finance/centavos";
import {
  CATEGORIA_DIVIDAS,
  compararDasDividas,
  dataQuitacao,
  estimarDividaEmAndamento,
  jurosDoMes,
  paraSim,
  pendenciasVinculaveis,
  progressoQuitacao,
  ROTULO_TIPO_DIVIDA,
  TIPOS_DIVIDA,
  type Estrategia,
  type ResultadoSim,
} from "@/lib/finance/dividas";
import { fromCents, toCents } from "@/lib/finance/money";
import type { Despesa, Divida, TipoDivida } from "@/lib/finance/types";
import { deInputData, formatBRL, formatData, formatMesAno, paraInputData, parseValorBR, valorParaCampo } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const numOuNulo = (t: string): number | null => {
  if (!t.trim()) return null;
  const v = parseValorBR(t);
  return Number.isFinite(v) ? v : Number.NaN;
};

function DividaForm({ editar, onFechar }: { editar: Divida | null; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [nome, setNome] = useState(editar?.nome ?? "");
  const [tipo, setTipo] = useState<TipoDivida>(editar?.tipo ?? "EMPRESTIMO");
  const [credor, setCredor] = useState(editar?.credor ?? "");
  const [saldo, setSaldo] = useState(editar ? valorParaCampo(editar.saldoDevedor) : "");
  const [original, setOriginal] = useState(editar?.valorOriginal ? valorParaCampo(editar.valorOriginal) : "");
  const [taxa, setTaxa] = useState(editar?.taxaJurosMensal ? valorParaCampo(editar.taxaJurosMensal) : "");
  const [parcela, setParcela] = useState(editar?.parcelaMinima ? valorParaCampo(editar.parcelaMinima) : "");
  const [parcelas, setParcelas] = useState(editar?.parcelasRestantes != null ? String(editar.parcelasRestantes) : "");
  const [dia, setDia] = useState(editar?.diaVencimento != null ? String(editar.diaVencimento) : "");
  const [conta, setConta] = useState(editar?.conta ?? "");
  const [ativa, setAtiva] = useState(editar?.ativa ?? true);
  const [erro, setErro] = useState<string | null>(null);
  const [totalParc, setTotalParc] = useState("");
  const [pagasParc, setPagasParc] = useState("");
  const [calcMsg, setCalcMsg] = useState<string | null>(null);

  /** R73 - dívida contratada antes e já em andamento: preenche saldo, parcela e parcelas restantes (editáveis depois). */
  function calcular() {
    const o = numOuNulo(original);
    const n = numOuNulo(totalParc);
    const p = numOuNulo(pagasParc);
    const tx = numOuNulo(taxa);
    const pc = numOuNulo(parcela);
    if (o === null || n === null || p === null || [o, n, p, tx, pc].some((v) => v !== null && Number.isNaN(v))) {
      return setCalcMsg("Informe o valor original, o total de parcelas e quantas já foram pagas.");
    }
    const r = estimarDividaEmAndamento({ valorOriginal: o, parcelasTotal: n, parcelasPagas: p, taxaMensal: tx, parcela: pc });
    if (typeof r === "string") return setCalcMsg(r);
    setSaldo(valorParaCampo(r.saldoDevedor));
    setParcela(valorParaCampo(r.parcela));
    setParcelas(String(r.parcelasRestantes));
    setCalcMsg(`Saldo estimado: ${formatBRL(r.saldoDevedor)}. Se o credor informar outro valor, ajuste o saldo devedor.`);
  }

  function enviar(e: FormEvent) {
    e.preventDefault();
    const s = parseValorBR(saldo);
    if (!Number.isFinite(s)) return setErro("Informe o saldo devedor.");
    const outros = [numOuNulo(original), numOuNulo(taxa), numOuNulo(parcela), numOuNulo(parcelas), numOuNulo(dia)];
    if (outros.some((v) => v !== null && Number.isNaN(v))) return setErro("Há um número inválido no formulário.");
    const r = acoes.salvarDivida(
      {
        nome,
        tipo,
        credor: credor || null,
        saldoDevedor: s,
        valorOriginal: outros[0],
        taxaJurosMensal: outros[1],
        parcelaMinima: outros[2],
        parcelasRestantes: outros[3],
        diaVencimento: outros[4],
        conta: conta || null,
        ativa,
      },
      editar?.id,
    );
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Dívida salva.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Nome" value={nome} onChange={(e) => setNome(e.target.value)} maxLength={60} required />
      <div className="grid grid-cols-2 gap-3">
        <Select rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoDivida)}>
          {TIPOS_DIVIDA.map((t) => (
            <option key={t} value={t}>
              {ROTULO_TIPO_DIVIDA[t]}
            </option>
          ))}
        </Select>
        <Input rotulo="Credor (opcional)" value={credor} onChange={(e) => setCredor(e.target.value)} maxLength={60} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <InputValor rotulo="Saldo devedor" value={saldo} onChange={(e) => setSaldo(e.target.value)} required />
        <InputValor rotulo="Valor original (opcional)" value={original} onChange={(e) => setOriginal(e.target.value)} dica="Só para o progresso" />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo="Juros ao mês (%)" inputMode="decimal" placeholder="0" value={taxa} onChange={(e) => setTaxa(e.target.value)} dica="Pode ficar em branco (sem juros)" />
        <InputValor rotulo="Parcela / mínimo mensal" value={parcela} onChange={(e) => setParcela(e.target.value)} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo="Parcelas restantes" type="number" min={0} value={parcelas} onChange={(e) => setParcelas(e.target.value)} />
        <Input rotulo="Dia do vencimento" type="number" min={1} max={31} value={dia} onChange={(e) => setDia(e.target.value)} />
      </div>
      <Select rotulo="Conta de pagamento (opcional)" value={conta} onChange={(e) => setConta(e.target.value)}>
        <option value="">—</option>
        {ds.contas.map((c) => (
          <option key={c.id} value={c.conta}>
            {c.banco} · {c.conta}
          </option>
        ))}
      </Select>
      {!editar && (
        <details className="rounded-lg border border-line p-3">
          <summary className="cursor-pointer text-sm font-medium">Já estou pagando (calcular o saldo atual)</summary>
          <p className="mt-2 text-xs text-muted">
            Para um empréstimo contratado antes: informe valor original, juros, total de parcelas e quantas já pagou. O saldo devedor, a parcela e as parcelas restantes são calculados; nada é lançado em conta.
          </p>
          <div className="mt-2 grid grid-cols-2 gap-3">
            <Input rotulo="Total de parcelas" type="number" min={1} value={totalParc} onChange={(e) => setTotalParc(e.target.value)} />
            <Input rotulo="Parcelas já pagas" type="number" min={0} value={pagasParc} onChange={(e) => setPagasParc(e.target.value)} />
          </div>
          <Button type="button" tamanho="sm" className="mt-2" onClick={calcular}>
            Calcular saldo devedor
          </Button>
          {calcMsg && <p className="mt-2 text-sm" aria-live="polite">{calcMsg}</p>}
        </details>
      )}
      <Checkbox rotulo="Dívida ativa" checked={ativa} onChange={(e) => setAtiva(e.target.checked)} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

/** R73 - traz pendências já lançadas (ex.: parcelas de um empréstimo) para Dívidas; as baixas abatem o saldo sozinhas. */
function VincularForm({ aoExistente, onFechar }: { aoExistente: Divida | null; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const pendencias = useMemo(() => pendenciasVinculaveis(ds).filter((d) => !ds.dividas.some((x) => x.lancamentoIds?.includes(d.id))), [ds]);
  const [sel, setSel] = useState<number[]>([]);
  const [nome, setNome] = useState("");
  const [tipo, setTipo] = useState<TipoDivida>("EMPRESTIMO");
  const [credor, setCredor] = useState("");
  const [taxa, setTaxa] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  const total = pendencias.filter((d) => sel.includes(d.id)).reduce((s, d) => s + toCents(d.valor), 0);

  function alternar(d: Despesa) {
    const grupo = d.grupoId ? pendencias.filter((x) => x.grupoId === d.grupoId).map((x) => x.id) : [d.id];
    const marcando = !sel.includes(d.id);
    setSel((atual) => (marcando ? [...new Set([...atual, ...grupo])] : atual.filter((i) => !grupo.includes(i))));
    if (marcando && !aoExistente && !nome) setNome(d.descricao.replace(/\s*\(\d+\/\d+\)\s*$/, "").trim());
  }

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (aoExistente) {
      const r = acoes.vincularPendencias(aoExistente.id, sel);
      if (!r.ok) return setErro(r.erro);
    } else {
      const tx = numOuNulo(taxa);
      if (tx !== null && Number.isNaN(tx)) return setErro("Juros inválidos.");
      const r = acoes.criarDividaDePendencias({ nome, tipo, credor: credor || null, taxaJurosMensal: tx }, sel);
      if (!r.ok) return setErro(r.erro);
    }
    avisar("sucesso", "Pendências vinculadas à dívida.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted">
        Escolha as pendências (a pagar) que formam a dívida. O saldo devedor passa a ser a soma das que ainda não foram pagas: ao dar baixa em uma pendência, a dívida abate sozinha e some quando a última é paga.
        Parcelas do mesmo parcelamento são marcadas juntas.
      </p>
      {!aoExistente && (
        <>
          <Input rotulo="Nome da dívida" value={nome} onChange={(e) => setNome(e.target.value)} maxLength={60} required />
          <div className="grid grid-cols-2 gap-3">
            <Select rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoDivida)}>
              {TIPOS_DIVIDA.map((t) => (
                <option key={t} value={t}>
                  {ROTULO_TIPO_DIVIDA[t]}
                </option>
              ))}
            </Select>
            <Input rotulo="Credor (opcional)" value={credor} onChange={(e) => setCredor(e.target.value)} maxLength={60} />
          </div>
          <Input rotulo="Juros ao mês (%) (opcional)" inputMode="decimal" value={taxa} onChange={(e) => setTaxa(e.target.value)} dica="Só para o simulador" />
        </>
      )}
      {pendencias.length === 0 ? (
        <p className="text-sm text-muted">Não há pendências de despesa livres para vincular.</p>
      ) : (
        <ul className="max-h-72 divide-y divide-line overflow-auto rounded-lg border border-line" aria-label="Pendências disponíveis">
          {pendencias.map((d) => (
            <li key={d.id} className="px-3 py-2">
              <label className="flex cursor-pointer items-center gap-3">
                <input type="checkbox" className="size-4 accent-[var(--primary)]" checked={sel.includes(d.id)} onChange={() => alternar(d)} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm">{d.descricao}</span>
                  <span className="block text-xs text-muted">{formatData(d.data)} · {d.categoria}</span>
                </span>
                <Money valor={d.valor} className="text-sm" />
              </label>
            </li>
          ))}
        </ul>
      )}
      <p className="text-sm" aria-live="polite">Selecionadas: <strong>{sel.length}</strong> · saldo devedor: <strong>{formatBRL(fromCents(total))}</strong></p>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Vincular" />
    </form>
  );
}

function PagamentoForm({ divida, onFechar }: { divida: Divida; onFechar: () => void }) {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const sugerido = divida.parcelaMinima && divida.parcelaMinima > 0 ? Math.min(divida.parcelaMinima, divida.saldoDevedor) : divida.saldoDevedor;
  const [valor, setValor] = useState(valorParaCampo(sugerido));
  const [juros, setJuros] = useState("");
  const [conta, setConta] = useState(divida.conta && ds.contas.some((c) => c.conta === divida.conta) ? divida.conta : (ds.contas[0]?.conta ?? ""));
  const [data, setData] = useState(paraInputData(agora));
  const [categoria, setCategoria] = useState(CATEGORIA_DIVIDAS);
  const [erro, setErro] = useState<string | null>(null);

  const v = parseValorBR(valor);
  const j = juros.trim() ? parseValorBR(juros) : 0;
  const novoSaldo = Number.isFinite(v) && Number.isFinite(j) ? fromCents(toCents(divida.saldoDevedor) + toCents(j) - toCents(v)) : null;

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!Number.isFinite(v) || !Number.isFinite(j)) return setErro("Informe valores válidos.");
    const d = deInputData(data);
    if (Number.isNaN(d)) return setErro("Informe uma data válida.");
    const r = acoes.pagarDivida({ dividaId: divida.id, valor: v, conta, data: d, categoria, juros: j > 0 ? j : undefined });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", r.quitada ? "Dívida quitada. Parabéns!" : `Pagamento registrado. Saldo devedor: ${formatBRL(r.saldoDevedor)}.`);
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted">
        Cria uma despesa paga na conta escolhida (categoria “{CATEGORIA_DIVIDAS}” por padrão) e abate o saldo devedor. O saldo da conta cai uma vez só, pelo lançamento.
      </p>
      <div className="grid grid-cols-2 gap-3">
        <InputValor rotulo="Valor pago" value={valor} onChange={(e) => setValor(e.target.value)} required />
        <InputValor rotulo="Juros do mês (opcional)" value={juros} onChange={(e) => setJuros(e.target.value)} dica="Soma ao saldo devedor antes de abater" />
      </div>
      <Select rotulo="Conta" value={conta} onChange={(e) => setConta(e.target.value)} required>
        {ds.contas.map((c) => (
          <option key={c.id} value={c.conta}>
            {c.banco} · {c.conta}
          </option>
        ))}
      </Select>
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo="Data" type="date" value={data} onChange={(e) => setData(e.target.value)} />
        <Input rotulo="Categoria" value={categoria} onChange={(e) => setCategoria(e.target.value)} list="categorias-divida" />
        <datalist id="categorias-divida">
          {ds.categorias.map((c) => (
            <option key={c.id} value={c.nome} />
          ))}
        </datalist>
      </div>
      {novoSaldo !== null && <p className="text-sm" aria-live="polite">Saldo devedor depois: <strong>{formatBRL(Math.max(0, novoSaldo))}</strong></p>}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Registrar pagamento" />
    </form>
  );
}

function JurosForm({ divida, onFechar }: { divida: Divida; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const estimado = jurosDoMes(toCents(divida.saldoDevedor), Math.round((divida.taxaJurosMensal ?? 0) * 100));
  const [valor, setValor] = useState(estimado > 0 ? valorParaCampo(fromCents(estimado)) : "");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const v = parseValorBR(valor);
    if (!Number.isFinite(v)) return setErro("Informe o valor dos juros.");
    const r = acoes.registrarJurosDivida(divida.id, v);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Juros somados ao saldo devedor.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted">Acrescenta os juros do mês ao saldo devedor. Nada é lançado em conta.</p>
      <InputValor rotulo="Juros do mês" value={valor} onChange={(e) => setValor(e.target.value)} dica={estimado > 0 ? `Estimativa pela taxa cadastrada: ${brlCentavos(estimado)}` : undefined} required />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Somar ao saldo" />
    </form>
  );
}

const ROTULO_ESTRATEGIA: Record<Estrategia, string> = {
  MINIMOS: "Apenas mínimos",
  BOLA_DE_NEVE: "Bola de neve (menor saldo primeiro)",
  AVALANCHE: "Avalanche (maior taxa primeiro)",
};

const COR_ESTRATEGIA: Record<Estrategia, string> = { MINIMOS: "var(--c3)", BOLA_DE_NEVE: "var(--c1)", AVALANCHE: "var(--c2)" };
const TRACO: Record<Estrategia, string | undefined> = { MINIMOS: "2 4", BOLA_DE_NEVE: undefined, AVALANCHE: "8 3" };

function GraficoSaldo({ inicial, series }: { inicial: number; series: ResultadoSim[] }) {
  const L = 560;
  const A = 160;
  const meses = Math.max(1, ...series.map((s) => s.cronograma.length));
  const topo = Math.max(1, inicial * 1.25);
  const x = (m: number) => 8 + (m / meses) * (L - 16);
  const y = (v: number) => 8 + (1 - Math.min(v, topo) / topo) * (A - 16);
  return (
    <svg viewBox={`0 0 ${L} ${A}`} className="w-full" role="img" aria-label="Evolução do saldo devedor total por estratégia, mês a mês">
      <line x1={8} y1={A - 8} x2={L - 8} y2={A - 8} stroke="var(--line)" />
      {series.map((s) => {
        const pts = [inicial, ...s.cronograma.map((c) => c.saldoFinal)];
        return (
          <polyline
            key={s.estrategia}
            fill="none"
            stroke={COR_ESTRATEGIA[s.estrategia]}
            strokeWidth={2}
            strokeDasharray={TRACO[s.estrategia]}
            points={pts.map((v, m) => `${x(m).toFixed(1)},${y(v).toFixed(1)}`).join(" ")}
          />
        );
      })}
    </svg>
  );
}

function Simulador({ dividas }: { dividas: Divida[] }) {
  const agora = useAgora();
  const [extraTxt, setExtraTxt] = useState("0");
  const [ver, setVer] = useState<Estrategia>("AVALANCHE");
  const extra = parseValorBR(extraTxt);
  const extraOk = Number.isFinite(extra) && extra >= 0 ? extra : 0;
  const comp = useMemo(() => compararDasDividas(dividas, extraOk), [dividas, extraOk]);
  const inicial = dividas.map(paraSim).reduce((s, d) => s + (d?.saldo ?? 0), 0);
  const ref = { ano: new Date(agora).getFullYear(), mes: new Date(agora).getMonth() + 1 };
  const linhas: ResultadoSim[] = [comp.minimos, comp.bolaDeNeve, comp.avalanche];
  const escolhido = linhas.find((l) => l.estrategia === ver) ?? comp.avalanche;
  const fmtMes = (r: ResultadoSim) => {
    const d = dataQuitacao(ref, r.meses);
    return d ? formatMesAno(d.mes, d.ano) : "—";
  };
  const eco = comp.economia;

  return (
    <Card aria-labelledby="t-sim" className="mt-6">
      <CardTitulo id="t-sim">Simulador de quitação</CardTitulo>
      <p className="mb-3 text-sm text-muted">
        Mostra em quantos meses você quita tudo e quanto paga de juros. Juros compostos mensais, arredondados em centavos; o 1º pagamento é no mês atual. Nas estratégias, o mínimo de uma dívida quitada é repassado para as outras.
      </p>
      <div className="max-w-xs">
        <InputValor rotulo="Orçamento extra por mês (além dos mínimos)" value={extraTxt} onChange={(e) => setExtraTxt(e.target.value)} />
      </div>

      {comp.avisos.length > 0 && (
        <ul className="mt-3 space-y-1 text-sm text-warn" aria-label="Avisos do simulador">
          {comp.avisos.map((a) => (
            <li key={a}>⚠ {a}</li>
          ))}
        </ul>
      )}

      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[520px] text-sm">
          <caption className="sr-only">Comparativo das estratégias de quitação</caption>
          <thead>
            <tr className="text-left text-xs uppercase text-muted">
              <th className="py-1 pr-2">Estratégia</th>
              <th className="py-1 pr-2">Meses</th>
              <th className="py-1 pr-2">Quita em</th>
              <th className="py-1 pr-2 text-right">Juros pagos</th>
              <th className="py-1 text-right">Total pago</th>
            </tr>
          </thead>
          <tbody>
            {linhas.map((l) => (
              <tr key={l.estrategia} className="border-t border-line">
                <td className="py-2 pr-2">
                  <span className="mr-2 inline-block h-0.5 w-5 align-middle" style={{ background: COR_ESTRATEGIA[l.estrategia] }} aria-hidden />
                  {ROTULO_ESTRATEGIA[l.estrategia]}
                </td>
                <td className="py-2 pr-2">{l.nuncaQuita ? <Badge tom="neg">nunca quita</Badge> : l.meses}</td>
                <td className="py-2 pr-2">{fmtMes(l)}</td>
                <td className="py-2 pr-2 text-right tabular-nums">{l.nuncaQuita ? "—" : <Money valor={fromCents(l.jurosTotal)} />}</td>
                <td className="py-2 text-right tabular-nums">{l.nuncaQuita ? "—" : <Money valor={fromCents(l.pagoTotal)} />}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-3 space-y-0.5 text-sm">
        {eco.avalancheVsMinimos !== null && <p>Avalanche economiza <strong>{brlCentavos(eco.avalancheVsMinimos)}</strong> de juros em relação a pagar só os mínimos.</p>}
        {eco.bolaVsMinimos !== null && <p>Bola de neve economiza <strong>{brlCentavos(eco.bolaVsMinimos)}</strong> de juros em relação a pagar só os mínimos.</p>}
        {eco.avalancheVsBola !== null && <p>Avalanche vs. bola de neve: <strong>{brlCentavos(eco.avalancheVsBola)}</strong> de diferença nos juros.</p>}
      </div>

      <div className="mt-4">
        <GraficoSaldo inicial={inicial} series={linhas} />
        <p className="text-xs text-muted">Saldo devedor total mês a mês (linha cheia: bola de neve; tracejada longa: avalanche; pontilhada: só mínimos).</p>
      </div>

      <div className="mt-4">
        <Segmentado rotulo="Cronograma de" valor={ver} opcoes={[{ valor: "MINIMOS", rotulo: "Mínimos" }, { valor: "BOLA_DE_NEVE", rotulo: "Bola de neve" }, { valor: "AVALANCHE", rotulo: "Avalanche" }]} onChange={setVer} />
        {escolhido.quitacoes.length > 0 && (
          <p className="mt-2 text-sm text-muted">
            Ordem de quitação: {escolhido.quitacoes.map((q) => `${q.nome} (mês ${q.mes})`).join(" → ")}
          </p>
        )}
        <div className="mt-2 max-h-72 overflow-auto rounded-lg border border-line">
          <table className="w-full text-sm">
            <caption className="sr-only">Cronograma mês a mês ({ROTULO_ESTRATEGIA[escolhido.estrategia]})</caption>
            <thead className="sticky top-0 bg-surface-2 text-left text-xs uppercase text-muted">
              <tr>
                <th className="px-3 py-1">Mês</th>
                <th className="px-3 py-1 text-right">Juros</th>
                <th className="px-3 py-1 text-right">Pago</th>
                <th className="px-3 py-1 text-right">Saldo</th>
              </tr>
            </thead>
            <tbody>
              {escolhido.cronograma.slice(0, 120).map((c) => {
                const d = dataQuitacao(ref, c.mes);
                return (
                  <tr key={c.mes} className="border-t border-line">
                    <td className="px-3 py-1">{d ? formatMesAno(d.mes, d.ano) : c.mes}</td>
                    <td className="px-3 py-1 text-right tabular-nums"><Money valor={fromCents(c.juros)} /></td>
                    <td className="px-3 py-1 text-right tabular-nums"><Money valor={fromCents(c.pago)} /></td>
                    <td className="px-3 py-1 text-right tabular-nums"><Money valor={fromCents(c.saldoFinal)} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {escolhido.cronograma.length > 120 && <p className="mt-1 text-xs text-muted">Mostrando os primeiros 120 meses.</p>}
      </div>
    </Card>
  );
}

export default function DividasPage() {
  const ds = useDataset();
  const [form, setForm] = useState<{ editar: Divida | null } | null>(null);
  const [pagando, setPagando] = useState<Divida | null>(null);
  const [jurando, setJurando] = useState<Divida | null>(null);
  const [excluir, setExcluir] = useState<Divida | null>(null);
  const [vincular, setVincular] = useState<{ aoExistente: Divida | null } | null>(null);
  const avisar = useStore((x) => x.avisar);
  const [verQuitadas, setVerQuitadas] = useState(false);

  const ordenadas = useMemo(() => [...ds.dividas].sort((a, b) => Number(b.ativa) - Number(a.ativa) || a.nome.localeCompare(b.nome, "pt-BR")), [ds.dividas]);
  const ativas = ordenadas.filter((d) => d.ativa && d.saldoDevedor > 0);
  const quitadas = ordenadas.filter((d) => !(d.ativa && d.saldoDevedor > 0));
  const totalSaldo = ativas.reduce((s, d) => s + toCents(d.saldoDevedor), 0);
  const totalMinimos = ativas.reduce((s, d) => s + toCents(d.parcelaMinima ?? 0), 0);
  const jurosMes = ativas.reduce((s, d) => s + jurosDoMes(toCents(d.saldoDevedor), Math.round((d.taxaJurosMensal ?? 0) * 100)), 0);

  function cartao(d: Divida) {
    const pr = progressoQuitacao(d);
    const vinculada = (d.lancamentoIds?.length ?? 0) > 0;
    const abertos = vinculada ? ds.despesas.filter((x) => d.lancamentoIds?.includes(x.id) && !x.pago).sort((a, b) => a.data - b.data) : [];
    const sim = paraSim(d);
    return (
      <Card key={d.id} aria-label={`Dívida ${d.nome}`}>
        <CardTitulo
          acao={
            <span className="flex items-center gap-1">
              <IconButton rotulo={`Editar ${d.nome}`} onClick={() => setForm({ editar: d })}>
                <Pencil size={16} aria-hidden />
              </IconButton>
              <IconButton rotulo={`Excluir ${d.nome}`} onClick={() => setExcluir(d)}>
                <Trash2 size={16} aria-hidden />
              </IconButton>
            </span>
          }
        >
          <span className="inline-flex flex-wrap items-center gap-2">
            {d.nome}
            <Badge>{ROTULO_TIPO_DIVIDA[d.tipo]}</Badge>
            {!(d.ativa && d.saldoDevedor > 0) && <Badge tom="pos">Quitada</Badge>}
          </span>
        </CardTitulo>
        <p className="text-2xl font-semibold tracking-tight">
          <Money valor={d.saldoDevedor} />
          {d.valorOriginal != null && d.valorOriginal > 0 && <span className="ml-2 text-sm font-normal text-muted">de <Money valor={d.valorOriginal} /></span>}
        </p>
        <div className="mt-2">
          <ProgressBar razao={pr} tom="pos" rotulo={`Progresso de quitação de ${d.nome}`} />
          <p className="mt-1 text-xs text-muted">{Math.round(pr * 100)}% quitado</p>
        </div>
        <p className="mt-2 text-xs text-muted">
          {d.credor ? `${d.credor} · ` : ""}
          {d.taxaJurosMensal ? `${String(d.taxaJurosMensal).replace(".", ",")}% ao mês · ` : "sem juros informados · "}
          {d.parcelaMinima ? `parcela/mínimo ${formatBRL(d.parcelaMinima)}` : "sem parcela informada"}
          {d.parcelasRestantes != null ? ` · ${d.parcelasRestantes} parcela(s) restante(s)` : ""}
          {d.diaVencimento != null ? ` · vence dia ${d.diaVencimento}` : ""}
        </p>
        {vinculada && (
          <div className="mt-3 rounded-lg border border-line p-3">
            <p className="flex items-center gap-1 text-xs text-muted">
              <Link2 size={14} aria-hidden /> Vinculada a {d.lancamentoIds?.length} lançamento(s): o saldo abate quando você dá baixa na pendência.
            </p>
            <ul className="mt-2 divide-y divide-line text-sm">
              {abertos.slice(0, 6).map((x) => (
                <li key={x.id} className="flex items-center gap-2 py-1.5">
                  <span className="min-w-0 flex-1 truncate">{x.descricao} <span className="text-xs text-muted">· {formatData(x.data)}</span></span>
                  <Money valor={x.valor} className="text-sm" />
                  <Button
                    tamanho="sm"
                    icone={<CircleCheck size={14} aria-hidden />}
                    aria-label={`Dar baixa em ${x.descricao}`}
                    onClick={() => {
                      const r = acoes.darBaixa(x.id);
                      avisar(r.ok ? "sucesso" : "erro", r.ok ? `Baixa em "${x.descricao}".` : r.erro);
                    }}
                  >
                    Dar baixa
                  </Button>
                </li>
              ))}
            </ul>
            {abertos.length > 6 && <p className="mt-1 text-xs text-muted">+ {abertos.length - 6} pendência(s) (veja em Pendências).</p>}
            <div className="mt-2 flex flex-wrap gap-2">
              <Button tamanho="sm" icone={<Link2 size={14} aria-hidden />} onClick={() => setVincular({ aoExistente: d })}>
                Vincular mais pendências
              </Button>
              <Button tamanho="sm" icone={<Link2Off size={14} aria-hidden />} onClick={() => acoes.desvincularDivida(d.id)}>
                Desvincular
              </Button>
            </div>
          </div>
        )}
        {sim && !vinculada && (
          <div className="mt-3 flex flex-wrap gap-2">
            <Button variante="primary" tamanho="sm" onClick={() => setPagando(d)} disabled={ds.contas.length === 0}>
              Registrar pagamento
            </Button>
            <Button tamanho="sm" onClick={() => setJurando(d)}>
              Registrar juros do mês
            </Button>
            <Button tamanho="sm" icone={<Link2 size={14} aria-hidden />} onClick={() => setVincular({ aoExistente: d })}>
              Vincular a pendências
            </Button>
          </div>
        )}
      </Card>
    );
  }

  return (
    <>
      <PageHeader
        titulo="Dívidas"
        descricao="Acompanhe o saldo devedor, registre pagamentos e simule a melhor forma de quitar"
        acoes={
          <span className="flex flex-wrap gap-2">
            <Button icone={<Link2 size={16} aria-hidden />} onClick={() => setVincular({ aoExistente: null })}>
              Trazer de pendências
            </Button>
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setForm({ editar: null })}>
              Nova dívida
            </Button>
          </span>
        }
      />

      {ordenadas.length === 0 ? (
        <EmptyState
          titulo="Nenhuma dívida cadastrada"
          descricao="Cadastre empréstimos, financiamentos, cheque especial ou cartão rotativo para acompanhar a quitação e comparar estratégias."
          icone={<TrendingDown size={32} />}
        />
      ) : (
        <>
          <div className="mb-4 grid gap-3 sm:grid-cols-3">
            <Card>
              <p className="text-xs uppercase text-muted">Saldo devedor total</p>
              <p className="text-xl font-semibold"><Money valor={fromCents(totalSaldo)} /></p>
            </Card>
            <Card>
              <p className="text-xs uppercase text-muted">Parcelas / mínimos por mês</p>
              <p className="text-xl font-semibold"><Money valor={fromCents(totalMinimos)} /></p>
            </Card>
            <Card>
              <p className="text-xs uppercase text-muted">Juros estimados no próximo mês</p>
              <p className="text-xl font-semibold"><Money valor={fromCents(jurosMes)} /></p>
            </Card>
          </div>
          <div className="space-y-4">{ativas.map(cartao)}</div>
          {quitadas.length > 0 && (
            <div className="mt-4">
              <Button tamanho="sm" onClick={() => setVerQuitadas(!verQuitadas)} aria-expanded={verQuitadas}>
                {verQuitadas ? "Ocultar quitadas/inativas" : `Ver quitadas/inativas (${quitadas.length})`}
              </Button>
              {verQuitadas && <div className="mt-3 space-y-4">{quitadas.map(cartao)}</div>}
            </div>
          )}
          {ativas.length > 0 && <Simulador dividas={ds.dividas} />}
          <p className="mt-4 text-xs text-muted">
            O saldo devedor é um controle próprio: o pagamento é um lançamento de despesa comum e o saldo da conta já cai por ele. Excluir esse lançamento não devolve o saldo devedor
            (ajuste editando a dívida).
          </p>
        </>
      )}

      <Modal aberto={form !== null} onFechar={() => setForm(null)} titulo={form?.editar ? "Editar dívida" : "Nova dívida"}>
        {form && <DividaForm editar={form.editar} onFechar={() => setForm(null)} />}
      </Modal>
      <Modal aberto={vincular !== null} onFechar={() => setVincular(null)} titulo={vincular?.aoExistente ? `Vincular pendências: ${vincular.aoExistente.nome}` : "Dívida a partir de pendências"}>
        {vincular && <VincularForm aoExistente={vincular.aoExistente} onFechar={() => setVincular(null)} />}
      </Modal>
      <Modal aberto={pagando !== null} onFechar={() => setPagando(null)} titulo={`Pagamento: ${pagando?.nome ?? ""}`}>
        {pagando && <PagamentoForm divida={pagando} onFechar={() => setPagando(null)} />}
      </Modal>
      <Modal aberto={jurando !== null} onFechar={() => setJurando(null)} titulo={`Juros: ${jurando?.nome ?? ""}`}>
        {jurando && <JurosForm divida={jurando} onFechar={() => setJurando(null)} />}
      </Modal>
      <Confirmar
        aberto={excluir !== null}
        titulo="Excluir dívida"
        mensagem={excluir ? `Excluir “${excluir.nome}”? Os lançamentos de pagamento já feitos continuam no extrato.` : ""}
        rotuloConfirmar="Excluir"
        perigo
        onCancelar={() => setExcluir(null)}
        onConfirmar={() => {
          if (excluir) acoes.excluirDivida(excluir.id);
          setExcluir(null);
        }}
      />
    </>
  );
}
